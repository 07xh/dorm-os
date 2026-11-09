import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {
 graphHasCycle,readCloudTaskGraph,setCloudTaskPrerequisites
} from '../lib/academic-task-dependencies.mjs';
import {WorkspaceError,patchPersonalTask} from '../lib/academic-workspace.mjs';
import {onRequestGet,onRequestPut,onRequestPost}
 from '../functions/api/v1/assignments/dependencies.js';

const dbFile=new DatabaseSync(':memory:');
dbFile.exec('PRAGMA foreign_keys=ON');
for(const name of ['0001_academic_core.sql','0005_academic_workspace.sql',
 '0019_academic_task_dependencies.sql']){
 dbFile.exec(readFileSync(new URL('../migrations/'+name,import.meta.url),'utf8'));
}
let beforeBatch=null,sequence=0;
const db={
 prepare(sql){let params=[];return{
  bind(...args){params=args;return this;},
  first(){return dbFile.prepare(sql).get(...params)??null;},
  all(){return {results:dbFile.prepare(sql).all(...params)};},
  run(){const x=dbFile.prepare(sql).run(...params);return {meta:{changes:x.changes}};}
 };},
 async batch(statements){
  if(beforeBatch){const fn=beforeBatch;beforeBatch=null;fn();}
  dbFile.exec('BEGIN');
  try{
   const result=statements.map(s=>s.run());
   dbFile.exec('COMMIT');return result;
  }catch(e){dbFile.exec('ROLLBACK');throw e;}
 }
};
const timestamp='2026-11-09T10:00:00.000Z';
let stampSequence=0;
const opts={
 id:()=> 'dependency-event-'+(++sequence),
 now:()=>new Date(Date.parse(timestamp)+1000*(++stampSequence)).toISOString()
};
for(const name of ['owner-a','owner-b'])
 dbFile.prepare('INSERT INTO academic_users(id,created_at) VALUES (?,?)')
  .run(name,timestamp);
for(const [id,owner,kind] of [
 ['a','owner-a','manual'],['b','owner-a','manual'],
 ['c','owner-a','manual'],['official','owner-a','platform'],
 ['other','owner-b','manual']
]){
 dbFile.prepare(`INSERT INTO academic_assignments
 (id,user_id,title,kind,created_at,updated_at)
 VALUES (?,?,?,?,?,?)`).run(id,owner,id,kind,timestamp,timestamp);
}
const read=()=>readCloudTaskGraph(db,'owner-a');
assert.deepEqual(await read(),{revision:0,edges:[]});
const originalPrepare=db.prepare;
let graphQueryCount=0;
db.prepare=(sql)=>{graphQueryCount++;return originalPrepare(sql);};
await read();
assert.equal(graphQueryCount,1,'graph revision and edges must use a single SQLite snapshot');
db.prepare=originalPrepare;
assert.equal(graphHasCycle([{taskId:'a',prerequisiteId:'b'},
 {taskId:'b',prerequisiteId:'a'}]),true);
assert.equal(graphHasCycle([{taskId:'b',prerequisiteId:'a'}]),false);
const set=(data)=>setCloudTaskPrerequisites(db,'owner-a',data,opts);
const rejected=(error,code,status)=>error instanceof WorkspaceError&&
 error.code===code&&(!status||error.status===status);
const first=await set({taskId:'b',taskRevision:0,graphRevision:0,prerequisiteIds:['a']});
assert.equal(first.graphRevision,1);
assert.equal(first.taskRevision,1);
assert.deepEqual((await read()).edges,[{taskId:'b',prerequisiteId:'a'}]);
const unchanged=await set({taskId:'b',taskRevision:1,graphRevision:1,prerequisiteIds:['a']});
assert.equal(unchanged.unchanged,true);
assert.equal((await read()).revision,1,'no-op must not increment graph');
await assert.rejects(()=>set({taskId:'a',taskRevision:0,graphRevision:1,
 prerequisiteIds:['b']}),e=>rejected(e,'dependency_cycle'));
await assert.rejects(()=>set({taskId:'b',taskRevision:1,graphRevision:1,
 prerequisiteIds:['other']}),e=>rejected(e,'unowned_or_platform_prerequisite',403));
await assert.rejects(()=>set({taskId:'official',taskRevision:0,graphRevision:1,
 prerequisiteIds:['a']}),e=>rejected(e,'platform_prerequisites_readonly',403));
await assert.rejects(()=>set({taskId:'b',taskRevision:1,graphRevision:1,
 prerequisiteIds:['a','a']}),e=>rejected(e,'invalid_prerequisites'));
await assert.rejects(()=>set({taskId:'b',taskRevision:1,graphRevision:0,
 prerequisiteIds:[]}),e=>rejected(e,'graph_revision_conflict',409));
await assert.rejects(()=>setCloudTaskPrerequisites(db,'owner-b',{
 taskId:'b',taskRevision:1,graphRevision:0,prerequisiteIds:[]
},opts),e=>rejected(e,'not_found',404));
const blocked=()=>patchPersonalTask(db,'owner-a','b',{revision:1,localStatus:'done'},opts);
await assert.rejects(blocked,e=>rejected(e,'prerequisites_incomplete',409));
assert.equal(dbFile.prepare("SELECT local_status FROM academic_assignments WHERE id='b'").get().local_status,'todo');
await patchPersonalTask(db,'owner-a','a',{revision:0,localStatus:'done'},opts);
// The pre-check sees completed A, but A is reopened by another request
// just before B's D1 batch starts. The DB trigger must reject B atomically.
beforeBatch=()=>dbFile.prepare(`UPDATE academic_assignments
 SET local_status='todo',revision=revision+1 WHERE id='a'`).run();
await assert.rejects(()=>patchPersonalTask(db,'owner-a','b',{
 revision:1,localStatus:'done'
},opts),e=>rejected(e,'prerequisites_incomplete',409));
assert.equal(dbFile.prepare("SELECT local_status FROM academic_assignments WHERE id='b'")
 .get().local_status,'todo','no illegal completion after predecessor race');
assert.equal(dbFile.prepare(`SELECT COUNT(*) AS n FROM academic_assignment_events
 WHERE assignment_id='b' AND event_type='personal_status_changed'`).get().n,0,
 'aborted update never creates a misleading completion history');
await patchPersonalTask(db,'owner-a','a',{revision:2,localStatus:'done'},opts);
await patchPersonalTask(db,'owner-a','b',{revision:1,localStatus:'done'},opts);
assert.equal(dbFile.prepare("SELECT local_status FROM academic_assignments WHERE id='b'").get().local_status,'done');
const add=await set({taskId:'c',taskRevision:0,graphRevision:1,prerequisiteIds:['b','a']});
assert.equal(add.graphRevision,2);
assert.equal((await read()).edges.length,3);
assert.equal((await readCloudTaskGraph(db,'owner-b')).edges.length,0);
assert.equal(dbFile.prepare('SELECT count(*) AS n FROM academic_task_graph_guards').get().n,0);

// Concurrency regression: a revision changes after the graph was read,
// but before its atomic write. The SQLite trigger must abort all mutations.
beforeBatch=()=>dbFile.prepare(`UPDATE academic_task_graph_versions
 SET revision=revision+1 WHERE user_id='owner-a'`).run();
await assert.rejects(()=>set({taskId:'c',taskRevision:1,graphRevision:2,
 prerequisiteIds:['a']}),e=>rejected(e,'graph_revision_conflict',409));
assert.equal((await read()).edges.length,3,'stale batch never deletes edges');
assert.equal(dbFile.prepare('SELECT count(*) AS n FROM academic_task_graph_guards').get().n,0);
beforeBatch=()=>dbFile.prepare(`UPDATE academic_assignments
 SET revision=revision+1 WHERE id='c'`).run();
await assert.rejects(()=>set({taskId:'c',taskRevision:1,graphRevision:3,
 prerequisiteIds:[]}),e=>rejected(e,'task_revision_conflict',409));
assert.equal((await read()).edges.length,3,'stale task write rolls back');
assert.equal(dbFile.prepare('SELECT count(*) AS n FROM academic_task_graph_guards').get().n,0);
assert.equal(dbFile.prepare(`SELECT count(*) AS n FROM academic_assignment_events
 WHERE event_type='prerequisites_changed'`).get().n,2,'history events only for committed changes');
assert.equal(dbFile.prepare('PRAGMA foreign_key_check').all().length,0);
assert.equal(dbFile.prepare('PRAGMA integrity_check').get().integrity_check,'ok');
// API remains fail-closed without existing owner auth; no second identity.
const apiReq=(method,body={},origin='https://academic.example')=>
 new Request('https://academic.example/api/v1/assignments/dependencies',{
  method,headers:{'content-type':'application/json','origin':origin},
  ...(method==='GET'?{}:{body:JSON.stringify(body)})
 });
assert.equal((await onRequestGet({request:apiReq('GET'),env:{ACADEMIC_DB:db}})).status,503);
assert.equal((await onRequestPut({request:apiReq('PUT',{},'https://evil.example'),
 env:{ACADEMIC_DB:db}})).status,403);
assert.equal((await onRequestPost({request:apiReq('POST',{}),env:{ACADEMIC_DB:db}})).status,503);
dbFile.close();
console.log('NextTodo cloud DAG: D1 migration, owner isolation, 2-parent unlock, cycle/stale CAS atomicity and auth failure gates PASS');
