$ErrorActionPreference = "Stop"
$Project = $PSScriptRoot
$Tools = Join-Path $env:USERPROFILE "dev-tools"
New-Item -ItemType Directory -Force -Path $Tools | Out-Null

function Find-JavaHome([string]$root) {
    $java = Get-ChildItem -Path $root -Filter java.exe -Recurse -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '\\bin\\java.exe$' } |
        Select-Object -First 1
    if (-not $java) { return $null }
    return (Resolve-Path (Join-Path $java.Directory.FullName "..")).Path
}

Write-Host "Preparing JDK"
$javaHome = Find-JavaHome $Tools
if (-not $javaHome) {
    $zip = Join-Path $Tools "jdk17.zip"
    if (-not (Test-Path $zip) -or (Get-Item $zip).Length -lt 1000000) {
        curl.exe -L --retry 3 --retry-delay 2 --fail -o $zip "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk"
    }
    Expand-Archive -Path $zip -DestinationPath $Tools -Force
    $javaHome = Find-JavaHome $Tools
}
if (-not $javaHome) { throw "JDK not found" }
$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;" + $env:Path
Write-Host "JAVA_HOME=$javaHome"

$Sdk = Join-Path $Tools "android-sdk"
$sdkmanager = Join-Path $Sdk "cmdline-tools\latest\bin\sdkmanager.bat"
if (-not (Test-Path $sdkmanager)) {
    Write-Host "Preparing Android command line tools"
    $cmdZip = Join-Path $Tools "cmdtools.zip"
    if (-not (Test-Path $cmdZip) -or (Get-Item $cmdZip).Length -lt 1000000) {
        curl.exe -L --retry 3 --retry-delay 2 --fail -o $cmdZip "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
    }
    $tmp = Join-Path $Tools "cmdtmp"
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Expand-Archive -Path $cmdZip -DestinationPath $tmp -Force
    New-Item -ItemType Directory -Force -Path (Join-Path $Sdk "cmdline-tools\latest") | Out-Null
    Copy-Item -Path (Join-Path $tmp "cmdline-tools\*") -Destination (Join-Path $Sdk "cmdline-tools\latest") -Recurse -Force
}

$env:ANDROID_HOME = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk
$platformJar = Join-Path $Sdk "platforms\android-34\android.jar"
$buildTools = Join-Path $Sdk "build-tools\34.0.0"
if (-not (Test-Path $platformJar) -or -not (Test-Path $buildTools)) {
    Write-Host "Installing Android 34 platform"
    $yes = ((1..80 | ForEach-Object { "y" }) -join "`n") + "`n"
    $yes | & $sdkmanager --sdk_root=$Sdk --licenses
    & $sdkmanager --sdk_root=$Sdk "platforms;android-34" "build-tools;34.0.0" "platform-tools"
}

$gradleHome = Get-ChildItem $Tools -Directory -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -like "gradle-8.7*" -and (Test-Path (Join-Path $_.FullName "bin\gradle.bat")) } |
    Select-Object -First 1
if (-not $gradleHome) {
    Write-Host "Preparing Gradle"
    $gradleZip = Join-Path $Tools "gradle-8.7-bin.zip"
    if (-not (Test-Path $gradleZip) -or (Get-Item $gradleZip).Length -lt 1000000) {
        curl.exe -L --retry 3 --retry-delay 2 --fail -o $gradleZip "https://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip"
        if ($LASTEXITCODE -ne 0) {
            curl.exe -L --retry 3 --retry-delay 2 --fail -o $gradleZip "https://services.gradle.org/distributions/gradle-8.7-bin.zip"
        }
    }
    Expand-Archive -Path $gradleZip -DestinationPath $Tools -Force
    $gradleHome = Get-ChildItem $Tools -Directory | Where-Object { $_.Name -like "gradle-8.7*" } | Select-Object -First 1
}
if (-not $gradleHome) { throw "Gradle not found" }

$sdkDir = $Sdk.Replace('\', '\\')
Set-Content -Path (Join-Path $Project "local.properties") -Value "sdk.dir=$sdkDir" -Encoding ASCII

Write-Host "Building APK"
& (Join-Path $gradleHome.FullName "bin\gradle.bat") -p $Project assembleDebug testDebugUnitTest --stacktrace
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$apk = Join-Path $Project "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) { throw "APK missing" }
Copy-Item $apk (Join-Path $Project "xiaoai-debug.apk") -Force
Write-Host "APK_READY $apk"
