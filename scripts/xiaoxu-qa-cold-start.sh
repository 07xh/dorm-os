#!/usr/bin/env bash
set -euo pipefail
gradle -p apps/xiaoxu-android :app:assembleDebug -PxiaoxuIsolatedQa=true --no-daemon
APK=apps/xiaoxu-android/app/build/outputs/apk/debug/app-debug.apk
test -s "$APK"
adb install -r "$APK"
PKG=io.github.x07xh.xiaoxu.unifiedqa
for pass in 1 2; do
  adb shell am force-stop "$PKG" || true
  adb logcat -c || true
  echo "Cold-start attempt $pass"
  adb shell am start -W -n "$PKG/io.github.x07xh.xiaoxu.MainActivity"
  sleep 8
  PID=$(adb shell pidof "$PKG" | tr -d '\r' || true)
  if [ -z "$PID" ]; then
    echo "::error::Isolated QA Android process exited after launch"
    adb logcat -d -b crash -v threadtime | tail -n 180 || true
    exit 1
  fi
  echo "Cold-start $pass survived (PID $PID)"
  CRASH=$(adb logcat -d -b crash -v brief | grep -E 'FATAL EXCEPTION|Process: io.github.x07xh.xiaoxu.unifiedqa' || true)
  if [ -n "$CRASH" ]; then
    echo "::error::Android crash buffer contains a fatal exception"
    adb logcat -d -b crash -v threadtime | tail -n 180 || true
    exit 1
  fi
done
echo "PASS: isolated QA app survived two cold launches on API 35 emulator"
