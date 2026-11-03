/* Synthetic, no real identities/rows/credentials: second-class 0011->0012 migration. */
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {classifySecondClassVault} from './ensure-second-class-session-schema.mjs';

const sql1=readFileSync(new URL('../migrations/0011_academic_provider_sessions.sql',import.meta.url),'utf8');
const sql2=readFileSync(new URL('../migrations/0012_academic_second_class_sessions.sql',import.meta.url),'utf8');
const db=new DatabaseSync(':memory:');
db.exec('PRAGMA foreign_keys=ON');
db.exec('CREATE TABLE academic_users (id TEXT PRIMARY KEY)');
db.prepare('INSERT INTO academic_users VALUES(?)').run('synthetic_owner');
const col=()=>db.prepare('PRAGMA table_info(academic_provider_sessions)').all();
const code=()=>db.prepare("SELECT sql FROM sqlite_master WHERE type='table' AND name='academic_provider_sessions'").get()?.sql??null;
assert.equal(classifySecondClassVault({tableSql:null,columns:[]}), 'missing');
db.exec(sql1);
assert.equal(classifySecondClassVault({tableSql:code(),columns:col()}),'legacy_0011');
db.prepare('INSERT INTO academic_provider_sessions VALUES(?,?,?,?,?,?)').run(
 'synthetic_owner','ncwu','SYNTHETIC_CIPHERTEXT','SYNTHETIC_NONCE',
 '2026-11-30T00:00:00.000Z','2026-11-03T00:00:00.000Z');
assert.throws(()=>db.prepare('INSERT INTO academic_provider_sessions VALUES(?,?,?,?,?,?)').run(
 'synthetic_owner','second_class','a','b','2026-11-30','2026-11-03'),/constraint/i);
db.exec(sql2);
assert.equal(classifySecondClassVault({tableSql:code(),columns:col()}),'ready');
const row=db.prepare('SELECT * FROM academic_provider_sessions WHERE provider=?').get('ncwu');
assert.equal(row.ciphertext,'SYNTHETIC_CIPHERTEXT','existing ciphertext must remain intact');
assert.equal(row.nonce,'SYNTHETIC_NONCE');
db.prepare('INSERT INTO academic_provider_sessions VALUES(?,?,?,?,?,?)').run(
 'synthetic_owner','second_class','SYNTHETIC_CIPHER2','SYNTHETIC_NONCE2',
 '2026-11-30T00:00:00.000Z','2026-11-03T00:00:00.000Z');
assert.deepEqual(db.prepare('PRAGMA foreign_key_check').all(),[]);
assert.deepEqual(db.prepare('PRAGMA quick_check').all(),[{quick_check:'ok'}]);
assert.equal(classifySecondClassVault({tableSql:code(),columns:col(),
 nextTableExists:true}),'unsafe_partial_migration');
assert.equal(classifySecondClassVault({tableSql:'CREATE TABLE academic_provider_sessions (bad TEXT)',columns:[{name:'bad'}]}),'unexpected_schema');
const workflow=readFileSync(new URL('../.github/workflows/deploy-cloudflare.yml',import.meta.url),'utf8');
const key=workflow.indexOf('node scripts/ensure-school-session-key.mjs');
const schema=workflow.indexOf('node scripts/ensure-second-class-session-schema.mjs');
const deploy=workflow.indexOf('npx --yes wrangler@4 pages deploy public');
assert.ok(key>=0&&schema>key&&deploy>schema,
 'A production release must provision session key and D1 schema before publishing');
db.close();
console.log('Second-class production D1: missing/legacy/ready gates, lossless session upgrade, foreign keys, deployment order passed');
