#!/bin/sh

set -eu

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
