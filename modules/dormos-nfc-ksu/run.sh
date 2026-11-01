#!/system/bin/sh
# One-shot KernelSU entry point. No service/daemon is installed.
MODDIR="${0%/*}"
if [ -x /system/bin/sh ]; then
  SHELL_BIN=/system/bin/sh
else
  SHELL_BIN=/bin/sh
fi
exec "$SHELL_BIN" "$MODDIR/dispatcher.sh" "$@"
