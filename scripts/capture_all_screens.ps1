# capture_all_screens.ps1
# Automates taking screenshots of all key screens and tabs in the RelateAI app.

$adb = "C:\Users\yhsom\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$artifactsDir = "C:\Users\yhsom\.gemini\antigravity-ide\brain\4852370c-153a-43f2-98d8-7d01ea8bc710"
$browserDir = "$artifactsDir\browser"

# Ensure output directories exist
if (-not (Test-Path $browserDir)) {
    New-Item -ItemType Directory -Path $browserDir -Force
}

# Files to modify and backup
$navGraphPath = "C:\Users\yhsom\OneDrive\Documents\AI-Birthday\app\src\main\java\com\example\navigation\RelateAINavGraph.kt"
$mainAppPath = "C:\Users\yhsom\OneDrive\Documents\AI-Birthday\app\src\main\java\com\example\navigation\MainAppScreen.kt"
$splashPath = "C:\Users\yhsom\OneDrive\Documents\AI-Birthday\feature\splash\src\main\kotlin\com\example\feature\splash\SplashScreen.kt"

# Create backups
Copy-Item $navGraphPath "$navGraphPath.bak" -Force
Copy-Item $mainAppPath "$mainAppPath.bak" -Force
Copy-Item $splashPath "$splashPath.bak" -Force

function Restore-Backups {
    Write-Host "Restoring backups..."
    Copy-Item "$navGraphPath.bak" $navGraphPath -Force
    Copy-Item "$mainAppPath.bak" $mainAppPath -Force
    Copy-Item "$splashPath.bak" $splashPath -Force
    Remove-Item "$navGraphPath.bak" -ErrorAction SilentlyContinue
    Remove-Item "$mainAppPath.bak" -ErrorAction SilentlyContinue
    Remove-Item "$splashPath.bak" -ErrorAction SilentlyContinue
}

# Register a cleanup handler
trap {
    Restore-Backups
    exit
}

function Build-And-Capture($screenName) {
    Write-Host "Building and deploying for screen: $screenName..."
    
    # Run gradle build
    $buildCmd = & ./gradlew.bat installDebug
    
    # Start app
    Write-Host "Launching app..."
    & $adb shell am force-stop com.aistudio.relateai.qxtjrk
    & $adb shell am start -n com.aistudio.relateai.qxtjrk/com.example.MainActivity
    
    # Wait for app to render
    Start-Sleep -Seconds 5
    
    # Capture screen
    Write-Host "Capturing screenshot..."
    & $adb shell screencap -p /sdcard/screen.png
    & $adb pull /sdcard/screen.png "$artifactsDir\screenshot_$screenName.png"
    Copy-Item "$artifactsDir\screenshot_$screenName.png" "$browserDir\screenshot_$screenName.png" -Force
    Write-Host "Captured screenshot_$screenName.png."
}

try {
    # 1. SPLASH SCREEN
    Write-Host "=== SPLASH SCREEN ==="
    (Get-Content $navGraphPath) -replace 'startDestination = AppRoutes.MAIN', 'startDestination = AppRoutes.SPLASH' | Set-Content $navGraphPath
    (Get-Content $splashPath) -replace 'delay\(3000\)', 'delay(300000)' | Set-Content $splashPath
    Build-And-Capture "splash"

    # Restore backups for next steps
    Restore-Backups
    Copy-Item $navGraphPath "$navGraphPath.bak" -Force
    Copy-Item $mainAppPath "$navGraphPath.bak" -Force
    Copy-Item $splashPath "$splashPath.bak" -Force

    # 2. LOGIN SCREEN
    Write-Host "=== LOGIN SCREEN ==="
    (Get-Content $navGraphPath) -replace 'startDestination = AppRoutes.MAIN', 'startDestination = AppRoutes.LOGIN' | Set-Content $navGraphPath
    Build-And-Capture "login"

    # Restore backups for next steps
    Restore-Backups
    Copy-Item $navGraphPath "$navGraphPath.bak" -Force
    Copy-Item $mainAppPath "$navGraphPath.bak" -Force
    Copy-Item $splashPath "$splashPath.bak" -Force

    # 3. ONBOARDING SCREEN
    Write-Host "=== ONBOARDING SCREEN ==="
    (Get-Content $navGraphPath) -replace 'startDestination = AppRoutes.MAIN', 'startDestination = AppRoutes.ONBOARDING' | Set-Content $navGraphPath
    Build-And-Capture "onboarding"

    # Restore backups for next steps
    Restore-Backups
    Copy-Item $navGraphPath "$navGraphPath.bak" -Force
    Copy-Item $mainAppPath "$navGraphPath.bak" -Force
    Copy-Item $splashPath "$splashPath.bak" -Force

    # For subsequent screens, startDestination is AppRoutes.MAIN
    # We will modify MainAppScreen's default tab in rememberSaveable { mutableStateOf("HOME") }
    $tabs = @("HOME", "CONTACTS", "EVENTS", "MESSAGES", "MORE", "ANALYTICS", "STYLE_COACH")
    
    foreach ($tab in $tabs) {
        Write-Host "=== TAB: $tab ==="
        # Reset back to original backup
        Copy-Item "$mainAppPath.bak" $mainAppPath -Force
        
        # Replace selectedTab
        $originalLine = 'var selectedTab by rememberSaveable \{ mutableStateOf\("HOME"\) \}'
        $replacementLine = 'var selectedTab by rememberSaveable { mutableStateOf("' + $tab + '") }'
        (Get-Content $mainAppPath) -replace $originalLine, $replacementLine | Set-Content $mainAppPath
        
        Build-And-Capture ($tab.ToLower())
    }
}
finally {
    Restore-Backups
    Write-Host "Cleanup done. Backups restored."
}
