#!/system/bin/sh
# Manual KernelSU Action shortcut: one-shot module health check only.
MODDIR="${0%/*}"
exec /system/bin/sh "$MODDIR/run.sh" --status
