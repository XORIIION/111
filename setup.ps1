# Автономера: автоматическая установка всего нужного, сборка и запуск на телефоне (Windows)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$root  = Split-Path -Parent $MyInvocation.MyCommand.Path
$tools = Join-Path $root '.tools'
$sdk   = Join-Path $tools 'android-sdk'
New-Item -ItemType Directory -Force $tools | Out-Null

function Say($t)  { Write-Host ">> $t" -ForegroundColor Cyan }
function Fail($t) { Write-Host "!! $t" -ForegroundColor Red }
function Download($url, $out) {
    Say "Скачиваю: $url"
    Invoke-WebRequest -Uri $url -OutFile $out -UseBasicParsing
}

try {
    # ── 1. JDK 17 ──
    $jdkRoot = Join-Path $tools 'jdk'
    $jdkDir = Get-ChildItem $jdkRoot -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $jdkDir) {
        $zip = Join-Path $tools 'jdk.zip'
        Download 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' $zip
        Say 'Распаковываю JDK...'
        Expand-Archive -Path $zip -DestinationPath $jdkRoot -Force
        Remove-Item $zip -Force
        $jdkDir = Get-ChildItem $jdkRoot -Directory | Select-Object -First 1
    }
    $env:JAVA_HOME = $jdkDir.FullName
    $env:Path = "$($env:JAVA_HOME)\bin;$($env:Path)"
    Say "JDK готов: $($env:JAVA_HOME)"

    # ── 2. Android SDK (командные инструменты) ──
    $env:ANDROID_HOME = $sdk
    $env:ANDROID_SDK_ROOT = $sdk
    $sdkm = Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
    if (-not (Test-Path $sdkm)) {
        $zip = Join-Path $tools 'cmdtools.zip'
        $tmp = Join-Path $tools 'cmdtmp'
        Download 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' $zip
        Expand-Archive -Path $zip -DestinationPath $tmp -Force
        New-Item -ItemType Directory -Force (Join-Path $sdk 'cmdline-tools') | Out-Null
        Move-Item (Join-Path $tmp 'cmdline-tools') (Join-Path $sdk 'cmdline-tools\latest') -Force
        Remove-Item $zip, $tmp -Recurse -Force
    }

    # ── 3. Лицензии и пакеты SDK ──
    $yes = (1..40 | ForEach-Object { 'y' })
    if (-not (Test-Path (Join-Path $sdk 'platforms\android-34')) -or
        -not (Test-Path (Join-Path $sdk 'platform-tools\adb.exe'))) {
        Say 'Принимаю лицензии Android SDK...'
        $yes | & $sdkm --licenses | Out-Null
        Say 'Устанавливаю Android SDK (platform-tools, android-34, build-tools)...'
        $yes | & $sdkm 'platform-tools' 'platforms;android-34' 'build-tools;34.0.0'
        if ($LASTEXITCODE -ne 0) { throw 'Не удалось установить Android SDK' }
    }

    # ── 4. Gradle ──
    $gradleHome = Join-Path $tools 'gradle-8.7'
    if (-not (Test-Path (Join-Path $gradleHome 'bin\gradle.bat'))) {
        $zip = Join-Path $tools 'gradle.zip'
        Download 'https://services.gradle.org/distributions/gradle-8.7-bin.zip' $zip
        Say 'Распаковываю Gradle...'
        Expand-Archive -Path $zip -DestinationPath $tools -Force
        Remove-Item $zip -Force
    }

    # ── 5. Путь к SDK для проекта ──
    ('sdk.dir=' + ($sdk -replace '\\', '/')) | Set-Content -Path (Join-Path $root 'local.properties') -Encoding ASCII

    # ── 6. Сборка ──
    Say 'Собираю игру (в первый раз это долго — качаются библиотеки)...'
    & (Join-Path $gradleHome 'bin\gradle.bat') -p $root assembleDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Сборка не удалась — ошибка выше' }
    $apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
    $apkCopy = Join-Path $root 'Avtonomera.apk'
    Copy-Item $apk $apkCopy -Force
    Say "Готово! APK: $apkCopy"

    # ── 7. Ярлык запуска на рабочем столе компьютера ──
    $lnkPath = Join-Path ([Environment]::GetFolderPath('Desktop')) 'Автономера (запуск на телефон).lnk'
    if (-not (Test-Path $lnkPath)) {
        $lnk = (New-Object -ComObject WScript.Shell).CreateShortcut($lnkPath)
        $lnk.TargetPath = Join-Path $root 'START_GAME.bat'
        $lnk.WorkingDirectory = $root
        $lnk.Save()
        Say 'Ярлык для быстрого запуска создан на рабочем столе компьютера.'
    }

    # ── 8. Установка на телефон ──
    $adb = Join-Path $sdk 'platform-tools\adb.exe'
    & $adb start-server | Out-Null
    $tries = 0
    while (-not ((& $adb devices) | Select-String "`tdevice$")) {
        if ($tries -eq 0) {
            Write-Host ''
            Write-Host 'Подключите телефон кабелем и включите «Отладку по USB»:' -ForegroundColor Yellow
            Write-Host '  Настройки → О телефоне → 7 раз нажать «Номер сборки»,' -ForegroundColor Yellow
            Write-Host '  затем Настройки → Для разработчиков → Отладка по USB.' -ForegroundColor Yellow
            Write-Host '  На телефоне нажмите «Разрешить» в окне отладки. Жду до 3 минут...' -ForegroundColor Yellow
        }
        if ($tries -ge 60) { break }
        Start-Sleep -Seconds 3
        $tries++
    }
    if ((& $adb devices) | Select-String "`tdevice$") {
        Say 'Устанавливаю игру на телефон...'
        & $adb install -r $apkCopy
        if ($LASTEXITCODE -ne 0) { throw 'Не удалось установить на телефон' }
        & $adb shell am start -n ru.platesgame/.MainActivity | Out-Null
        Say 'Игра запущена на телефоне! Подтвердите добавление ярлыка на экране телефона.'
    } else {
        Fail 'Телефон не найден. Файл Avtonomera.apk лежит в папке проекта — перекиньте его на телефон и откройте.'
    }
}
catch {
    Fail $_.Exception.Message
}
