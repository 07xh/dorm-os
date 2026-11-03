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
 'scripts/xiaoxu-second-class-cas-export-tests.mjs'
])assert.equal(existsSync(new URL('../'+path,import.meta.url)),false,'obsolete script/endpoint remains: '+path);
const ui=read('public/xiaoxu-academic-client.js');
const source=read('functions/api/v1/connections.js');
const pkg=read('package.json');
for(const token of ['data-xu-bridge-','data-xu-second-class-import',
 'xiaoxu-second-class-export.user.js','ScriptCat','/api/v1/second-class/bridge'])
 assert.equal(ui.includes(token),false,'obsolete connection-center UI remains: '+token);
assert.equal(source.includes('browserBridge'),false,'obsolete provider state remains');
assert.equal(pkg.includes('xiaoxu-secondclass-browser-bridge-tests.mjs'),false);
assert.ok(ui.includes('学校使用统一身份认证'),'school link remains');
assert.ok(ui.includes('未完成真实账号联通'),'honest unavailable status remains');
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
