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
assert.ok(ui.includes('学校账号真实登录尚待验收'),'honest unverified status remains');
assert.ok(ui.includes('data-xu-cas-check="second_class"'),'explicit school CAS preflight remains');
assert.equal(ui.includes('maybeAutoRefreshSecondClass'),false);
assert.equal(ui.includes('data-xu-second-class-official-only'),false,'obsolete official-only blockade removed');
assert.ok(ui.includes('form.dataset.xuSchoolLogin=checkProvider'));

assert.equal(read('functions/api/v1/_middleware.js').includes('makeNcwuCasServerAdapter'),false);
assert.equal(read('functions/api/v1/connections/[provider]/login.js').includes('official_sso_only'),false);
assert.ok(read('functions/api/v1/connections/[provider]/preflight.js').includes("provider==='second_class'"));
assert.ok(read('functions/api/v1/connections.js').includes('nativeCasPreflight:available'));
assert.ok(read('lib/academic-ncwu-secondclass-server.mjs').includes('makeNcwuSecondClassServerAdapter'));
assert.ok(read('scripts/xiaoxu-secondclass-server-tests.mjs').includes('SYNTHETIC_ACCOUNT'));
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
