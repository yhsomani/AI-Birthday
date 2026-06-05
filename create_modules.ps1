$features = @('splash','login','dashboard','contacts','events','analytics','onboarding','settings','messages')

foreach ($f in $features) {
    $dir = "feature\$f\src\main\kotlin\com\example\feature\$f"
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    
    $manifest = "feature\$f\src\main\AndroidManifest.xml"
    if (-not (Test-Path $manifest)) {
        Set-Content $manifest "<?xml version=`"1.0`" encoding=`"utf-8`"?>`n<manifest />"
    }
    Write-Host "Created $f"
}
