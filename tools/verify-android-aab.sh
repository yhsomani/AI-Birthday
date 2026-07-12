#!/usr/bin/env bash
set -euo pipefail

main() {
  if [[ $# -ne 7 || $3 != "--play-evidence" || ($7 != "lab" && $7 != "prod") ]]; then
    echo "usage: $0 <aab> <expected-package> --play-evidence <json> <raw-signature> <authority-public-key> <lab|prod>" >&2
    exit 64
  fi

  local aab=$1
  local expected_package=$2
  local evidence=$4
  local evidence_signature=$5
  local authority_public_key=$6
  local tier=$7
  local sdk_root=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
  local java_home=${JAVA_HOME:-}
  local script_dir
  script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

  if [[ ! -f "$aab" || ! -s "$aab" ]]; then
    echo "FAIL AAB not found or empty: $aab" >&2
    exit 1
  fi
  if [[ -z "$sdk_root" || -z "$java_home" ]]; then
    echo "FAIL ANDROID_HOME and JAVA_HOME are required" >&2
    exit 1
  fi

  local jarsigner="$java_home/bin/jarsigner"
  local keytool="$java_home/bin/keytool"
  # shellcheck source=android-ndk-host-tag.sh
  source "$script_dir/android-ndk-host-tag.sh"
  local ndk_host_tag
  ndk_host_tag=$(resolve_android_ndk_host_tag)
  local readelf="$sdk_root/ndk/27.1.12297006/toolchains/llvm/prebuilt/$ndk_host_tag/bin/llvm-readelf"
  for tool in "$jarsigner" "$keytool" "$readelf"; do
    if [[ ! -x "$tool" ]]; then
      echo "FAIL required tool missing: $tool" >&2
      exit 1
    fi
  done
  for command_name in node unzip zipinfo file; do
    if ! command -v "$command_name" >/dev/null 2>&1; then
      echo "FAIL required tool missing: $command_name" >&2
      exit 1
    fi
  done

  local entries
  if ! entries=$(zipinfo -1 "$aab"); then
    echo "FAIL AAB is not a readable ZIP archive" >&2
    exit 1
  fi
  if [[ -z "$entries" ]] ||
    grep -Eq '(^/|(^|/)\.\.(/|$)|\\)' <<<"$entries" ||
    [[ $(sort <<<"$entries" | uniq -d | wc -l | tr -d ' ') != 0 ]]; then
    echo "FAIL AAB contains an unsafe or duplicate ZIP entry" >&2
    exit 1
  fi
  for required_entry in \
    BundleConfig.pb \
    base/manifest/AndroidManifest.xml \
    base/resources.pb \
    base/dex/classes.dex; do
    if ! grep -Fxq "$required_entry" <<<"$entries"; then
      echo "FAIL AAB is missing $required_entry" >&2
      exit 1
    fi
  done

  local signature_report
  if ! signature_report=$(LC_ALL=C "$jarsigner" -verify -verbose -certs "$aab" 2>&1); then
    echo "FAIL AAB JAR signature does not verify" >&2
    exit 1
  fi
  if ! grep -Fxq 'jar verified.' <<<"$signature_report" ||
    grep -Eiq '(unsigned entries|jar is unsigned|does not verify)' <<<"$signature_report"; then
    echo "FAIL AAB contains unsigned or unverifiable content" >&2
    exit 1
  fi

  local certificate_report
  if ! certificate_report=$(LC_ALL=C "$keytool" -printcert -jarfile "$aab" 2>&1); then
    echo "FAIL AAB signing certificate is unavailable" >&2
    exit 1
  fi
  if [[ $(grep -Ec '^Signer #[0-9]+:$' <<<"$certificate_report") != 1 ]]; then
    echo "FAIL AAB must contain exactly one signer" >&2
    exit 1
  fi
  local signing_certificate
  signing_certificate=$(sed -n 's/^[[:space:]]*SHA256: //p' <<<"$certificate_report" | sed -n '1p' | tr -d ':[:space:]' | tr 'A-F' 'a-f')
  if [[ ! "$signing_certificate" =~ ^[0-9a-f]{64}$ ]]; then
    echo "FAIL AAB signing certificate digest is unavailable" >&2
    exit 1
  fi

  local manifest_summary
  manifest_summary=$(ANDROID_HOME="$sdk_root" ANDROID_SDK_ROOT="$sdk_root" \
    node "$script_dir/inspect-android-aab-manifest.mjs" \
      --aab "$aab" \
      --package "$expected_package" \
      --tier "$tier")
  local manifest_package version_code version_name minimum_sdk target_sdk extra_manifest_field
  IFS=$'\t' read -r \
    manifest_package \
    version_code \
    version_name \
    minimum_sdk \
    target_sdk \
    extra_manifest_field <<<"$manifest_summary"
  if [[ "$manifest_package" != "$expected_package" ||
    ! "$version_code" =~ ^[1-9][0-9]*$ ||
    -z "$version_name" ||
    "$minimum_sdk" != "29" ||
    "$target_sdk" != "36" ||
    -n "$extra_manifest_field" ]]; then
    echo "FAIL decoded AAB manifest summary is malformed" >&2
    exit 1
  fi

  tmp_dir=$(mktemp -d "${TMPDIR:-/tmp}/birthday-aab.XXXXXX")
  trap 'rm -rf "$tmp_dir"' EXIT
  local native_entries=()
  while IFS= read -r entry; do
    native_entries+=("$entry")
  done < <(grep '^base/lib/.*\.so$' <<<"$entries" || true)
  if [[ ${#native_entries[@]} -eq 0 ]]; then
    echo "FAIL AAB contains no native libraries" >&2
    exit 1
  fi
  local load_segments=0
  for entry in "${native_entries[@]}"; do
    if [[ "$entry" != base/lib/arm64-v8a/* ]]; then
      echo "FAIL AAB contains a non-arm64 native library: $entry" >&2
      exit 1
    fi
    local library="$tmp_dir/$(basename "$entry")"
    unzip -p "$aab" "$entry" >"$library"
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

  node "$script_dir/validate-distribution-evidence.mjs" \
    --file "$evidence" \
    --signature "$evidence_signature" \
    --public-key "$authority_public_key" \
    --tier "$tier" \
    --package "$manifest_package" \
    --version-code "$version_code" \
    --version-name "$version_name" \
    --artifact-mode play-aab \
    --artifact-signing-certificate "$signing_certificate" \
    --artifact-file "$aab"

  echo "PASS $manifest_package AAB version=$version_code min=$minimum_sdk target=$target_sdk upload-signature=verified arm64-libs=${#native_entries[@]} load-segments=$load_segments"
}

if [[ ${BASH_SOURCE[0]} == "$0" ]]; then
  main "$@"
fi
