#!/usr/bin/env bash
set -euo pipefail

assert_truly_unsigned_apk() {
  local candidate_apk=$1
  local pinned_apksigner=$2
  local signature_report

  if signature_report=$(LC_ALL=C "$pinned_apksigner" \
    verify --verbose --print-certs "$candidate_apk" 2>&1); then
    echo "FAIL unsigned dev Release artifact unexpectedly has a valid signature" >&2
    return 1
  fi

  if [[ "$signature_report" != $'DOES NOT VERIFY\nERROR: Missing META-INF/MANIFEST.MF' ]]; then
    echo "FAIL unsigned dev Release artifact has malformed or unverifiable signature metadata" >&2
    return 1
  fi

  local zip_entries
  if ! zip_entries=$(zipinfo -1 "$candidate_apk"); then
    echo "FAIL unable to inspect APK signing entries" >&2
    return 1
  fi
  if grep -Eiq \
    '^META-INF/(MANIFEST\.MF|[^/]+\.(SF|RSA|DSA|EC)|SIG-[^/]+)$' \
    <<<"$zip_entries"; then
    echo "FAIL unsigned dev Release artifact contains JAR signature metadata" >&2
    return 1
  fi
  if LC_ALL=C grep -aFq 'APK Sig Block 42' "$candidate_apk"; then
    echo "FAIL unsigned dev Release artifact contains an APK Signing Block" >&2
    return 1
  fi
}

main() {
if [[ $# -ne 2 && $# -ne 3 && $# -ne 8 && $# -ne 13 ]]; then
  echo "usage: $0 <apk> <expected-package> [--unsigned-dev-release | --restricted-evidence <json> <raw-signature> <authority-public-key> <supporting-evidence-root> <lab|prod> | --play-delivered-evidence <json> <raw-signature> <authority-public-key> <supporting-evidence-root> <lab|prod> <physical-device-serial> --report <output-json> --installed-apk-output-root <new-directory>]" >&2
  exit 64
fi

apk=$1
expected_package=$2
restricted_evidence=""
restricted_evidence_signature=""
distribution_authority_public_key=""
restricted_evidence_root=""
restricted_tier=""
unsigned_dev_release=false
play_delivered_apk=false
physical_device_serial=""
play_delivery_report=""
installed_apk_output_root=""
if [[ $# -eq 3 ]]; then
  if [[ $3 != "--unsigned-dev-release" ]]; then
    echo "FAIL unsigned artifact argument is invalid" >&2
    exit 64
  fi
  unsigned_dev_release=true
elif [[ $# -eq 8 ]]; then
  if [[ $3 != "--restricted-evidence" || ($8 != "lab" && $8 != "prod") ]]; then
    echo "FAIL restricted evidence arguments are invalid" >&2
    exit 64
  fi
  restricted_evidence=$4
  restricted_evidence_signature=$5
  distribution_authority_public_key=$6
  restricted_evidence_root=$7
  restricted_tier=$8
elif [[ $# -eq 13 ]]; then
  if [[ $3 != "--play-delivered-evidence" || ($8 != "lab" && $8 != "prod") || -z $9 || ${10} != "--report" || -z ${11} || ${12} != "--installed-apk-output-root" || -z ${13} ]]; then
    echo "FAIL Play-delivered evidence arguments are invalid" >&2
    exit 64
  fi
  restricted_evidence=$4
  restricted_evidence_signature=$5
  distribution_authority_public_key=$6
  restricted_evidence_root=$7
  restricted_tier=$8
  physical_device_serial=$9
  play_delivery_report=${11}
  installed_apk_output_root=${13}
  play_delivered_apk=true
fi
sdk_root=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
java_home=${JAVA_HOME:-}
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

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
# shellcheck source=android-ndk-host-tag.sh
source "$script_dir/android-ndk-host-tag.sh"
ndk_host_tag=$(resolve_android_ndk_host_tag)
readelf="$sdk_root/ndk/27.1.12297006/toolchains/llvm/prebuilt/$ndk_host_tag/bin/llvm-readelf"

for tool in "$aapt2" "$zipalign" "$apkanalyzer" "$apksigner" "$readelf"; do
  if [[ ! -x "$tool" ]]; then
    echo "FAIL required tool missing: $tool" >&2
    exit 1
  fi
done

badging=$("$aapt2" dump badging "$apk")
package_name=$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$badging")
version_code=$(sed -n "s/^package:.*versionCode='\([^']*\)'.*/\1/p" <<<"$badging")
version_name=$(sed -n "s/^package:.*versionName='\([^']*\)'.*/\1/p" <<<"$badging")
min_sdk=$(sed -n "s/^minSdkVersion:'\([^']*\)'.*/\1/p" <<<"$badging")
target_sdk=$(sed -n "s/^targetSdkVersion:'\([^']*\)'.*/\1/p" <<<"$badging")

[[ "$package_name" == "$expected_package" ]] || {
  echo "FAIL package $package_name, expected $expected_package" >&2
  exit 1
}
[[ "$min_sdk" == "29" ]] || { echo "FAIL min SDK $min_sdk" >&2; exit 1; }
[[ "$target_sdk" == "36" ]] || { echo "FAIL target SDK $target_sdk" >&2; exit 1; }
if [[ "$unsigned_dev_release" == true &&
  "$package_name" != "com.yashsomani.birthdayautopilot.dev" ]]; then
  echo "FAIL unsigned verification is allowed only for the fixed dev application ID" >&2
  exit 1
fi

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
if [[ "$unsigned_dev_release" == true ]]; then
  if [[ "$has_restricted_sms" == true ]]; then
    echo "FAIL an unsigned artifact can never contain SEND_SMS" >&2
    exit 1
  fi
  if grep -Eq 'android:debuggable="true"|android:usesCleartextTraffic="true"' <<<"$manifest_xml"; then
    echo "FAIL unsigned dev Release artifact is debuggable or permits cleartext traffic" >&2
    exit 1
  fi
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

signing_certificate=""
signing_certificate_sha1=""
if [[ "$unsigned_dev_release" == true ]]; then
  assert_truly_unsigned_apk "$apk" "$apksigner"
else
  signature_report=$("$apksigner" verify --verbose --print-certs "$apk")
  if ! grep -Eq '^Verified using v[23] scheme .*: true$' <<<"$signature_report"; then
    echo "FAIL APK is not verified with signature scheme v2 or v3" >&2
    exit 1
  fi
  signing_certificate=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<<"$signature_report" | tr 'A-F' 'a-f')
  if [[ ! "$signing_certificate" =~ ^[0-9a-fA-F]{64}$ ]]; then
    echo "FAIL APK signing certificate digest is unavailable" >&2
    exit 1
  fi
  if grep -Eq '^Signer #[2-9]' <<<"$signature_report"; then
    echo "FAIL APK contains more than one signer" >&2
    exit 1
  fi
  signing_certificate_sha1=$(sed -n 's/^Signer #1 certificate SHA-1 digest: //p' <<<"$signature_report" | tr 'A-F' 'a-f')
  if [[ "$play_delivered_apk" == true && ! "$signing_certificate_sha1" =~ ^[0-9a-f]{40}$ ]]; then
    echo "FAIL APK signing certificate SHA-1 digest is unavailable" >&2
    exit 1
  fi
fi
if [[ -n "$restricted_evidence" ]]; then
  if ! command -v node >/dev/null 2>&1; then
    echo "FAIL Node is required to validate authority-signed distribution evidence" >&2
    exit 1
  fi
  artifact_mode="direct-apk"
  if [[ "$play_delivered_apk" == true ]]; then
    artifact_mode="play-delivered-apk"
  fi
  validation_output=$(node "$script_dir/validate-distribution-evidence.mjs" \
    --file "$restricted_evidence" \
    --signature "$restricted_evidence_signature" \
    --public-key "$distribution_authority_public_key" \
    --evidence-root "$restricted_evidence_root" \
    --tier "$restricted_tier" \
    --package "$package_name" \
    --version-code "$version_code" \
    --version-name "$version_name" \
    --artifact-mode "$artifact_mode" \
    --artifact-signing-certificate "$signing_certificate" \
    --artifact-file "$apk" \
    --output json)
fi

tmp_dir=$(mktemp -d "${TMPDIR:-/tmp}/birthday-apk.XXXXXX")
trap 'rm -rf "$tmp_dir"' EXIT
apk_files_to_inspect=("$apk")

if [[ "$play_delivered_apk" == true ]]; then
  adb="$sdk_root/platform-tools/adb"
  if [[ ! -x "$adb" ]]; then
    echo "FAIL required tool missing: $adb" >&2
    exit 1
  fi
  if [[ $("$adb" -s "$physical_device_serial" get-state 2>/dev/null | tr -d '\r') != "device" ]]; then
    echo "FAIL the named Play verification device is not connected and authorized" >&2
    exit 1
  fi
  device_api=$("$adb" -s "$physical_device_serial" shell getprop ro.build.version.sdk | tr -d '\r[:space:]')
  maximum_certified_api=$(node -e 'const fs=require("node:fs"); const value=JSON.parse(fs.readFileSync(0,"utf8")); process.stdout.write(String(value.approval.maximumCertifiedApi));' <<<"$validation_output")
  if [[ ! "$device_api" =~ ^[0-9]+$ ]] || (( device_api < 29 || device_api > maximum_certified_api )); then
    echo "FAIL device API $device_api is outside the authority-certified range" >&2
    exit 1
  fi
  qemu=$("$adb" -s "$physical_device_serial" shell getprop ro.kernel.qemu | tr -d '\r[:space:]')
  hardware=$("$adb" -s "$physical_device_serial" shell getprop ro.hardware | tr -d '\r[:space:]')
  if [[ "$qemu" == "1" || "$hardware" == "ranchu" || "$hardware" == "goldfish" ]]; then
    echo "FAIL Play-delivered verification requires a physical Android device" >&2
    exit 1
  fi

  install_source=$("$adb" -s "$physical_device_serial" shell cmd package get-install-source "$package_name" 2>/dev/null | tr -d '\r')
  installer_package=$(sed -nE 's/.*(Installer package name|installingPackageName)[=:][[:space:]]*([^[:space:]]+).*/\2/p' <<<"$install_source" | sed -n '1p')
  if [[ "$installer_package" != "com.android.vending" ]]; then
    echo "FAIL installed package was not installed by com.android.vending" >&2
    exit 1
  fi

  installed_paths=()
  while IFS= read -r path; do
    [[ -n "$path" ]] && installed_paths+=("$path")
  done < <("$adb" -s "$physical_device_serial" shell pm path "$package_name" | tr -d '\r' | sed -n 's/^package://p')
  base_paths=()
  for installed_path in "${installed_paths[@]}"; do
    [[ "$installed_path" == */base.apk ]] && base_paths+=("$installed_path")
  done
  if [[ ${#installed_paths[@]} -eq 0 || ${#base_paths[@]} -ne 1 ]]; then
    echo "FAIL installed Play package paths are missing or ambiguous" >&2
    exit 1
  fi
  approved_apk_digest=$(node -e 'const fs=require("node:fs"); const value=JSON.parse(fs.readFileSync(0,"utf8")); process.stdout.write(value.approval.artifactApkSha256);' <<<"$validation_output")
  device_base_digest=$("$adb" -s "$physical_device_serial" shell toybox sha256sum "${base_paths[0]}" | tr -d '\r' | awk '{print $1}')
  if [[ "$device_base_digest" != "$approved_apk_digest" ]]; then
    echo "FAIL installed base APK bytes do not match the authority-approved delivered APK" >&2
    exit 1
  fi

  apk_files_to_inspect=()
  split_index=0
  for installed_path in "${installed_paths[@]}"; do
    installed_copy="$tmp_dir/device-$split_index.apk"
    "$adb" -s "$physical_device_serial" pull "$installed_path" "$installed_copy" >/dev/null
    apk_files_to_inspect+=("$installed_copy")
    ((split_index += 1))
  done
fi

native_archive_files=()
native_entries=()
load_segments=0

for inspected_apk in "${apk_files_to_inspect[@]}"; do
  "$zipalign" -c -P 16 4 "$inspected_apk" >/dev/null
  if [[ "$play_delivered_apk" == true ]]; then
    split_signature_report=$("$apksigner" verify --verbose --print-certs "$inspected_apk")
    split_certificate=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<<"$split_signature_report" | tr 'A-F' 'a-f')
    if [[ "$split_certificate" != "$signing_certificate" ]] || grep -Eq '^Signer #[2-9]' <<<"$split_signature_report"; then
      echo "FAIL a Play-delivered split APK has an unapproved signer" >&2
      exit 1
    fi
  fi
  while IFS= read -r entry; do
    native_archive_files+=("$inspected_apk")
    native_entries+=("$entry")
  done < <(zipinfo -1 "$inspected_apk" | grep '^lib/.*\.so$' || true)
done

if [[ ${#native_entries[@]} -eq 0 ]]; then
  echo "FAIL APK set contains no native libraries" >&2
  exit 1
fi

for index in "${!native_entries[@]}"; do
  entry=${native_entries[$index]}
  archive=${native_archive_files[$index]}
  if [[ "$entry" != lib/arm64-v8a/* ]]; then
    echo "FAIL non-arm64 library: $entry" >&2
    exit 1
  fi
  library="$tmp_dir/native-$index-$(basename "$entry")"
  unzip -p "$archive" "$entry" >"$library"
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

if [[ "$play_delivered_apk" == true ]]; then
  if [[ -e "$installed_apk_output_root" || -L "$installed_apk_output_root" ]]; then
    echo "FAIL installed APK output root must not already exist" >&2
    exit 1
  fi
  mkdir -m 700 -- "$installed_apk_output_root"
  inventory="$tmp_dir/installed-artifacts.ndjson"
  : >"$inventory"
  for index in "${!installed_paths[@]}"; do
    installed_path=${installed_paths[$index]}
    installed_copy=${apk_files_to_inspect[$index]}
    file_name=$(basename "$installed_path")
    retained_copy="$installed_apk_output_root/$file_name"
    if [[ -e "$retained_copy" || -L "$retained_copy" ]]; then
      echo "FAIL installed Play APK file names are not unique" >&2
      exit 1
    fi
    cp -- "$installed_copy" "$retained_copy"
    chmod 600 -- "$retained_copy"
    role=split
    [[ "$file_name" == base.apk ]] && role=base
    installed_digest=$(shasum -a 256 "$retained_copy" | awk '{print $1}')
    installed_bytes=$(wc -c <"$retained_copy" | tr -d '[:space:]')
    node --input-type=module - \
      "$role" "$installed_path" "$file_name" "$installed_bytes" \
      "$installed_digest" "$signing_certificate_sha1" "$signing_certificate" \
      >>"$inventory" <<'NODE'
const [role, packagePath, fileName, bytes, digest, sha1, sha256] = process.argv.slice(2);
process.stdout.write(`${JSON.stringify({
  role,
  packagePath,
  fileName,
  bytes: Number(bytes),
  sha256: digest,
  signingCertificateSha1: sha1,
  signingCertificateSha256: sha256,
})}\n`);
NODE
  done

  observation="$tmp_dir/play-device-observation.json"
  validation_file="$tmp_dir/validated-distribution-evidence.json"
  printf '%s\n' "$validation_output" >"$validation_file"
  node --input-type=module - \
    "$inventory" "$validation_file" "$observation" "$physical_device_serial" "$device_api" \
    "$installer_package" "$package_name" "$version_code" "$version_name" \
    "$signing_certificate_sha1" "$signing_certificate" \
    <<'NODE'
import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
const [inventoryPath, validationPath, outputPath, serial, deviceApi, installer, applicationId,
  versionCode, versionName, signingSha1, signingSha256] = process.argv.slice(2);
const validation = JSON.parse(readFileSync(validationPath, 'utf8'));
const installedArtifacts = readFileSync(inventoryPath, 'utf8')
  .trim()
  .split('\n')
  .filter(Boolean)
  .map(line => JSON.parse(line));
writeFileSync(outputPath, `${JSON.stringify({
  schemaVersion: 1,
  observedAt: new Date().toISOString(),
  physicalDevice: true,
  deviceSerialSha256: createHash('sha256').update(serial, 'utf8').digest('hex'),
  deviceApi: Number(deviceApi),
  installerOfRecord: installer,
  applicationId,
  versionCode: Number(versionCode),
  versionName,
  uploadAabSha256: validation.approval.artifactAabSha256,
  deliveredBaseApkSha256: validation.approval.artifactApkSha256,
  installedSigningCertificateSha1: signingSha1,
  installedSigningCertificateSha256: signingSha256,
  installedArtifacts,
})}\n`, { flag: 'wx', mode: 0o600 });
NODE
  node "$script_dir/create-play-delivered-verification-report.mjs" \
    --evidence "$restricted_evidence" \
    --signature "$restricted_evidence_signature" \
    --public-key "$distribution_authority_public_key" \
    --evidence-root "$restricted_evidence_root" \
    --tier "$restricted_tier" \
    --observation "$observation" \
    --output "$play_delivery_report"
fi

signature_state="verified"
if [[ "$unsigned_dev_release" == true ]]; then
  signature_state="unsigned-ci"
elif [[ "$play_delivered_apk" == true ]]; then
  signature_state="play-delivered-device"
fi
echo "PASS $package_name version=$version_code min=$min_sdk target=$target_sdk signature=$signature_state arm64-libs=${#native_entries[@]} load-segments=$load_segments"
}

if [[ ${BASH_SOURCE[0]} == "$0" ]]; then
  main "$@"
fi
