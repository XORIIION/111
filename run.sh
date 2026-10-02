#!/usr/bin/env bash
# Автономера: автоматическая установка всего нужного, сборка и запуск на телефоне (macOS / Linux)
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
TOOLS="$ROOT/.tools"
SDK="$TOOLS/android-sdk"
mkdir -p "$TOOLS"

say()  { printf '\033[36m>> %s\033[0m\n' "$1"; }
fail() { printf '\033[31m!! %s\033[0m\n' "$1"; }

for cmd in curl unzip tar; do
  command -v "$cmd" >/dev/null || { fail "Установите '$cmd' и запустите снова"; exit 1; }
done

case "$(uname -s)" in
  Darwin) OS=mac;   TOOLOS=mac ;;
  *)      OS=linux; TOOLOS=linux ;;
esac
case "$(uname -m)" in
  arm64|aarch64) ARCH=aarch64 ;;
  *)             ARCH=x64 ;;
esac

# ── 1. JDK 17 ──
if [ ! -d "$TOOLS/jdk" ]; then
  say "Скачиваю JDK 17..."
  mkdir -p "$TOOLS/jdk"
  curl -fL "https://api.adoptium.net/v3/binary/latest/17/ga/$OS/$ARCH/jdk/hotspot/normal/eclipse" -o "$TOOLS/jdk.tar.gz"
  tar -xzf "$TOOLS/jdk.tar.gz" -C "$TOOLS/jdk"
  rm -f "$TOOLS/jdk.tar.gz"
fi
JAVA_BIN="$(find "$TOOLS/jdk" -path '*/bin/java' -type f | head -n 1)"
export JAVA_HOME="$(dirname "$(dirname "$JAVA_BIN")")"
export PATH="$JAVA_HOME/bin:$PATH"
say "JDK готов: $JAVA_HOME"

# ── 2. Android SDK ──
export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"
SDKM="$SDK/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKM" ]; then
  say "Скачиваю Android command-line tools..."
  curl -fL "https://dl.google.com/android/repository/commandlinetools-$TOOLOS-11076708_latest.zip" -o "$TOOLS/cmdtools.zip"
  rm -rf "$TOOLS/cmdtmp"; mkdir -p "$TOOLS/cmdtmp" "$SDK/cmdline-tools"
  unzip -q "$TOOLS/cmdtools.zip" -d "$TOOLS/cmdtmp"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$TOOLS/cmdtmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$TOOLS/cmdtools.zip" "$TOOLS/cmdtmp"
fi

# ── 3. Лицензии и пакеты SDK ──
if [ ! -d "$SDK/platforms/android-34" ] || [ ! -x "$SDK/platform-tools/adb" ]; then
  say "Принимаю лицензии и устанавливаю Android SDK..."
  yes | "$SDKM" --licenses >/dev/null || true
  yes | "$SDKM" "platform-tools" "platforms;android-34" "build-tools;34.0.0" || true
fi
[ -d "$SDK/platforms/android-34" ] || { fail "Не удалось установить Android SDK"; exit 1; }

# ── 4. Gradle ──
GRADLE="$TOOLS/gradle-8.7/bin/gradle"
if [ ! -x "$GRADLE" ]; then
  say "Скачиваю Gradle..."
  curl -fL "https://services.gradle.org/distributions/gradle-8.7-bin.zip" -o "$TOOLS/gradle.zip"
  unzip -q "$TOOLS/gradle.zip" -d "$TOOLS"
  rm -f "$TOOLS/gradle.zip"
fi

# ── 5. Путь к SDK + сборка ──
echo "sdk.dir=$SDK" > "$ROOT/local.properties"
say "Собираю игру (в первый раз это долго — качаются библиотеки)..."
"$GRADLE" -p "$ROOT" assembleDebug --console=plain
cp "$ROOT/app/build/outputs/apk/debug/app-debug.apk" "$ROOT/Avtonomera.apk"
say "Готово! APK: $ROOT/Avtonomera.apk"

# ── 6. Установка на телефон ──
ADB="$SDK/platform-tools/adb"
"$ADB" start-server >/dev/null 2>&1 || true
has_device() { "$ADB" devices | grep -q "[[:space:]]device$"; }
tries=0
while ! has_device; do
  if [ "$tries" -eq 0 ]; then
    echo
    echo "Подключите телефон кабелем и включите «Отладку по USB»:"
    echo "  Настройки → О телефоне → 7 раз нажать «Номер сборки»,"
    echo "  затем Настройки → Для разработчиков → Отладка по USB."
    echo "  На телефоне нажмите «Разрешить». Жду до 3 минут..."
  fi
  [ "$tries" -ge 60 ] && break
  sleep 3; tries=$((tries + 1))
done
if has_device; then
  say "Устанавливаю игру на телефон..."
  "$ADB" install -r "$ROOT/Avtonomera.apk"
  "$ADB" shell am start -n ru.platesgame/.MainActivity >/dev/null
  say "Игра запущена на телефоне! Подтвердите добавление ярлыка на экране телефона."
else
  fail "Телефон не найден. Файл Avtonomera.apk лежит в папке проекта — перекиньте его на телефон и откройте."
fi
