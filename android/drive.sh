#!/usr/bin/env bash
# A minimal UI driver over adb, for walking the app the way a user would.
#
# It resolves an element by its resource-id from the uiautomator tree and taps the centre of its
# bounds — the same contract ARIA/Appium uses, so an id that works here works there.
#
# Every command below takes an id and NOTHING ELSE: the screens are LazyColumns, so a control
# further down is not composed until it is scrolled into view, and a run that had to swipe for
# itself was a run full of coordinates typed for one screen size. tap, type_into, text_of and
# exists scroll until the id is there; the swipe is measured off the scrolling container itself.
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

# Prints "left top right bottom" for the first node with this resource-id, or nothing.
bounds_of() {
    local id="$1"
    grep -o "resource-id=\"$id\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" "$TMP" \
        | head -1 \
        | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' \
        | sed 's/bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]"/\1 \2 \3 \4/'
}

# Prints "x y" for the centre of the node with this resource-id, or nothing.
centre() {
    bounds_of "$1" | awk '{print int(($1+$3)/2), int(($2+$4)/2)}'
}

# One swipe over the scrolling container that is on screen, "down" or "up".
#
# The figures come out of the container's OWN bounds instead of being typed for one device: the
# emulator is 1080x2400 and the phone the app is carried on is not, and a swipe that starts below
# the list lands on the bottom bar and changes the screen instead of scrolling it. A quarter of
# the container's height per step is far enough to make progress on a long list and short enough
# that nothing passes unseen between two dumps.
scroll_swipe() {
    local direction="$1" box l t r b x mid step y1 y2
    box=$(grep -o 'scrollable="true"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' "$TMP" \
        | head -1 \
        | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' \
        | sed 's/bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]"/\1 \2 \3 \4/')
    # A screen whose content fits is reported scrollable="false" by uiautomator, and then there is
    # simply nothing left to reveal. Quiet, because scroll_to asks this question on every lookup.
    if [ -z "$box" ]; then return 1; fi

    read -r l t r b <<< "$box"
    x=$(((l + r) / 2))
    mid=$(((t + b) / 2))
    step=$(((b - t) / 4))
    if [ "$direction" = "down" ]; then
        y1=$((mid + step)); y2=$((mid - step))
    else
        y1=$((mid - step)); y2=$((mid + step))
    fi
    adb shell input swipe "$x" "$y1" "$x" "$y2" 350 > /dev/null
    sleep 1.2
}

# Leaves a fresh tree in $TMP with this resource-id in it, scrolling the screen's list until the
# id appears. This is what lets every other command take an id and nothing else.
#
# "Not found" is the ORDINARY state of a control below the fold, not a fault: a LazyColumn does
# not park a row off screen, it does not compose it at all, so the node does not exist to be
# found. Looking downwards first is right for every screen of this app — the parse, the Abgleich
# and the export all grow downwards — and the sweep back up catches an id above the current
# position. A tree that comes back unmoved means the list has hit its end, and another swipe in
# that direction cannot reveal anything.
scroll_to() {
    local id="$1" steps="${2:-8}" direction i before
    dump || return 1
    grep -q "resource-id=\"$id\"" "$TMP" && return 0

    for direction in down up; do
        i=0
        while [ "$i" -lt "$steps" ]; do
            before=$(grep -o 'bounds="[^"]*"' "$TMP" | md5sum)
            scroll_swipe "$direction" || return 1
            dump || return 1
            grep -q "resource-id=\"$id\"" "$TMP" && return 0
            [ "$(grep -o 'bounds="[^"]*"' "$TMP" | md5sum)" = "$before" ] && break
            i=$((i + 1))
        done
    done
    return 1
}

exists() {
    scroll_to "$1" && echo "PASS $1" || { echo "FAIL $1"; return 1; }
}

tap() {
    scroll_to "$1" || { echo "FAIL tap $1 (not found)"; return 1; }
    local xy
    xy=$(centre "$1")
    if [ -z "$xy" ]; then echo "FAIL tap $1 (no bounds)"; return 1; fi
    adb shell input tap $xy > /dev/null
    echo "tap $1 at $xy"
    sleep "${2:-2}"
}

type_into() {
    scroll_to "$1" || { echo "FAIL type $1 (not found)"; return 1; }
    local xy
    xy=$(centre "$1")
    if [ -z "$xy" ]; then echo "FAIL type $1 (no bounds)"; return 1; fi
    adb shell input tap $xy > /dev/null
    sleep 0.6
    # Clear first, the way ARIA's ui_type does: typing into a field that still holds something
    # appends to it, and "AAA" typed twice into the posting field reached the parser as "AAAAAA".
    # Select-all and one DEL rather than a DEL per character — a Stellenanzeige is hundreds.
    adb shell input keycombination 113 29 > /dev/null 2>&1   # CTRL + A
    adb shell input keyevent 67 > /dev/null                  # DEL
    sleep 0.3
    # "input text" has NO way to carry a newline — it drops it silently, and a Stellenanzeige that
    # arrives as one line parses to something else entirely: the requirements are read off bullets
    # that have to stand on a line of their own, and the Stellenbezeichnung became the whole first
    # sentence. So the lines go in one at a time with ENTER between them.
    # Every adb call in here reads from /dev/null: "adb shell" takes stdin, and inside a loop that
    # is reading its lines from stdin it swallows the ones not read yet. The first line went in,
    # the other nine disappeared, and the posting arrived as a single sentence with no symptom
    # other than a parse that read too little.
    local first=1 line escaped
    while IFS= read -r line || [ -n "$line" ]; do
        [ "$first" -eq 1 ] || adb shell input keyevent 66 < /dev/null > /dev/null
        first=0
        [ -z "$line" ] && continue
        # "input text" runs inside the device's shell, so (, ), &, ; and quotes are interpreted
        # there unless the whole argument is single-quoted on the device side. A space has to
        # become %s regardless — the tool has no other way to receive one.
        escaped=$(printf '%s' "$line" | sed "s/'/'\\\\''/g; s/ /%s/g")
        adb shell "input text '$escaped'" < /dev/null > /dev/null
    done <<< "$2"
    sleep 0.6
    adb shell input keyevent 111 > /dev/null   # ESCAPE closes the soft keyboard
    echo "type $1 <- $2"
}

# Prints the text of every TextView under a node carrying this resource-id, up to the next node
# that carries one.
#
# The window used to be a flat 4000 characters after the id, which ran clean through the node's
# own subtree into whatever followed it: text_of posting_field_reference answered with the
# POSITION field's value and a run read it as the Referenz. Two things had to change. The dump is
# split into one node per line first, because a node writes its own text BEFORE its resource-id
# and reading forward from the id therefore missed the text of every leaf — posting_photo_advice
# answered with nothing at all. And the subtree ends at the next node that carries an id of its
# own: the price is that asking a CARD stops at its first tagged child, so name the child.
text_of() {
    scroll_to "$1" || { echo "FAIL text_of $1 (not found)"; return 1; }
    tr '<' '\n' < "$TMP" | awk -v id="resource-id=\"$1\"" '
        index($0, id) { mine = 1 }
        !index($0, id) && mine && /resource-id="[^"]/ { exit }
        mine && match($0, /text="[^"]*"/) { print substr($0, RSTART, RLENGTH) }
    ' | head -20
}

scroll_down() { dump || return 1; scroll_swipe down; }
scroll_up()   { dump || return 1; scroll_swipe up; }

shot() {
    adb exec-out screencap -p > "$1"
    echo "shot $1"
}

"$@"
