```bat
@echo off
setlocal EnableExtensions EnableDelayedExpansion
title AI-Birthday - Clean Build, APK Install and Launch

REM ============================================================
REM AI-Birthday - COMPLETE ANDROID BUILD SCRIPT
REM Save as: build-and-install-android.bat
REM Place this file in the repository root and run it.
REM ============================================================

cd /d "%~dp0"

set "PROJECT_DIR=%CD%"
set "ANDROID_DIR=%PROJECT_DIR%\android"
set "APK_PATH=%ANDROID_DIR%\app\build\outputs\apk\debug\app-debug.apk"
set "PACKAGE_ID="
set "DEVICE_ID="

echo.
echo ============================================================
echo                 AI-BIRTHDAY BUILD TOOL
echo ============================================================
echo Project: %PROJECT_DIR%
echo.

REM ------------------------------------------------------------
REM STEP 1: CHECK REQUIRED TOOLS AND PROJECT FILES
REM ------------------------------------------------------------

echo [1/10] Checking prerequisites...

if not exist "package.json" (
    echo ERROR: package.json not found.
    echo Place this BAT file in the repository root.
    goto :FAIL
)

where node >nul 2>&1
if errorlevel 1 (
    echo ERROR: Node.js is not installed or is not in PATH.
    goto :FAIL
)

where npm >nul 2>&1
if errorlevel 1 (
    echo ERROR: npm is not installed or is not in PATH.
    goto :FAIL
)

where adb >nul 2>&1
if errorlevel 1 (
    echo ERROR: Android platform-tools / adb is not in PATH.
    goto :FAIL
)

if not exist "%ANDROID_DIR%\gradlew.bat" (
    echo ERROR: Android Gradle project not found at:
    echo %ANDROID_DIR%
    echo This script expects an existing Android project.
    goto :FAIL
)

echo Node:
node --version
echo npm:
npm --version
echo.

REM ------------------------------------------------------------
REM STEP 2: VERIFY ANDROID DEVICE
REM ------------------------------------------------------------

echo [2/10] Checking connected Android device...

adb start-server
if errorlevel 1 (
    echo ERROR: Could not start the ADB server.
    goto :FAIL
)

set "DEVICE_COUNT=0"

for /f "skip=1 tokens=1,2" %%A in ('adb devices') do (
    if "%%B"=="device" (
        set /a DEVICE_COUNT+=1
        set "DEVICE_ID=%%A"
    )
)

if "!DEVICE_COUNT!"=="0" (
    echo ERROR: No authorized Android device detected.
    echo.
    echo Connect your phone with USB debugging enabled.
    echo Accept the USB debugging authorization prompt.
    echo Then run: adb devices
    goto :FAIL
)

if not "!DEVICE_COUNT!"=="1" (
    echo ERROR: Multiple authorized devices detected.
    echo Disconnect extra devices and run this script again.
    goto :FAIL
)

echo Connected device: !DEVICE_ID!
echo.

REM ------------------------------------------------------------
REM STEP 3: STOP RUNNING BUILD PROCESSES
REM ------------------------------------------------------------

echo [3/10] Stopping Gradle daemons...

call "%ANDROID_DIR%\gradlew.bat" --stop
if errorlevel 1 (
    echo WARNING: Gradle stop returned a non-zero exit code.
    echo Continuing with cleanup...
)

REM ------------------------------------------------------------
REM STEP 4: REMOVE GENERATED FILES AND CACHES
REM ------------------------------------------------------------

echo.
echo [4/10] Removing generated build files...

if exist "node_modules" (
    echo Removing node_modules...
    rmdir /s /q "node_modules"
)

if exist "dist" (
    echo Removing dist...
    rmdir /s /q "dist"
)

if exist "build" (
    echo Removing root build directory...
    rmdir /s /q "build"
)

if exist ".next" rmdir /s /q ".next"
if exist ".nuxt" rmdir /s /q ".nuxt"
if exist ".output" rmdir /s /q ".output"
if exist "out" rmdir /s /q "out"
if exist "coverage" rmdir /s /q "coverage"
if exist ".turbo" rmdir /s /q ".turbo"
if exist ".vite" rmdir /s /q ".vite"

if exist "%ANDROID_DIR%\app\build" (
    echo Removing Android app build directory...
    rmdir /s /q "%ANDROID_DIR%\app\build"
)

if exist "%ANDROID_DIR%\build" (
    echo Removing Android build directory...
    rmdir /s /q "%ANDROID_DIR%\build"
)

if exist "%ANDROID_DIR%\.gradle" (
    echo Removing project-local Gradle cache...
    rmdir /s /q "%ANDROID_DIR%\.gradle"
)

echo.
echo Clearing npm cache...
call npm cache clean --force
if errorlevel 1 (
    echo WARNING: npm cache cleanup reported an error.
)

echo Verifying npm cache...
call npm cache verify
if errorlevel 1 (
    echo WARNING: npm cache verification reported an error.
)

REM ------------------------------------------------------------
REM STEP 5: INSTALL DEPENDENCIES
REM ------------------------------------------------------------

echo.
echo [5/10] Installing project dependencies...

if exist "package-lock.json" (
    echo package-lock.json found. Running npm ci...
    call npm ci
    if errorlevel 1 (
        echo ERROR: npm ci failed.
        echo Check the dependency errors above.
        goto :FAIL
    )
) else if exist "npm-shrinkwrap.json" (
    echo npm-shrinkwrap.json found. Running npm ci...
    call npm ci
    if errorlevel 1 (
        echo ERROR: npm ci failed.
        goto :FAIL
    )
) else (
    if exist "pnpm-lock.yaml" (
        echo ERROR: pnpm-lock.yaml found but package-lock.json is missing.
        echo Use the project's intended package manager.
        goto :FAIL
    )

    if exist "yarn.lock" (
        echo ERROR: yarn.lock found but package-lock.json is missing.
        echo Use the project's intended package manager.
        goto :FAIL
    )

    echo No npm lockfile found. Running npm install...
    call npm install
    if errorlevel 1 (
        echo ERROR: npm install failed.
        goto :FAIL
    )
)

REM ------------------------------------------------------------
REM STEP 6: BUILD WEB APPLICATION
REM ------------------------------------------------------------

echo.
echo [6/10] Building the web application...

call npm run build
if errorlevel 1 (
    echo ERROR: Web build failed.
    echo Review the build output above.
    goto :FAIL
)

echo Web build completed successfully.

REM ------------------------------------------------------------
REM STEP 7: SYNC CAPACITOR WHEN CONFIGURATION EXISTS
REM ------------------------------------------------------------

echo.
echo [7/10] Checking Android web asset synchronization...

if exist "capacitor.config.ts" (
    echo Capacitor configuration found.
    call npx cap sync android
    if errorlevel 1 (
        echo ERROR: Capacitor Android synchronization failed.
        goto :FAIL
    )
) else if exist "capacitor.config.js" (
    echo Capacitor configuration found.
    call npx cap sync android
    if errorlevel 1 (
        echo ERROR: Capacitor Android synchronization failed.
        goto :FAIL
    )
) else if exist "capacitor.config.json" (
    echo Capacitor configuration found.
    call npx cap sync android
    if errorlevel 1 (
        echo ERROR: Capacitor Android synchronization failed.
        goto :FAIL
    )
) else (
    echo No root Capacitor configuration detected.
    echo Continuing with the existing Android Gradle project.
)

REM ------------------------------------------------------------
REM STEP 8: BUILD ANDROID DEBUG APK
REM ------------------------------------------------------------

echo.
echo [8/10] Building the Android debug APK...

call "%ANDROID_DIR%\gradlew.bat" -p "%ANDROID_DIR%" clean
if errorlevel 1 (
    echo ERROR: Gradle clean failed.
    goto :FAIL
)

call "%ANDROID_DIR%\gradlew.bat" -p "%ANDROID_DIR%" assembleDebug
if errorlevel 1 (
    echo ERROR: Android APK build failed.
    goto :FAIL
)

if not exist "%APK_PATH%" (
    echo ERROR: Expected APK was not found:
    echo %APK_PATH%
    echo.
    echo The project may use product flavors or a different APK output path.
    goto :FAIL
)

echo.
echo APK generated successfully:
echo %APK_PATH%
echo.

REM ------------------------------------------------------------
REM STEP 9: DETECT APPLICATION ID
REM ------------------------------------------------------------

echo [9/10] Detecting the Android application ID...

set "GRADLE_FILE=%ANDROID_DIR%\app\build.gradle"

if not exist "!GRADLE_FILE!" (
    set "GRADLE_FILE=%ANDROID_DIR%\app\build.gradle.kts"
)

if not exist "!GRADLE_FILE!" (
    echo ERROR: app build.gradle or build.gradle.kts not found.
    goto :FAIL
)

REM Extract the first literal applicationId from the app Gradle file.
REM This supports common Groovy and Kotlin DSL declarations.
for /f "usebackq delims=" %%A in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "$m = Select-String -Path '!GRADLE_FILE!' -Pattern '^\s*applicationId\s+[''']?([^'''\s]+)' | Select-Object -First 1; if ($m) { $v = $m.Matches[0].Groups[1].Value.Trim('""', ''''); if ($v -match '^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+$') { Write-Output $v } }"`) do (
    set "PACKAGE_ID=%%A"
)

if not defined PACKAGE_ID (
    echo ERROR: Could not detect a literal applicationId.
    echo Check this file:
    echo !GRADLE_FILE!
    echo.
    echo If applicationId is generated from a variable or product flavor,
    echo update this script to use the final application ID.
    goto :FAIL
)

echo Application ID: !PACKAGE_ID!
echo.

REM ------------------------------------------------------------
REM STEP 10: UNINSTALL OLD APP, INSTALL NEW APK, LAUNCH
REM ------------------------------------------------------------

echo [10/10] Installing the new APK on the connected device...

adb -s "!DEVICE_ID!" shell pm list packages | findstr /I /X /C:"package:!PACKAGE_ID!" >nul 2>&1

if not errorlevel 1 (
    echo Existing app found. Uninstalling !PACKAGE_ID! ...
    adb -s "!DEVICE_ID!" uninstall "!PACKAGE_ID!"
    if errorlevel 1 (
        echo ERROR: Failed to uninstall the existing app.
        echo Check device permissions and package ID.
        goto :FAIL
    )
) else (
    echo Existing app not found. Skipping uninstall.
)

echo.
echo Installing APK...
adb -s "!DEVICE_ID!" install "%APK_PATH%"
if errorlevel 1 (
    echo ERROR: APK installation failed.
    echo Check Android version compatibility, signing, and device storage.
    goto :FAIL
)

echo.
echo Verifying installation...
adb -s "!DEVICE_ID!" shell pm list packages | findstr /I /X /C:"package:!PACKAGE_ID!" >nul 2>&1

if errorlevel 1 (
    echo ERROR: Package verification failed after installation.
    goto :FAIL
)

echo Installation verified.

echo.
echo Launching the application...
adb -s "!DEVICE_ID!" shell monkey -p "!PACKAGE_ID!" -c android.intent.category.LAUNCHER 1

if errorlevel 1 (
    echo WARNING: Automatic launch failed.
    echo The app may not declare a launcher activity.
    echo Check the Android manifest and launch activity.
    goto :FAIL
)

echo.
echo ============================================================
echo                  BUILD SUCCESSFUL
echo ============================================================
echo Web build:       SUCCESS
echo Android APK:     SUCCESS
echo APK path:        %APK_PATH%
echo Application ID:  !PACKAGE_ID!
echo Device ID:       !DEVICE_ID!
echo Installation:    SUCCESS
echo Launch request:  SENT
echo ============================================================
echo.
echo Note: A successful launch request does not guarantee that
echo every screen or feature works correctly. Test the app on-device.
echo.
pause
exit /b 0

:FAIL
echo.
echo ============================================================
echo                     BUILD FAILED
echo ============================================================
echo Review the error messages above, fix the issue, and rerun.
echo.
pause
exit /b 1
```