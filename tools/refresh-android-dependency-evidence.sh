#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ "$(node --version)" != "v24.18.0" ]]; then
  printf '%s\n' 'Android dependency evidence requires Node v24.18.0.' >&2
  exit 1
fi
if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]]; then
  printf '%s\n' 'Set JAVA_HOME to the pinned JDK 21 installation.' >&2
  exit 1
fi
if [[ -z "${ANDROID_HOME:-}" || ! -d "$ANDROID_HOME" ]]; then
  printf '%s\n' 'Set ANDROID_HOME to the provisioned Android SDK.' >&2
  exit 1
fi

TASKS=(
  :app:testDevDebugUnitTest
  :app:testDevDebugOptimizedUnitTest
  :app:testE2eDebugUnitTest
  :app:testDevReleaseUnitTest
  :app:testStagingDebugUnitTest
  :app:testStagingDebugOptimizedUnitTest
  :app:testStagingReleaseUnitTest
  :app:testLabReleaseUnitTest
  :app:testProdReleaseUnitTest
  :app:compileDevDebugAndroidTestKotlin
  :app:compileE2eDebugAndroidTestKotlin
  :app:compileStagingDebugAndroidTestKotlin
  :app:assembleDevDebugAndroidTest
  :app:assembleE2eDebugAndroidTest
  :app:assembleStagingDebugAndroidTest
  :app:lintDevDebug
  :app:lintE2eDebug
  :app:lintSmokeDebug
  :app:lintDevRelease
  :app:lintStagingDebug
  :app:lintStagingRelease
  :app:lintLabRelease
  :app:lintProdRelease
  :app:assembleDevDebug
  :app:assembleE2eDebug
  :app:assembleSmokeDebug
  :app:assembleDevRelease
  :app:assembleStagingDebug
  :app:assembleStagingRelease
)

cd "$ROOT_DIR/android"
./gradlew \
  "${TASKS[@]}" \
  -PreactNativeArchitectures=arm64-v8a,x86_64 \
  --write-locks \
  --write-verification-metadata sha256 \
  --no-configuration-cache

# The orchestrator is installed beside the test APK and is not resolved by an
# assemble task. Pin its APK and service APK explicitly.
./gradlew \
  :app:dependencies \
  --configuration androidTestUtil \
  --write-locks \
  --write-verification-metadata sha256 \
  --no-configuration-cache

test -s app/gradle.lockfile
test -s buildscript-gradle.lockfile
test -s settings-gradle.lockfile
test -s gradle/verification-metadata.xml

printf '%s\n' 'Android dependency locks and SHA-256 verification metadata refreshed.'
