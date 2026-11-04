// Narrow verification of installable Android alpha: no private credentials,
// no hidden WebView bridge, real same-origin site, distinct from TapFlow.
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const read=p=>readFileSync(new URL('../'+p,import.meta.url),'utf8');
const manifest=read('apps/xiaoxu-android/app/src/main/AndroidManifest.xml');
const strings=read('apps/xiaoxu-android/app/src/main/res/values/strings.xml');
const gradle=read('apps/xiaoxu-android/app/build.gradle');
const ci=read('.github/workflows/build-xiaoxu-android.yml');
assert.match(strings,/https:\/\/dorm-os-dk9\.pages\.dev\/#dashboard/);
assert.match(manifest,/com\.google\.androidbrowserhelper\.trusted\.LauncherActivity/);
assert.match(manifest,/android:exported="true"/);
assert.match(manifest,/android:allowBackup="false"/);
assert.match(manifest,/android:usesCleartextTraffic="false"/);
assert.match(manifest,/CustomTabsService/);
assert.doesNotMatch(manifest,/WebView|addJavascriptInterface|QUERY_ALL_PACKAGES|READ_SMS|READ_CONTACTS/i);
assert.doesNotMatch(strings+manifest,/wxu_[A-Za-z0-9_-]+|SCHOOL_SESSION_KEY|OWNER_LOGIN_SECRET/);
assert.match(gradle,/io\.github\.x07xh\.xiaoxu/);
assert.match(gradle,/androidbrowserhelper:2\.7\.4/);
assert.match(ci,/assembleDebug/);
assert.match(ci,/upload-artifact/);
console.log('校序 Android V0.1：唯一生产域名、最小权限、TWA 基座和独立 CI 检查通过');
