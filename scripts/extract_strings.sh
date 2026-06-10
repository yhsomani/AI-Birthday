#!/usr/bin/env bash
set -euo pipefail

if [[ $# -gt 0 ]]; then
  ROOT="$1"
else
  ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
fi

rg -n 'Text\("' "$ROOT" \
  --glob '*.kt' \
  --glob '!**/build/**' \
  --glob '!**/strings.xml' \
  | rg -v 'stringResource' || {
    status=$?
    if [[ $status -eq 1 ]]; then
      exit 0
    fi
    exit "$status"
  }
