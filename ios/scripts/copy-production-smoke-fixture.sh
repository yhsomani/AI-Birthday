#!/bin/sh
set -eu

source_fixture="${SRCROOT}/../e2e/production-smoke/production-smoke-projections.json"
destination="${TARGET_BUILD_DIR}/${UNLOCALIZED_RESOURCES_FOLDER_PATH}/production-smoke-projections.json"

if [ "${CONFIGURATION:-}" != "Smoke" ]; then
  rm -f "$destination"
  exit 0
fi

if [ "${BIRTHDAY_PRODUCTION_SMOKE:-NO}" != "YES" ] ||
  [ "${PRODUCT_BUNDLE_IDENTIFIER:-}" != "com.yashsomani.birthdayautopilot.smoke" ] ||
  [ "${ACTION:-}" != "build" ] ||
  [ "${PLATFORM_NAME:-}" != "iphonesimulator" ] ||
  [ "${EFFECTIVE_PLATFORM_NAME:-}" != "-iphonesimulator" ] ||
  [ "${CODE_SIGNING_ALLOWED:-NO}" != "NO" ] ||
  [ "${CODE_SIGNING_REQUIRED:-NO}" != "NO" ] ||
  [ "${DEPLOYMENT_LOCATION:-NO}" = "YES" ]; then
  echo 'error: the production-path smoke fixture is authorized only for an unsigned simulator build' >&2
  exit 1
fi

if [ ! -f "$source_fixture" ] || [ -L "$source_fixture" ]; then
  echo 'error: the checked-in production-path smoke fixture is unavailable' >&2
  exit 1
fi
fixture_bytes=$(/usr/bin/stat -f '%z' "$source_fixture")
if [ "$fixture_bytes" -le 0 ] || [ "$fixture_bytes" -gt 262144 ]; then
  echo 'error: the production-path smoke fixture has an invalid size' >&2
  exit 1
fi

# Use the same pinned JavaScript runtime as React Native bundling and the
# repository validator rather than relying on plutil's OS-dependent JSON mode.
if [ -f "${SRCROOT}/.xcode.env" ]; then
  . "${SRCROOT}/.xcode.env"
fi
if [ -f "${SRCROOT}/.xcode.env.local" ]; then
  . "${SRCROOT}/.xcode.env.local"
fi
if [ -z "${NODE_BINARY:-}" ] || [ ! -x "$NODE_BINARY" ] ||
  [ "$("$NODE_BINARY" --version 2>/dev/null || true)" != 'v24.18.0' ]; then
  echo 'error: the pinned Node v24.18.0 executable is required' >&2
  exit 1
fi
"$NODE_BINARY" "${SRCROOT}/../tools/validate-production-smoke-fixture.mjs" "$source_fixture"
/bin/mkdir -p "$(/usr/bin/dirname "$destination")"
/usr/bin/ditto "$source_fixture" "$destination"
