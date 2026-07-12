#!/bin/sh
set -eu

destination="${TARGET_BUILD_DIR}/${UNLOCALIZED_RESOURCES_FOLDER_PATH}/GoogleService-Info.plist"

if [ "${BIRTHDAY_PRODUCTION_SMOKE:-NO}" = "YES" ]; then
  if [ "${CONFIGURATION:-}" != "Smoke" ] ||
    [ "${BIRTHDAY_FIREBASE_ENV:-}" != "production-path-smoke" ] ||
    [ "${PRODUCT_BUNDLE_IDENTIFIER:-}" != "com.yashsomani.birthdayautopilot.smoke" ] ||
    [ "${ACTION:-}" != "build" ] ||
    [ "${PLATFORM_NAME:-}" != "iphonesimulator" ] ||
    [ "${EFFECTIVE_PLATFORM_NAME:-}" != "-iphonesimulator" ] ||
    [ "${CODE_SIGNING_ALLOWED:-NO}" != "NO" ] ||
    [ "${CODE_SIGNING_REQUIRED:-NO}" != "NO" ] ||
    [ "${DEPLOYMENT_LOCATION:-NO}" = "YES" ]; then
    echo "error: production-path smoke is limited to an unsigned simulator build" >&2
    exit 1
  fi
  if [ -e "${SRCROOT}/Config/production-path-smoke/GoogleService-Info.plist" ]; then
    echo "error: production-path smoke must never have a Firebase configuration" >&2
    exit 1
  fi
  rm -f "$destination"
  exit 0
fi

if [ "${BIRTHDAY_E2E_FIXTURE:-NO}" = "YES" ]; then
  if [ "${CONFIGURATION:-}" != "E2E" ] ||
    [ "${BIRTHDAY_FIREBASE_ENV:-}" != "e2e-fixture" ] ||
    [ "${PRODUCT_BUNDLE_IDENTIFIER:-}" != "com.yashsomani.birthdayautopilot.e2e" ] ||
    [ "${ACTION:-}" != "build" ] ||
    [ "${PLATFORM_NAME:-}" != "iphonesimulator" ] ||
    [ "${EFFECTIVE_PLATFORM_NAME:-}" != "-iphonesimulator" ] ||
    [ "${CODE_SIGNING_ALLOWED:-NO}" != "NO" ] ||
    [ "${CODE_SIGNING_REQUIRED:-NO}" != "NO" ] ||
    [ "${DEPLOYMENT_LOCATION:-NO}" = "YES" ]; then
    echo "error: E2E fixture configuration is limited to an unsigned simulator build" >&2
    exit 1
  fi
  if [ -e "${SRCROOT}/Config/e2e-fixture/GoogleService-Info.plist" ]; then
    echo "error: the E2E fixture must never have a Firebase configuration" >&2
    exit 1
  fi
  rm -f "$destination"
  exit 0
fi

if [ "${CONFIGURATION:-}" = "E2E" ] || [ "${CONFIGURATION:-}" = "Smoke" ]; then
  echo "error: isolated simulator configuration is missing its compile-time marker" >&2
  exit 1
fi

case "${BIRTHDAY_FIREBASE_ENV:-}" in
  dev|staging|prod) ;;
  *) echo "error: invalid or missing BIRTHDAY_FIREBASE_ENV" >&2; exit 1 ;;
esac

case "$BIRTHDAY_FIREBASE_ENV" in
  dev) expected_bundle="com.yashsomani.birthdayautopilot.dev" ;;
  staging) expected_bundle="com.yashsomani.birthdayautopilot.staging" ;;
  prod) expected_bundle="com.yashsomani.birthdayautopilot" ;;
esac
if [ "${PRODUCT_BUNDLE_IDENTIFIER:-}" != "$expected_bundle" ]; then
  echo "error: iOS bundle ID does not match the selected Firebase tier" >&2
  exit 1
fi

config="${SRCROOT}/Config/${BIRTHDAY_FIREBASE_ENV}/GoogleService-Info.plist"

allow_missing_config="NO"
if [ "${BIRTHDAY_IOS_SIMULATOR_COMPILE_SMOKE:-NO}" = "YES" ]; then
  if [ "${ACTION:-}" = "build" ] &&
    [ "${PLATFORM_NAME:-}" = "iphonesimulator" ] &&
    [ "${SDK_NAME:-}" != "" ] &&
    [ "${SDK_NAME#iphonesimulator}" != "${SDK_NAME}" ] &&
    [ "${EFFECTIVE_PLATFORM_NAME:-}" = "-iphonesimulator" ] &&
    [ "${CODE_SIGNING_ALLOWED:-}" = "NO" ] &&
    [ "${CODE_SIGNING_REQUIRED:-}" = "NO" ] &&
    [ "${DEPLOYMENT_LOCATION:-NO}" != "YES" ]; then
    allow_missing_config="YES"
  else
    echo "error: missing Firebase config exception is limited to an unsigned simulator compile-smoke build" >&2
    exit 1
  fi
fi

if [ ! -f "$config" ]; then
  if [ "$allow_missing_config" = "YES" ]; then
    rm -f "$destination"
    exit 0
  fi
  echo "error: tier-specific GoogleService-Info.plist is required" >&2
  exit 1
fi

privacy_review_required="NO"
if [ "${CONFIGURATION:-}" = "Release" ] ||
  [ "${PLATFORM_NAME:-}" = "iphoneos" ] ||
  [ "${ACTION:-build}" != "build" ] ||
  [ "${DEPLOYMENT_LOCATION:-NO}" = "YES" ]; then
  privacy_review_required="YES"
fi
if [ "$privacy_review_required" = "YES" ] &&
  [ "${BIRTHDAY_PRIVACY_REVIEW_APPROVED:-NO}" != "YES" ]; then
  echo "error: reviewed iOS privacy/App Store declaration evidence is required" >&2
  exit 1
fi

# Schema 1 predates every recoverable decoder in this source tree. A device/archive build is
# therefore allowed only when an independently signed, source-bound release history proves that no
# schema-1 build was ever distributed. If such a build existed, release stays blocked until a
# separately reviewed decoder and fixture migration are implemented.
release_history_required="NO"
if [ "${CONFIGURATION:-}" = "Release" ]; then
  if [ "${PLATFORM_NAME:-}" = "iphoneos" ] ||
    [ "${ACTION:-build}" != "build" ] ||
    [ "${DEPLOYMENT_LOCATION:-NO}" = "YES" ]; then
    release_history_required="YES"
  fi
fi
if [ "$release_history_required" = "YES" ]; then
  history_file="${BIRTHDAY_IOS_RELEASE_HISTORY_FILE:-}"
  history_signature="${BIRTHDAY_IOS_RELEASE_HISTORY_SIGNATURE_FILE:-}"
  authority_key="${BIRTHDAY_DISTRIBUTION_AUTHORITY_PUBLIC_KEY_FILE:-}"
  if [ ! -f "$history_file" ] || [ ! -f "$history_signature" ] ||
    [ ! -f "$authority_key" ]; then
    echo "error: signed iOS protected-store release history is required" >&2
    exit 1
  fi
  source_revision="${BIRTHDAY_SOURCE_REVISION:-}"
  if [ "${#source_revision}" -ne 40 ]; then
    echo "error: the exact 40-character Git source revision is required" >&2
    exit 1
  fi
  case "$source_revision" in
    *[!0-9a-f]*)
      echo "error: the Git source revision must be lowercase hexadecimal" >&2
      exit 1
      ;;
  esac
  head_revision=$(git -C "${SRCROOT}/.." rev-parse --verify HEAD)
  if [ "$source_revision" != "$head_revision" ]; then
    echo "error: embedded source revision does not match the release checkout" >&2
    exit 1
  fi
  node_binary="${NODE_BINARY:-node}"
  "$node_binary" "${SRCROOT}/../tools/validate-ios-release-history.mjs" \
    --file "$history_file" \
    --signature "$history_signature" \
    --public-key "$authority_key" \
    --bundle "${PRODUCT_BUNDLE_IDENTIFIER}" \
    --version "${MARKETING_VERSION}" \
    --build "${CURRENT_PROJECT_VERSION}"
fi

bytes=$(stat -f '%z' "$config")
if [ "$bytes" -gt 65536 ]; then
  echo "error: GoogleService-Info.plist exceeds 64 KiB" >&2
  exit 1
fi
/usr/bin/plutil -lint "$config" >/dev/null

read_key() {
  /usr/libexec/PlistBuddy -c "Print :$1" "$config" 2>/dev/null || true
}

bundle_id=$(read_key BUNDLE_ID)
project_id=$(read_key PROJECT_ID)
client_id=$(read_key CLIENT_ID)
reversed_id=$(read_key REVERSED_CLIENT_ID)
api_key=$(read_key API_KEY)
app_id=$(read_key GOOGLE_APP_ID)
sender_id=$(read_key GCM_SENDER_ID)

if [ -z "$bundle_id" ] || [ -z "$project_id" ] || [ -z "$client_id" ] ||
  [ -z "$reversed_id" ] || [ -z "$api_key" ] || [ -z "$app_id" ] || [ -z "$sender_id" ]; then
  echo "error: GoogleService-Info.plist is missing required iOS fields" >&2
  exit 1
fi
if [ "$bundle_id" != "${PRODUCT_BUNDLE_IDENTIFIER}" ]; then
  echo "error: Firebase plist bundle ID does not match this target" >&2
  exit 1
fi
if [ -z "${BIRTHDAY_FIREBASE_PROJECT_ID:-}" ] ||
  [ "$project_id" != "${BIRTHDAY_FIREBASE_PROJECT_ID}" ]; then
  echo "error: Firebase plist project does not match the selected tier" >&2
  exit 1
fi
if [ -z "${BIRTHDAY_GOOGLE_REVERSED_CLIENT_ID:-}" ] ||
  [ "$reversed_id" != "${BIRTHDAY_GOOGLE_REVERSED_CLIENT_ID}" ]; then
  echo "error: Google reversed client ID does not match the selected tier" >&2
  exit 1
fi
case "$client_id" in
  *.apps.googleusercontent.com) ;;
  *) echo "error: invalid iOS OAuth client ID" >&2; exit 1 ;;
esac
expected_reversed=$(printf '%s' "$client_id" | awk -F. '{ for (i=NF; i>0; i--) printf "%s%s", $i, (i>1 ? "." : "") }')
if [ "$reversed_id" != "$expected_reversed" ]; then
  echo "error: reversed client ID is inconsistent with the iOS OAuth client" >&2
  exit 1
fi

mkdir -p "$(dirname "$destination")"
/usr/bin/ditto "$config" "$destination"
