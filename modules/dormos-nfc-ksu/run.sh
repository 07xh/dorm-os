#!/system/bin/sh
# Called ONLY by user-approved DORM OS APK after a known local NFC profile matches.
# Fixed allowlist, never evaluate NFC payloads, filenames or user shell text.
set -eu
MODDIR="${0%/*}"
case "${1:-}" in
  wifi-status)
    exec /system/bin/sh "$MODDIR/scripts/wifi-status.sh"
    ;;
  *)
    echo "DORM_KSU=unsupported_action"
    exit 64
    ;;
esac
