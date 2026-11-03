import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {DatabaseSync} from 'node:sqlite';
import {REQUIRED_COLUMNS,REQUIRED_OWNER_TABLES,inspectAcademicSchema,readAcademicSchema}
 from '../lib/academic-schema-readiness.mjs';
import {onRequestGet} from '../functions/api/v1/health.js';
import {exactProductionDatabase} from './audit-academic-d1-production.mjs';
const migration=name=>readFileSync(new URL('../migrations/'+name,import.meta.url),'utf8');
const database=new DatabaseSync(':memory:');
database.exec('PRAGMA foreign_keys=ON');
const names=[
 '0001_academic_core.sql','0002_academic_legacy_links.sql','0003_academic_verified_snapshots.sql',
 '0004_academic_source_course.sql','0005_academic_workspace.sql',
 '0006_academic_scheduler.sql','0007_academic_reminder_inbox.sql',
 '0008_academic_second_class_source.sql','0009_ncwu_second_class_official_scores.sql'
];
const db={prepare(sql){let params=[];return {
 bind(...items){params=items;return this;},
 first(){return database.prepare(sql).get(...params)??null;},
 all(){return {results:database.prepare(sql).all(...params)};},
 run(){return database.prepare(sql).run(...params);}
};}};
for(const name of names)database.exec(migration(name));
let scanned=await readAcademicSchema(db,{authMode:'access'});
assert.equal(scanned.databaseReady,true,'legacy Access mode uses the complete 0001–0009 core');
assert.equal(scanned.ownerAuthSchemaReady,false);
assert.equal((await readAcademicSchema(db,{authMode:'owner_password'})).databaseReady,false);
assert.deepEqual((await readAcademicSchema(db,{authMode:'owner_password'})).missingTables,REQUIRED_OWNER_TABLES);
database.exec(migration('0010_academic_owner_auth.sql'));
scanned=await readAcademicSchema(db,{authMode:'owner_password'});
assert.equal(scanned.status,'ready');
assert.equal(scanned.databaseReady,true);
assert.equal(scanned.coreSchemaReady,true);
assert.equal(scanned.ownerAuthSchemaReady,true);
assert.deepEqual(scanned.missingTables,[]);
assert.deepEqual(scanned.missingColumns,[]);

const fullTables=database.prepare("SELECT name FROM sqlite_master WHERE type='table'").all();
const allColumns={};
for(const name of Object.keys(REQUIRED_COLUMNS))
 allColumns[name]=database.prepare('PRAGMA table_info('+name+')').all();
const badColumns=structuredClone(allColumns);
badColumns.academic_verified_batches=badColumns.academic_verified_batches
 .filter(column=>column.name!=='provenance');
const missing=inspectAcademicSchema(fullTables,badColumns,{authMode:'owner_password'});
assert.equal(missing.databaseReady,false);
assert.equal(missing.coreSchemaReady,false);
assert.deepEqual(missing.missingColumns,['academic_verified_batches.provenance']);
const noSession=fullTables.filter(r=>r.name!=='academic_auth_sessions');
const missingSession=inspectAcademicSchema(noSession,allColumns,{authMode:'owner_password'});
assert.equal(missingSession.coreSchemaReady,true);
assert.equal(missingSession.ownerAuthSchemaReady,false);
assert.deepEqual(missingSession.missingTables,['academic_auth_sessions']);
assert.throws(()=>inspectAcademicSchema(fullTables,allColumns,{authMode:'unverified'}),/invalid_schema_input/);

const denied=await onRequestGet({request:new Request('https://test.example/api/v1/health'),env:{ACADEMIC_DB:db}});
assert.equal(denied.status,503,'health does not expose schema before auth');
assert.match(denied.headers.get('cache-control')||'',/no-store/);

const owner='local-owner-v1',when=new Date().toISOString();
database.prepare('INSERT INTO academic_users(id,created_at) VALUES (?,?)').run(owner,when);
database.prepare('INSERT INTO academic_auth_identities(id,user_id,login_identifier,enrolled_at) VALUES (?,?,?,?)')
 .run('identity-one',owner,'owner',when);
const token=Buffer.alloc(32,17).toString('base64url');
const tokenHash=createHash('sha256').update(token).digest('hex');
database.prepare('INSERT INTO academic_auth_sessions(id,identity_id,token_hash,created_at,expires_at) VALUES (?,?,?,?,?)')
 .run('session-one','identity-one',tokenHash,when,new Date(Date.now()+3600000).toISOString());
const env={ACADEMIC_AUTH_MODE:'owner_password',ACADEMIC_DB:db};
const req=new Request('https://test.example/api/v1/health',
 {headers:{cookie:'__Host-xiaoxu_session='+token}});
const live=await onRequestGet({request:req,env});
assert.equal(live.status,200);
const body=await live.json();
assert.equal(body.status,'database_ready');
assert.equal(body.databaseReady,true);
assert.equal(body.loginAuthenticated,true);
assert.equal(body.authMode,'owner_password');
assert.equal(body.schoolConnection,'not_verified');
assert.equal(body.automaticSync,false);
assert.equal(JSON.stringify(body).includes(token),false);
assert.equal(JSON.stringify(body).includes(owner),false);
assert.equal(JSON.stringify(body).includes(tokenHash),false);
assert.match(live.headers.get('vary')||'',/Cookie/);

const uuid='01234567-89ab-cdef-0123-456789abcdef';
const cfg={name:'dorm-os',deployment_configs:{production:{d1_databases:{ACADEMIC_DB:{id:uuid}}}}};
assert.equal(exactProductionDatabase(cfg,[{uuid,name:'bound-production-db'}]),'bound-production-db');
assert.throws(()=>exactProductionDatabase(cfg,[{uuid:'other',name:'wrong-db'}]),/production_db_identity_mismatch/);
assert.throws(()=>exactProductionDatabase(cfg,[{uuid,name:'correct'}, {uuid,name:'duplicate'}]),/production_db_identity_mismatch/);
assert.throws(()=>exactProductionDatabase(cfg,[{uuid,name:'bad name with spaces'}]),/production_db_identity_mismatch/);
assert.equal(database.prepare('PRAGMA integrity_check').get().integrity_check,'ok');
database.close();
console.log('M16 readiness: all D1 migrations, full owner sessions, auth isolation, strict schema columns and production binding passed');
