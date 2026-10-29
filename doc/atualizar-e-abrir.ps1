[CmdletBinding()]
param(
    [ValidateSet('Release', 'Dev')]
    [string]$Modo = 'Release',

    [string]$AvdName = 'Medium_Phone_API_37.0'
)

$ErrorActionPreference = 'Stop'
$mobileRoot = Join-Path (Split-Path -Parent $PSScriptRoot) 'mobile'
$androidRoot = Join-Path $mobileRoot 'android'

function Invoke-Checked {
    param(
        [Parameter(Mandatory)]
        [string]$Executable,

        [Parameter(Mandatory)]
        [string[]]$Arguments,

        [Parameter(Mandatory)]
        [string]$WorkingDirectory
    )

    Push-Location $WorkingDirectory
    try {
        & $Executable @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "Comando falhou com codigo $LASTEXITCODE`: $Executable $($Arguments -join ' ')"
        }
    }
    finally {
        Pop-Location
    }
}

if (-not (Test-Path $mobileRoot)) {
    throw "Pasta mobile nao encontrada: $mobileRoot"
}

$jdk17 = Get-ChildItem (Join-Path $env:LOCALAPPDATA 'Programs\Eclipse Adoptium') `
    -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending |
    Select-Object -First 1

if ($jdk17) {
    $env:JAVA_HOME = $jdk17.FullName
}
elseif ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $javaVersion = (& (Join-Path $env:JAVA_HOME 'bin\java.exe') -version 2>&1 | Out-String)
    if ($javaVersion -notmatch 'version "17\.') {
        throw 'Este projeto requer JDK 17. Configure JAVA_HOME para um JDK 17 compativel.'
    }
}
else {
    throw 'JDK 17 nao encontrado. Instale o JDK 17 ou configure JAVA_HOME.'
}

$env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH

$sdkRoot = $env:ANDROID_HOME
if (-not $sdkRoot) {
    $sdkRoot = $env:ANDROID_SDK_ROOT
}
if (-not $sdkRoot) {
    $sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
}

$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$emulator = Join-Path $sdkRoot 'emulator\emulator.exe'
if (-not (Test-Path $adb) -or -not (Test-Path $emulator)) {
    throw "Android SDK incompleto em '$sdkRoot'. Instale Platform-Tools e Android Emulator ou configure ANDROID_HOME."
}
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot

$availableAvds = @(& $emulator -list-avds)
if ($LASTEXITCODE -ne 0 -or $availableAvds -notcontains $AvdName) {
    $list = $availableAvds -join ', '
    throw "AVD '$AvdName' nao encontrado. AVDs disponiveis: $list"
}

$npm = (Get-Command npm.cmd -ErrorAction Stop).Source
$npx = (Get-Command npx.cmd -ErrorAction Stop).Source

Write-Host 'Sincronizando dependencias do package.json...'
Invoke-Checked $npm @('install', '--no-audit', '--no-fund') $mobileRoot
Invoke-Checked $npx @('expo', 'install', '--fix') $mobileRoot
Invoke-Checked $npx @('expo', 'install', '--check') $mobileRoot

Write-Host 'Atualizando o projeto Android nativo...'
$env:CI = '1'
Invoke-Checked $npx @('expo', 'prebuild', '--platform', 'android') $mobileRoot

$deviceLines = @(& $adb 'devices' | Where-Object { $_ -match '^emulator-\d+\s+device$' })
if ($deviceLines.Count -eq 0) {
    Write-Host "Iniciando AVD '$AvdName'..."
    Start-Process -FilePath $emulator -ArgumentList @('-avd', $AvdName) | Out-Null
    & $adb 'wait-for-device'
    if ($LASTEXITCODE -ne 0) {
        throw 'ADB nao conseguiu conectar ao emulador.'
    }
    $deviceLines = @(& $adb 'devices' | Where-Object { $_ -match '^emulator-\d+\s+device$' })
}

if ($deviceLines.Count -eq 0) {
    throw 'Nenhum emulador Android conectado ao ADB.'
}
$serial = ($deviceLines[0] -split '\s+')[0]
$bootDeadline = [DateTime]::UtcNow.AddMinutes(5)
while ([DateTime]::UtcNow -lt $bootDeadline) {
    $bootState = (& $adb '-s' $serial 'shell' 'getprop' 'sys.boot_completed' 2>$null | Out-String).Trim()
    if ($bootState -eq '1') {
        break
    }
    Start-Sleep -Seconds 2
}
if ($bootState -ne '1') {
    throw "O emulador '$serial' nao concluiu a inicializacao em cinco minutos."
}

if ($Modo -eq 'Dev') {
    Write-Host 'Iniciando app de desenvolvimento. Deixe este terminal aberto para o Fast Refresh.'
    Invoke-Checked $npx @('expo', 'run:android') $mobileRoot
    exit 0
}

$gradlew = Join-Path $androidRoot 'gradlew.bat'
if (-not (Test-Path $gradlew)) {
    throw "Gradle wrapper nao encontrado: $gradlew"
}
Write-Host 'Compilando APK release...'
Invoke-Checked $gradlew @('assembleRelease') $androidRoot

$apk = Join-Path $androidRoot 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apk)) {
    throw "Build terminou sem produzir o APK esperado: $apk"
}

$appConfig = Get-Content (Join-Path $mobileRoot 'app.json') -Raw | ConvertFrom-Json
$packageName = $appConfig.expo.android.package
if (-not $packageName) {
    throw 'android.package nao definido em mobile/app.json.'
}

Write-Host 'Instalando e abrindo o APK...'
Invoke-Checked $adb @('-s', $serial, 'install', '-r', $apk) $mobileRoot
Invoke-Checked $adb @('-s', $serial, 'shell', 'am', 'force-stop', $packageName) $mobileRoot
Invoke-Checked $adb @('-s', $serial, 'shell', 'am', 'start', '-n', "$packageName/.MainActivity") $mobileRoot
Write-Host "App aberto no emulador $serial. APK: $apk"
