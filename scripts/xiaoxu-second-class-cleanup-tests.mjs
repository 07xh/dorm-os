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
 'docs/XIAOXU-SECONDCLASS-NATIVE-CAS-M23.md',
 'functions/api/academic/second-class-cas.js',
 'lib/academic-cas-failure-evidence.mjs',
 'scripts/probe-ncwu-secondclass-cas-live.mjs',
 'scripts/verify-cloudflare-secondclass-cas.mjs',
 'scripts/xiaoxu-cloudflare-secondclass-cas-tests.mjs'
])assert.equal(existsSync(new URL('../'+path,import.meta.url)),false,'obsolete script/endpoint remains: '+path);
const ui=read('public/xiaoxu-academic-client.js');
const source=read('functions/api/v1/connections.js');
const pkg=read('package.json');
for(const token of ['data-xu-bridge-','data-xu-second-class-import',
 'xiaoxu-second-class-export.user.js','ScriptCat','/api/v1/second-class/bridge'])
 assert.equal(ui.includes(token),false,'obsolete connection-center UI remains: '+token);
assert.equal(source.includes('browserBridge'),false,'obsolete provider state remains');
assert.equal(pkg.includes('xiaoxu-secondclass-browser-bridge-tests.mjs'),false);
assert.equal(pkg.includes('xiaoxu-cloudflare-secondclass-cas-tests.mjs'),false);
assert.equal(read('.github/workflows/check.yml').includes('probe-ncwu-secondclass-cas-live'),false);
assert.equal(read('.github/workflows/deploy-cloudflare.yml').includes('verify-cloudflare-secondclass-cas'),false);
assert.equal(read('public/xiaoxu-academic-client.js').includes('casErrorKind'),false);
assert.ok(ui.includes('打开学校官方平台'),'official entry remains');
assert.ok(ui.includes('请在校序 App 的'),'official App reader is the connection entry');
assert.equal(ui.includes('扫码会话尚未接入校序'),false,'obsolete QR placeholder is removed');
assert.equal(ui.includes('检测扫码接入'),false,'discovery must not masquerade as an authorization flow');
assert.equal(ui.includes('第二课堂连接状态'),false,'duplicate guide removed');
assert.ok(read('functions/api/v1/connections/[provider]/preflight.js').includes('qrSessionLinked:false'));
assert.equal(ui.includes('data-xu-cas-check="second_class"'),false,'retired second-class password preflight removed');
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
