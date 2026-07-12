#!/usr/bin/env bash
set -euo pipefail

resolve_android_ndk_host_tag() {
  local os=${1:-$(uname -s)}
  local architecture=${2:-$(uname -m)}

  case "$os:$architecture" in
    Darwin:arm64|Darwin:x86_64)
      printf '%s\n' 'darwin-x86_64'
      ;;
    Linux:x86_64|Linux:amd64)
      printf '%s\n' 'linux-x86_64'
      ;;
    *)
      printf 'FAIL unsupported Android NDK verifier host: %s/%s\n' \
        "$os" "$architecture" >&2
      return 1
      ;;
  esac
}

if [[ ${BASH_SOURCE[0]} == "$0" ]]; then
  if [[ $# -gt 2 ]]; then
    printf 'usage: %s [os [architecture]]\n' "$0" >&2
    exit 64
  fi
  resolve_android_ndk_host_tag "$@"
fi
