#!/system/bin/sh
# Minimal personal NFC-to-SH dispatcher for KernelSU. No daemon, polling or boot hook.
set -eu
umask 077
MODDIR="${0%/*}"
if [ -x /system/bin/sh ]; then SH=/system/bin/sh; else SH=/bin/sh; fi

if [ "${DORM_NFC_TESTING:-0}" = "1" ] && [ "$(id -u 2>/dev/null || echo 0)" != "0" ]; then
  DATA_DIR="${DORM_NFC_TEST_ROOT:?test root required}"
else
  DATA_DIR=/data/adb/dormos_nfc_bridge
fi
ENABLED="$DATA_DIR/nfc_enabled"
SCRIPTS="$DATA_DIR/scripts"
BINDINGS="$DATA_DIR/script_bindings.tsv"
die() { echo "DORM_NFC_RESULT=failed"; echo "DORM_NFC_ERROR=$1"; exit "${2:-1}"; }
valid_id() { printf '%s' "$1" | grep -Eq '^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$'; }
ensure_data() {
  [ ! -L "$DATA_DIR" ] && [ ! -L "$SCRIPTS" ] && [ ! -L "$BINDINGS" ] && \
    [ ! -L "$ENABLED" ] && [ ! -L "$DATA_DIR/scenes.tsv" ] || die unsafe_storage 70
  mkdir -p "$SCRIPTS" || die storage_unavailable 73
  chmod 700 "$DATA_DIR" "$SCRIPTS" 2>/dev/null || die storage_permissions_failed 73
}
enabled_value() {
  _value=$(cat "$ENABLED" 2>/dev/null || printf '0')
  case "$_value" in 0|1) printf '%s' "$_value" ;; *) printf '0' ;; esac
}
script_count() {
  # Status is a hot path. Count valid local entries without starting another
  # manager process and awk just for a UI badge.
  _count=0
  for _script in "$SCRIPTS"/*.sh; do
    [ -f "$_script" ] && [ ! -L "$_script" ] || continue
    _id="${_script##*/}"; _id="${_id%.sh}"
    valid_id "$_id" || continue
    [ -f "$SCRIPTS/$_id.name64" ] && [ ! -L "$SCRIPTS/$_id.name64" ] || continue
    _count=$((_count + 1))
  done
  printf '%s\n' "$_count"
}
binding_count() {
  [ -f "$BINDINGS" ] || { echo 0; return; }
  awk -F '|' 'NF == 2 {n++} END {print n+0}' "$BINDINGS"
}
ensure_data
case "${1:-}" in
  --status)
    echo "DORM_MODULE=ready"
    echo "NFC_ENABLED=$(enabled_value)"
    echo "SCRIPT_COUNT=$(script_count)"
    echo "BINDING_COUNT=$(binding_count)"
    echo "BACKGROUND_SERVICE=none"
    ;;
  --enable) exec "$SH" "$MODDIR/script-manager.sh" --enable "${2:-}" ;;
  --nfc)
    tag_id="${2:-}"
    valid_id "$tag_id" || die invalid_tag_id 64
    [ "$(enabled_value)" = "1" ] || die nfc_trigger_disabled 77
    if "$SH" "$MODDIR/script-manager.sh" --run-bound "$tag_id"; then
      echo "DORM_NFC_RESULT=success"
      echo "DORM_ACTION=personal-script"
    else
      code=$?
      echo "DORM_NFC_RESULT=failed"
      echo "DORM_ACTION=personal-script"
      exit "$code"
    fi
    ;;
  --folder-list) exec "$SH" "$MODDIR/folder-manager.sh" --list ;;
  --folder-create) exec "$SH" "$MODDIR/folder-manager.sh" --create "${2:-}" "${3:-}" "${4:-}" ;;
  --folder-rename) exec "$SH" "$MODDIR/folder-manager.sh" --rename "${2:-}" "${3:-}" ;;
  --folder-move) exec "$SH" "$MODDIR/folder-manager.sh" --move "${2:-}" "${3:-}" ;;
  --folder-delete) exec "$SH" "$MODDIR/folder-manager.sh" --delete "${2:-}" ;;
  --script-folders) exec "$SH" "$MODDIR/folder-manager.sh" --script-folders ;;
  --script-move) exec "$SH" "$MODDIR/folder-manager.sh" --script-move "${2:-}" "${3:-}" ;;
  --script-rename) exec "$SH" "$MODDIR/script-manager.sh" --rename "${2:-}" "${3:-}" ;;
  --script-list) exec "$SH" "$MODDIR/script-manager.sh" --list ;;
  --script-read) exec "$SH" "$MODDIR/script-manager.sh" --read "${2:-}" ;;
  --script-save) exec "$SH" "$MODDIR/script-manager.sh" --save "${2:-}" "${3:-}" "${4:-}" ;;
  --script-delete) exec "$SH" "$MODDIR/script-manager.sh" --delete "${2:-}" ;;
  --script-run) exec "$SH" "$MODDIR/script-manager.sh" --run "${2:-}" ;;
  --script-bind) exec "$SH" "$MODDIR/script-manager.sh" --bind "${2:-}" "${3:-}" ;;
  --script-unbind) exec "$SH" "$MODDIR/script-manager.sh" --unbind "${2:-}" ;;
  --script-bindings) exec "$SH" "$MODDIR/script-manager.sh" --bindings ;;
  --scene-list) exec "$SH" "$MODDIR/script-manager.sh" --scene-list ;;
  --scene-save) exec "$SH" "$MODDIR/script-manager.sh" --scene-save "${2:-}" "${3:-}" ;;
  --scene-enable) exec "$SH" "$MODDIR/script-manager.sh" --scene-enable "${2:-}" "${3:-}" ;;
  --script-logs) exec "$SH" "$MODDIR/script-manager.sh" --logs ;;
  --script-clear-logs) exec "$SH" "$MODDIR/script-manager.sh" --clear-logs ;;
  *)
    die unknown_action 64
    ;;
esac
