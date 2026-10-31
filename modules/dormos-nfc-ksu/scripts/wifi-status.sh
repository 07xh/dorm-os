#!/system/bin/sh
# Quick, read-only diagnostic. Never print IP/MAC/BSSID, secrets or private files.
set -eu
echo "DORM_KSU=ok"
echo "ACTION=wifi-status"
if [ -r /sys/class/net/wlan0/operstate ]; then
  state="$(cat /sys/class/net/wlan0/operstate 2>/dev/null || true)"
  case "$state" in
    up|unknown) echo "WLAN_INTERFACE=present" ;;
    *) echo "WLAN_INTERFACE=not_up" ;;
  esac
else
  echo "WLAN_INTERFACE=unavailable"
fi
if [ -d /sys/class/net/wlan0 ]; then
  echo "WLAN_DEVICE=found"
else
  echo "WLAN_DEVICE=missing"
fi
echo "LOGIN=not_attempted"
