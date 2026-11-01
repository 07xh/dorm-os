#!/system/bin/sh
# Read-only Wi-Fi interface check. No network request, credentials or polling.
set -u
echo "DORM_KSU=ok"
echo "ACTION=wifi-status"
if [ -d /sys/class/net/wlan0 ]; then
  echo "WLAN_DEVICE=found"
else
  echo "WLAN_DEVICE=missing"
fi
if [ -r /sys/class/net/wlan0/operstate ]; then
  _state="$(cat /sys/class/net/wlan0/operstate 2>/dev/null || printf 'unknown')"
  case "$_state" in
    up|unknown) echo "WLAN_INTERFACE=present" ;;
    *) echo "WLAN_INTERFACE=not_up" ;;
  esac
else
  echo "WLAN_INTERFACE=unavailable"
fi
echo "LOGIN=not_attempted"
exit 0
