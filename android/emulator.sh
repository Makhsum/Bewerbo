#!/usr/bin/env bash
# Brings up what testing on Android needs: an emulator, an Appium server, and this build of the app
# installed and running on it. "up" does all of it, in order; each step can also be run on its own.
#
# Every step checks first and only acts when it has to, so "up" is the command for a cold machine
# and the command for one where half of it is already running — an emulator that is booted is not
# booted a second time, and an Appium server that answers is not started next to itself.
#
# Readiness is polled, never slept on. An emulator process that has started is not a device that
# has booted (sys.boot_completed), and an Appium process that has started is not a server that
# accepts a session (/status); a run that trusts either of them fails its first tool call.
set -uo pipefail

# The same as in drive.sh: Git Bash rewrites "/sdcard/…" into a Windows path before adb sees it.
export MSYS_NO_PATHCONV=1

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PKG=de.bewerbo.app
AVD="${BEWERBO_AVD:-bewerbo_api35}"
IMAGE="system-images;android-35;google_apis;x86_64"
APPIUM_PORT=4723
APPIUM_LOG=/tmp/bewerbo-appium.log
EMULATOR_LOG=/tmp/bewerbo-emulator.log

# The SDK is the one local.properties names — the build reads it from there, so the device the app
# is tested on comes out of the same SDK as the app itself. ANDROID_HOME wins when it is set.
# The file carries "C\:/…" because a properties file eats a bare backslash; undo that here.
sdk_dir() {
    if [ -n "${ANDROID_HOME:-}" ]; then echo "$ANDROID_HOME"; return 0; fi
    local line
    line=$(grep '^sdk.dir=' "$HERE/local.properties" 2>/dev/null | head -1)
    if [ -z "$line" ]; then
        echo "FAIL sdk (no ANDROID_HOME and no sdk.dir in android/local.properties)" >&2
        return 1
    fi
    echo "${line#sdk.dir=}" | sed 's/\\:/:/g; s/\\\\/\//g'
}

SDK="$(sdk_dir)" || exit 1
# Appium finds adb through ANDROID_HOME and through nothing else: started without it, the server
# reports ready and then refuses every session with "Neither ANDROID_HOME nor ANDROID_SDK_ROOT".
# It is a Windows process, so it gets the Windows form of the path.
export ANDROID_HOME="${ANDROID_HOME:-$(cygpath -w "$SDK" 2>/dev/null || echo "$SDK")}"
# PATH is split on ':', so "C:/…" would break it in two; Git Bash wants the "/c/…" form there.
SDK="$(cygpath -u "$SDK" 2>/dev/null || echo "$SDK")"
export PATH="$SDK/platform-tools:$SDK/emulator:$SDK/cmdline-tools/latest/bin:$PATH"

# Android Studio's bundled JDK is enough for avdmanager and Gradle; a JAVA_HOME already set wins.
if [ -z "${JAVA_HOME:-}" ] && [ -d "/c/Program Files/Android/Android Studio/jbr" ]; then
    export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
fi

# The serial of the first attached emulator, or nothing.
serial() {
    adb devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }'
}

# Creates the AVD once. The system image is NOT downloaded from here: it is over a gigabyte, and a
# script that quietly starts that download looks hung. It names the command instead.
avd() {
    if emulator -list-avds | tr -d '\r' | grep -qx "$AVD"; then
        echo "avd $AVD"
        return 0
    fi
    if [ ! -d "$SDK/system-images/android-35/google_apis/x86_64" ]; then
        echo "FAIL avd (system image missing — run: sdkmanager \"$IMAGE\")" >&2
        return 1
    fi
    # On Windows the tool is a .bat, which Git Bash does not find under its bare name.
    local avdmanager=avdmanager
    command -v avdmanager.bat > /dev/null && avdmanager=avdmanager.bat
    # "no" answers the one question avdmanager asks: whether to write a custom hardware profile.
    echo no | "$avdmanager" create avd -n "$AVD" -k "$IMAGE" -d pixel_7 > /dev/null || {
        echo "FAIL avd (avdmanager could not create $AVD)" >&2
        return 1
    }
    echo "avd $AVD created"
}

# Starts the emulator unless one is attached, and returns once it has finished booting.
#
# Animations go off afterwards, every time: they are the biggest single source of flaky UI runs,
# because an element is "there" in the tree while it is still sliding into place. A cold boot with
# -no-snapshot-save keeps every run starting from the same device, not from the last run's leftovers.
boot() {
    local s i
    s=$(serial)
    if [ -z "$s" ]; then
        nohup emulator -avd "$AVD" -no-boot-anim -no-snapshot-save > "$EMULATOR_LOG" 2>&1 &
        disown
        echo "emulator $AVD starting (log $EMULATOR_LOG)"
    fi

    # Three minutes covers a cold boot on WHPX with room to spare; past that, something is wrong
    # and waiting longer only hides it.
    i=0
    while [ "$i" -lt 90 ]; do
        s=$(serial)
        if [ -n "$s" ] && [ "$(adb -s "$s" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
            adb -s "$s" shell settings put global window_animation_scale 0
            adb -s "$s" shell settings put global transition_animation_scale 0
            adb -s "$s" shell settings put global animator_duration_scale 0
            echo "booted $s"
            return 0
        fi
        sleep 2
        i=$((i + 1))
    done
    echo "FAIL boot (no completed boot after 180 s — see $EMULATOR_LOG)" >&2
    return 1
}

appium_ready() {
    curl -s "http://127.0.0.1:$APPIUM_PORT/status" 2>/dev/null | grep -q '"ready":true'
}

# Starts Appium unless a server on the port already says it is ready. ARIA talks to it; it is what
# installs and drives the UiAutomator2 server on the device.
appium_up() {
    if appium_ready; then
        echo "appium ready on $APPIUM_PORT"
        return 0
    fi
    if ! command -v appium > /dev/null; then
        echo "FAIL appium (not installed — run: npm install -g appium && appium driver install uiautomator2)" >&2
        return 1
    fi
    nohup appium --port "$APPIUM_PORT" > "$APPIUM_LOG" 2>&1 &
    disown

    local i=0
    while [ "$i" -lt 30 ]; do
        if appium_ready; then
            echo "appium ready on $APPIUM_PORT (log $APPIUM_LOG)"
            return 0
        fi
        sleep 1
        i=$((i + 1))
    done
    echo "FAIL appium (no ready /status after 30 s — see $APPIUM_LOG)" >&2
    return 1
}

# Builds the debug app and installs the APK that matches the device.
#
# The build splits by ABI (app/build.gradle.kts), so there is no app-debug.apk: there is one APK
# for arm64-v8a and one for x86_64. The device is asked which one it runs rather than assuming the
# emulator — the same command then installs onto a phone plugged in by USB.
install() {
    local s abi apk
    s=$(serial)
    if [ -z "$s" ]; then s=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1; exit }'); fi
    if [ -z "$s" ]; then echo "FAIL install (no device attached — run: $0 boot)" >&2; return 1; fi

    (cd "$HERE" && ./gradlew :app:assembleDebug -q) || { echo "FAIL install (build failed)" >&2; return 1; }

    abi=$(adb -s "$s" shell getprop ro.product.cpu.abi | tr -d '\r')
    apk="$HERE/app/build/outputs/apk/debug/app-$abi-debug.apk"
    if [ ! -f "$apk" ]; then echo "FAIL install (no APK for $abi — the build makes arm64-v8a and x86_64)" >&2; return 1; fi
    # MSYS_NO_PATHCONV also stops Git Bash from turning this "/c/…" path into one adb.exe can open,
    # so it is converted here explicitly.
    adb -s "$s" install -r "$(cygpath -w "$apk" 2>/dev/null || echo "$apk")" > /dev/null \
        || { echo "FAIL install ($apk)" >&2; return 1; }
    echo "installed $(basename "$apk") on $s"
}

# Starts the app the way the launcher does.
launch() {
    local s
    s=$(serial)
    adb ${s:+-s "$s"} shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1 \
        || { echo "FAIL launch $PKG" >&2; return 1; }
    echo "launched $PKG"
}

up() {
    avd && boot && appium_up && install && launch
}

# Shuts the emulator down. Appium is left running: it holds no device state of its own and the
# next "up" reuses it.
down() {
    local s
    s=$(serial)
    if [ -z "$s" ]; then echo "no emulator running"; return 0; fi
    adb -s "$s" emu kill > /dev/null
    echo "stopped $s"
}

"${@:-up}"
