@echo off
setlocal EnableExtensions EnableDelayedExpansion
title AI-Birthday - Complete Build and Android Installer

REM ============================================================
REM AI-Birthday - AUTOMATED WEB BUILD + ANDROID APK INSTALL
REM Filename: build-and-install-android.bat
REM Save this file in the repository root.
REM IMPORTANT: Do not include Markdown code fences in this file.
REM ============================================================

cd /d "%~dp0"
set "PROJECT_DIR=%CD%"
set "ANDROID_DIR="
set "FRONTEND_DIR="
set "PACKAGE_ID="
set "DEVICE_ID="
set "GRADLE_FILE="
set "APK_PATH="
set "BUILD_PACKAGE="
set "BUILD_COUNT=0"

REM ============================================================
REM OPTIONAL MANUAL OVERRIDES
REM Leave blank to use automatic detection.
REM Example: set "FRONTEND_DIR=%PROJECT_DIR%\backend\hosting"
REM Example: set "PACKAGE_ID=com.example.aibirthday"
REM ============================================================

REM set "FRONTEND_DIR="
REM set "PACKAGE_ID="

echo.
echo ============================================================
echo          AI-BIRTHDAY - COMPLETE BUILD TOOL
echo ============================================================
echo Project: %PROJECT_DIR%
echo.

REM ------------------------------------------------------------
REM STEP 1: CHECK TOOLS
REM ------------------------------------------------------------

echo [1/9] Checking required tools...

where node >nul 2>&1
if errorlevel 1 (
    echo ERROR: Node.js is missing or not in PATH.
    goto :FAIL
)

where npm >nul 2>&1
if errorlevel 1 (
    echo ERROR: npm is missing or not in PATH.
    goto :FAIL
)

where adb >nul 2>&1
if errorlevel 1 (
    echo ERROR: adb is missing or not in PATH.
    echo Install Android SDK Platform-Tools and configure PATH.
    goto :FAIL
)

where powershell >nul 2>&1
if errorlevel 1 (
    echo ERROR: Windows PowerShell is required.
    goto :FAIL
)

echo Node.js:
node --version
echo npm:
call npm --version
echo.

REM ------------------------------------------------------------
REM STEP 2: DISCOVER ANDROID PROJECT
REM ------------------------------------------------------------

echo [2/9] Locating the Android Gradle project...

for /r "%PROJECT_DIR%" %%F in (gradlew.bat) do (
    if not defined ANDROID_DIR (
        set "CANDIDATE_DIR=%%~dpF"
        set "CANDIDATE_DIR=!CANDIDATE_DIR:~0,-1!"

        if exist "!CANDIDATE_DIR!\app\build.gradle" (
            set "ANDROID_DIR=!CANDIDATE_DIR!"
            set "GRADLE_FILE=!CANDIDATE_DIR!\app\build.gradle"
        ) else if exist "!CANDIDATE_DIR!\app\build.gradle.kts" (
            set "ANDROID_DIR=!CANDIDATE_DIR!"
            set "GRADLE_FILE=!CANDIDATE_DIR!\app\build.gradle.kts"
        )
    )
)

if not defined ANDROID_DIR (
    echo ERROR: Could not find an Android Gradle app project.
    echo Expected gradlew.bat and app\build.gradle or build.gradle.kts.
    goto :FAIL
)

echo Android project: !ANDROID_DIR!
echo Gradle config: !GRADLE_FILE!
echo.

REM ------------------------------------------------------------
REM STEP 3: FIND THE FRONTEND PACKAGE
REM ------------------------------------------------------------

echo [3/9] Detecting the web application...

if defined FRONTEND_DIR (
    if not exist "!FRONTEND_DIR!\package.json" (
        echo ERROR: Configured FRONTEND_DIR has no package.json:
        echo !FRONTEND_DIR!
        goto :FAIL
    )
    set "BUILD_PACKAGE=!FRONTEND_DIR!\package.json"
) else (
    REM Prefer backend\hosting when it has an npm build script.
    if exist "%PROJECT_DIR%\backend\hosting\package.json" (
        powershell -NoProfile -Command "$p=ConvertFrom-Json (Get-Content -Raw -LiteralPath '%PROJECT_DIR%\backend\hosting\package.json'); if ($p.scripts.build) { Write-Output YES }" > "%TEMP%\aibirthday_buildcheck.txt"

        findstr /X /C:"YES" "%TEMP%\aibirthday_buildcheck.txt" >nul 2>&1
        if not errorlevel 1 (
            set "BUILD_PACKAGE=%PROJECT_DIR%\backend\hosting\package.json"
        )
    )

    REM Otherwise search for packages with a build script,
    REM excluding dependency folders and backend/functions.
    if not defined BUILD_PACKAGE (
        for /r "%PROJECT_DIR%" %%F in (package.json) do (
            set "CHECK_PACKAGE=%%~fF"

            echo(!CHECK_PACKAGE!| findstr /I /C:"\node_modules\" /C:"\.git\" >nul
            if errorlevel 1 (
                echo(!CHECK_PACKAGE!| findstr /I /C:"\backend\functions\" >nul
                if errorlevel 1 (
                    powershell -NoProfile -Command "$p=ConvertFrom-Json (Get-Content -Raw -LiteralPath '%%~fF'); if ($p.scripts.build) { Write-Output YES }" > "%TEMP%\aibirthday_buildcheck.txt"

                    findstr /X /C:"YES" "%TEMP%\aibirthday_buildcheck.txt" >nul 2>&1
                    if not errorlevel 1 (
                        set /a BUILD_COUNT+=1
                        if not defined BUILD_PACKAGE (
                            set "BUILD_PACKAGE=%%~fF"
                        )
                    )
                )
            )
        )
    )
)

if not defined BUILD_PACKAGE (
    echo ERROR: No suitable frontend npm build script was detected.
    echo.
    echo Package files found in your repository previously included:
    echo backend\functions\package.json
    echo backend\hosting\package.json
    echo.
    echo Check whether the actual frontend is stored in another folder.
    echo If backend\hosting is your frontend, set FRONTEND_DIR manually
    echo near the top of this script.
    goto :FAIL
)

for %%F in ("!BUILD_PACKAGE!") do set "FRONTEND_DIR=%%~dpF"
if "!FRONTEND_DIR:~-1!"=="\" set "FRONTEND_DIR=!FRONTEND_DIR:~0,-1!"

echo Frontend directory: !FRONTEND_DIR!
echo Package file: !BUILD_PACKAGE!

if !BUILD_COUNT! GTR 1 (
    echo WARNING: Multiple build packages were found.
    echo Selected package: !BUILD_PACKAGE!
    echo Verify this is the actual frontend before relying on the APK.
)

echo.

REM ------------------------------------------------------------
REM STEP 4: VERIFY ANDROID DEVICE
REM ------------------------------------------------------------

echo [4/9] Checking Android device...

adb start-server
if errorlevel 1 (
    echo ERROR: Failed to start ADB.
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
    echo ERROR: No authorized Android device found.
    echo Connect your phone, enable USB debugging, and authorize it.
    adb devices
    goto :FAIL
)

if not "!DEVICE_COUNT!"=="1" (
    echo ERROR: Multiple authorized devices found.
    echo Disconnect extra devices and retry.
    adb devices
    goto :FAIL
)

echo Device: !DEVICE_ID!
echo.

REM ------------------------------------------------------------
REM STEP 5: CLEAN GENERATED OUTPUTS
REM ------------------------------------------------------------

echo [5/9] Cleaning generated files and npm cache...

call "!ANDROID_DIR!\gradlew.bat" --stop

if exist "%PROJECT_DIR%\node_modules" rmdir /s /q "%PROJECT_DIR%\node_modules"
if exist "%PROJECT_DIR%\dist" rmdir /s /q "%PROJECT_DIR%\dist"
if exist "%PROJECT_DIR%\build" rmdir /s /q "%PROJECT_DIR%\build"
if exist "%PROJECT_DIR%\.next" rmdir /s /q "%PROJECT_DIR%\.next"
if exist "%PROJECT_DIR%\.nuxt" rmdir /s /q "%PROJECT_DIR%\.nuxt"
if exist "%PROJECT_DIR%\.output" rmdir /s /q "%PROJECT_DIR%\.output"
if exist "%PROJECT_DIR%\.turbo" rmdir /s /q "%PROJECT_DIR%\.turbo"
if exist "%PROJECT_DIR%\coverage" rmdir /s /q "%PROJECT_DIR%\coverage"

if exist "!FRONTEND_DIR!\node_modules" rmdir /s /q "!FRONTEND_DIR!\node_modules"
if exist "!FRONTEND_DIR!\dist" rmdir /s /q "!FRONTEND_DIR!\dist"
if exist "!FRONTEND_DIR!\build" rmdir /s /q "!FRONTEND_DIR!\build"

if exist "!ANDROID_DIR!\app\build" rmdir /s /q "!ANDROID_DIR!\app\build"
if exist "!ANDROID_DIR!\build" rmdir /s /q "!ANDROID_DIR!\build"
if exist "!ANDROID_DIR!\.gradle" rmdir /s /q "!ANDROID_DIR!\.gradle"

call npm cache clean --force
if errorlevel 1 echo WARNING: npm cache cleanup returned an error.

echo Cleanup completed.
echo.

REM ------------------------------------------------------------
REM STEP 6: INSTALL FRONTEND DEPENDENCIES
REM ------------------------------------------------------------

echo [6/9] Installing frontend dependencies...

pushd "!FRONTEND_DIR!"

if exist "package-lock.json" (
    call npm ci
    if errorlevel 1 (
        popd
        echo ERROR: npm ci failed.
        goto :FAIL
    )
) else if exist "npm-shrinkwrap.json" (
    call npm ci
    if errorlevel 1 (
        popd
        echo ERROR: npm ci failed.
        goto :FAIL
    )
) else if exist "pnpm-lock.yaml" (
    popd
    echo ERROR: This frontend uses pnpm. This script will not replace
    echo the project's package manager automatically.
    goto :FAIL
) else if exist "yarn.lock" (
    popd
    echo ERROR: This frontend uses Yarn. This script will not replace
    echo the project's package manager automatically.
    goto :FAIL
) else (
    call npm install
    if errorlevel 1 (
        popd
        echo ERROR: npm install failed.
        goto :FAIL
    )
)

REM ------------------------------------------------------------
REM STEP 7: BUILD WEB APP AND SYNC CAPACITOR
REM ------------------------------------------------------------

echo.
echo Building frontend...

call npm run build
if errorlevel 1 (
    popd
    echo ERROR: Frontend build failed.
    goto :FAIL
)

popd

REM Sync only when a root-level Capacitor configuration exists.
if exist "%PROJECT_DIR%\capacitor.config.ts" (
    echo Syncing Capacitor from repository root...
    pushd "%PROJECT_DIR%"
    call npx cap sync android
    if errorlevel 1 (
        popd
        echo ERROR: Capacitor synchronization failed.
        goto :FAIL
    )
    popd
) else if exist "%PROJECT_DIR%\capacitor.config.json" (
    echo Syncing Capacitor from repository root...
    pushd "%PROJECT_DIR%"
    call npx cap sync android
    if errorlevel 1 (
        popd
        echo ERROR: Capacitor synchronization failed.
        goto :FAIL
    )
    popd
) else (
    echo No root Capacitor config detected.
    echo Continuing with the existing Android project configuration.
    echo Verify that the Android web assets are generated from the latest
    echo frontend build if this project uses Capacitor.
)

REM ------------------------------------------------------------
REM BUILD ANDROID APK
REM ------------------------------------------------------------

echo.
echo Building Android debug APK...

call "!ANDROID_DIR!\gradlew.bat" -p "!ANDROID_DIR!" clean
if errorlevel 1 (
    echo ERROR: Gradle clean failed.
    goto :FAIL
)

call "!ANDROID_DIR!\gradlew.bat" -p "!ANDROID_DIR!" assembleDebug
if errorlevel 1 (
    echo ERROR: assembleDebug failed.
    goto :FAIL
)

REM Prefer the standard debug APK location.
set "APK_PATH=!ANDROID_DIR!\app\build\outputs\apk\debug\app-debug.apk"

if not exist "!APK_PATH!" (
    for /r "!ANDROID_DIR!\app\build\outputs\apk" %%F in (*.apk) do (
        if not defined FOUND_APK set "FOUND_APK=%%~fF"
    )
    if defined FOUND_APK set "APK_PATH=!FOUND_APK!"
)

if not exist "!APK_PATH!" (
    echo ERROR: No APK was found after the Gradle build.
    goto :FAIL
)

echo.
echo APK ready: !APK_PATH!
echo.

REM ------------------------------------------------------------
REM STEP 8: DETECT APPLICATION ID
REM ------------------------------------------------------------

echo [8/9] Detecting application ID...

if not defined PACKAGE_ID (
    for /f "usebackq delims=" %%A in (`powershell -NoProfile -Command "$c=Get-Content -Raw -LiteralPath '!GRADLE_FILE!'; if ($c -match 'applicationId\s*(?:=|\s)\s*[''\"\"]([^''\"\"]+)[''\"\"]') { $Matches[1] }"`) do (
        set "PACKAGE_ID=%%A"
    )
)

if not defined PACKAGE_ID (
    echo Could not automatically detect the final application ID.
    set /p "PACKAGE_ID=Enter the app application ID from Gradle: "
)

if not defined PACKAGE_ID (
    echo ERROR: Application ID is required.
    goto :FAIL
)

echo Application ID: !PACKAGE_ID!
echo.

REM ------------------------------------------------------------
REM STEP 9: UNINSTALL, INSTALL, VERIFY, LAUNCH
REM ------------------------------------------------------------

echo [9/9] Installing APK...

echo Checking for existing app...
adb -s "!DEVICE_ID!" shell pm list packages | findstr /I /X /C:"package:!PACKAGE_ID!" >nul 2>&1

if not errorlevel 1 (
    echo Existing app detected.
    echo Uninstalling it. Existing local app data may be lost.
    adb -s "!DEVICE_ID!" uninstall "!PACKAGE_ID!"
    if errorlevel 1 (
        echo ERROR: Could not uninstall the existing application.
        goto :FAIL
    )
) else (
    echo Existing app not detected.
)

echo Installing new APK...
adb -s "!DEVICE_ID!" install "!APK_PATH!"
if errorlevel 1 (
    echo ERROR: APK installation failed.
    goto :FAIL
)

adb -s "!DEVICE_ID!" shell pm list packages | findstr /I /X /C:"package:!PACKAGE_ID!" >nul 2>&1
if errorlevel 1 (
    echo ERROR: Installation verification failed.
    goto :FAIL
)

echo Installation verified.
echo Launching app...

adb -s "!DEVICE_ID!" shell monkey -p "!PACKAGE_ID!" -c android.intent.category.LAUNCHER 1

if errorlevel 1 (
    echo WARNING: Automatic launch failed.
    echo Check the launcher activity and Android manifest.
    goto :FAIL
)

echo.
echo ============================================================
echo                   BUILD COMPLETED
echo ============================================================
echo Frontend:       Built successfully
echo Android APK:    !APK_PATH!
echo Application ID: !PACKAGE_ID!
echo Device:         !DEVICE_ID!
echo Install:        Verified
echo Launch:         Requested
echo.
echo IMPORTANT:
echo A successful build and launch request do not prove that every
echo screen, API, or interactive feature works correctly.
echo Test the app on your device.
echo ============================================================
echo.
if exist "%TEMP%\aibirthday_buildcheck.txt" del "%TEMP%\aibirthday_buildcheck.txt" >nul 2>&1
pause
exit /b 0

:FAIL
echo.
echo ============================================================
echo                       BUILD FAILED
echo ============================================================
echo Fix the reported error before retrying.
echo No successful installation is claimed.
echo ============================================================
if exist "%TEMP%\aibirthday_buildcheck.txt" del "%TEMP%\aibirthday_buildcheck.txt" >nul 2>&1
pause
exit /b 1
