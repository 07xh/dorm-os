import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {randomBytes,createHash} from 'node:crypto';
import {DatabaseSync} from 'node:sqlite';
import {runChaoxingAutoCycle} from '../lib/academic-chaoxing-auto-sync.mjs';
import {verifyCronToken} from '../lib/academic-cron-auth.mjs';
import {readCronWorkerHealth,isRecentCronHeartbeat,recordCronWorkerHeartbeat} from '../lib/academic-chaoxing-cron-health.mjs';
import {cronHash} from './deploy-chaoxing-hourly-cron.mjs';
import {onRequestPost as hourly} from '../functions/api/v1/internal/chaoxing-hourly.js';

const sqlite=new DatabaseSync(':memory:');sqlite.exec('PRAGMA foreign_keys=ON');
for(const name of [
 '0001_academic_core.sql','0002_academic_legacy_links.sql',
 '0003_academic_verified_snapshots.sql','0004_academic_source_course.sql',
 '0005_academic_workspace.sql','0006_academic_scheduler.sql',
 '0016_academic_chaoxing_cron.sql',
 '0017_academic_chaoxing_cron_health.sql'
])sqlite.exec(readFileSync(new URL('../migrations/'+name,import.meta.url),'utf8'));
const db={
 prepare(sql){let values=[];return{
  bind(...args){values=args;return this;},
  first(){return sqlite.prepare(sql).get(...values)??null;},
  all(){return {results:sqlite.prepare(sql).all(...values)};},
  run(){const result=sqlite.prepare(sql).run(...values);
   return {meta:{changes:Number(result.changes)}};
  }
 }},
 async batch(stmts){sqlite.exec('BEGIN');try{
  const rows=stmts.map(x=>x.run());sqlite.exec('COMMIT');return rows;
 }catch(e){sqlite.exec('ROLLBACK');throw e;}}
};
const token=randomBytes(32).toString('base64url');
const digest=cronHash(token);
assert.equal(digest,createHash('sha256').update(token).digest('hex'));
sqlite.prepare('INSERT INTO academic_cron_identity VALUES (?,?,?)').run(
 'chaoxing-hourly',digest,'2026-11-04T01:00:00Z');
assert.equal(await verifyCronToken(db,token),true);
assert.equal(await verifyCronToken(db,randomBytes(32).toString('base64url')),false);
assert.equal(await verifyCronToken(db,'short'),false);
assert.equal(JSON.stringify(sqlite.prepare('SELECT * FROM academic_cron_identity').all()).includes(token),false);
// A token row is NOT proof of a deployed, reachable scheduled Worker.
assert.equal((await readCronWorkerHealth(db)).ready,false);
const old='2026-11-04T01:00:00Z',heartbeat='2026-11-04T02:00:00Z';
assert.equal(isRecentCronHeartbeat({
 configured_at:old,observed_configured_at:old,last_heartbeat_at:heartbeat
},Date.parse('2026-11-04T03:00:00Z')),true);
assert.equal(isRecentCronHeartbeat({
 configured_at:old,observed_configured_at:old,last_heartbeat_at:heartbeat
},Date.parse('2026-11-04T05:00:00Z')),false);
assert.equal(isRecentCronHeartbeat({
 configured_at:old,observed_configured_at:'rotated',
 last_heartbeat_at:heartbeat
},Date.parse('2026-11-04T03:00:00Z')),false);
assert.equal(isRecentCronHeartbeat({
 configured_at:old,observed_configured_at:old,last_heartbeat_at:'invalid'
},Date.parse('2026-11-04T03:00:00Z')),false);
const request=key=>new Request('https://dorm-os-dk9.pages.dev/api/v1/internal/chaoxing-hourly',{
 method:'POST',headers:{'content-type':'application/json',...(key?{'x-xu-cron-key':key}:{})},
 body:'{}'
});
const env={ACADEMIC_DB:db,SCHOOL_SESSION_KEY:'not-a-real-school-key'};
assert.equal((await hourly({request:request(null),env})).status,404);
assert.equal((await hourly({request:request('wrong'),env})).status,404);
const idle=await hourly({request:request(token),env});
assert.equal(idle.status,200);
assert.equal((await idle.json()).status,'idle');
assert.equal((await readCronWorkerHealth(db)).ready,true,
 'only an authenticated successful Worker invocation supplies live proof');
assert.equal(sqlite.prepare("SELECT COUNT(*) AS n FROM academic_cron_health").get().n,1);
// A credential rotation invalidates old Worker evidence until the new
// scheduled token actually calls back.
const originalConfigured=sqlite.prepare("SELECT configured_at FROM academic_cron_identity").get().configured_at;
sqlite.prepare("UPDATE academic_cron_identity SET configured_at=?").run(
 new Date(Date.now()-1000).toISOString());
assert.equal((await readCronWorkerHealth(db)).ready,false);
sqlite.prepare("UPDATE academic_cron_identity SET configured_at=?").run(originalConfigured);
assert.equal((await readCronWorkerHealth(db)).ready,true);

let when=Date.parse('2026-11-04T10:00:00Z');
const now=()=>new Date(when).toISOString();
sqlite.prepare('INSERT INTO academic_users(id,created_at) VALUES (?,?)').run('owner-1',now());
sqlite.prepare("INSERT INTO academic_connections(id,user_id,provider,mode,status) VALUES (?,?, 'chaoxing','readonly','connected')").run('c1','owner-1');
sqlite.prepare("INSERT INTO academic_sync_jobs(user_id,provider,enabled,interval_minutes,next_at) VALUES (?,'chaoxing',1,60,?)").run('owner-1',now());
const courses=Array.from({length:5},(_,i)=>({
 courseId:String(100+i),classId:String(200+i),cpi:String(300+i),title:'Synthetic Course '+i
}));
const calls=[],source={
 async readCourses(){return courses;},
 async readHomeworkList(keys){
  calls.push(keys.courseId);
  return {course:courses.find(c=>c.courseId===keys.courseId),
   verifiedEmpty:false,items:[{workId:'99',title:'Synthetic Assignment',
    status:'未提交',statusCode:'todo',platformVerified:true}]};
 }
};
const proof={authorized:true,scope:'self',school:'ncwu',provider:'chaoxing',
 identityBound:true,principalBoundTo:'owner-1',session:{uid:'1001'}};
let reads=0;
const readOwner=async({userId,reader})=>{
 assert.equal(userId,'owner-1');reads++;return reader(source,proof);
};
const adapter={provider:'chaoxing',forSession:async()=>source};
let renewed=0;
const renew=async({userId,proof})=>{
 assert.equal(userId,'owner-1');assert.equal(proof.session.uid,'1001');renewed++;
};
const opt={now,readOwner,renew};
let first=await runChaoxingAutoCycle(db,adapter,'synthetic-secret',opt);
assert.equal(first.status,'success');assert.equal(first.coursesChecked,3);
assert.deepEqual(calls,['100','101','102']);
assert.equal(sqlite.prepare("SELECT COUNT(*) AS n FROM academic_assignments").get().n,3);
assert.equal(sqlite.prepare("SELECT next_course FROM academic_chaoxing_sync_cursor").get().next_course,3);
assert.equal(sqlite.prepare("SELECT last_result FROM academic_sync_jobs").get().last_result,'success');
assert.equal((await runChaoxingAutoCycle(db,adapter,'synthetic-secret',opt)).status,'idle');
when+=60*60000;
let second=await runChaoxingAutoCycle(db,adapter,'synthetic-secret',opt);
assert.equal(second.status,'success');
assert.deepEqual(calls.slice(3),['103','104','100']);
assert.equal(sqlite.prepare("SELECT COUNT(*) AS n FROM academic_assignments").get().n,5);
assert.equal(reads,2);assert.equal(renewed,2);
sqlite.prepare("UPDATE academic_sync_jobs SET enabled=0,next_at=?,lease_until=NULL").run(now());
assert.equal((await runChaoxingAutoCycle(db,adapter,'synthetic-secret',opt)).status,'idle');
sqlite.prepare("UPDATE academic_sync_jobs SET enabled=1,next_at=?,lease_until=NULL").run(now());
let failing=async()=>{const error=Error('hidden school data');error.code='session_expired';throw error;};
const expired=await runChaoxingAutoCycle(db,adapter,'synthetic-secret',{now,readOwner:failing,renew});
assert.equal(expired.status,'expired');
assert.equal(sqlite.prepare("SELECT enabled FROM academic_sync_jobs").get().enabled,0);
assert.equal((await runChaoxingAutoCycle(db,adapter,'synthetic-secret',opt)).status,'idle');
assert.equal(sqlite.prepare('PRAGMA quick_check').get().quick_check,'ok');
sqlite.close();
console.log('Hourly Chaoxing: authenticated trigger, D1-only digest, explicit opt-in, bounded round-robin, trusted owner updates, lease, expiry pause passed');
