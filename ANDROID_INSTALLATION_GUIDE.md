# Birthday Autopilot — Android Installation, Build & Deployment Guide

> **Target Audience**: Developers, Quality Assurance (QA) Engineers, Release Engineers, and Technical Support Engineers.  
> **Platform**: Android Only (React Native 0.86 + Android Kotlin Native Daemon)  
> **Minimum Supported Android Version**: Android 10.0 (API Level 29)  
> **Target Android Version**: Android 16 (API Level 36)

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Prerequisites & System Requirements](#2-prerequisites--system-requirements)
3. [Development Environment Setup](#3-development-environment-setup)
4. [Command-by-Command Reference](#4-command-by-command-reference)
5. [Build Process (Debug, Release, APK, AAB)](#5-build-process)
6. [Running the Application (Emulator & Physical Device)](#6-running-the-application)
7. [APK Installation Process (Manual & ADB)](#7-apk-installation-process)
8. [Updating the Application & Cache Management](#8-updating-the-application)
9. [Comprehensive Troubleshooting Guide](#9-comprehensive-troubleshooting-guide)
10. [End-to-End Validation Checklist](#10-end-to-end-validation-checklist)
11. [Frequently Asked Questions (FAQs)](#11-faqs)

---

## 1. Project Overview

### 1.1 Application Overview

**Birthday Autopilot** is an autonomous, unattended birthday SMS automation application designed exclusively for Android. It operates across two distinct execution tiers:

1. **Foreground User Interface (React Native / TypeScript)**: A graphical interface used for initial onboarding, contact enrollment, AI message customization, policy configuration, and privacy data management.
2. **Background Automation Engine (Android Kotlin Native)**: An autonomous background daemon scheduled via Android `WorkManager` (`BirthdayWorker.kt`). It interacts directly with Android's `SmsManager` and an encrypted Room SQLite database (`SQLCipher`). **It does not rely on the React Native JavaScript runtime being active to send messages.**

### 1.2 Supported Android Versions

- **Minimum SDK (`minSdkVersion`)**: `29` (Android 10.0)
- **Compile SDK (`compileSdkVersion`)**: `36` (Android 16)
- **Target SDK (`targetSdkVersion`)**: `36` (Android 16)
- **Architecture Support**: `arm64-v8a`, `armeabi-v7a`, `x86_64`

### 1.3 System Requirements

| Component            | Minimum Requirement                                                               | Recommended Specification                              |
| -------------------- | --------------------------------------------------------------------------------- | ------------------------------------------------------ |
| **Operating System** | macOS 13+ (Ventura/Sonoma/Sequoia), Linux (Ubuntu 22.04+), or Windows 11 (64-bit) | macOS with Apple Silicon (M1/M2/M3/M4) or Linux x86_64 |
| **RAM**              | 8 GB                                                                              | 16 GB or 32 GB                                         |
| **Storage**          | 15 GB free disk space                                                             | 30 GB free SSD storage                                 |
| **Processor**        | Intel Core i5 / AMD Ryzen 5                                                       | Multi-core Intel/AMD or Apple Silicon                  |

### 1.4 Required Tools & Software Stack

- **Node.js**: `v20.x` or `v24.x` (LTS)
- **Package Manager**: `npm v10.x` or `v11.x`
- **Java Development Kit (JDK)**: `JDK 17` (OpenJDK / Eclipse Temurin / Azul Zulu 17)
- **Android Studio**: Android Studio Ladybug (2024.2+) or Koala / Hedgehog
- **Android SDK Platform**: API Level `36`
- **Android SDK Build-Tools**: `36.0.0`
- **Android NDK**: Version `27.1.12297006`
- **Android Command-line Tools**: Latest
- **Android Emulator**: Hypervisor-accelerated AVD (x86_64 / arm64)

---

## 2. Prerequisites & System Requirements

### 2.1 Node.js & npm Installation

#### macOS (via Homebrew or nvm)

```bash
# Using NVM (Recommended)
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
source ~/.zshrc
nvm install 24
nvm use 24
nvm alias default 24
```

#### Linux (Ubuntu/Debian via NodeSource)

```bash
curl -fsSL https://deb.nodesource.com/setup_24.x | sudo -E bash -
sudo apt-get install -y nodejs
```

#### Windows (via winget or installer)

```powershell
winget install OpenJS.NodeJS.LTS
```

---

### 2.2 Java Development Kit (JDK 17) Installation

> [!IMPORTANT]
> This project strictly requires **Java 17**. Java 8, 11, or 21 will cause Gradle build failures due to AGP and Kotlin compiler compatibility requirements.

#### macOS

```bash
# Install Eclipse Temurin JDK 17 via Homebrew
brew install --cask temurin@17
```

#### Linux (Ubuntu/Debian)

```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk
```

#### Windows

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
```

---

### 2.3 Android Studio & Android SDK Setup

1. **Download & Install**: Download Android Studio from [developer.android.com/studio](https://developer.android.com/studio).
2. **Launch Setup Wizard**: Choose the **Standard** setup type.
3. **Open SDK Manager**:
   - In Android Studio, go to **Settings** (or **Preferences** on macOS) → **Languages & Frameworks** → **Android SDK**.
4. **SDK Platforms Tab**:
   - Check **Android 16 (VanillaIceCream / API Level 36)**.
   - Check **Android 14 (UpsideDownCake / API Level 34)** (optional for testing).
5. **SDK Tools Tab**:
   - Check **Android SDK Build-Tools 36.0.0**.
   - Check **NDK (Side by side)** → Expand and check version **`27.1.12297006`**.
   - Check **Android SDK Command-line Tools (latest)**.
   - Check **Android SDK Platform-Tools** (includes `adb`).
   - Check **Android Emulator**.
6. Click **Apply** and accept all licenses.

---

### 2.4 Environment Variable Configuration

Add the following export statements to your shell configuration file (`~/.zshrc`, `~/.bashrc`, or Windows Environment Variables).

#### macOS / Linux (`~/.zshrc` or `~/.bashrc`)

```bash
# Java 17 Home
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo "/usr/lib/jvm/java-17-openjdk-amd64")

# Android SDK Root
export ANDROID_HOME=$HOME/Library/Android/sdk   # macOS
# export ANDROID_HOME=$HOME/Android/Sdk         # Linux

# Add Android Platform Tools, Emulator, and Build Tools to PATH
export PATH=$PATH:$ANDROID_HOME/emulator
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/build-tools/36.0.0
```

Apply the changes:

```bash
source ~/.zshrc    # or source ~/.bashrc
```

#### Windows (PowerShell - Persistent User Environment)

```powershell
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-17.0.x-hotspot", "User")
[Environment]::SetEnvironmentVariable("ANDROID_HOME", "$env:LOCALAPPDATA\Android\Sdk", "User")
$currentPath = [Environment]::GetEnvironmentVariable("Path", "User")
$newPath = "$currentPath;$env:LOCALAPPDATA\Android\Sdk\platform-tools;$env:LOCALAPPDATA\Android\Sdk\emulator"
[Environment]::SetEnvironmentVariable("Path", $newPath, "User")
```

---

### 2.5 Verification of Prerequisites

Execute the following commands in your terminal to verify that all tools are correctly installed:

| Tool                 | Verification Command | Expected Output Example               |
| -------------------- | -------------------- | ------------------------------------- |
| **Node.js**          | `node -v`            | `v24.18.0` (or `v20.x.x`)             |
| **npm**              | `npm -v`             | `11.6.0` (or `10.x.x`)                |
| **Java Compiler**    | `javac -version`     | `javac 17.0.x`                        |
| **Java Runtime**     | `java -version`      | `openjdk version "17.0.x"`            |
| **Android ADB**      | `adb --version`      | `Android Debug Bridge version 1.0.41` |
| **Android Emulator** | `emulator -version`  | `Android emulator version 35.x.x`     |

---

## 3. Development Environment Setup

### 3.1 Clone the Repository

```bash
git clone https://github.com/your-org/AI-Birthday.git
cd AI-Birthday
```

### 3.2 Install Project Dependencies

Install all Node.js runtime and development dependencies:

```bash
npm install
```

_Note: The `postinstall` hook will automatically execute `node tools/patch-react-native-codegen.mjs` to configure React Native codegen bridges._

### 3.3 Verify Workspace Health (Doctor Utility)

The project includes a built-in diagnostics tool to verify your environment against pinned repository invariants:

```bash
npm run doctor:android
```

**Expected Output**:

```
PASS Android development environment is healthy.
```

---

## 4. Command-by-Command Reference

This table provides an exhaustive breakdown of every command used in development, testing, building, and deployment:

### 4.1 Dependency & Code Quality Commands

#### 1. `npm install`

- **Purpose**: Installs all required Node packages into `node_modules` according to `package-lock.json`.
- **Expected Output**: `added X packages, and audited Y packages in Zs`.
- **Common Errors**: `EACCES: permission denied` or network timeout.
- **Troubleshooting**: Run with standard user permissions (avoid `sudo`). Clear cache with `npm cache clean --force`.

#### 2. `npm run typecheck`

- **Purpose**: Executes TypeScript compiler (`tsc --noEmit`) to verify 100% type safety across all 88+ source files without producing build artifacts.
- **Expected Output**: Process exits with code `0` and no output.
- **Common Errors**: `TS2322: Type '...' is not assignable to type '...'`.
- **Troubleshooting**: Inspect the reported file and line number; ensure types match definitions in `src/domain/`.

#### 3. `npm run lint`

- **Purpose**: Runs ESLint across the codebase to catch static syntax, lifecycle, and rule violations.
- **Expected Output**: Clean exit with 0 errors.
- **Common Errors**: `unused-vars`, `react-hooks/exhaustive-deps`.
- **Troubleshooting**: Prefix intentionally unused variables with `_` (e.g. `_capability`).

#### 4. `npm run format:check` / `npm run format:write`

- **Purpose**: `format:check` verifies code adherence to Prettier formatting. `format:write` automatically formats all files.
- **Expected Output**: `All matched files use Prettier code style!`.

#### 5. `npm test` / `npm run test:ci`

- **Purpose**: Runs the full Jest test suite (31 test suites, 385 tests) covering all screens, hooks, and native adapters.
- **Expected Output**: `Test Suites: 31 passed, 31 total. Tests: 385 passed, 385 total.`

---

### 4.2 Android Build Commands

#### 6. `npm run android:build` (or `cd android && ./gradlew :app:assembleDevDebug`)

- **Purpose**: Compiles Kotlin/Java native code, runs KSP Room generators, processes Android resources, and packages the **Development Debug APK**.
- **Expected Output**: `BUILD SUCCESSFUL in Xs`. Generated APK located at `android/app/build/outputs/apk/dev/debug/app-dev-debug.apk`.
- **Common Errors**: `SDK location not found`, `Gradle connection refused`, `Room schema conflict`.
- **Troubleshooting**: Ensure `ANDROID_HOME` is set or create `android/local.properties` with `sdk.dir=/path/to/sdk`.

#### 7. `npm run android:test` (or `cd android && ./gradlew :app:testDevDebugUnitTest`)

- **Purpose**: Executes Android JVM unit tests for Kotlin domain policies, recurrence planning, and Room database migrations.
- **Expected Output**: `BUILD SUCCESSFUL`. Test reports output to `android/app/build/reports/tests/testDevDebugUnitTest/index.html`.

#### 8. `npm run android:verify`

- **Purpose**: Verifies that the compiled APK satisfies strict Android security invariants, package naming rules, and permission constraints.
- **Expected Output**: `PASS APK verification passed for com.yashsomani.birthdayautopilot.dev`.

#### 9. `npm run check:portable`

- **Purpose**: Master validation gate running linter, typecheck, codegen patches, secret scanner, React Native Jest suite, Firebase backend tests, and Hosting tests.
- **Expected Output**: All gates pass with exit code `0`.

---

## 5. Build Process

The project is configured with multiple **Product Flavors** and **Build Types**:

```
Product Flavors:
  ├── dev       ──► Application ID: com.yashsomani.birthdayautopilot.dev     (Developer build with hot-reload)
  ├── staging   ──► Application ID: com.yashsomani.birthdayautopilot.staging (Pre-production test environment)
  ├── lab       ──► Application ID: com.yashsomani.birthdayautopilot.lab     (Internal QA / test lab build)
  └── prod      ──► Application ID: com.yashsomani.birthdayautopilot         (Official production release)

Build Types:
  ├── debug     ──► Signed with default debug.keystore, cleartext traffic enabled for Metro bundler
  └── release   ──► Minified with R8/ProGuard, resource shrinking enabled, production signing
```

---

### 5.1 Generating Debug APK (Standard Developer Build)

To build the standard development APK:

```bash
# From project root:
npm run android:build

# Alternatively, directly via Gradle:
cd android
./gradlew :app:assembleDevDebug
cd ..
```

**Output Artifact Location**:

```
android/app/build/outputs/apk/dev/debug/app-dev-debug.apk
```

---

### 5.2 Generating Release APK / Production AAB

> [!NOTE]
> Production builds require release signing keys and Firebase project configurations placed in `android/app/src/prod/google-services.json`.

#### Step 1: Export Release Signing Environment Variables

```bash
export BIRTHDAY_UPLOAD_STORE_FILE="/path/to/release.keystore"
export BIRTHDAY_UPLOAD_STORE_PASSWORD="YourKeystorePassword"
export BIRTHDAY_UPLOAD_KEY_ALIAS="YourKeyAlias"
export BIRTHDAY_UPLOAD_KEY_PASSWORD="YourKeyPassword"
export BIRTHDAY_SIGNING_CERT_SHA256="aabbcc...64_character_hex_digest"
```

#### Step 2: Assemble Release APK

```bash
cd android
./gradlew :app:assembleProdRelease
cd ..
```

**Output Artifact Location**:

```
android/app/build/outputs/apk/prod/release/app-prod-release.apk
```

#### Step 3: Generate Android App Bundle (AAB for Google Play Store)

```bash
cd android
./gradlew :app:bundleProdRelease
cd ..
```

**Output Artifact Location**:

```
android/app/build/outputs/bundle/prodRelease/app-prod-release.aab
```

---

## 6. Running the Application

### 6.1 Running on an Android Emulator

#### Step 1: Create an Android Virtual Device (AVD)

1. Open **Android Studio** → **Tools** → **Device Manager**.
2. Click **Create Device**.
3. Select **Pixel 8** or **Pixel 7** hardware profile.
4. Select system image: **API Level 34** or **API Level 36 (x86_64)** with Google Play.
5. Click **Finish**.

#### Step 2: Start the Emulator

Launch from terminal or Device Manager:

```bash
# List available emulators
emulator -list-avds

# Start the emulator (replace Pixel_8_API_34 with your AVD name)
emulator -avd Pixel_8_API_34 -netdelay none -netspeed full
```

#### Step 3: Start the Metro Bundler

In a dedicated terminal window:

```bash
npm start
```

#### Step 4: Deploy and Launch App

In a second terminal window:

```bash
npm run android
```

_This command compiles the `devDebug` variant, installs it on the active emulator, configures port forwarding, and starts the activity._

---

### 6.2 Running on a Physical Android Device

#### Step 1: Enable Developer Options on Device

1. Open **Settings** on your Android device.
2. Scroll to **About Phone**.
3. Tap **Build Number** 7 times continuously until you see the prompt: _"You are now a developer!"_.

#### Step 2: Enable USB Debugging

1. Go back to **Settings** → **System** → **Developer Options**.
2. Toggle **USB Debugging** to **ON**.
3. (Optional but recommended) Toggle **Stay Awake** to ON while charging.

#### Step 3: Connect Device via USB

1. Connect phone to computer via a USB-C data cable.
2. When prompted on the phone screen with _"Allow USB debugging?"_, check _"Always allow from this computer"_ and tap **Allow**.

#### Step 4: Verify Device Detection via ADB

Run:

```bash
adb devices
```

**Expected Output**:

```
List of devices attached
19281FDE400129    device
```

_(If it displays `unauthorized`, unlock your phone and accept the RSA key fingerprint dialog)._

#### Step 5: Reverse Metro Bundler Port

Ensure your Android device can connect to the local Node.js Metro development server:

```bash
adb reverse tcp:8081 tcp:8081
```

#### Step 6: Deploy App

```bash
npm run android
```

---

## 7. APK Installation Process

### 7.1 Manual Installation via ADB (Fastest)

Ensure device is connected via `adb devices`, then execute:

```bash
# 1. Build the debug APK
npm run android:build

# 2. Install directly via ADB (-r replaces existing app while keeping data)
adb install -r android/app/build/outputs/apk/dev/debug/app-dev-debug.apk
```

**Expected Terminal Output**:

```
Performing Streamed Install
Success
```

---

### 7.2 Manual Installation via Device File Manager (Sideloading)

1. **Copy APK to Device**:
   ```bash
   adb push android/app/build/outputs/apk/dev/debug/app-dev-debug.apk /sdcard/Download/
   ```
2. **Open Files App**: Open the **Files** or **Downloads** app on your Android device.
3. **Locate APK**: Tap `app-dev-debug.apk`.
4. **Grant Unknown App Sources**: If prompted, tap **Settings** and toggle **Allow from this source**.
5. **Confirm Install**: Tap **Install**.

---

### 7.3 Launching and Validating the Application

#### Launch via ADB Command Line:

```bash
adb shell am start -n com.yashsomani.birthdayautopilot.dev/com.yashsomani.birthdayautopilot.MainActivity
```

#### Verify Package Registration:

```bash
adb shell pm list packages | grep birthdayautopilot
```

**Expected Output**:

```
package:com.yashsomani.birthdayautopilot.dev
```

#### Monitor Live Application Logs:

```bash
adb logcat -s BirthdayAutopilot:V ReactNativeJS:V AndroidRuntime:E
```

---

## 8. Updating the Application

### 8.1 When Modifying TypeScript / UI Code (`src/`)

- **Fast Refresh**: If Metro is running (`npm start`), saving changes in `.tsx` or `.ts` files instantly updates the running app on your device without rebuilding native code.
- **Force Reload**: Press `R` twice on your keyboard in the Metro terminal or press `Ctrl+M` (`Cmd+M` on macOS emulator) and tap **Reload**.

### 8.2 When Modifying Kotlin Native Code (`android/app/src/main/java/`)

Any change to Room database schemas, `AndroidManifest.xml`, native Kotlin classes, or Gradle dependencies requires a native recompilation:

```bash
# 1. Stop active Metro server
# 2. Re-assemble and install
npm run android:build
adb install -r android/app/build/outputs/apk/dev/debug/app-dev-debug.apk
# 3. Restart Metro
npm start
```

### 8.3 Cache Clearing Procedures

When experiencing unexpected build caching or bundler resolution anomalies:

```bash
# 1. Clear React Native / Metro Bundler Cache
npx react-native start --reset-cache

# 2. Clean Android Gradle Build Cache
cd android
./gradlew clean
rm -rf .gradle app/build
cd ..

# 3. Clear Application Data on Android Device
adb shell pm clear com.yashsomani.birthdayautopilot.dev
```

---

## 9. Comprehensive Troubleshooting Guide

### 9.1 Environment & Toolchain Issues

#### Issue: `JAVA_HOME is not set and no 'java' command could be found in your PATH`

- **Root Cause**: Shell configuration does not export `JAVA_HOME`.
- **Solution**:
  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 17)
  export PATH=$JAVA_HOME/bin:$PATH
  ```

#### Issue: `SDK location not found. Define location with an ANDROID_HOME environment variable`

- **Root Cause**: Gradle cannot locate Android SDK.
- **Solution**: Create a file named `android/local.properties` with:
  ```properties
  sdk.dir=/Users/<your-username>/Library/Android/sdk
  ```
  _(On Windows: `sdk.dir=C\:\\Users\\<your-username>\\AppData\\Local\\Android\\Sdk`)_

---

### 9.2 Android Build & Gradle Failures

#### Issue: `Room: Schema export directory is not provided to the annotation processor`

- **Root Cause**: Missing KSP room schema argument.
- **Solution**: Handled automatically in `android/app/build.gradle`. Ensure you run `./gradlew clean` and rebuild.

#### Issue: `Dependency resolution failed: strict dependency locking is active`

- **Root Cause**: Gradle dependency lockfile mismatch.
- **Solution**: Run Gradle with lockfile write flag (only when updating verified dependencies):
  ```bash
  cd android && ./gradlew build --write-locks && cd ..
  ```

#### Issue: `Cannot find NDK version 27.1.12297006`

- **Root Cause**: Specific NDK version not installed.
- **Solution**: Open Android Studio → SDK Manager → SDK Tools → Check "Show Package Details" → Under NDK (Side by side), check `27.1.12297006` and click Apply.

---

### 9.3 Device & Connectivity Issues

#### Issue: `adb: device unauthorized. This adb server's $ADB_VENDOR_KEYS is not set`

- **Root Cause**: RSA key not accepted on device.
- **Solution**: Unplug USB cable, reconnect, wake phone screen, and tap "Always allow from this computer". Alternatively, restart ADB server:
  ```bash
  adb kill-server
  adb start-server
  ```

#### Issue: `Unable to load script. Make sure your Metro bundler is running or you have bundled your assets.`

- **Root Cause**: App cannot communicate with Metro bundler on port 8081.
- **Solution**:
  1. Verify Metro is running (`npm start`).
  2. Run reverse port forwarding:
     ```bash
     adb reverse tcp:8081 tcp:8081
     ```

#### Issue: `INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package has different signature`

- **Root Cause**: Device has an existing APK signed with a different key (e.g. release vs debug).
- **Solution**: Uninstall existing app first:
  ```bash
  adb uninstall com.yashsomani.birthdayautopilot.dev
  adb install -r android/app/build/outputs/apk/dev/debug/app-dev-debug.apk
  ```

---

## 10. End-to-End Validation Checklist

Before declaring the deployment complete, verify each of the following criteria:

- [ ] **1. Toolchain Health**: `npm run doctor:android` exits with `PASS`.
- [ ] **2. Code Quality**: `npm run typecheck` and `npm run lint` report `0 errors`.
- [ ] **3. Test Suite**: `npm test` passes all 31 suites and 385 tests.
- [ ] **4. Build Artifact**: `android/app/build/outputs/apk/dev/debug/app-dev-debug.apk` is generated and non-empty.
- [ ] **5. ADB Detection**: `adb devices` shows attached emulator or device with status `device`.
- [ ] **6. Installation**: `adb install -r <apk>` completes with `Success`.
- [ ] **7. Application Launch**: App launches into the **10-Step Setup Wizard** (`LiveSetupScreen`).
- [ ] **8. Port Forwarding**: Metro logs incoming request: `BUNDLE ./index.js 100%`.
- [ ] **9. Runtime Permissions**: Granting `Contacts` and `SMS` permissions in setup wizard updates readiness indicators.
- [ ] **10. Foreground Loopback Test**: Step 7 test send dispatches and registers in the Activity log.

---

## 11. Frequently Asked Questions (FAQs)

#### Q1: Can I run this application on an iOS Simulator or iPhone?

**A**: No. Birthday Autopilot is an Android-only application. It utilizes native Android telephony components (`android.telephony.SmsManager`, `SubscriptionManager`) and Android `WorkManager` for background execution that do not exist on iOS.

#### Q2: Does the phone need to stay awake or unlocked to send birthday SMS?

**A**: No. The Kotlin `BirthdayWorker` runs in the native Android background tier via `WorkManager`. Once setup is complete and battery optimization exemption is granted (Step 9 of onboarding), messages send autonomously on schedule even when the phone is locked.

#### Q3: Why is Hermes required?

**A**: Hermes is the dedicated JavaScript engine optimized for React Native on Android. The project has pinned Hermes as a strict production invariant (`hermesEnabled = true` in Gradle) for instant TTI (Time to Interactive) and lower memory footprint.

#### Q4: How do I test the application without sending real SMS messages?

**A**: The application supports an `e2e` fixture build variant (`npm run e2e:android`) that uses local synthetic mocks and does not trigger cellular SMS transmissions or require Firebase credentials.

---

_Document Maintained by Birthday Autopilot Core Engineering Team._
