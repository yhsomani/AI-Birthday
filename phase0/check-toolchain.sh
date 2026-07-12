#!/usr/bin/env bash

set -uo pipefail

EXPECTED_NODE="v24.18.0"
EXPECTED_NODE_PIN="24.18.0"
EXPECTED_NPM="11.6.0"
failures=0

pass() {
  printf 'PASS  %s\n' "$1"
}

warn() {
  printf 'WARN  %s\n' "$1"
}

fail() {
  printf 'FAIL  %s\n' "$1"
  failures=$((failures + 1))
}

property_value() {
  local file="$1"
  local key="$2"

  awk -F= -v key="$key" '$1 == key { print substr($0, index($0, "=") + 1); exit }' "$file" 2>/dev/null
}

if [[ -f .nvmrc ]]; then
  nvmrc_value="$(tr -d '[:space:]' < .nvmrc)"
  if [[ "$nvmrc_value" == "$EXPECTED_NODE_PIN" ]]; then
    pass ".nvmrc pins Node $EXPECTED_NODE_PIN"
  else
    fail ".nvmrc pins ${nvmrc_value:-nothing}; expected $EXPECTED_NODE_PIN"
  fi
else
  fail ".nvmrc is missing"
fi

if command -v node >/dev/null 2>&1; then
  actual_node="$(node -v 2>/dev/null || true)"
  if [[ "$actual_node" == "$EXPECTED_NODE" ]]; then
    pass "Node is $actual_node"
  else
    fail "Node is ${actual_node:-unreadable}; expected $EXPECTED_NODE (run: nvm use)"
  fi
else
  fail "Node is not on PATH"
fi

if command -v npm >/dev/null 2>&1; then
  actual_npm="$(npm -v 2>/dev/null || true)"
  if [[ "$actual_npm" == "$EXPECTED_NPM" ]]; then
    pass "npm is $actual_npm"
  else
    fail "npm is ${actual_npm:-unreadable}; expected $EXPECTED_NPM from Node 24.18.0"
  fi
else
  fail "npm is not on PATH"
fi

java_home_version=""
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  java_home_version="$("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"
  if [[ "$java_home_version" == *'version "21.'* ]]; then
    pass "JAVA_HOME supplies $java_home_version"
  else
    fail "JAVA_HOME must supply Java 21; found ${java_home_version:-unreadable}"
  fi
else
  fail "JAVA_HOME must point to an executable JDK 21 (recommended: /opt/homebrew/opt/openjdk@21)"
fi

if command -v java >/dev/null 2>&1; then
  path_java_version="$(java -version 2>&1 | head -n 1)"
  if [[ "$path_java_version" == *'version "21.'* ]]; then
    pass "PATH supplies $path_java_version"
  else
    fail "PATH must resolve Java 21; found ${path_java_version:-unreadable}"
  fi
  if [[ -n "$java_home_version" && "$path_java_version" != "$java_home_version" ]]; then
    fail "PATH java and JAVA_HOME/bin/java report different runtimes"
  fi
else
  fail "Java is not on PATH"
fi

sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
if [[ -d "$sdk_root" ]]; then
  pass "Android SDK exists at $sdk_root"
else
  fail "Android SDK not found at $sdk_root"
fi

if [[ -n "${ANDROID_HOME:-}" && -n "${ANDROID_SDK_ROOT:-}" && "$ANDROID_HOME" != "$ANDROID_SDK_ROOT" ]]; then
  fail "ANDROID_HOME and ANDROID_SDK_ROOT point to different locations"
elif [[ -z "${ANDROID_HOME:-}" || -z "${ANDROID_SDK_ROOT:-}" ]]; then
  warn "ANDROID_HOME and/or ANDROID_SDK_ROOT is unset; configure both for build and CI parity"
else
  pass "ANDROID_HOME and ANDROID_SDK_ROOT agree"
fi

adb_path="$sdk_root/platform-tools/adb"
if [[ -x "$adb_path" ]]; then
  adb_output="$($adb_path version 2>/dev/null || true)"
  adb_version="$(printf '%s\n' "$adb_output" | head -n 1)"
  adb_revision="$(printf '%s\n' "$adb_output" | awk '/^Version / { print $2; exit }')"
  if [[ "$adb_revision" == 37.* ]]; then
    pass "${adb_version:-adb is installed}; platform-tools $adb_revision"
  else
    fail "SDK adb platform-tools revision is ${adb_revision:-unreadable}; expected 37.x"
  fi
else
  fail "adb is missing from $sdk_root/platform-tools"
fi

if [[ -x "$sdk_root/cmdline-tools/latest/bin/sdkmanager" && -x "$sdk_root/cmdline-tools/latest/bin/avdmanager" ]]; then
  sdkmanager_output="$("$sdk_root/cmdline-tools/latest/bin/sdkmanager" --version 2>&1 || true)"
  sdkmanager_version="$(printf '%s\n' "$sdkmanager_output" | awk '/^[0-9]+([.][0-9]+)*$/ { print; exit }')"
  if [[ -n "$sdkmanager_version" ]]; then
    pass "Android command-line tools are installed; sdkmanager $sdkmanager_version"
  else
    fail "sdkmanager is executable but did not report a version"
  fi
  if [[ "$sdkmanager_output" == *"only understands SDK XML versions up to"* ]]; then
    warn "Command-line tools report an SDK XML schema compatibility warning; reconcile them with Android Studio before release"
  fi
else
  fail "Android command-line tools are missing (sdkmanager/avdmanager)"
fi

platform_complete=false
for platform_dir in "$sdk_root"/platforms/android-37*; do
  [[ -d "$platform_dir" ]] || continue
  platform_source="$platform_dir/source.properties"
  [[ -s "$platform_source" && -s "$platform_dir/android.jar" ]] || continue
  platform_api="$(property_value "$platform_source" "AndroidVersion.ApiLevel")"
  if [[ "$platform_api" == 37* ]]; then
    platform_complete=true
    break
  fi
done
if [[ "$platform_complete" == true ]]; then
  pass "Android 17/API 37 platform is complete"
else
  fail "Android 17/API 37 platform is missing or incomplete"
fi

build_tools_complete=false
for build_tools_dir in "$sdk_root"/build-tools/37*; do
  [[ -d "$build_tools_dir" ]] || continue
  build_tools_source="$build_tools_dir/source.properties"
  [[ -s "$build_tools_source" && -x "$build_tools_dir/aapt2" ]] || continue
  build_tools_revision="$(property_value "$build_tools_source" "Pkg.Revision")"
  if [[ "$build_tools_revision" == 37.* ]]; then
    build_tools_complete=true
    break
  fi
done
if [[ "$build_tools_complete" == true ]]; then
  pass "Android Build Tools $build_tools_revision is complete"
else
  fail "Android Build Tools 37.x is missing or incomplete"
fi

find_complete_image() {
  local api="$1"
  local image_dir
  local image_source
  local image_api
  local image_abi

  found_image_dir=""
  for image_dir in "$sdk_root"/system-images/android-"$api"*/*/arm64-v8a; do
    [[ -d "$image_dir" ]] || continue
    image_source="$image_dir/source.properties"
    [[ -s "$image_source" && -s "$image_dir/package.xml" && -s "$image_dir/system.img" ]] || continue
    image_api="$(property_value "$image_source" "AndroidVersion.ApiLevel")"
    image_abi="$(property_value "$image_source" "SystemImage.Abi")"
    if [[ "$image_api" == "$api"* && "$image_abi" == "arm64-v8a" ]]; then
      found_image_dir="$image_dir"
      return 0
    fi
  done
  return 1
}

for api in 29 30 31 32 33 34 35 36 37; do
  if find_complete_image "$api"; then
    pass "A registered, complete ARM64 API $api emulator system image is installed"
  else
    warn "No complete ARM64 API $api emulator system image; the release matrix still needs coverage"
  fi
done

check_avd_profile() {
  local name="$1"
  local expected_sysdir="$2"
  local config="${HOME:-}/.android/avd/$name.avd/config.ini"
  local actual_sysdir

  if [[ -s "$config" ]]; then
    actual_sysdir="$(property_value "$config" "image.sysdir.1")"
    if [[ "$actual_sysdir" == "$expected_sysdir" ]]; then
      pass "AVD $name points to $expected_sysdir"
    else
      warn "AVD $name points to ${actual_sysdir:-an unreadable image}; expected $expected_sysdir"
    fi
  else
    warn "AVD $name is missing; image presence alone is not boot evidence"
  fi
}

check_avd_profile "Birthday_API_29" "system-images/android-29/google_apis_playstore/arm64-v8a/"
check_avd_profile "Birthday_API_37_16K" "system-images/android-37.0/google_apis_playstore_ps16k/arm64-v8a/"

if [[ -x "./gradlew" ]]; then
  pass "Project Gradle wrapper exists"
else
  warn "No Gradle wrapper yet; expected until the approved scaffold is created"
fi

available_kib="$(df -Pk . | awk 'NR==2 {print $4}')"
if [[ "$available_kib" =~ ^[0-9]+$ ]] && (( available_kib < 80 * 1024 * 1024 )); then
  warn "Less than 80 GiB is free; multi-API images and build caches may need more space"
fi

if (( failures > 0 )); then
  printf '\nToolchain preflight failed with %d blocking issue(s).\n' "$failures"
  exit 1
fi

printf '\nCore toolchain preflight passed. Warnings remain Phase 0 evidence items.\n'
