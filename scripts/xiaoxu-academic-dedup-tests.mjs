import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import {DatabaseSync} from 'node:sqlite';
import {listAssignments} from '../lib/academic-assignments.mjs';

const sql=new DatabaseSync(':memory:');
sql.exec('PRAGMA foreign_keys=ON');
for(const name of ['0001_academic_core.sql','0002_academic_legacy_links.sql',
 '0003_academic_verified_snapshots.sql','0004_academic_source_course.sql',
 '0005_academic_workspace.sql','0017_academic_wechat_candidates.sql']){
 sql.exec(readFileSync(new URL('../migrations/'+name,import.meta.url),'utf8'));
}
const db={prepare(statement){let args=[];return {
 bind(...values){args=values;return this;},
 first(){return sql.prepare(statement).get(...args)??null;},
 all(){return {results:sql.prepare(statement).all(...args)};},
 run(){return sql.prepare(statement).run(...args);}
}}};
const timestamp='2026-11-03T02:00:00.000Z';
for(const user of ['owner-a','owner-b']){
 sql.prepare('INSERT INTO academic_users(id,created_at) VALUES (?,?)').run(user,timestamp);
}
for(const [id,user] of [['conn-a','owner-a'],['conn-b','owner-b']]){
 sql.prepare(`INSERT INTO academic_connections(id,user_id,provider,profile_key,mode,status)
 VALUES (?,?,'chaoxing','default','readonly','connected')`).run(id,user);
}
for(const [id,user,title] of [
 ['cloud-a','owner-a','同名课程作业'],['cloud-b','owner-b','另一账号的作业'],
 ['legacy-a','owner-a','历史学习通作业'],['manual-a','owner-a','纸质作业']]){
 sql.prepare(`INSERT INTO academic_assignments
 (id,user_id,title,kind,created_at,updated_at)
 VALUES (?,?,?, ?,?,?)`).run(id,user,title,id==='manual-a'?'manual':'platform',timestamp,timestamp);
}
for(const [task,conn,user,external] of [
 ['cloud-a','conn-a','owner-a','work:1'],['cloud-b','conn-b','owner-b','work:b']]){
 sql.prepare(`INSERT INTO academic_assignment_sources
 (assignment_id,connection_id,user_id,external_id,platform_status,last_seen_at,course_title)
 VALUES (?,?,?,?,'todo',?,'Python')`).run(task,conn,user,external,timestamp);
}
sql.prepare(`INSERT INTO academic_legacy_links
(user_id,legacy_id,assignment_id,legacy_source,legacy_external_id,original_fingerprint,migrated_at)
VALUES ('owner-a','local-legacy','legacy-a','chaoxing','work:legacy','hash',?)`).run(timestamp);
const a=await listAssignments(db,'owner-a');
const b=await listAssignments(db,'owner-b');
assert.equal(a.length,3);assert.equal(b.length,1);
assert.deepEqual(a.find(x=>x.id==='cloud-a')&&[
 a.find(x=>x.id==='cloud-a').source_provider,
 a.find(x=>x.id==='cloud-a').source_external_id
],['chaoxing','work:1']);
assert.equal(a.find(x=>x.id==='legacy-a').source_external_id,'work:legacy');
assert.equal(a.find(x=>x.id==='legacy-a').source_provider,'chaoxing');
assert.equal(a.find(x=>x.id==='manual-a').source_external_id,null);
assert.ok(!a.some(x=>x.source_external_id==='work:b'),'cannot leak other user source mappings');
assert.equal(b[0].source_external_id,'work:b');
sql.close();

const cloudCode=readFileSync(new URL('../public/xiaoxu-cloud-tasks.js',import.meta.url),'utf8');
const academicCode=readFileSync(new URL('../public/xiaoxu-academic-client.js',import.meta.url),'utf8');
const html=readFileSync(new URL('../public/index.html',import.meta.url),'utf8');
assert.ok(html.includes('XiaoxuWorkspace.tasks({local:state.tasks,cloud:XiaoxuCloudTasks.displayRecords(),remote:XiaoxuAcademic.records(\'assignments\')}'),
 'task projection receives existing browser tasks');
const cloudItems=[
 {id:'cloud-a',title:'同名课程作业',revision:0,kind:'platform',
  local_status:'todo',source_provider:'chaoxing',source_external_id:'work:1',
  due_precision:'date',due_local_date:'2026-11-20'},
 {id:'cloud-legacy',title:'历史学习通作业',revision:0,kind:'platform',
  local_status:'todo',source_provider:'chaoxing',source_external_id:'work:legacy',
  legacy_id:'local-legacy',due_precision:'unknown'},
 {id:'cloud-local',title:'来自本地学习通',revision:0,kind:'platform',
  local_status:'todo',source_provider:'chaoxing',source_external_id:'work:2',
  due_precision:'unknown'}
];
const snapshots=[
 {externalId:'work:1',title:'同名课程作业',course:'Python',platformStatus:'todo'},
 {externalId:'work:2',title:'来自本地学习通',course:'Python',platformStatus:'todo'},
 {externalId:'work:legacy',title:'历史学习通作业',course:'Python',platformStatus:'todo'},
 {externalId:'work:4',title:'同名课程作业',course:'Python',platformStatus:'todo'}
];
const local=[
 {id:'local-legacy',title:'历史学习通作业',source:'chaoxing',sourceId:'work:legacy',done:false},
 {id:'browser2',title:'来自本地学习通',source:'chaoxing',sourceId:'work:2',done:false}
];
const respond=data=>new Response(JSON.stringify(data),
 {status:200,headers:{'content-type':'application/json'}});
const ctx={Response,Date,Intl,fetch:async url=>{
 if(url.startsWith('/api/v1/assignments?'))return respond({items:cloudItems});
 if(url==='/api/v1/sources')return respond({connections:[],userSnapshots:[]});
 if(url==='/api/v1/connections')return respond({schema:'xiaoxu/connections-v1',platforms:[{id:'ncwu',status:'not_connected'},{id:'chaoxing',status:'not_connected'},{id:'second_class',status:'not_connected'}]});
 if(url==='/api/v1/verified/assignments')return respond({kind:'assignments',
  status:'ready',lastSuccessAt:timestamp,records:snapshots});
 if(url.startsWith('/api/v1/verified/'))return respond({kind:url.split('/').pop(),
  status:'not_connected',records:[],lastSuccessAt:null});
 throw Error('unexpected endpoint '+url);
}};
runInNewContext(cloudCode,ctx);
runInNewContext(academicCode,ctx);
const cloud=ctx.XiaoxuCloudTasks,academic=ctx.XiaoxuAcademic;
assert.deepEqual([...cloud.snapshotKeys()],[]);
await cloud.connect();
await academic.connect();
const cloudRendered=cloud.taskRows('all',local);
assert.ok(cloudRendered.includes('同名课程作业'));
assert.ok(!cloudRendered.includes('历史学习通作业'));
assert.ok(!cloudRendered.includes('来自本地学习通'));
const snapshotRendered=academic.taskRows('all',local);
assert.ok(!snapshotRendered.includes('来自本地学习通'));
assert.ok(!snapshotRendered.includes('历史学习通作业'));
assert.equal(snapshotRendered.match(/同名课程作业/g)?.length,1,
 'same title with a different ID must remain visible');
assert.equal(cloud.reminderCandidates(local).length,1,
 'suppressed cloud duplicates must not generate extra foreground reminders');
assert.equal(cloud.snapshotKeys().length,3);
cloud.disconnect();
assert.equal(academic.taskRows('all',local).match(/同名课程作业/g)?.length,2,
 'disconnected cloud restores independent snapshot entries');
assert.ok(!academic.taskRows('all',local).includes('来自本地学习通'),
 'even without cloud, existing local imported tasks are not repeated');
console.log('校序 M6：SQLite 用户隔离、来源稳定 ID、跨本地/快照/云端去重与同名不同 ID 防误合并通过');
