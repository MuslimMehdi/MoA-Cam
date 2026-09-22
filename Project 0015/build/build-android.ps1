$ErrorActionPreference = 'Stop'
Set-Location android
if (Test-Path .\gradlew.bat) { .\gradlew.bat assembleDebug } else { gradle assembleDebug }
Write-Host "APK: app\\build\\outputs\\apk\\debug\\app-debug.apk"
