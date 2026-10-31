#!/system/bin/sh
# Optional manual health check from KernelSU Manager Action button.
MODDIR="${0%/*}"
exec /system/bin/sh "$MODDIR/run.sh" wifi-status
