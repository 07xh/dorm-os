#!/system/bin/sh
# KernelSU-only personal SH library, called only on demand.
set -eu
umask 077
if [ -x /system/bin/sh ]; then SH=/system/bin/sh; else SH=/bin/sh; fi
if [ "${DORM_NFC_TESTING:-0}" = "1" ] && [ "$(id -u)" != "0" ]; then
  BASE="${DORM_NFC_TEST_ROOT:?}"
else
  BASE=/data/adb/dormos_nfc_bridge
fi
SCRIPTS="$BASE/scripts"
BINDINGS="$BASE/script_bindings.tsv"
LOG="$BASE/script_events.log"
die() { echo "DORM_SCRIPT_ERROR=$1"; exit "${2:-1}"; }
uuid() { printf '%s' "$1" | grep -Eq '^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$'; }
b64() { printf '%s' "$1" | grep -Eq '^[A-Za-z0-9+/]+={0,2}$'; }
[ ! -L "$BASE" ] && [ ! -L "$SCRIPTS" ] || die unsafe_dir 70
mkdir -p "$SCRIPTS"
chmod 700 "$BASE" "$SCRIPTS" 2>/dev/null || :
item() {
  uuid "$1" || die invalid_id 64
  FILE="$SCRIPTS/$1.sh"
  NAME="$SCRIPTS/$1.name64"
  [ ! -L "$FILE" ] && [ ! -L "$NAME" ] || die symlink_refused 70
}
record() {
  printf '%s|%s|%s\n' "$(date +%s)" "$1" "$2" >> "$LOG"
  tail -n 80 "$LOG" > "$LOG.tmp"
  mv "$LOG.tmp" "$LOG"
  chmod 600 "$LOG" 2>/dev/null || :
}
case "${1:-}" in
  --list)
    for f in "$SCRIPTS"/*.sh; do
      [ -f "$f" ] && [ ! -L "$f" ] || continue
      id="${f##*/}"; id="${id%.sh}"
      uuid "$id" || continue
      [ -f "$SCRIPTS/$id.name64" ] && [ ! -L "$SCRIPTS/$id.name64" ] || continue
      printf '%s|%s|%s\n' "$id" "$(cat "$SCRIPTS/$id.name64")" "$(wc -c < "$f" | tr -d ' ')"
    done ;;
  --read)
    item "${2:-}"
    [ -f "$FILE" ] && [ -f "$NAME" ] || die not_found 66
    echo "SCRIPT_NAME64=$(cat "$NAME")"
    printf 'SCRIPT_BODY64='
    base64 < "$FILE" | tr -d '\r\n'
    printf '\n' ;;
  --save)
    item "${2:-}"
    title="${3:-}"; body="${4:-}"
    [ -n "$title" ] && [ "${#title}" -le 360 ] && b64 "$title" || die bad_title 64
    [ -n "$body" ] && [ "${#body}" -le 24000 ] && b64 "$body" || die bad_body 64
    display=$(printf '%s' "$title" | base64 -d 2>/dev/null) || die bad_title64 65
    [ -n "$display" ] && [ "$(printf '%s' "$display" | wc -c | tr -d ' ')" -le 120 ] || die title_too_long 64
    printf '%s' "$display" | grep -Eq '^[^/\\|[:cntrl:]]+\.sh    tmp="$SCRIPTS/.script.$$"
    printf '%s' "$body" | base64 -d > "$tmp" 2>/dev/null || { rm -f "$tmp"; die bad_body64 65; }
    [ "$(wc -c < "$tmp" | tr -d ' ')" -le 16384 ] || { rm -f "$tmp"; die body_too_large 64; }
    "$SH" -n "$tmp" || { rm -f "$tmp"; die syntax_error 65; }
    chmod 600 "$tmp"
    mv "$tmp" "$FILE"
    printf '%s\n' "$title" > "$NAME"
    chmod 600 "$NAME"
    echo "DORM_SCRIPT_SAVED=1" ;;
  --delete)
    item "${2:-}"
    [ -f "$FILE" ] || die not_found 66
    rm -f "$FILE" "$NAME"
    if [ -f "$BINDINGS" ]; then
      awk -F '|' -v id="$2" '$2 != id {print}' "$BINDINGS" > "$BINDINGS.tmp"
      mv "$BINDINGS.tmp" "$BINDINGS"
    fi
    echo "DORM_SCRIPT_DELETED=1" ;;
  --run)
    item "${2:-}"
    [ -f "$FILE" ] || die not_found 66
    command -v timeout >/dev/null 2>&1 || die timeout_unavailable 69
    if timeout 20 "$SH" "$FILE"; then
      record "$2" ok
      echo "DORM_SCRIPT_RESULT=success"
    else
      code=$?
      record "$2" failed
      echo "DORM_SCRIPT_RESULT=failed"
      exit "$code"
    fi ;;
  --bind)
    uuid "${2:-}" || die invalid_tag 64
    item "${3:-}"
    [ -f "$FILE" ] || die not_found 66
    tmp="$BINDINGS.tmp.$$"
    if [ -f "$BINDINGS" ]; then awk -F '|' -v tag="$2" '$1 != tag {print}' "$BINDINGS" > "$tmp"; else : > "$tmp"; fi
    printf '%s|%s\n' "$2" "$3" >> "$tmp"
    chmod 600 "$tmp"; mv "$tmp" "$BINDINGS"
    echo "DORM_SCRIPT_BOUND=1" ;;
  --unbind)
    uuid "${2:-}" || die invalid_tag 64
    if [ -f "$BINDINGS" ]; then
      awk -F '|' -v tag="$2" '$1 != tag {print}' "$BINDINGS" > "$BINDINGS.tmp"
      mv "$BINDINGS.tmp" "$BINDINGS"
    fi
    echo "DORM_SCRIPT_UNBOUND=1" ;;
  --bindings)
    [ ! -L "$BINDINGS" ] || die unsafe_bindings 70
    [ -f "$BINDINGS" ] && cat "$BINDINGS" || : ;;
  --resolve)
    uuid "${2:-}" || die invalid_tag 64
    [ -f "$BINDINGS" ] && [ ! -L "$BINDINGS" ] || die unbound 66
    id=$(awk -F '|' -v tag="$2" '$1 == tag {print $2; exit}' "$BINDINGS")
    uuid "$id" || die unbound 66
    item "$id"
    [ -f "$FILE" ] || die not_found 66
    echo "DORM_SCRIPT_ID=$id" ;;
  --logs)
    [ -f "$LOG" ] && tail -n 80 "$LOG" || : ;;
  --clear-logs)
    : > "$LOG"
    chmod 600 "$LOG" 2>/dev/null || :
    echo "DORM_SCRIPT_LOGS=CLEARED" ;;
  *) die unknown_action 64 ;;
esac
 || die invalid_script_name 64
    tmp="$SCRIPTS/.script.$$"
    printf '%s' "$body" | base64 -d > "$tmp" 2>/dev/null || { rm -f "$tmp"; die bad_body64 65; }
    [ "$(wc -c < "$tmp" | tr -d ' ')" -le 16384 ] || { rm -f "$tmp"; die body_too_large 64; }
    "$SH" -n "$tmp" || { rm -f "$tmp"; die syntax_error 65; }
    chmod 600 "$tmp"
    mv "$tmp" "$FILE"
    printf '%s\n' "$title" > "$NAME"
    chmod 600 "$NAME"
    echo "DORM_SCRIPT_SAVED=1" ;;
  --delete)
    item "${2:-}"
    [ -f "$FILE" ] || die not_found 66
    rm -f "$FILE" "$NAME"
    if [ -f "$BINDINGS" ]; then
      awk -F '|' -v id="$2" '$2 != id {print}' "$BINDINGS" > "$BINDINGS.tmp"
      mv "$BINDINGS.tmp" "$BINDINGS"
    fi
    echo "DORM_SCRIPT_DELETED=1" ;;
  --run)
    item "${2:-}"
    [ -f "$FILE" ] || die not_found 66
    command -v timeout >/dev/null 2>&1 || die timeout_unavailable 69
    if timeout 20 "$SH" "$FILE"; then
      record "$2" ok
      echo "DORM_SCRIPT_RESULT=success"
    else
      code=$?
      record "$2" failed
      echo "DORM_SCRIPT_RESULT=failed"
      exit "$code"
    fi ;;
  --bind)
    uuid "${2:-}" || die invalid_tag 64
    item "${3:-}"
    [ -f "$FILE" ] || die not_found 66
    tmp="$BINDINGS.tmp.$$"
    if [ -f "$BINDINGS" ]; then awk -F '|' -v tag="$2" '$1 != tag {print}' "$BINDINGS" > "$tmp"; else : > "$tmp"; fi
    printf '%s|%s\n' "$2" "$3" >> "$tmp"
    chmod 600 "$tmp"; mv "$tmp" "$BINDINGS"
    echo "DORM_SCRIPT_BOUND=1" ;;
  --unbind)
    uuid "${2:-}" || die invalid_tag 64
    if [ -f "$BINDINGS" ]; then
      awk -F '|' -v tag="$2" '$1 != tag {print}' "$BINDINGS" > "$BINDINGS.tmp"
      mv "$BINDINGS.tmp" "$BINDINGS"
    fi
    echo "DORM_SCRIPT_UNBOUND=1" ;;
  --bindings)
    [ ! -L "$BINDINGS" ] || die unsafe_bindings 70
    [ -f "$BINDINGS" ] && cat "$BINDINGS" || : ;;
  --resolve)
    uuid "${2:-}" || die invalid_tag 64
    [ -f "$BINDINGS" ] && [ ! -L "$BINDINGS" ] || die unbound 66
    id=$(awk -F '|' -v tag="$2" '$1 == tag {print $2; exit}' "$BINDINGS")
    uuid "$id" || die unbound 66
    item "$id"
    [ -f "$FILE" ] || die not_found 66
    echo "DORM_SCRIPT_ID=$id" ;;
  --logs)
    [ -f "$LOG" ] && tail -n 80 "$LOG" || : ;;
  *) die unknown_action 64 ;;
esac
