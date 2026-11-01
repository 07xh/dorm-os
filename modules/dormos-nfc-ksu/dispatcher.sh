#!/system/bin/sh
# DORM NFC Bridge dispatcher. Every action is fixed and allowlisted.
set -eu
umask 077
MODDIR="${0%/*}"
if [ -x /system/bin/sh ]; then SHELL_BIN=/system/bin/sh; else SHELL_BIN=/bin/sh; fi

if [ "${DORM_NFC_TESTING:-0}" = "1" ] && [ "$(id -u 2>/dev/null || echo 0)" != "0" ]; then
  DATA_DIR="${DORM_NFC_TEST_ROOT:?test root required}"
else
  DATA_DIR="/data/adb/dormos_nfc_bridge"
fi
CONFIG="$DATA_DIR/scenes.tsv"
ENABLED="$DATA_DIR/nfc_enabled"
LOG_FILE="$DATA_DIR/events.log"
HEADER="DORM_NFC_SCENES_V1"

fail() {
  echo "DORM_NFC_RESULT=failed"
  echo "DORM_NFC_ERROR=$1"
  exit "${2:-1}"
}
ensure_data_dir() {
  [ ! -L "$DATA_DIR" ] || fail "unsafe_data_directory" 70
  mkdir -p "$DATA_DIR" || fail "cannot_create_data_directory" 73
  chmod 700 "$DATA_DIR" 2>/dev/null || :
  [ -d "$DATA_DIR" ] || fail "data_directory_missing" 73
}
valid_id() { printf '%s' "$1" | grep -Eq '^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$'; }
valid_b64() { [ -z "$1" ] || printf '%s' "$1" | grep -Eq '^[A-Za-z0-9+/]*={0,2}$'; }
decode_b64() { printf '%s' "$1" | base64 -d 2>/dev/null; }

safe_https_url() {
  _url="$1"
  case "$_url" in https://*) ;; *) return 1 ;; esac
  printf '%s' "$_url" | grep -Eq '^https://[A-Za-z0-9:/?&=._#%+-]+$' || return 1
  _rest="${_url#https://}"
  _host="${_rest%%/*}"
  _host="${_host%%\?*}"
  _host="${_host%%#*}"
  case "$_host" in
    ""|*:*|*@*|localhost|*.local|*.internal|127.*|10.*|169.254.*|192.168.*|172.16.*|172.17.*|172.18.*|172.19.*|172.2[0-9].*|172.3[0-1].*) return 1 ;;
  esac
  case "$_host" in *.*) ;; *) return 1 ;; esac
  _lower=$(printf '%s' "$_url" | tr 'A-Z' 'a-z')
  case "$_lower" in
    *password=*|*passwd=*|*pwd=*|*token=*|*secret=*|*cookie=*|*authorization=*|*session=*|*%5ftoken=*|*%5fpassword=*|*%5fsecret=*) return 1 ;;
  esac
  return 0
}

valid_scene_file() {
  _vf_file="$1"
  [ -f "$_vf_file" ] || return 1
  [ "$(wc -c < "$_vf_file" | tr -d ' ')" -le 16384 ] || return 1
  [ "$(head -n 1 "$_vf_file")" = "$HEADER" ] || return 1
  _vf_seen="$DATA_DIR/.seen.$$"
  : > "$_vf_seen"
  _vf_count=0
  _vf_line_no=0
  while IFS='|' read -r _vf_id _vf_action _vf_target64 _vf_name64 _vf_extra || [ -n "${_vf_id:-}${_vf_action:-}${_vf_target64:-}${_vf_name64:-}${_vf_extra:-}" ]; do
    if [ "$_vf_line_no" -eq 0 ]; then _vf_line_no=1; continue; fi
    [ -z "${_vf_id:-}" ] && continue
    [ -z "${_vf_extra:-}" ] || { rm -f "$_vf_seen"; return 1; }
    valid_id "$_vf_id" || { rm -f "$_vf_seen"; return 1; }
    grep -Fxq "$_vf_id" "$_vf_seen" && { rm -f "$_vf_seen"; return 1; }
    printf '%s\n' "$_vf_id" >> "$_vf_seen"
    valid_b64 "${_vf_target64:-}" || { rm -f "$_vf_seen"; return 1; }
    valid_b64 "${_vf_name64:-}" || { rm -f "$_vf_seen"; return 1; }
    [ -n "${_vf_name64:-}" ] || { rm -f "$_vf_seen"; return 1; }
    _vf_name=$(decode_b64 "$_vf_name64") || { rm -f "$_vf_seen"; return 1; }
    [ -n "$_vf_name" ] || { rm -f "$_vf_seen"; return 1; }
    [ "$(printf '%s' "$_vf_name" | wc -c | tr -d ' ')" -le 160 ] || { rm -f "$_vf_seen"; return 1; }
    case "$_vf_action" in
      wifi-status|ncwu-check)
        [ -z "${_vf_target64:-}" ] || { rm -f "$_vf_seen"; return 1; } ;;
      open-url)
        [ -n "${_vf_target64:-}" ] || { rm -f "$_vf_seen"; return 1; }
        _vf_target=$(decode_b64 "$_vf_target64") || { rm -f "$_vf_seen"; return 1; }
        safe_https_url "$_vf_target" || { rm -f "$_vf_seen"; return 1; } ;;
      dorm-scene)
        [ -n "${_vf_target64:-}" ] || { rm -f "$_vf_seen"; return 1; }
        _vf_target=$(decode_b64 "$_vf_target64") || { rm -f "$_vf_seen"; return 1; }
        case "$_vf_target" in study|rest|dev|away) ;; *) rm -f "$_vf_seen"; return 1 ;; esac ;;
      *)
        rm -f "$_vf_seen"
        return 1 ;;
    esac
    _vf_count=$((_vf_count + 1))
    [ "$_vf_count" -le 32 ] || { rm -f "$_vf_seen"; return 1; }
  done < "$_vf_file"
  rm -f "$_vf_seen"
  return 0
}
scene_count() {
  if [ ! -f "$CONFIG" ]; then printf '0'; else
    awk -F '|' 'NR > 1 && NF >= 4 { n++ } END { print n+0 }' "$CONFIG"
  fi
}
log_event() {
  _now=$(date +%s 2>/dev/null || printf '0')
  printf '%s|%s|%s|%s\n' "$_now" "$1" "$2" "$3" >> "$LOG_FILE"
  _lines=$(wc -l < "$LOG_FILE" | tr -d ' ')
  if [ "$_lines" -gt 100 ]; then
    tail -n 100 "$LOG_FILE" > "$LOG_FILE.tmp"
    mv "$LOG_FILE.tmp" "$LOG_FILE"
    chmod 600 "$LOG_FILE" 2>/dev/null || :
  fi
}
set_enabled() {
  case "${1:-}" in 0|1) ;; *) fail "invalid_enabled_value" 64 ;; esac
  printf '%s\n' "$1" > "$ENABLED.tmp"
  chmod 600 "$ENABLED.tmp"
  mv "$ENABLED.tmp" "$ENABLED"
  echo "NFC_ENABLED=$1"
}
save_config() {
  _encoded="${1:-}"
  [ -n "$_encoded" ] || fail "empty_config" 64
  [ "${#_encoded}" -le 24000 ] || fail "config_too_large" 64
  printf '%s' "$_encoded" | grep -Eq '^[A-Za-z0-9+/]+={0,2}$' || fail "invalid_base64" 64
  _tmp="$DATA_DIR/scenes.tsv.tmp.$$"
  decode_b64 "$_encoded" > "$_tmp" || { rm -f "$_tmp"; fail "base64_decode_failed" 65; }
  valid_scene_file "$_tmp" || { rm -f "$_tmp"; fail "scene_config_validation_failed" 65; }
  chmod 600 "$_tmp"
  mv "$_tmp" "$CONFIG"
  echo "DORM_CONFIG=saved"
  echo "SCENE_COUNT=$(scene_count)"
}
run_action() {
  _action="$1"
  _target64="$2"
  case "$_action" in
    wifi-status) "$SHELL_BIN" "$MODDIR/scripts/wifi-status.sh" ;;
    ncwu-check) "$SHELL_BIN" "$MODDIR/scripts/ncwu-check.sh" ;;
    open-url)
      _url=$(decode_b64 "$_target64") || return 65
      safe_https_url "$_url" || return 65
      am start -a android.intent.action.VIEW -d "$_url" >/dev/null 2>&1 || return 70
      echo "BROWSER=launch_requested" ;;
    dorm-scene)
      _scene=$(decode_b64 "$_target64") || return 65
      case "$_scene" in study|rest|dev|away) ;; *) return 65 ;; esac
      _url="https://dorm-os-dk9.pages.dev/?dormScene=$_scene#scenes"
      am start -a android.intent.action.VIEW -d "$_url" >/dev/null 2>&1 || return 70
      echo "DORM_SCENE=launch_requested" ;;
    *) return 64 ;;
  esac
}
dispatch_scene() {
  _source="$1"
  _id="$2"
  valid_id "$_id" || fail "invalid_scene_id" 64
  if [ "$_source" = "nfc" ]; then
    [ "$(cat "$ENABLED" 2>/dev/null || printf '0')" = "1" ] || fail "nfc_trigger_disabled" 77
  fi
  # NFC script binding takes priority over older built-in scene actions.
  # A cloned tag is never allowed to provide a script path or shell argument.
  if [ "$_source" = "nfc" ]; then
    if _script_result=$("$SHELL_BIN" "$MODDIR/script-manager.sh" --resolve "$_id" 2>/dev/null); then
      _script_id=$(printf '%s\n' "$_script_result" | sed -n 's/^DORM_SCRIPT_ID=//p' | head -n 1)
      if "$SHELL_BIN" "$MODDIR/script-manager.sh" --run "$_script_id"; then
        log_event "$_id" "personal-script" success
        echo "DORM_NFC_RESULT=success"
        echo "DORM_ACTION=personal-script"
        return 0
      else
        _rc=$?
        log_event "$_id" "personal-script" failed
        echo "DORM_NFC_RESULT=failed"
        exit "$_rc"
      fi
    fi
  fi
  [ -f "$CONFIG" ] || fail "scene_config_missing" 66
  valid_scene_file "$CONFIG" || fail "scene_config_invalid" 65
  _entry=$(awk -F '|' -v id="$_id" '$1 == id { print $2 "|" $3; exit }' "$CONFIG")
  [ -n "$_entry" ] || fail "scene_not_registered" 66
  IFS='|' read -r _action _target64 <<EOF
$_entry
EOF
  if run_action "$_action" "${_target64:-}"; then
    log_event "$_id" "$_action" "success"
    echo "DORM_NFC_RESULT=success"
    echo "DORM_SCENE_ID=$_id"
    echo "DORM_ACTION=$_action"
  else
    _rc=$?
    log_event "$_id" "$_action" "failed"
    echo "DORM_NFC_RESULT=failed"
    echo "DORM_SCENE_ID=$_id"
    echo "DORM_ACTION=$_action"
    exit "$_rc"
  fi
}

ensure_data_dir
case "${1:-}" in
  --status)
    _enabled=$(cat "$ENABLED" 2>/dev/null || printf '0')
    case "$_enabled" in 0|1) ;; *) _enabled=0 ;; esac
    _receiver="missing"
    if command -v pm >/dev/null 2>&1 && pm path xyz.dormos.nfc 2>/dev/null | grep -q '^package:'; then _receiver="installed"; fi
    echo "DORM_MODULE=ready"
    echo "NFC_ENABLED=$_enabled"
    echo "SCENE_COUNT=$(scene_count)"
    echo "NFC_RECEIVER=$_receiver"
    echo "BACKGROUND_SERVICE=none"
    ;;
  --list)
    if [ -f "$CONFIG" ]; then valid_scene_file "$CONFIG" || fail "scene_config_invalid" 65; cat "$CONFIG"; else printf '%s\n' "$HEADER"; fi
    ;;
  --logs)
    if [ -f "$LOG_FILE" ]; then tail -n 100 "$LOG_FILE"; else echo "暂无执行记录。"; fi
    ;;
  --clear-logs)
    : > "$LOG_FILE"
    chmod 600 "$LOG_FILE"
    echo "LOGS=CLEARED"
    ;;
  --enable) set_enabled "${2:-}" ;;
  --save-b64) save_config "${2:-}" ;;
  --run)
    [ -n "${2:-}" ] || fail "scene_id_required" 64
    dispatch_scene "manual" "$2" ;;
  --nfc)
    [ -n "${2:-}" ] || fail "scene_id_required" 64
    dispatch_scene "nfc" "$2" ;;
  --legacy-ncwu)
    [ "$(cat "$ENABLED" 2>/dev/null || printf '0')" = "1" ] || fail "nfc_trigger_disabled" 77
    "$SHELL_BIN" "$MODDIR/scripts/ncwu-check.sh"
    log_event "legacy" "ncwu-check" "success"
    echo "DORM_NFC_RESULT=success"
    echo "DORM_ACTION=ncwu-check" ;;
  --script-list) exec "$SHELL_BIN" "$MODDIR/script-manager.sh" --list ;;
  --script-read) exec "$SHELL_BIN" "$MODDIR/script-manager.sh" --read "${2:-}" ;;
  --script-save) exec "$SHELL_BIN" "$MODDIR/script-manager.sh" --save "${2:-}" "${3:-}" "${4:-}" ;;
  --script-delete) exec "$SHELL_BIN" "$MODDIR/script-manager.sh" --delete "${2:-}" ;;
  --script-run) exec "$SHELL_BIN" "$MODDIR/script-manager.sh" --run "${2:-}" ;;
  --script-bind) exec "$SHELL_BIN" "$MODDIR/script-manager.sh" --bind "${2:-}" "${3:-}" ;;
  --script-unbind) exec "$SHELL_BIN" "$MODDIR/script-manager.sh" --unbind "${2:-}" ;;
  --script-bindings) exec "$SHELL_BIN" "$MODDIR/script-manager.sh" --bindings ;;
  --script-logs) exec "$SHELL_BIN" "$MODDIR/script-manager.sh" --logs ;;
  --write-tag)
    _id="${2:-}"
    valid_id "$_id" || fail "invalid_scene_id" 64
    command -v pm >/dev/null 2>&1 && pm path xyz.dormos.nfc >/dev/null 2>&1 || fail "nfc_receiver_not_installed" 69
    am start -n xyz.dormos.nfc/.MainActivity -a xyz.dormos.nfc.action.WRITE_TAG --es scene_id "$_id" ;;
  *)
    echo "用法：run.sh --status | --list | --logs | --clear-logs | --enable 0/1 | --save-b64 BASE64 | --run UUID | --nfc UUID | --legacy-ncwu | --write-tag UUID" ;;
esac
