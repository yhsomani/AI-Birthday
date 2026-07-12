#!/bin/sh

set -eu

normalized_entry_file=$(printf '%s' "${ENTRY_FILE:-index.js}" | sed 's#^\./##')
if [ "${CONFIGURATION:-}" = "E2E" ]; then
  if [ "${BIRTHDAY_E2E_FIXTURE:-NO}" != "YES" ] ||
    [ "${PLATFORM_NAME:-}" != "iphonesimulator" ] ||
    [ "${PRODUCT_BUNDLE_IDENTIFIER:-}" != "com.yashsomani.birthdayautopilot.e2e" ]; then
    echo 'error: the fixture JavaScript entry is simulator-E2E-only.' >&2
    exit 1
  fi
  ENTRY_FILE='e2e/index.js'
  export ENTRY_FILE
elif [ "${CONFIGURATION:-}" = "Smoke" ]; then
  if [ "${BIRTHDAY_PRODUCTION_SMOKE:-NO}" != "YES" ] ||
    [ "${PLATFORM_NAME:-}" != "iphonesimulator" ] ||
    [ "${PRODUCT_BUNDLE_IDENTIFIER:-}" != "com.yashsomani.birthdayautopilot.smoke" ] ||
    [ "$normalized_entry_file" != "index.js" ]; then
    echo 'error: the production-path smoke host must use the simulator-only production entry.' >&2
    exit 1
  fi
  ENTRY_FILE='index.js'
  export ENTRY_FILE
elif [ "$normalized_entry_file" = 'e2e/index.js' ]; then
  echo 'error: the fixture JavaScript entry cannot be selected outside E2E.' >&2
  exit 1
fi

expected_node_version='v24.18.0'

if [ -z "${NODE_BINARY:-}" ] || [ ! -x "${NODE_BINARY}" ]; then
  echo 'error: NODE_BINARY must point to the pinned Node executable.' >&2
  exit 1
fi

actual_node_version="$("${NODE_BINARY}" --version 2>/dev/null || true)"
if [ "${actual_node_version}" != "${expected_node_version}" ]; then
  echo "error: iOS bundling requires Node ${expected_node_version}; found ${actual_node_version:-missing}." >&2
  exit 1
fi

exec "${REACT_NATIVE_PATH}/scripts/react-native-xcode.sh"
