#!/bin/sh
set -eu

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

required="${BIRTHDAY_FIREBASE_CONFIG_REQUIRED:-NO}"
config="${SRCROOT}/Config/${BIRTHDAY_FIREBASE_ENV}/GoogleService-Info.plist"
destination="${TARGET_BUILD_DIR}/${UNLOCALIZED_RESOURCES_FOLDER_PATH}/GoogleService-Info.plist"

if [ ! -f "$config" ]; then
  if [ "$required" = "YES" ]; then
    echo "error: tier-specific GoogleService-Info.plist is required" >&2
    exit 1
  fi
  rm -f "$destination"
  exit 0
fi

if [ "$required" = "YES" ] && [ "${BIRTHDAY_PRIVACY_REVIEW_APPROVED:-NO}" != "YES" ]; then
  echo "error: reviewed iOS privacy/App Store declaration evidence is required" >&2
  exit 1
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
