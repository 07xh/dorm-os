import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {dueJob,nextAttempt,runDueJobs} from '../lib/academic-scheduler.mjs';
const db0=new DatabaseSync(':memory:');
for(const file of ['0001_academic_core.sql','0002_academic_legacy_links.sql',
'0003_academic_verified_snapshots.sql','0004_academic_source_course.sql',
'0005_academic_workspace.sql','0006_academic_scheduler.sql'])
 db0.exec(readFileSync(new URL('../migrations/'+file,import.meta.url),'utf8'));
db0.exec('PRAGMA foreign_keys=ON');
const db={prepare(sql){let args=[];return{
 bind(...values){args=values;return this;},
 all(){return {results:db0.prepare(sql).all(...args)};},
 first(){return db0.prepare(sql).get(...args);},
 run(){const r=db0.prepare(sql).run(...args);return {meta:{changes:Number(r.changes)}};}
}}};
const at='2026-11-03T10:00:00.000Z';
assert.equal(nextAttempt(at,120),'2026-11-03T12:00:00.000Z');
assert.equal(nextAttempt(at,120,1,'failed'),'2026-11-03T10:30:00.000Z');
assert.equal(nextAttempt(at,120,9,'failed'),'2026-11-04T10:00:00.000Z');
assert.throws(()=>nextAttempt(at,1),/invalid/);
assert.equal(dueJob({provider:'chaoxing',enabled:1,next_at:at,lease_until:null},at),true);
assert.equal(dueJob({provider:'chaoxing',enabled:0,next_at:at},at),false);
db0.prepare('INSERT INTO academic_users(id,created_at) VALUES (?,?)').run('owner-1',at);
db0.prepare("INSERT INTO academic_connections(id,user_id,provider,mode,status) VALUES (?,?,'chaoxing','readonly','connected')").run('conn-1','owner-1');
db0.prepare('INSERT INTO academic_sync_jobs(user_id,provider,enabled,interval_minutes,next_at) VALUES (?,?,1,120,?)').run('owner-1','chaoxing',at);
let called=0;
const adapters={chaoxing:{provider:'chaoxing',verifySession:async()=>({authorized:true}),readPage:async()=>({tasks:[],complete:true})}};
const ok=await runDueJobs(db,adapters,{now:()=>at,run:async()=>{called++;return {coverage:'complete'}}});
assert.equal(ok[0].status,'success');assert.equal(called,1);
assert.equal(db0.prepare('SELECT last_result FROM academic_sync_jobs').get().last_result,'success');
assert.equal((await runDueJobs(db,adapters,{now:()=>at,run:async()=>{called++;return {coverage:'complete'}}})).length,0);
assert.equal(called,1,'not repeated before nextDue');
db0.prepare('UPDATE academic_sync_jobs SET next_at=?,lease_until=NULL').run(at);
const failed=await runDueJobs(db,adapters,{now:()=>at,run:async()=>{const err=Error('secret cookie');err.code='authorization_failed';throw err;}});
assert.equal(failed[0].status,'expired');
assert.equal(db0.prepare('SELECT failure_count FROM academic_sync_jobs').get().failure_count,1);
db0.prepare('UPDATE academic_sync_jobs SET enabled=0,next_at=?').run(at);
assert.equal((await runDueJobs(db,adapters,{now:()=>at,run:async()=>{called++;}})).length,0);
db0.close();
console.log('M5 default-disabled scheduler, SQL, lease claim, backoff, expired auth and private-error redaction passed');
