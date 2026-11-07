import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {classifyWechatSchema,classifyWechatLinksSchema} from './ensure-wechat-candidates-schema.mjs';

assert.equal(classifyWechatSchema([],{}),'missing');
assert.equal(classifyWechatSchema([{name:'academic_wechat_devices'}],{}),'partial');
assert.equal(classifyWechatLinksSchema([],{}),'missing');
assert.equal(classifyWechatLinksSchema([{name:'academic_wechat_devices'}],{}),'missing');
assert.throws(()=>classifyWechatLinksSchema('not-a-table-list'),/invalid_schema/);

const sqlite=new DatabaseSync(':memory:');
sqlite.exec('PRAGMA foreign_keys=ON');
sqlite.exec(readFileSync(new URL('../migrations/0001_academic_core.sql',import.meta.url),'utf8'));
const tables=()=>sqlite.prepare(
 "SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'academic_%'"
).all();
const fields=()=>Object.fromEntries(
 ['academic_wechat_devices','academic_wechat_batches','academic_wechat_candidates',
  'academic_wechat_candidate_links']
 .filter(name=>tables().some(row=>row.name===name))
 .map(name=>[name,sqlite.prepare('PRAGMA table_info('+name+')').all()])
);
assert.equal(classifyWechatSchema(tables(),fields()),'missing');
assert.equal(classifyWechatLinksSchema(tables(),fields()),'missing');
sqlite.exec(readFileSync(new URL('../migrations/0017_academic_wechat_candidates.sql',import.meta.url),'utf8'));
assert.equal(classifyWechatSchema(tables(),fields()),'ready');
assert.equal(classifyWechatLinksSchema(tables(),fields()),'missing',
 'candidates created before V1 links must not be reported as partial');
sqlite.exec(readFileSync(new URL('../migrations/0018_academic_wechat_candidate_links.sql',import.meta.url),'utf8'));
assert.equal(classifyWechatLinksSchema(tables(),fields()),'ready');
sqlite.exec(readFileSync(new URL('../migrations/0017_academic_wechat_candidates.sql',import.meta.url),'utf8'));
sqlite.exec(readFileSync(new URL('../migrations/0018_academic_wechat_candidate_links.sql',import.meta.url),'utf8'));
assert.equal(classifyWechatSchema(tables(),fields()),'ready',
 'replaying a safely complete migration must not change schema');
assert.equal(classifyWechatLinksSchema(tables(),fields()),'ready',
 'replaying the links migration must not change schema');
// The links table itself blocks non-https and cross-owner rows at the DB level.
sqlite.exec("INSERT INTO academic_users(id,created_at) VALUES ('owner-l','2026-11-07T00:00:00Z')");
sqlite.exec(`INSERT INTO academic_wechat_devices(id,user_id,display_name,token_sha256,created_at)
 VALUES ('wxd_linktest','owner-l','dev','${'a'.repeat(64)}','2026-11-07T00:00:00Z')`);
sqlite.exec(`INSERT INTO academic_wechat_candidates(id,user_id,device_id,message_key,candidate_key,
 chat_key,kind,title,sent_at,due_precision,created_at)
 VALUES ('cand-l','owner-l','wxd_linktest','m1','c1','g','homework','提交报告',
 '2026-11-07T01:00:00Z','unknown','2026-11-07T01:00:00Z')`);
sqlite.exec(`INSERT INTO academic_wechat_candidate_links(candidate_id,user_id,position,url,created_at)
 VALUES ('cand-l','owner-l',0,'https://course.example/hw','2026-11-07T01:00:00Z')`);
assert.throws(()=>sqlite.prepare(
 `INSERT INTO academic_wechat_candidate_links(candidate_id,user_id,position,url,created_at)
  VALUES ('cand-l','owner-l',1,'http://course.example/insecure','t')`).run(),
 /CHECK constraint/,'plain http URLs are rejected by the database itself');
assert.throws(()=>sqlite.prepare(
 `INSERT INTO academic_wechat_candidate_links(candidate_id,user_id,position,url,created_at)
  VALUES ('cand-l','owner-b',2,'https://course.example/other','t')`).run(),
 /FOREIGN KEY constraint/,'the database itself blocks cross-owner link rows');
assert.throws(()=>sqlite.prepare(
 `INSERT INTO academic_wechat_candidate_links(candidate_id,user_id,position,url,created_at)
  VALUES ('cand-l','owner-l',3,'https://course.example/fourth','t')`).run(),
 /CHECK constraint/,'more than three positions are rejected by the database itself');
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
