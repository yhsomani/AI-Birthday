#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 && $# -ne 5 ]]; then
  echo "usage: $0 <apk> <expected-package> [--restricted-evidence <json> <lab|prod>]" >&2
  exit 64
fi

apk=$1
expected_package=$2
restricted_evidence=""
restricted_tier=""
if [[ $# -eq 5 ]]; then
  if [[ $3 != "--restricted-evidence" || ($5 != "lab" && $5 != "prod") ]]; then
    echo "FAIL restricted evidence arguments are invalid" >&2
    exit 64
  fi
  restricted_evidence=$4
  restricted_tier=$5
fi
sdk_root=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
java_home=${JAVA_HOME:-}

if [[ ! -f "$apk" ]]; then
  echo "FAIL APK not found: $apk" >&2
  exit 1
fi
if [[ -z "$sdk_root" || -z "$java_home" ]]; then
  echo "FAIL ANDROID_HOME and JAVA_HOME are required" >&2
  exit 1
fi

aapt2="$sdk_root/build-tools/36.0.0/aapt2"
zipalign="$sdk_root/build-tools/36.0.0/zipalign"
apkanalyzer="$sdk_root/cmdline-tools/latest/bin/apkanalyzer"
apksigner="$sdk_root/build-tools/36.0.0/apksigner"
readelf="$sdk_root/ndk/27.1.12297006/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-readelf"

for tool in "$aapt2" "$zipalign" "$apkanalyzer" "$apksigner" "$readelf"; do
  if [[ ! -x "$tool" ]]; then
    echo "FAIL required tool missing: $tool" >&2
    exit 1
  fi
done

badging=$("$aapt2" dump badging "$apk")
package_name=$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$badging")
version_code=$(sed -n "s/^package:.*versionCode='\([^']*\)'.*/\1/p" <<<"$badging")
min_sdk=$(sed -n "s/^minSdkVersion:'\([^']*\)'.*/\1/p" <<<"$badging")
target_sdk=$(sed -n "s/^targetSdkVersion:'\([^']*\)'.*/\1/p" <<<"$badging")

[[ "$package_name" == "$expected_package" ]] || {
  echo "FAIL package $package_name, expected $expected_package" >&2
  exit 1
}
[[ "$min_sdk" == "29" ]] || { echo "FAIL min SDK $min_sdk" >&2; exit 1; }
[[ "$target_sdk" == "36" ]] || { echo "FAIL target SDK $target_sdk" >&2; exit 1; }

permissions=$(JAVA_HOME="$java_home" "$apkanalyzer" manifest permissions "$apk")
manifest_xml=$(JAVA_HOME="$java_home" "$apkanalyzer" manifest print "$apk")
for forbidden in \
  android.permission.READ_CONTACTS \
  android.permission.WRITE_CONTACTS \
  android.permission.READ_SMS \
  android.permission.RECEIVE_SMS \
  android.permission.READ_CALL_LOG \
  android.permission.WRITE_CALL_LOG \
  android.permission.ACCESS_FINE_LOCATION \
  android.permission.ACCESS_COARSE_LOCATION \
  android.permission.CAMERA \
  android.permission.RECORD_AUDIO \
  android.permission.SCHEDULE_EXACT_ALARM \
  android.permission.USE_EXACT_ALARM; do
  if grep -Fxq "$forbidden" <<<"$permissions"; then
    echo "FAIL forbidden permission: $forbidden" >&2
    exit 1
  fi
done

has_restricted_sms=false
if grep -Fxq android.permission.SEND_SMS <<<"$permissions"; then
  has_restricted_sms=true
fi
if [[ "$has_restricted_sms" == true && -z "$restricted_evidence" ]]; then
  echo "FAIL SEND_SMS requires certificate-bound distribution evidence" >&2
  exit 1
fi
if [[ "$has_restricted_sms" == false && -n "$restricted_evidence" ]]; then
  echo "FAIL restricted artifact does not contain SEND_SMS" >&2
  exit 1
fi
if [[ "$has_restricted_sms" == true ]] &&
  ! grep -Fxq android.permission.READ_PHONE_STATE <<<"$permissions"; then
  echo "FAIL restricted artifact cannot verify the approved active SIM" >&2
  exit 1
fi
if [[ "$has_restricted_sms" == false ]] &&
  grep -Fxq android.permission.READ_PHONE_STATE <<<"$permissions"; then
  echo "FAIL non-restricted artifact unexpectedly contains READ_PHONE_STATE" >&2
  exit 1
fi

if [[ "$has_restricted_sms" == true ]]; then
  for release_forbidden in \
    android.permission.SYSTEM_ALERT_WINDOW \
    android.permission.REQUEST_INSTALL_PACKAGES \
    android.permission.QUERY_ALL_PACKAGES \
    android.permission.MANAGE_EXTERNAL_STORAGE \
    android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS; do
    if grep -Fxq "$release_forbidden" <<<"$permissions"; then
      echo "FAIL forbidden restricted-release permission: $release_forbidden" >&2
      exit 1
    fi
  done
  if grep -Eq 'android:debuggable="true"|android:usesCleartextTraffic="true"' <<<"$manifest_xml"; then
    echo "FAIL restricted artifact is debuggable or permits cleartext traffic" >&2
    exit 1
  fi
fi

signature_report=$("$apksigner" verify --verbose --print-certs "$apk")
if ! grep -Eq '^Verified using v[23] scheme .*: true$' <<<"$signature_report"; then
  echo "FAIL APK is not verified with signature scheme v2 or v3" >&2
  exit 1
fi
signing_certificate=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<<"$signature_report")
if [[ ! "$signing_certificate" =~ ^[0-9a-fA-F]{64}$ ]]; then
  echo "FAIL APK signing certificate digest is unavailable" >&2
  exit 1
fi
if grep -Eq '^Signer #[2-9]' <<<"$signature_report"; then
  echo "FAIL APK contains more than one signer" >&2
  exit 1
fi
if [[ -n "$restricted_evidence" ]]; then
  if ! command -v node >/dev/null 2>&1; then
    echo "FAIL Node is required to validate structured distribution evidence" >&2
    exit 1
  fi
  script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
  node "$script_dir/validate-distribution-evidence.mjs" \
    --file "$restricted_evidence" \
    --tier "$restricted_tier" \
    --package "$package_name" \
    --version-code "$version_code" \
    --certificate "$signing_certificate"
fi

"$zipalign" -c -P 16 4 "$apk" >/dev/null

native_entries=()
while IFS= read -r entry; do
  native_entries+=("$entry")
done < <(zipinfo -1 "$apk" | grep '^lib/.*\.so$' || true)
if [[ ${#native_entries[@]} -eq 0 ]]; then
  echo "FAIL APK contains no native libraries" >&2
  exit 1
fi

tmp_dir=$(mktemp -d "${TMPDIR:-/tmp}/birthday-apk.XXXXXX")
trap 'rm -rf "$tmp_dir"' EXIT
load_segments=0

for entry in "${native_entries[@]}"; do
  if [[ "$entry" != lib/arm64-v8a/* ]]; then
    echo "FAIL non-arm64 library: $entry" >&2
    exit 1
  fi
  library="$tmp_dir/$(basename "$entry")"
  unzip -p "$apk" "$entry" >"$library"
  if ! file "$library" | grep -q 'ELF 64-bit.*ARM aarch64'; then
    echo "FAIL invalid native binary: $entry" >&2
    exit 1
  fi
  while read -r alignment; do
    ((load_segments += 1))
    if (( alignment < 0x4000 )); then
      echo "FAIL LOAD alignment $alignment in $entry" >&2
      exit 1
    fi
  done < <("$readelf" -lW "$library" | awk '$1 == "LOAD" { print $NF }')
done

if (( load_segments == 0 )); then
  echo "FAIL no ELF LOAD segments found" >&2
  exit 1
fi

echo "PASS $package_name version=$version_code min=$min_sdk target=$target_sdk arm64-libs=${#native_entries[@]} load-segments=$load_segments"
