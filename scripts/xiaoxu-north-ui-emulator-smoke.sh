#!/usr/bin/env bash
set -euo pipefail

SMOKE_DIR=/tmp/xiaoxu-smoke
mkdir -p "$SMOKE_DIR"

boot_completed=""
device_state=""
attempt=0
while [ "$attempt" -lt 90 ]; do
  device_state="$(adb get-state 2>/dev/null || true)"
  if [ "$device_state" = "device" ]; then
    boot_completed="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [ "$boot_completed" = "1" ]; then
      break
    fi
  fi
  attempt=$((attempt + 1))
  sleep 2
done
if [ "$device_state" != "device" ] || [ "$boot_completed" != "1" ]; then
  echo "Emulator did not become ready: adb_state=$device_state boot_completed=$boot_completed" >&2
  adb devices -l >&2 || true
  exit 1
fi
echo "Emulator ready after $attempt readiness checks."

adb shell settings put global window_animation_scale 0.0 >/dev/null 2>&1 || true
adb shell settings put global transition_animation_scale 0.0 >/dev/null 2>&1 || true
adb shell settings put global animator_duration_scale 0.0 >/dev/null 2>&1 || true

apk="apps/xiaoxu-android/migrated/app/build/outputs/apk/debug/app-debug.apk"
test_apk="apps/xiaoxu-android/migrated/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
test -s "$apk"
test -s "$test_apk"
adb install --no-streaming -r "$apk"
adb install --no-streaming -r "$test_apk"

if ! adb shell am instrument -w -r -e class io.github.x07xh.xiaoxu.data.LocalTaskDatabaseMigrationTest io.github.x07xh.xiaoxu.northui.test/androidx.test.runner.AndroidJUnitRunner > "$SMOKE_DIR/room-migration-test.txt"; then
  cat "$SMOKE_DIR/room-migration-test.txt"
  exit 1
fi
cat "$SMOKE_DIR/room-migration-test.txt"
grep -Eq 'OK \([0-9]+ tests?\)' "$SMOKE_DIR/room-migration-test.txt"

adb shell am force-stop io.github.x07xh.xiaoxu.northui
adb shell am start -W -n io.github.x07xh.xiaoxu.northui/com.tyust.course.MainActivity > "$SMOKE_DIR/activity-start.txt"
cat "$SMOKE_DIR/activity-start.txt"
sleep 12
adb shell uiautomator dump /sdcard/window.xml >/dev/null
adb pull /sdcard/window.xml "$SMOKE_DIR/window.xml"
adb shell screencap -p /sdcard/screen.png
adb pull /sdcard/screen.png "$SMOKE_DIR/screen.png"
adb shell dumpsys gfxinfo io.github.x07xh.xiaoxu.northui framestats > "$SMOKE_DIR/frame-stats.txt"
adb shell dumpsys activity activities > "$SMOKE_DIR/activities.txt"
grep -Fq 'com.tyust.course.MainActivity' "$SMOKE_DIR/activities.txt"
test -s "$SMOKE_DIR/screen.png"

python3 - <<'PY'
import subprocess
import time
import xml.etree.ElementTree as ET

tree = ET.parse('/tmp/xiaoxu-smoke/window.xml')
matches = []
for node in tree.iter('node'):
    label = (node.attrib.get('text', '') + ' ' + node.attrib.get('content-desc', '')).strip()
    bounds = node.attrib.get('bounds', '')
    if '课表' not in label:
        continue
    numbers = bounds.replace('][', ',').strip('[]').split(',')
    if len(numbers) == 4 and all(value.isdigit() for value in numbers):
        x1, y1, x2, y2 = map(int, numbers)
        matches.append((y1, (x1 + x2) // 2, (y1 + y2) // 2))
if matches:
    _, x, y = max(matches)
    subprocess.run(['adb', 'shell', 'input', 'tap', str(x), str(y)], check=True)
    time.sleep(5)
    subprocess.run(['adb', 'shell', 'uiautomator', 'dump', '/sdcard/schedule.xml'],
                   check=True, stdout=subprocess.DEVNULL)
    subprocess.run(['adb', 'pull', '/sdcard/schedule.xml',
                    '/tmp/xiaoxu-smoke/schedule.xml'], check=True)
    subprocess.run(['adb', 'shell', 'input', 'swipe', '540', '1500', '540', '850', '350'],
                   check=True)
    subprocess.run(['adb', 'shell', 'input', 'swipe', '900', '1150', '250', '1150', '350'],
                   check=True)
    time.sleep(2)
    subprocess.run(['adb', 'shell', 'screencap', '-p', '/sdcard/schedule.png'], check=True)
    subprocess.run(['adb', 'pull', '/sdcard/schedule.png',
                    '/tmp/xiaoxu-smoke/schedule.png'], check=True)
    print('Captured original schedule route and performed non-destructive scroll/swipe smoke gestures.')
else:
    print('Schedule tab label not found in UI hierarchy; home screenshot and hierarchy retained.')
PY

adb shell dumpsys gfxinfo io.github.x07xh.xiaoxu.northui framestats > "$SMOKE_DIR/frame-stats-after-navigation.txt"

if ! adb shell am instrument -w -r -e class 'io.github.x07xh.xiaoxu.data.CampusRunEncryptedStorageTest,io.github.x07xh.xiaoxu.data.CampusUtilitiesEncryptedStorageTest' io.github.x07xh.xiaoxu.northui.test/androidx.test.runner.AndroidJUnitRunner > "$SMOKE_DIR/migrated-storage-instrumentation.txt"; then
  cat "$SMOKE_DIR/migrated-storage-instrumentation.txt"
  exit 1
fi
cat "$SMOKE_DIR/migrated-storage-instrumentation.txt"
grep -Eq 'OK \(4 tests\)' "$SMOKE_DIR/migrated-storage-instrumentation.txt"
