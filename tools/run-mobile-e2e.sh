#!/bin/sh
set -eu

platform=${1:-}
suite=${2:-smoke}

case "$platform" in
  android) expected_app_id='com.yashsomani.birthdayautopilot.e2e' ;;
  *) echo 'usage: tools/run-mobile-e2e.sh android [smoke|large-text]' >&2; exit 64 ;;
esac


case "$suite" in
  smoke|large-text) ;;
  *) echo 'error: suite must be smoke or large-text' >&2; exit 64 ;;
esac

if [ "${E2E_APP_ID:-$expected_app_id}" != "$expected_app_id" ]; then
  echo 'error: mobile E2E is authorized only for the isolated fixture app ID' >&2
  exit 1
fi

tool_directory=${MOBILE_E2E_TOOL_DIR:-"${TMPDIR:-/tmp}/birthday-autopilot-maestro-2.6.1"}
if [ -n "${MAESTRO_BIN:-}" ]; then
  echo 'error: MAESTRO_BIN overrides are forbidden; use the checksum-verified installer' >&2
  exit 1
fi
maestro_binary=$("$(dirname "$0")/install-maestro.sh" "$tool_directory")
if [ ! -x "$maestro_binary" ]; then
  echo 'error: verified Maestro executable is unavailable' >&2
  exit 1
fi
runner_state_home="${TMPDIR:-/tmp}/birthday-autopilot-maestro-runner-state"
mkdir -p "$runner_state_home/.maestro"

maestro_version=$(
  HOME="$runner_state_home" MAESTRO_OPTS="-Duser.home=$runner_state_home" \
    MAESTRO_CLI_NO_ANALYTICS=true MAESTRO_DISABLE_UPDATE_CHECK=true \
    MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    "$maestro_binary" --version 2>/dev/null |
    sed -n 's/[^0-9]*\([0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*\).*/\1/p' |
    sed -n '1p'
)
if [ "$maestro_version" != '2.6.1' ]; then
  echo 'error: Maestro executable is not the reviewed version' >&2
  exit 1
fi

report_root=${E2E_REPORT_ROOT:-"${TMPDIR:-/tmp}/birthday-mobile-e2e/${platform}/${suite}"}
mkdir -p "$report_root/artifacts" "$report_root/maestro-home"

export E2E_APP_ID="$expected_app_id"
export E2E_PLATFORM="$platform"
export MAESTRO_CLI_NO_ANALYTICS=true
export MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true
export MAESTRO_DISABLE_UPDATE_CHECK=true
export CI=${CI:-true}

mkdir -p "$report_root/maestro-home/.maestro"
exec env \
  HOME="$report_root/maestro-home" \
  MAESTRO_OPTS="-Duser.home=$report_root/maestro-home" \
  "$maestro_binary" test \
  --no-ansi \
  --format junit \
  --env "E2E_APP_ID=$expected_app_id" \
  --include-tags "$suite" \
  --output "$report_root/report.xml" \
  --test-output-dir "$report_root/artifacts" \
  --debug-output "$report_root/debug" \
  e2e/maestro
