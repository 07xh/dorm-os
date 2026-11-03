import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {readFileSync} from 'node:fs';
import {classifySessionVaultUpgrade,classifySecondClassSessionUpgrade} from './upgrade-academic-owner-auth-production.mjs';
const fixtures=()=>new DatabaseSync(':memory:');
const db=fixtures();
db.exec('PRAGMA foreign_keys=ON');
for(const file of ['0001_academic_core.sql','0003_academic_verified_snapshots.sql'])
 db.exec(readFileSync(new URL('../migrations/'+file,import.meta.url),'utf8'));
const tableRows=()=>db.prepare("SELECT name FROM sqlite_master WHERE type='table'").all();
const columnRows=()=>db.prepare('PRAGMA table_info(academic_provider_sessions)').all();
assert.equal(classifySessionVaultUpgrade(tableRows(),columnRows()),'apply');
const beforeUsers=db.prepare('SELECT COUNT(*) AS n FROM academic_users').get().n;
db.exec(readFileSync(new URL('../migrations/0011_academic_provider_sessions.sql',import.meta.url),'utf8'));
assert.equal(classifySessionVaultUpgrade(tableRows(),columnRows()),'verify');
assert.equal(db.prepare('SELECT COUNT(*) AS n FROM academic_users').get().n,beforeUsers);
assert.deepEqual(db.prepare('PRAGMA foreign_key_check').all(),[]);
assert.equal(db.prepare('PRAGMA quick_check').get().quick_check,'ok');
const required=['user_id','provider','ciphertext','nonce','expires_at','updated_at'];
assert.ok(required.every(key=>columnRows().some(row=>row.name===key)));
assert.equal(classifySessionVaultUpgrade(tableRows(),columnRows().filter(row=>row.name!=='nonce')),'partial_session_schema');
assert.throws(()=>classifySessionVaultUpgrade(null,[]),/invalid_session_schema/);
db.exec(readFileSync(new URL('../migrations/0011_academic_provider_sessions.sql',import.meta.url),'utf8'));
assert.equal(classifySessionVaultUpgrade(tableRows(),columnRows()),'verify','migration must be idempotent');
assert.equal(db.prepare('SELECT COUNT(*) AS n FROM academic_provider_sessions').get().n,0);
// Production can already contain AES-GCM ciphertext from the original two
// providers. The forward-only SQLite rebuild must retain every encrypted byte.
const definition=()=>db.prepare("SELECT sql FROM sqlite_master WHERE type='table' AND name='academic_provider_sessions'").get()?.sql;
assert.equal(classifySecondClassSessionUpgrade(definition()),'apply');
assert.equal(classifySecondClassSessionUpgrade('unrecognized table'), 'unexpected_session_constraint');
const now=new Date().toISOString();
db.prepare('INSERT INTO academic_users(id,created_at) VALUES(?,?)').run('owner-one',now);
db.prepare('INSERT INTO academic_provider_sessions(user_id,provider,ciphertext,nonce,expires_at,updated_at) VALUES(?,?,?,?,?,?)')
 .run('owner-one','ncwu','EXISTING_ENCRYPTED_PAYLOAD','NONCE_VALUE',now,now);
assert.throws(()=>db.prepare('INSERT INTO academic_provider_sessions(user_id,provider,ciphertext,nonce,expires_at,updated_at) VALUES(?,?,?,?,?,?)')
 .run('owner-one','second_class','x','y',now,now),/CHECK constraint/);
const old=db.prepare("SELECT * FROM academic_provider_sessions WHERE user_id='owner-one'").get();
db.exec(readFileSync(new URL('../migrations/0012_academic_second_class_sessions.sql',import.meta.url),'utf8'));
assert.equal(classifySecondClassSessionUpgrade(definition()),'verify');
assert.deepEqual(db.prepare("SELECT * FROM academic_provider_sessions WHERE user_id='owner-one'").get(),old);
db.prepare('INSERT INTO academic_provider_sessions(user_id,provider,ciphertext,nonce,expires_at,updated_at) VALUES(?,?,?,?,?,?)')
 .run('owner-one','second_class','NEW_CIPHER','NEW_NONCE',now,now);
assert.equal(db.prepare("SELECT count(*) AS n FROM academic_provider_sessions WHERE user_id='owner-one'").get().n,2);
assert.deepEqual(db.prepare('PRAGMA foreign_key_check').all(),[]);
assert.equal(db.prepare('PRAGMA integrity_check').get().integrity_check,'ok');
db.close();
console.log('School session vault 0011→0012: existing ciphertext retained, second-class allowed, scope checks and integrity passed');
