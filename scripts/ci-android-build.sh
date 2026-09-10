#!/usr/bin/env bash
# Baut die Android-App im Container — ohne Android Studio, ohne SDK auf dem Rechner. Nur Docker noetig.
#
#   scripts/ci-android-build.sh                       # Unit-Tests + installierbares Debug-APK (Debug-Schluessel)
#   scripts/ci-android-build.sh :app:assembleRelease  # unsigniertes Release-APK (fuer die Abgabe neu signieren)
#   scripts/ci-android-build.sh :app:testDebugUnitTest :app:assembleDebug :app:lintDebug   # dazu Lint-Bericht
#
# Ergebnis: android-starter/app/build/outputs/apk/... und android-starter/app/build/reports/{tests,lint-results-debug.html}.
# Das Debug-APK wird zusaetzlich nach app/stundenplan.apk kopiert (Debug-Signatur — fuer die Abgabe neu signieren).
#
# Zwei Dinge, die Zeit kosten und deshalb hier stehen:
# - Das Abbild laeuft unter `--platform linux/amd64`. Das Android-SDK bringt fuer aapt2, d8 und die
#   Lint-Werkzeuge nur x86_64-Binaerdateien mit; auf den Apple-Silicon-Macs des Teams laufen sie unter
#   Emulation. Gemessen 05.09.: erster Lauf 19 min, danach mit warmem Cache deutlich schneller.
# - SDK und Gradle-Ablage liegen in Docker-Volumes (`android-sdk`, `android-gradle`), damit der zweite
#   Lauf nicht 1,5 GB neu laedt. Wer einen warmen Cache als Ordner hat, setzt ANDROID_SDK_DIR und
#   ANDROID_GRADLE_DIR (Bind-Mount statt Volume).
#
# Der Container heisst stundenplan-android-build und wird NICHT mit --rm gestartet: nach dem Lauf steht er
# beendet da (`docker logs stundenplan-android-build` zeigt die Ausgabe), der naechste Lauf raeumt ihn weg.
#
# In Android Studio ist das nicht noetig — dort reicht `Open` auf android-starter/ (siehe dessen README).
# Dieses Skript ist fuer die Pipeline und fuer Rechner ohne SDK.
set -euo pipefail
cd "$(dirname "$0")/.."

SDK_VOLUME=${ANDROID_SDK_VOLUME:-android-sdk}
GRADLE_VOLUME=${ANDROID_GRADLE_VOLUME:-android-gradle}
SDK_MOUNT=${ANDROID_SDK_DIR:-$SDK_VOLUME}
GRADLE_MOUNT=${ANDROID_GRADLE_DIR:-$GRADLE_VOLUME}
CONTAINER=${ANDROID_BUILD_CONTAINER:-stundenplan-android-build}
JDK_IMAGE=eclipse-temurin:17-jdk        # Android braucht JDK 17; das Backend laeuft auf 21 (zwei Welten)
CMDLINE_TOOLS=commandlinetools-linux-11076708_latest.zip
TASKS=${*:-":app:testDebugUnitTest :app:assembleDebug"}

if [ -z "${ANDROID_SDK_DIR:-}" ]; then
    docker volume inspect "$SDK_VOLUME" >/dev/null 2>&1 || docker volume create "$SDK_VOLUME" >/dev/null
fi
if [ -z "${ANDROID_GRADLE_DIR:-}" ]; then
    docker volume inspect "$GRADLE_VOLUME" >/dev/null 2>&1 || docker volume create "$GRADLE_VOLUME" >/dev/null
fi

# Rest des vorigen Laufs wegraeumen (beendeter Container gleichen Namens).
if docker container inspect "$CONTAINER" >/dev/null 2>&1; then
    docker container stop "$CONTAINER" >/dev/null 2>&1 || true
    docker container rm "$CONTAINER" >/dev/null
fi

echo "==> Gradle-Aufgaben: $TASKS"
echo "==> SDK: $SDK_MOUNT · Gradle-Ablage: $GRADLE_MOUNT · Container: $CONTAINER"
docker run --name "$CONTAINER" --platform linux/amd64 \
    -v "$PWD/android-starter":/proj -w /proj \
    -v "$SDK_MOUNT":/sdk \
    -v "$GRADLE_MOUNT":/root/.gradle \
    -e ANDROID_HOME=/sdk -e ANDROID_SDK_ROOT=/sdk \
    -e TASKS="$TASKS" -e CMDLINE_TOOLS="$CMDLINE_TOOLS" \
    "$JDK_IMAGE" bash -euc '
export PATH=/sdk/cmdline-tools/latest/bin:$PATH
if [ ! -x /sdk/cmdline-tools/latest/bin/sdkmanager ]; then
    echo "==> Android-Kommandozeilenwerkzeuge holen"
    apt-get update -qq && apt-get install -y -qq curl unzip >/dev/null
    curl -sSL -o /tmp/cmdline.zip "https://dl.google.com/android/repository/$CMDLINE_TOOLS"
    mkdir -p /sdk/cmdline-tools /tmp/cl
    unzip -q /tmp/cmdline.zip -d /tmp/cl
    cp -a /tmp/cl/cmdline-tools /sdk/cmdline-tools/latest
fi
yes | sdkmanager --licenses >/dev/null 2>&1 || true
sdkmanager --install "platform-tools" "platforms;android-36" "build-tools;36.0.0" | tail -2
./gradlew --no-daemon --no-watch-fs $TASKS   # kein Dateiwaechter: im Bind-Mount meldet er nur Fehler
'

echo
echo "==> Ergebnis"
ls -la android-starter/app/build/outputs/apk/*/ 2>/dev/null || echo "kein APK gebaut (nur Tests?)"

DEBUG_APK=android-starter/app/build/outputs/apk/debug/app-debug.apk
if [ -f "$DEBUG_APK" ]; then
    mkdir -p app
    cp "$DEBUG_APK" app/stundenplan.apk
    echo "==> app/stundenplan.apk ($(du -h app/stundenplan.apk | cut -f1)) — Debug-Signatur, fuer die Abgabe neu signieren"
fi
