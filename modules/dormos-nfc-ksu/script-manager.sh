#!/system/bin/sh
# KernelSU-only personal SH library, invoked only on demand.
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
SCENES="$BASE/scenes.tsv"
LOG="$BASE/script_events.log"
ENABLED="$BASE/nfc_enabled"
LOCK="$BASE/script-run.lock"
LOCK_DIR="$BASE/script-run.lock.d"

die() { echo "DORM_SCRIPT_ERROR=$1"; exit "${2:-1}"; }
uuid() { printf '%s' "$1" | grep -Eq '^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$'; }
b64() { printf '%s' "$1" | grep -Eq '^[A-Za-z0-9+/]+={0,2}$'; }

[ ! -L "$BASE" ] && [ ! -L "$SCRIPTS" ] && [ ! -L "$BINDINGS" ] && [ ! -L "$SCENES" ] && [ ! -L "$LOG" ] && [ ! -L "$ENABLED" ] && [ ! -L "$LOCK" ] && [ ! -L "$LOCK_DIR" ] || die unsafe_dir 70
mkdir -p "$SCRIPTS" || die storage_unavailable 73
chmod 700 "$BASE" "$SCRIPTS" 2>/dev/null || die storage_permissions_failed 73

item() {
  uuid "$1" || die invalid_id 64
  FILE="$SCRIPTS/$1.sh"
  NAME="$SCRIPTS/$1.name64"
  [ ! -L "$FILE" ] && [ ! -L "$NAME" ] || die symlink_refused 70
}
scene_active() {
  value=1
  if [ -f "$SCENES" ] && [ ! -L "$SCENES" ]; then
    value=$(awk -F '|' -v tag="$1" '$1 == tag {print $3; exit}' "$SCENES")
  fi
  case "$value" in 0|1) printf '%s' "$value" ;; *) printf '1' ;; esac
}
scene_name64() {
  if [ -f "$SCENES" ] && [ ! -L "$SCENES" ]; then
    awk -F '|' -v tag="$1" '$1 == tag {print $2; exit}' "$SCENES"
  fi
}
enabled_value() {
  value=$(cat "$ENABLED" 2>/dev/null || printf '0')
  case "$value" in 0|1) printf '%s' "$value" ;; *) printf '0' ;; esac
}
set_enabled() {
  value="${1:-}"
  case "$value" in 0|1) ;; *) die invalid_enabled_value 64 ;; esac
  tmp=$(mktemp "$ENABLED.tmp.XXXXXX") || die storage_unavailable 73
  printf '%s\n' "$value" > "$tmp"
  chmod 600 "$tmp" || { rm -f "$tmp"; die storage_permissions_failed 73; }
  mv "$tmp" "$ENABLED" || { rm -f "$tmp"; die storage_unavailable 73; }
  echo "NFC_ENABLED=$value"
}
valid_scene_name() {
  value=$(printf '%s' "$1" | base64 -d 2>/dev/null) || return 1
  [ -n "$value" ] || return 1
  [ "$(printf '%s' "$value" | wc -c | tr -d ' ')" -le 120 ] || return 1
  case "$value" in *'|'*|*'/'*|*'\\'*) return 1 ;; esac
  ! printf '%s' "$value" | grep -q '[[:cntrl:]]'
}
record() {
  printf '%s|%s|%s\n' "$(date +%s)" "$1" "$2" >> "$LOG"
  tmp="$(mktemp "$LOG.tmp.XXXXXX")" || die log_temp_failed 73
  tail -n 80 "$LOG" > "$tmp"
  chmod 600 "$tmp"
  mv "$tmp" "$LOG"
}
RUNNER_PID=""
LOCK_MODE=""
LOCK_HELD=0
flock_supported() {
  command -v flock >/dev/null 2>&1 || return 1
  probe="$(mktemp "$BASE/.flock-probe.XXXXXX")" || return 1
  if ( exec 8>"$probe"; flock -n 8 ) 2>/dev/null; then
    rm -f "$probe"
    return 0
  fi
  rm -f "$probe"
  return 1
}
cleanup_run_lock() {
  if [ -n "$RUNNER_PID" ]; then
    kill "$RUNNER_PID" 2>/dev/null || :
    wait "$RUNNER_PID" 2>/dev/null || :
  fi
  if [ "$LOCK_HELD" = "1" ]; then
    case "$LOCK_MODE" in
      flock) exec 9>&- ;;
      mkdir) rmdir "$LOCK_DIR" 2>/dev/null || : ;;
    esac
  fi
}
acquire_run_lock() {
  [ ! -L "$LOCK" ] && [ ! -L "$LOCK_DIR" ] && [ ! -L "$ENABLED" ] || die unsafe_run_lock 70
  if [ "${DORM_NFC_FORCE_MKDIR_LOCK:-0}" != "1" ] && flock_supported; then
    : >> "$LOCK"
    chmod 600 "$LOCK"
    exec 9>>"$LOCK"
    if ! flock -n 9 2>/dev/null; then
      exec 9>&-
      die script_busy 75
    fi
    LOCK_MODE=flock
  else
    if ! mkdir "$LOCK_DIR" 2>/dev/null; then
      die script_busy_or_stale_lock 75
    fi
    LOCK_MODE=mkdir
  fi
  LOCK_HELD=1
  trap 'cleanup_run_lock' EXIT
  trap 'exit 143' HUP INT TERM
}
run_script() {
  script_id="$1"
  item "$script_id"
  [ -f "$FILE" ] || die not_found 66
  command -v timeout >/dev/null 2>&1 || die timeout_unavailable 69
  "$SH" -n "$FILE" || die syntax_error 65
  timeout 20 "$SH" "$FILE" &
  RUNNER_PID=$!
  if wait "$RUNNER_PID"; then
    RUNNER_PID=""
    record "$script_id" ok
    echo "DORM_SCRIPT_RESULT=success"
  else
    code=$?
    RUNNER_PID=""
    record "$script_id" failed
    echo "DORM_SCRIPT_RESULT=failed"
    exit "$code"
  fi
}
case "${1:-}" in
  --enable)
    acquire_run_lock
    set_enabled "${2:-}"
    ;;
  --list)
    for f in "$SCRIPTS"/*.sh; do
      [ -f "$f" ] && [ ! -L "$f" ] || continue
      id="${f##*/}"; id="${id%.sh}"
      uuid "$id" || continue
      [ -f "$SCRIPTS/$id.name64" ] && [ ! -L "$SCRIPTS/$id.name64" ] || continue
      printf '%s|%s|%s\n' "$id" "$(cat "$SCRIPTS/$id.name64")" "$(wc -c < "$f" | tr -d ' ')"
    done
    ;;
  --read)
    item "${2:-}"
    [ -f "$FILE" ] && [ -f "$NAME" ] || die not_found 66
    echo "SCRIPT_NAME64=$(cat "$NAME")"
    printf 'SCRIPT_BODY64='
    base64 < "$FILE" | tr -d '\r\n'
    printf '\n'
    ;;
  --save)
    acquire_run_lock
    item "${2:-}"
    title="${3:-}"; body="${4:-}"
    [ -n "$title" ] && [ "${#title}" -le 360 ] && b64 "$title" || die bad_title 64
    [ -n "$body" ] && [ "${#body}" -le 24000 ] && b64 "$body" || die bad_body 64
    display=$(printf '%s' "$title" | base64 -d 2>/dev/null) || die bad_title64 65
    [ -n "$display" ] && [ "$(printf '%s' "$display" | wc -c | tr -d ' ')" -le 120 ] || die title_too_long 64
    case "$display" in
      *.sh|*.SH|*.Sh|*.sH) ;;
      *) die invalid_script_name 64 ;;
    esac
    case "$display" in
      */*|*\\*|*'|'*) die invalid_script_name 64 ;;
    esac
    if printf '%s' "$display" | grep -q '[[:cntrl:]]'; then die invalid_script_name 64; fi
    tmp="$(mktemp "$SCRIPTS/.script.XXXXXX")" || die script_temp_failed 73
    printf '%s' "$body" | base64 -d > "$tmp" 2>/dev/null || { rm -f "$tmp"; die bad_body64 65; }
    [ "$(wc -c < "$tmp" | tr -d ' ')" -le 16384 ] || { rm -f "$tmp"; die body_too_large 64; }
    "$SH" -n "$tmp" || { rm -f "$tmp"; die syntax_error 65; }
    chmod 600 "$tmp"
    mv "$tmp" "$FILE"
    printf '%s\n' "$title" > "$NAME"
    chmod 600 "$NAME"
    echo "DORM_SCRIPT_SAVED=1"
    ;;
  --rename)
    acquire_run_lock
    item "${2:-}"
    [ -f "$FILE" ] && [ -f "$NAME" ] || die not_found 66
    title="${3:-}"
    [ -n "$title" ] && [ "${#title}" -le 360 ] && b64 "$title" || die bad_title 64
    display=$(printf '%s' "$title" | base64 -d 2>/dev/null) || die bad_title64 65
    [ -n "$display" ] && [ "$(printf '%s' "$display" | wc -c | tr -d ' ')" -le 120 ] || die bad_title 64
    case "$display" in *.sh|*.SH|*.Sh|*.sH) ;; *) die invalid_script_name 64 ;; esac
    case "$display" in */*|*\\*|*'|'*) die invalid_script_name 64 ;; esac
    if printf '%s' "$display" | grep -q '[[:cntrl:]]'; then die invalid_script_name 64; fi
    tmp="$(mktemp "$SCRIPTS/.name.XXXXXX")" || die name_temp_failed 73
    printf '%s\n' "$title" > "$tmp"
    chmod 600 "$tmp" || { rm -f "$tmp"; die storage_permissions_failed 73; }
    mv "$tmp" "$NAME" || { rm -f "$tmp"; die storage_unavailable 73; }
    echo "DORM_SCRIPT_RENAMED=1"
    ;;
  --delete)
    acquire_run_lock
    item "${2:-}"
    [ -f "$FILE" ] || die not_found 66
    if [ -f "$BINDINGS" ] && [ -f "$SCENES" ]; then
      tmp="$(mktemp "$SCENES.tmp.XXXXXX")" || die scene_temp_failed 73
      awk -F '|' -v id="$2" 'FILENAME == ARGV[1] {if ($2 == id) removed[$1]=1; next} !($1 in removed)' \
        "$BINDINGS" "$SCENES" > "$tmp" || { rm -f "$tmp"; die scene_update_failed 73; }
      chmod 600 "$tmp" || { rm -f "$tmp"; die storage_permissions_failed 73; }
      mv "$tmp" "$SCENES" || { rm -f "$tmp"; die storage_unavailable 73; }
    fi
    if [ -f "$BINDINGS" ]; then
      tmp="$(mktemp "$BINDINGS.tmp.XXXXXX")" || die binding_temp_failed 73
      awk -F '|' -v id="$2" '$2 != id {print}' "$BINDINGS" > "$tmp" || { rm -f "$tmp"; die binding_update_failed 73; }
      chmod 600 "$tmp" || { rm -f "$tmp"; die storage_permissions_failed 73; }
      mv "$tmp" "$BINDINGS" || { rm -f "$tmp"; die storage_unavailable 73; }
    fi
    rm -f "$FILE" "$NAME" "$SCRIPTS/$2.folder"
    echo "DORM_SCRIPT_DELETED=1"
    ;;
  --run)
    acquire_run_lock
    run_script "${2:-}"
    ;;
  --run-bound)
    acquire_run_lock
    uuid "${2:-}" || die invalid_tag 64
    [ "$(enabled_value)" = "1" ] || die nfc_trigger_disabled 77
    [ -f "$BINDINGS" ] && [ ! -L "$BINDINGS" ] || die unbound 66
    script_id=$(awk -F '|' -v tag="$2" '$1 == tag {print $2; exit}' "$BINDINGS")
    uuid "$script_id" || die unbound 66
    [ "$(scene_active "$2")" = "1" ] || die scene_disabled 77
    item "$script_id"
    [ -f "$FILE" ] || die not_found 66
    run_script "$script_id"
    echo "DORM_SCRIPT_ID=$script_id"
    ;;
  --bind)
    acquire_run_lock
    uuid "${2:-}" || die invalid_tag 64
    item "${3:-}"
    [ -f "$FILE" ] || die not_found 66
    tmp="$(mktemp "$BINDINGS.tmp.XXXXXX")" || die binding_temp_failed 73
    if [ -f "$BINDINGS" ]; then
      awk -F '|' -v tag="$2" '$1 != tag {print}' "$BINDINGS" > "$tmp" || { rm -f "$tmp"; die binding_update_failed 73; }
    else
      : > "$tmp"
    fi
    printf '%s|%s\n' "$2" "$3" >> "$tmp"
    chmod 600 "$tmp" || { rm -f "$tmp"; die storage_permissions_failed 73; }
    mv "$tmp" "$BINDINGS" || { rm -f "$tmp"; die storage_unavailable 73; }
    echo "DORM_SCRIPT_BOUND=1"
    ;;
  --unbind)
    acquire_run_lock
    uuid "${2:-}" || die invalid_tag 64
    if [ -f "$BINDINGS" ]; then
      tmp="$(mktemp "$BINDINGS.tmp.XXXXXX")" || die binding_temp_failed 73
      awk -F '|' -v tag="$2" '$1 != tag {print}' "$BINDINGS" > "$tmp" || { rm -f "$tmp"; die binding_update_failed 73; }
      chmod 600 "$tmp" || { rm -f "$tmp"; die storage_permissions_failed 73; }
      mv "$tmp" "$BINDINGS" || { rm -f "$tmp"; die storage_unavailable 73; }
    fi
    if [ -f "$SCENES" ]; then
      tmp="$(mktemp "$SCENES.tmp.XXXXXX")" || die scene_temp_failed 73
      awk -F '|' -v tag="$2" '$1 != tag {print}' "$SCENES" > "$tmp" || { rm -f "$tmp"; die scene_update_failed 73; }
      chmod 600 "$tmp" || { rm -f "$tmp"; die storage_permissions_failed 73; }
      mv "$tmp" "$SCENES" || { rm -f "$tmp"; die storage_unavailable 73; }
    fi
    echo "DORM_SCRIPT_UNBOUND=1"
    ;;
  --bindings)
    [ ! -L "$BINDINGS" ] || die unsafe_bindings 70
    [ -f "$BINDINGS" ] && cat "$BINDINGS" || :
    ;;
  --scene-list)
    [ ! -L "$BINDINGS" ] && [ ! -L "$SCENES" ] || die unsafe_scenes 70
    if [ -f "$BINDINGS" ]; then
      while IFS='|' read -r tag script_id; do
        uuid "$tag" && uuid "$script_id" || continue
        item "$script_id"
        [ -f "$FILE" ] && [ -f "$NAME" ] || continue
        display=$(scene_name64 "$tag")
        [ -n "$display" ] || display=$(cat "$NAME")
        active=$(scene_active "$tag")
        printf '%s|%s|%s|%s\n' "$tag" "$script_id" "$display" "$active"
      done < "$BINDINGS"
    fi
    ;;
  --scene-save)
    acquire_run_lock
    uuid "${2:-}" || die invalid_tag 64
    display64="${3:-}"
    [ -n "$display64" ] && [ "${#display64}" -le 360 ] && b64 "$display64" && valid_scene_name "$display64" || die invalid_scene_name 64
    [ -f "$BINDINGS" ] && awk -F '|' -v tag="$2" '$1 == tag {found=1} END {exit !found}' "$BINDINGS" || die unbound 66
    active=$(scene_active "$2")
    tmp="$(mktemp "$SCENES.tmp.XXXXXX")" || die scene_temp_failed 73
    if [ -f "$SCENES" ]; then awk -F '|' -v tag="$2" '$1 != tag {print}' "$SCENES" > "$tmp" || { rm -f "$tmp"; die scene_update_failed 73; }; else : > "$tmp"; fi
    printf '%s|%s|%s\n' "$2" "$display64" "$active" >> "$tmp"
    chmod 600 "$tmp" || { rm -f "$tmp"; die storage_permissions_failed 73; }
    mv "$tmp" "$SCENES" || { rm -f "$tmp"; die storage_unavailable 73; }
    echo "DORM_SCENE_SAVED=1"
    ;;
  --scene-enable)
    acquire_run_lock
    uuid "${2:-}" || die invalid_tag 64
    enabled="${3:-}"
    case "$enabled" in 0|1) ;; *) die invalid_enabled_value 64 ;; esac
    [ -f "$BINDINGS" ] && awk -F '|' -v tag="$2" '$1 == tag {found=1} END {exit !found}' "$BINDINGS" || die unbound 66
    display=$(scene_name64 "$2")
    if [ -z "$display" ]; then
      script_id=$(awk -F '|' -v tag="$2" '$1 == tag {print $2; exit}' "$BINDINGS")
      item "$script_id"
      [ -f "$NAME" ] || die not_found 66
      display=$(cat "$NAME")
    fi
    tmp="$(mktemp "$SCENES.tmp.XXXXXX")" || die scene_temp_failed 73
    if [ -f "$SCENES" ]; then awk -F '|' -v tag="$2" '$1 != tag {print}' "$SCENES" > "$tmp" || { rm -f "$tmp"; die scene_update_failed 73; }; else : > "$tmp"; fi
    printf '%s|%s|%s\n' "$2" "$display" "$enabled" >> "$tmp"
    chmod 600 "$tmp" || { rm -f "$tmp"; die storage_permissions_failed 73; }
    mv "$tmp" "$SCENES" || { rm -f "$tmp"; die storage_unavailable 73; }
    echo "DORM_SCENE_ENABLED=$enabled"
    ;;
  --resolve)
    uuid "${2:-}" || die invalid_tag 64
    [ -f "$BINDINGS" ] && [ ! -L "$BINDINGS" ] || die unbound 66
    id=$(awk -F '|' -v tag="$2" '$1 == tag {print $2; exit}' "$BINDINGS")
    uuid "$id" || die unbound 66
    item "$id"
    [ -f "$FILE" ] || die not_found 66
    echo "DORM_SCRIPT_ID=$id"
    ;;
  --logs)
    [ -f "$LOG" ] && tail -n 80 "$LOG" || :
    ;;
  --clear-logs)
    acquire_run_lock
    tmp="$(mktemp "$LOG.clear.XXXXXX")" || die log_temp_failed 73
    : > "$tmp"
    chmod 600 "$tmp" || { rm -f "$tmp"; die storage_permissions_failed 73; }
    mv "$tmp" "$LOG" || { rm -f "$tmp"; die storage_unavailable 73; }
    echo "DORM_SCRIPT_LOGS=CLEARED"
    ;;
  *)
    die unknown_action 64
    ;;
esac
