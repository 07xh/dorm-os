#!/system/bin/sh
set -u
MODDIR="${0%/*}/.."
SCENE="${1:-status}"
say() { printf '%s\n' "$*"; }
fail() { say "ERROR: $*" >&2; exit 1; }
case "$SCENE" in status|study|dingtalk|ncwu-check|dorm) ;; *) fail "Unknown scene" ;; esac
if [ "${DORMOS_TEST_MODE:-0}" != 1 ] && [ "$(id -u)" != 0 ]; then fail "Root required"; fi
if [ "$SCENE" = status ]; then
  say "DORM OS NFC v0.1.0"; say "No resident daemon or polling"
  say "Allowlisted scenes: study dingtalk ncwu-check dorm"
  say "Ncwu authentication disabled until verified"
  exit 0
fi
if [ "${DORMOS_TEST_MODE:-0}" != 1 ]; then
  mkdir -p "$MODDIR/state" || fail "Cannot create state"
  if ! mkdir "$MODDIR/state/.lock" 2>/dev/null; then say "Busy"; exit 0; fi
  trap 'rmdir "$MODDIR/state/.lock" 2>/dev/null || true' EXIT HUP INT TERM
  NOW=$(date +%s); PREV=$(cat "$MODDIR/state/last" 2>/dev/null || echo 0)
  case "$PREV" in *[!0-9]*|'') PREV=0;; esac
  if [ $((NOW-PREV)) -lt 3 ] && [ $((NOW-PREV)) -ge 0 ]; then say "Duplicate tap ignored"; exit 0; fi
  printf '%s\n' "$NOW" > "$MODDIR/state/last"
fi
case "$SCENE" in
  study)
    if [ "${DORMOS_TEST_MODE:-0}" = 1 ]; then say "TEST: open website"; else
      /system/bin/am start -a android.intent.action.VIEW -d "https://0701000.xyz/"
    fi;;
  dingtalk)
    if [ "${DORMOS_TEST_MODE:-0}" = 1 ]; then say "TEST: open DingTalk"; else
      /system/bin/monkey -p com.alibaba.android.rimet -c android.intent.category.LAUNCHER 1
    fi;;
  ncwu-check)
    say "Read-only Wi-Fi status. No campus authentication."
    if [ "${DORMOS_TEST_MODE:-0}" = 1 ]; then say "TEST: query wifi"; else
      /system/bin/cmd wifi status 2>&1 | head -n 18
      /system/bin/ip -o -4 addr show dev wlan0 2>&1 | head -n 2
    fi;;
  dorm) say "DORM OS deployment URL not yet verified; no action";;
esac
say "Completed: $SCENE"
