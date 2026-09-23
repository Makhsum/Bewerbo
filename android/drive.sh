#!/usr/bin/env bash
# A minimal UI driver over adb, for walking the app the way a user would.
#
# It resolves an element by its resource-id from the uiautomator tree and taps the centre of its
# bounds — the same contract ARIA/Appium uses, so an id that works here works there.
set -uo pipefail

# Git Bash rewrites anything that looks like a POSIX path into a Windows one before adb sees it,
# so "/sdcard/ui.xml" arrives as "C:/Program Files/Git/sdcard/ui.xml". This turns that off.
export MSYS_NO_PATHCONV=1
# Compose testTags surface as BARE resource-ids ("overview_readiness_ring"), not as
# "de.bewerbo.app:id/overview_readiness_ring" the way an R.id on a View would. Matching on the
# package-prefixed form finds nothing.
PKG=de.bewerbo.app
TMP=/tmp/bewerbo-ui.xml

# A dump that FAILS must not leave the previous tree lying around: "uiautomator dump" goes quiet
# when the UiAutomation connection is held by a dead instrumentation, and reading the stale
# /sdcard/ui.xml then reports the screen you were on minutes ago — every bound, every
# clickable="…" wrong, and a run drawing confident conclusions from it. Delete first, verify the
# device wrote a new file, and say so loudly when it did not.
dump() {
    adb shell rm -f /sdcard/ui.xml > /dev/null 2>&1
    rm -f "$TMP"
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
    adb exec-out cat /sdcard/ui.xml 2>/dev/null | tr -d '\r' > "$TMP"

    # Not just "non-empty": a broken dump can still write a few bytes without a single node, and
    # that is indistinguishable from "the screen is blank" unless the root element is checked.
    if ! grep -q '<hierarchy' "$TMP"; then
        echo "FAIL dump (uiautomator wrote no hierarchy — the UiAutomation connection is gone;" \
             "check for a leftover Appium instrumentation, or reboot the device)" >&2
        return 1
    fi
}

# Prints "x y" for the centre of the node with this resource-id, or nothing.
centre() {
    local id="$1"
    grep -o "resource-id=\"$id\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" "$TMP" \
        | head -1 \
        | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' \
        | sed 's/bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]"/\1 \2 \3 \4/' \
        | awk '{print int(($1+$3)/2), int(($2+$4)/2)}'
}

exists() {
    dump || return 1
    grep -q "resource-id=\"$1\"" "$TMP" && echo "PASS $1" || { echo "FAIL $1"; return 1; }
}

tap() {
    dump || return 1
    local xy
    xy=$(centre "$1")
    if [ -z "$xy" ]; then echo "FAIL tap $1 (not found)"; return 1; fi
    adb shell input tap $xy > /dev/null
    echo "tap $1 at $xy"
    sleep "${2:-2}"
}

type_into() {
    dump || return 1
    local xy
    xy=$(centre "$1")
    if [ -z "$xy" ]; then echo "FAIL type $1 (not found)"; return 1; fi
    adb shell input tap $xy > /dev/null
    sleep 0.6
    # "input text" runs inside the device's shell, so (, ), &, ; and quotes are interpreted there
    # unless the whole argument is single-quoted on the device side. A space has to become %s
    # regardless — the tool has no other way to receive one.
    local escaped
    escaped=$(printf '%s' "$2" | sed "s/'/'\\\\''/g; s/ /%s/g")
    adb shell "input text '$escaped'" > /dev/null
    sleep 0.6
    adb shell input keyevent 111 > /dev/null   # ESCAPE closes the soft keyboard
    echo "type $1 <- $2"
}

# Prints the text of every TextView under a node carrying this resource-id.
text_of() {
    dump || return 1
    grep -o "resource-id=\"$1\".\{0,4000\}" "$TMP" | head -1 | grep -o 'text="[^"]*"' | head -20
}

scroll_down() { adb shell input swipe 540 1600 540 700 350 > /dev/null; sleep 1.2; }
scroll_up()   { adb shell input swipe 540 700 540 1600 350 > /dev/null; sleep 1.2; }

shot() {
    adb exec-out screencap -p > "$1"
    echo "shot $1"
}

"$@"
