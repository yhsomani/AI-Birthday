@echo off
setlocal EnableExtensions EnableDelayedExpansion
title AI-Birthday - Build and Install Android APK

REM Run this script from the Flutter project's root directory.
cd /d "%~dp0"

echo ============================================================
echo   AI-Birthday - Flutter Android Build and Install
echo ============================================================
echo.

if not exist "pubspec.yaml" (
    echo [ERROR] pubspec.yaml was not found.
    echo Place this BAT file in the AI-Birthday Flutter project root.
    goto :fail
)

if not exist "android\gradlew.bat" (
    echo [ERROR] android\gradlew.bat was not found. This does not look like a Flutter Android project.
    goto :fail
)

where flutter >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Flutter was not found in PATH. Install Flutter or add flutter\bin to PATH.
    goto :fail
)

REM Keep a valid JAVA_HOME; repair it if it is missing or invalid.
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" goto :java_ready

set "ANDROID_STUDIO_JBR=%ProgramFiles%\Android\Android Studio\jbr"
if exist "%ANDROID_STUDIO_JBR%\bin\java.exe" (
    set "JAVA_HOME=%ANDROID_STUDIO_JBR%"
    goto :java_ready
)

REM Fallback: look for an installed JDK under Program Files\Java.
for /d %%J in ("%ProgramFiles%\Java\jdk*") do (
    if exist "%%~fJ\bin\java.exe" set "JAVA_HOME=%%~fJ"
)
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" goto :java_ready

echo [ERROR] No valid JDK was found.
echo Install a JDK or Android Studio, then rerun this script.
echo Example JAVA_HOME must point to the JDK root, not its bin folder.
goto :fail

:java_ready
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo [INFO] JAVA_HOME: %JAVA_HOME%
java -version
if errorlevel 1 (
    echo [ERROR] Java could not be started.
    goto :fail
)
echo.

REM Prefer the Android SDK's adb to avoid conflicts with other adb installations.
set "ADB="
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB (
    for /f "delims=" %%A in ('where adb 2^>nul') do if not defined ADB set "ADB=%%A"
)
if not defined ADB (
    echo [ERROR] adb was not found. Install Android SDK Platform-Tools or add adb to PATH.
    goto :fail
)
echo [INFO] Using ADB: %ADB%
echo.

set "FLAVOR=personal"
if /I "%~1"=="play" set "FLAVOR=play"
if /I "%~1"=="personal" set "FLAVOR=personal"
if /I "%~2"=="play" set "FLAVOR=play"
if /I "%~2"=="personal" set "FLAVOR=personal"

if /I "%~1"=="--clean" (
    echo [INFO] Clean build requested...
    call flutter clean
    if errorlevel 1 (
        echo [ERROR] flutter clean failed.
        goto :fail
    )
    echo.
)
if /I "%~2"=="--clean" (
    echo [INFO] Clean build requested...
    call flutter clean
    if errorlevel 1 (
        echo [ERROR] flutter clean failed.
        goto :fail
    )
    echo.
)

echo [1/4] Resolving Flutter dependencies...
call flutter pub get
if errorlevel 1 (
    echo [ERROR] flutter pub get failed.
    goto :fail
)
echo.

echo [2/4] Building Android debug APK (!FLAVOR! flavor)...
call flutter build apk --flavor !FLAVOR! --debug -t lib/main.dart
if errorlevel 1 (
    echo [ERROR] Flutter Android build failed. Review the error messages above.
    goto :fail
)
echo.

set "APK=%CD%\build\app\outputs\flutter-apk\app-!FLAVOR!-debug.apk"
if not exist "%APK%" (
    if exist "%CD%\build\app\outputs\flutter-apk\app-debug.apk" (
        set "APK=%CD%\build\app\outputs\flutter-apk\app-debug.apk"
    )
)
if not exist "%APK%" (
    echo [ERROR] Build command finished but the APK was not found at:
    echo %APK%
    goto :fail
)
echo [SUCCESS] APK created:
echo %APK%
echo.

echo [3/4] Detecting connected Android devices...
"%ADB%" start-server
"%ADB%" devices
set "DEVICE_COUNT=0"
set "DEVICE_SERIAL="
for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices') do (
    if "%%B"=="device" (
        set /a DEVICE_COUNT+=1
        if !DEVICE_COUNT! EQU 1 set "DEVICE_SERIAL=%%A"
    )
)

if !DEVICE_COUNT! EQU 0 (
    echo.
    echo [WARNING] APK built successfully, but no authorized Android device is connected.
    echo Connect your phone with USB debugging enabled, then run this script again.
    echo APK: %APK%
    goto :success_without_install
)

if !DEVICE_COUNT! GTR 1 (
    echo.
    echo Multiple devices are connected.
    set "DEVICE_SERIAL="
    set /p "DEVICE_SERIAL=Enter the exact device serial from the list above: "
    if not defined DEVICE_SERIAL (
        echo [ERROR] No device serial was entered.
        goto :fail
    )
)

echo.
echo [4/4] Installing APK on device !DEVICE_SERIAL!...
"%ADB%" -s "!DEVICE_SERIAL!" install -r "%APK%"
if errorlevel 1 (
    echo [ERROR] Installation failed. Check the device connection, authorization, and app-signature compatibility.
    goto :fail
)

echo.
echo ============================================================
echo [SUCCESS] Build and installation completed.
echo APK: %APK%
echo Device: !DEVICE_SERIAL!
echo ============================================================
echo.
pause
exit /b 0

:success_without_install
echo.
pause
exit /b 0

:fail
echo.
echo [FAILED] The script did not complete successfully.
echo Check the error messages above and fix the reported issue.
echo.
pause
exit /b 1
