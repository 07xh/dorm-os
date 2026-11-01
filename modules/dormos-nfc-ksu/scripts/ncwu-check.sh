#!/system/bin/sh
# Read-only Ncwu identification. It never submits credentials or probes a third-party URL.
set -u
echo "DORM_KSU=ok"
echo "ACTION=ncwu-check"
_SSID="$(dumpsys wifi 2>/dev/null | sed -n 's/.*SSID: "\([^"]*\)".*/\1/p' | head -n 1)"
if [ "$_SSID" = "Ncwu" ]; then
  echo "NCWU_SSID=match"
elif [ -n "$_SSID" ] && [ "$_SSID" != "<unknown ssid>" ]; then
  echo "NCWU_SSID=mismatch"
else
  echo "NCWU_SSID=unknown"
fi
_IP="$(ip -4 -o addr show dev wlan0 2>/dev/null | awk '{print $4}' | head -n 1)"
if [ -n "$_IP" ]; then
  echo "NCWU_IPV4=present"
else
  echo "NCWU_IPV4=missing"
fi
echo "LOGIN=not_attempted"
exit 0
