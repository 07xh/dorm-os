import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';

const root = '../apps/xiaoxu-android/app/src/';
const read = path => readFileSync(new URL(root + path, import.meta.url), 'utf8');
for (const path of [
  'main/java/io/github/x07xh/xiaoxu/MainActivity.kt',
  'main/java/io/github/x07xh/xiaoxu/ui/ProfileSettingsPage.kt',
  'main/java/io/github/x07xh/xiaoxu/ui/ScheduleScreen.kt'
]) {
  const source = read(path);
  assert.doesNotMatch(source, /AnimatedContent|xiaoxuSettingsRouteTransition|xiaoxuPrimaryPageTransition/,
    `${path}: retired page animation containers must not return`);
  assert.match(source, /ImmediateRouteContent\(/, `${path}: use the shared immediate route host`);
}
const host = read('main/java/io/github/x07xh/xiaoxu/ui/ImmediateRouteContent.kt');
assert.match(host, /rememberSaveableStateHolder\(\)/);
assert.match(host, /SaveableStateProvider\(route\)/);
assert.match(host, /clipToBounds\(\)/);
assert.doesNotMatch(host, /androidx\.compose\.animation|transition|durationMillis/);
assert.equal(existsSync(new URL(root + 'main/java/io/github/x07xh/xiaoxu/ui/PrimaryPageMotion.kt', import.meta.url)), false);
console.log('Immediate native routes: no retired animation host; route state and clipping retained');
