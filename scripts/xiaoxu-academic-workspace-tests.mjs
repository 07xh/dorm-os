import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {patchPersonalTask,readTaskHistory,readSourceHealth,WorkspaceError}
 from '../lib/academic-workspace.mjs';
import {listAssignments} from '../lib/academic-assignments.mjs';
import {onRequestPatch,onRequestPost} from '../functions/api/v1/assignments/[id].js';

const sqlite=new DatabaseSync(':memory:');
sqlite.exec('PRAGMA foreign_keys=ON');
for(let i=1;i<=5;i++){
 const name=({
  1:'0001_academic_core.sql',
  2:'0002_academic_legacy_links.sql',
  3:'0003_academic_verified_snapshots.sql',
  4:'0004_academic_source_course.sql',
  5:'0005_academic_workspace.sql'
 })[i];
 sqlite.exec(readFileSync(new URL('../migrations/'+name,import.meta.url),'utf8'));
}
// Task ledger queries include reviewed WeChat task provenance in the live schema.
sqlite.exec(readFileSync(new URL(
 '../migrations/0017_academic_wechat_candidates.sql',import.meta.url),'utf8'));
const db={
 prepare(sql){let params=[];return{
   bind(...values){params=values;return this;},
   first(){return sqlite.prepare(sql).get(...params)??null;},
   all(){return {results:sqlite.prepare(sql).all(...params)};},
   run(){let result=sqlite.prepare(sql).run(...params);return {meta:{changes:result.changes}};}
  };
 },
 async batch(statements){
  sqlite.exec('BEGIN');
  try{const done=statements.map(s=>s.run());sqlite.exec('COMMIT');return done;}
  catch(e){sqlite.exec('ROLLBACK');throw e;}
 }
};
const time='2026-11-03T10:00:00.000Z';
sqlite.prepare('INSERT INTO academic_users(id,created_at) VALUES (?,?)').run('me',time);
sqlite.prepare('INSERT INTO academic_users(id,created_at) VALUES (?,?)').run('other',time);
sqlite.prepare(`INSERT INTO academic_assignments
(id,user_id,title,kind,due_local_date,due_precision,local_status,created_at,updated_at)
VALUES ('manual-1','me','工程制图','manual','2026-11-20','date','todo',?,?)`).run(time,time);
sqlite.prepare(`INSERT INTO academic_assignments
(id,user_id,title,kind,local_status,created_at,updated_at)
VALUES ('remote-1','me','学习通练习','platform','todo',?,?)`).run(time,time);
sqlite.prepare(`INSERT INTO academic_assignments
(id,user_id,title,kind,local_status,created_at,updated_at)
VALUES ('other-1','other','隐藏作业','manual','todo',?,?)`).run(time,time);
let sequence=0;
const options={id:()=> 'evt-'+(++sequence),now:()=> '2026-11-03T11:00:00.000Z'};
const first=await patchPersonalTask(db,'me','manual-1',{
 revision:0,localStatus:'in_progress'
},options);
assert.equal(first.revision,1);
assert.equal(sqlite.prepare("SELECT local_status FROM academic_assignments WHERE id='manual-1'").get().local_status,'in_progress');
assert.equal((await readTaskHistory(db,'me','manual-1')).length,1);
const unchanged=await patchPersonalTask(db,'me','manual-1',{revision:1,localStatus:'in_progress'},options);
assert.equal(unchanged.unchanged,true);
assert.equal((await readTaskHistory(db,'me','manual-1')).length,1);
await assert.rejects(()=>patchPersonalTask(db,'me','manual-1',{revision:0,localStatus:'done'},options),
  e=>e instanceof WorkspaceError&&e.status===409);
await assert.rejects(()=>patchPersonalTask(db,'me','other-1',{revision:0,localStatus:'done'},options),
  e=>e instanceof WorkspaceError&&e.status===404);
await assert.rejects(()=>readTaskHistory(db,'me','other-1'),
  e=>e instanceof WorkspaceError&&e.status===404);
const edit=await patchPersonalTask(db,'me','manual-1',{
 revision:1,title:'工程制图练习册',
 deadline:{precision:'exact',date:'2026-11-22',at:'2026-11-22T23:59:00+08:00'},
 personalTargetAt:'2026-11-21T20:00:00+08:00',
 localStatus:'done'
},{...options,now:()=> '2026-11-03T11:05:00.000Z'});
assert.equal(edit.revision,2);
const stored=sqlite.prepare("SELECT * FROM academic_assignments WHERE id='manual-1'").get();
assert.equal(stored.due_at_utc,'2026-11-22T15:59:00.000Z');
assert.equal(stored.due_precision,'exact');
assert.equal(stored.local_status,'done');
assert.equal((await readTaskHistory(db,'me','manual-1')).length,5);
await assert.rejects(()=>patchPersonalTask(db,'me','remote-1',{
 revision:0,title:'伪造官方标题'
},options),e=>e instanceof WorkspaceError&&e.code==='source_fields_readonly');
const localOnly=await patchPersonalTask(db,'me','remote-1',{
 revision:0,localStatus:'done'
},options);
assert.equal(localOnly.revision,1);
assert.equal(sqlite.prepare("SELECT title FROM academic_assignments WHERE id='remote-1'").get().title,'学习通练习');
await assert.rejects(()=>patchPersonalTask(db,'me','remote-1',{
 revision:1,deadline:{precision:'date',date:'2026-11-22'}
},options),e=>e instanceof WorkspaceError&&e.status===403);
await assert.rejects(()=>patchPersonalTask(db,'me','manual-1',{
 revision:2,deadline:{precision:'date',date:'2026-02-30'}
},options),e=>e instanceof WorkspaceError);
const rows=await listAssignments(db,'me');
assert.equal(rows.length,2);
assert.equal(rows.find(x=>x.id==='manual-1').revision,2);
sqlite.prepare(`INSERT INTO academic_connections(id,user_id,provider,mode,status,last_success_at)
VALUES ('conn1','me','chaoxing','readonly','partial',?)`).run(time);
sqlite.prepare(`INSERT INTO academic_verified_batches
(user_id,kind,source,last_success_at,batch_id,updated_at)
VALUES ('me','assignments','chaoxing',?,'batch-1',?)`).run(time,time);
const health=await readSourceHealth(db,'me');
assert.equal(health.connections.length,1);
assert.equal(health.connections[0].status,'partial');
assert.equal(health.userSnapshots[0].state,'user_supplied_snapshot');
assert.equal((await readSourceHealth(db,'other')).connections.length,0);
// API fail-closed with no Cloudflare Access configuration.
const noAccess=await onRequestPatch({
 request:new Request('https://example.com/api/v1/assignments/manual-1',{
 method:'PATCH',headers:{'content-type':'application/json'},
 body:JSON.stringify({revision:2,localStatus:'todo'})}),
 params:{id:'manual-1'},env:{ACADEMIC_DB:db}
});
assert.equal(noAccess.status,503);
const forbidden=await onRequestPatch({
 request:new Request('https://example.com/api/v1/assignments/manual-1',{
 method:'PATCH',headers:{origin:'https://evil.example','content-type':'application/json'},
 body:JSON.stringify({revision:2,localStatus:'todo'})}),
 params:{id:'manual-1'},env:{}
});
assert.equal(forbidden.status,403);
const androidNoSession=await onRequestPost({
 request:new Request('https://example.com/api/v1/assignments/manual-1',{
 method:'POST',headers:{'content-type':'application/json'},
 body:JSON.stringify({revision:2,localStatus:'todo'})}),
 params:{id:'manual-1'},env:{ACADEMIC_DB:db}
});
assert.equal(androidNoSession.status,503,
 'Android personal-status POST must fail closed without owner authentication');
const androidForeignOrigin=await onRequestPost({
 request:new Request('https://example.com/api/v1/assignments/manual-1',{
 method:'POST',headers:{origin:'https://evil.example','content-type':'application/json'},
 body:JSON.stringify({revision:2,localStatus:'todo'})}),
 params:{id:'manual-1'},env:{}
});
assert.equal(androidForeignOrigin.status,403,
 'Android personal-status POST must retain same-origin enforcement');
console.log('校序 M3: D1 迁移/用户隔离/并发版本/历史事件/平台只读限制/健康状态/鉴权拒绝全部通过');
