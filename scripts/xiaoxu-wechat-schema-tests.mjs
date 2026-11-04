import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {classifyWechatSchema} from './ensure-wechat-candidates-schema.mjs';

assert.equal(classifyWechatSchema([],{}),'missing');
assert.equal(classifyWechatSchema([{name:'academic_wechat_devices'}],{}),'partial');
assert.throws(()=>classifyWechatSchema('not-a-table-list'),/invalid_schema/);

const sqlite=new DatabaseSync(':memory:');
sqlite.exec('PRAGMA foreign_keys=ON');
sqlite.exec(readFileSync(new URL('../migrations/0001_academic_core.sql',import.meta.url),'utf8'));
const tables=()=>sqlite.prepare(
 "SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'academic_%'"
).all();
const fields=()=>Object.fromEntries(
 ['academic_wechat_devices','academic_wechat_batches','academic_wechat_candidates']
 .filter(name=>tables().some(row=>row.name===name))
 .map(name=>[name,sqlite.prepare('PRAGMA table_info('+name+')').all()])
);
assert.equal(classifyWechatSchema(tables(),fields()),'missing');
sqlite.exec(readFileSync(new URL('../migrations/0017_academic_wechat_candidates.sql',import.meta.url),'utf8'));
assert.equal(classifyWechatSchema(tables(),fields()),'ready');
sqlite.exec(readFileSync(new URL('../migrations/0017_academic_wechat_candidates.sql',import.meta.url),'utf8'));
assert.equal(classifyWechatSchema(tables(),fields()),'ready',
 'replaying a safely complete migration must not change schema');
assert.equal(sqlite.prepare('PRAGMA foreign_key_check').all().length,0);
assert.equal(sqlite.prepare('PRAGMA quick_check').get().quick_check,'ok');
const workflow=readFileSync(new URL('../.github/workflows/deploy-cloudflare.yml',import.meta.url),'utf8');
const migrationPos=workflow.indexOf('run: node scripts/ensure-wechat-candidates-schema.mjs');
const publishPos=workflow.indexOf('name: Publish verified static website');
assert.ok(migrationPos>0&&publishPos>migrationPos,
 'migration gate must run before the public Pages deployment');
assert.ok(workflow.includes("steps.publish_guard.outputs.fresh == 'true'"));
assert.ok(!workflow.includes('continue-on-error: true\n        run: node scripts/ensure-wechat-candidates-schema.mjs'),
 'production schema gate must not silently continue on failure');
sqlite.close();
console.log('微信 D1: 完整/部分 schema 判定、重复迁移、外键完整性、生产部署门禁均通过');
