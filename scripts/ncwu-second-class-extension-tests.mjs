import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
const root=new URL('../integrations/ncwu-second-class-extension/',import.meta.url);
const read=name=>readFileSync(new URL(name,root),'utf8');
const manifest=JSON.parse(read('manifest.json'));
assert.equal(manifest.manifest_version,3);
assert.deepEqual(manifest.host_permissions.sort(),[
 'https://dekt.ncwu.edu.cn/*','https://dorm-os-dk9.pages.dev/*'
].sort());
assert.ok(manifest.permissions.includes('alarms'));
assert.ok(manifest.permissions.includes('tabs'));
assert.ok(!manifest.permissions.includes('cookies'));
assert.ok(!manifest.permissions.includes('webRequest'));
assert.ok(!manifest.permissions.includes('debugger'));
assert.ok(manifest.content_scripts.some(x=>x.matches.includes('https://dekt.ncwu.edu.cn/*')&&
 x.js.includes('adapter.js')&&x.js.includes('school-content.js')));
assert.ok(manifest.content_scripts.some(x=>x.matches.includes('https://dorm-os-dk9.pages.dev/*')&&
 x.js.includes('portal-content.js')));
for(const name of ['adapter.js','school-content.js','portal-content.js','background.js','popup.js']){
 new vm.Script(read(name),{filename:name});
}
const background=read('background.js');
assert.ok(background.includes('chrome.alarms.onAlarm'));
assert.ok(background.includes('periodInMinutes'));
assert.ok(background.includes('lastSyncAt'));
assert.ok(!/chrome\.cookies|document\.cookie|Cookie\s*:/i.test(background));
const school=read('school-content.js');
assert.ok(school.includes('NCWU_READ_ONLY_SYNC'));
assert.ok(!/document\.cookie|localStorage|sessionStorage/i.test(school));
const portal=read('portal-content.js');
assert.ok(portal.includes('/api/v1/verified/snapshot'));
assert.ok(portal.includes("credentials:'same-origin'"));
assert.ok(!/document\.cookie|localStorage|sessionStorage/i.test(portal));
console.log('NCWU extension manifest, least-privilege host scope, local-only status storage, alarms and no-credential-handling checks passed.');
