import assert from 'node:assert/strict';
import {readFileSync,existsSync} from 'node:fs';
const read=path=>readFileSync(new URL('../'+path,import.meta.url),'utf8');
for(const path of [
 'public/xiaoxu-second-class-export.user.js',
 'functions/api/v1/second-class/bridge.js',
 'functions/api/v1/second-class/bridge/ingest.js',
 'lib/academic-secondclass-browser-bridge.mjs',
 'lib/academic-secondclass-personal-totals.mjs',
 'scripts/xiaoxu-secondclass-browser-bridge-tests.mjs',
 'scripts/xiaoxu-second-class-cas-export-tests.mjs',
 'lib/academic-ncwu-cas-server.mjs',
 'scripts/xiaoxu-ncwu-cas-native-tests.mjs',
 'scripts/xiaoxu-second-class-school-login-tests.mjs',
 'scripts/xiaoxu-second-class-foreground-sync-tests.mjs',
 'docs/XIAOXU-SECONDCLASS-NATIVE-CAS-M23.md'
])assert.equal(existsSync(new URL('../'+path,import.meta.url)),false,'obsolete script/endpoint remains: '+path);
const ui=read('public/xiaoxu-academic-client.js');
const source=read('functions/api/v1/connections.js');
const pkg=read('package.json');
for(const token of ['data-xu-bridge-','data-xu-second-class-import',
 'xiaoxu-second-class-export.user.js','ScriptCat','/api/v1/second-class/bridge'])
 assert.equal(ui.includes(token),false,'obsolete connection-center UI remains: '+token);
assert.equal(source.includes('browserBridge'),false,'obsolete provider state remains');
assert.equal(pkg.includes('xiaoxu-secondclass-browser-bridge-tests.mjs'),false);
assert.ok(ui.includes('打开第二课堂官网'),'official school link remains');
assert.ok(ui.includes('尚未通过本人真实学校登录验证'),'honest unverified status remains');
assert.equal(ui.includes('data-xu-second-cas-check'),false,'retired CAS password probe must not appear');
assert.equal(ui.includes('maybeAutoRefreshSecondClass'),false);
assert.ok(ui.includes('data-xu-second-class-official-only'),'official-only status should remain');
assert.equal(ui.includes('form.dataset.xuSchoolLogin=\'second_class\''),false);
assert.equal(read('functions/api/v1/_middleware.js').includes('makeNcwuCasServerAdapter'),false);
assert.ok(read('functions/api/v1/connections/[provider]/login.js').includes("provider==='second_class')return json({error:'official_sso_only'},410)"),
 'legacy login endpoint must reject old clients before reading credentials');
assert.ok(read('functions/api/v1/connections/[provider]/preflight.js').includes("status:'official_sso_only'"));
assert.ok(read('functions/api/v1/connections.js').includes("nativeCasPreflight:false"));
assert.ok(!read('lib/academic-ncwu-qingguo-cas-shared.mjs').includes('dekt.ncwu.edu.cn'));
assert.ok(read('lib/academic-ncwu-second-class.mjs').includes('NCWU_SECOND_CLASS_READS'),
 'real school HAR adapter must remain');
assert.ok(read('lib/academic-school-login.mjs').includes('second_class'),
 'server session work must remain');
for(const migration of [
 'migrations/0013_academic_secondclass_browser_bridge.sql',
 'migrations/0014_academic_secondclass_personal_totals.sql'
])assert.equal(existsSync(new URL('../'+migration,import.meta.url)),true,
 'applied production migration history must not be deleted');
const migrator=read('scripts/upgrade-academic-owner-auth-production.mjs');
assert.equal(migrator.includes('--file=./migrations/0013'),false);
assert.equal(migrator.includes('--file=./migrations/0014'),false);
console.log('Second-class M22 clean: script, browser bridge, old UI removed; real parser and D1 history preserved');
