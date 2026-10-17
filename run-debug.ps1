param(
    [string]$JavaHome = "D:\android\jbr",
    [string]$PackageName = "com.screenguard.app",
    [string]$MainActivity = ".MainActivity"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectRoot

$javaExe = Join-Path $JavaHome "bin\java.exe"
if (-not (Test-Path $javaExe)) {
    throw "Java not found: $javaExe"
}

$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;$env:Path"

Write-Host "[1/3] Install debug APK..."
& ".\gradlew.bat" installDebug
if ($LASTEXITCODE -ne 0) {
    throw "Gradle installDebug failed."
}

$adb = "adb"
$localProperties = Join-Path $projectRoot "local.properties"
if (Test-Path $localProperties) {
    $sdkLine = Get-Content $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
    if ($sdkLine) {
        $raw = $sdkLine.Substring(8)
        $sdkDir = $raw -replace '\\:', ':' -replace '\\\\', '\\'
        $adbCandidate = Join-Path $sdkDir "platform-tools\adb.exe"
        if (Test-Path $adbCandidate) {
            $adb = $adbCandidate
        }
    }
}

Write-Host "[2/3] Connected devices:"
& $adb devices

Write-Host "[3/3] Launch app..."
& $adb shell am start -n "$PackageName/$MainActivity"
if ($LASTEXITCODE -ne 0) {
    throw "Failed to launch $PackageName/$MainActivity"
}

Write-Host "Done. App started successfully."