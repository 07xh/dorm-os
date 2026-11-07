import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {webcrypto} from 'node:crypto';
import {DatabaseSync} from 'node:sqlite';
import {
 createWechatDevice,authenticateWechatDevice,ingestWechatCandidates,
 listWechatCandidates,listWechatDevices,revokeWechatDevice,reviewWechatCandidate,WechatDeviceError
} from '../lib/academic-wechat-device-sync.mjs';
import {WECHAT_CANDIDATE_SCHEMA} from '../lib/academic-wechat-candidate-contract.mjs';
import {listAssignments} from '../lib/academic-assignments.mjs';
import {patchPersonalTask,WorkspaceError} from '../lib/academic-workspace.mjs';
import {onRequestPost as ingestRoute} from '../functions/api/v1/wechat/ingest.js';

globalThis.crypto??=webcrypto;
const sqlite=new DatabaseSync(':memory:');
sqlite.exec('PRAGMA foreign_keys=ON');
for(const name of [
 '0001_academic_core.sql','0002_academic_legacy_links.sql',
 '0003_academic_verified_snapshots.sql','0004_academic_source_course.sql',
 '0005_academic_workspace.sql','0017_academic_wechat_candidates.sql',
 '0018_academic_wechat_candidate_links.sql'
]) sqlite.exec(readFileSync(new URL('../migrations/'+name,import.meta.url),'utf8'));
const db={
 prepare(sql){let params=[];return{
  bind(...args){params=args;return this;},
  first(){return sqlite.prepare(sql).get(...params)??null;},
  all(){return {results:sqlite.prepare(sql).all(...params)};},
  run(){return {meta:{changes:Number(sqlite.prepare(sql).run(...params).changes)}};}
 };},
 async batch(statements){sqlite.exec('BEGIN');try{
  const result=statements.map(x=>x.run());sqlite.exec('COMMIT');return result;
 }catch(error){sqlite.exec('ROLLBACK');throw error;}}
};
const owner='owner-a',str=n=>String(n).repeat(20);
const paired=await createWechatDevice(db,owner,{name:'我的 Windows 电脑'});
assert.ok(/^wxu_[A-Za-z0-9_-]{43}$/.test(paired.token));
assert.equal((await listWechatDevices(db,owner)).length,1);
assert.ok(!JSON.stringify(await listWechatDevices(db,owner)).includes(paired.token),
 'only the initial pairing response exposes the token');
assert.ok(!JSON.stringify(sqlite.prepare('SELECT * FROM academic_wechat_devices').all()).includes(paired.token),
 'the database stores only token digests');
const device=await authenticateWechatDevice(db,'Bearer '+paired.token);
assert.equal(device.userId,owner);
const refused=async(promise,code)=>assert.rejects(promise,e=>e instanceof WechatDeviceError&&e.code===code);
await refused(authenticateWechatDevice(db,'Bearer fake'),'device_unauthorized');
const batch={
 schema:WECHAT_CANDIDATE_SCHEMA,deviceId:paired.device.id,batchId:str('q'),
 capturedAt:'2026-11-04T12:00:00+08:00',
 records:[{
  messageKey:str('m'),candidateKey:str('c'),chatKey:str('g'),
  kind:'homework',title:'完成电路实验记录',
  sentAt:'2026-11-04T11:59:00+08:00',
  deadline:{precision:'date',date:'2026-11-06'}
 }]
};
const first=await ingestWechatCandidates(db,device,batch);
assert.deepEqual([first.received,first.inserted,first.duplicateBatch],[1,1,false]);
assert.equal((await listWechatCandidates(db,owner)).length,1);
assert.equal((await listWechatCandidates(db,'owner-b')).length,0,
 'candidate listings are owner isolated');
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n,0,
 'unreviewed WeChat messages are never silently promoted into coursework');
assert.equal((await ingestWechatCandidates(db,device,batch)).duplicateBatch,true,
 'identical batch can safely retry');
await refused(ingestWechatCandidates(db,device,{...batch,records:[
 {...batch.records[0],title:'changed'}
]}),'batch_id_conflict');
const repeat=await ingestWechatCandidates(db,device,{...batch,batchId:str('r')});
assert.equal(repeat.inserted,0,'same candidate ID in a different batch is still deduplicated');
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_wechat_candidates').get().n,1);
const detailed='11月6日前提交电路实验报告。'+'必须附原理、截图和实验结论。'.repeat(100);
assert.ok(detailed.length > 240 && detailed.length < 4000);
const backfilled=await ingestWechatCandidates(db,device,{...batch,batchId:str('B'),records:[
 {...batch.records[0],excerpt:detailed}
]});
assert.equal(backfilled.inserted,0,'rescan must not create duplicate candidate');
assert.equal((await listWechatCandidates(db,owner))[0].excerpt,detailed,
 'a pending legacy candidate must receive its full redacted message text');
await refused(ingestWechatCandidates(db,device,{...batch,deviceId:str('x')}),'device_mismatch');
await refused(ingestWechatCandidates(db,device,{...batch,batchId:str('z'),records:[
 {...batch.records[0],cookie:'do not persist'}
]}),'record_invalid');
// A scan with no task candidates is still an authenticated network heartbeat.
const heartbeat={...batch,batchId:str('h'),capturedAt:'2026-11-04T13:00:00+08:00',records:[]};
const hb=await ingestWechatCandidates(db,device,heartbeat);
assert.equal(hb.received,0);
assert.equal(hb.inserted,0);
assert.equal(hb.duplicateBatch,false);
assert.equal((await ingestWechatCandidates(db,device,heartbeat)).duplicateBatch,true);
assert.equal(sqlite.prepare(
 'SELECT COUNT(*) AS n FROM academic_wechat_candidates'
).get().n,1,'heartbeat must not invent tasks');
assert.ok(sqlite.prepare(
 'SELECT last_seen_at FROM academic_wechat_devices WHERE id=?'
).get(device.id)?.last_seen_at,'heartbeat updates verified last sync time');
const http=await ingestRoute({env:{ACADEMIC_DB:db},request:new Request(
 'https://academic.example/api/v1/wechat/ingest',{
  method:'POST',headers:{'authorization':'Bearer '+paired.token,
  'content-type':'application/json'},
  body:JSON.stringify({...batch,batchId:str('s')})
 })});
assert.equal(http.status,202);
assert.equal((await http.json()).inserted,0);
const forbidden=await ingestRoute({env:{ACADEMIC_DB:db},request:new Request(
 'https://academic.example/api/v1/wechat/ingest',{
  method:'POST',headers:{'origin':'https://evil.example',
  'authorization':'Bearer '+paired.token,'content-type':'application/json'},
  body:JSON.stringify(batch)
 })});
assert.equal(forbidden.status,403);
const pending=(await listWechatCandidates(db,owner))[0];
await refused(reviewWechatCandidate(db,'owner-b',pending.id,'accept'),'candidate_not_found');
const accepted=await reviewWechatCandidate(db,owner,pending.id,'accept');
assert.equal(accepted.status,'accepted');
await ingestWechatCandidates(db,device,{...batch,batchId:str('C'),records:[
 {...batch.records[0],excerpt:detailed+'补充原文'}
]});
assert.equal(sqlite.prepare('SELECT excerpt FROM academic_wechat_candidates WHERE id=?')
 .get(pending.id).excerpt,detailed,
 'reviewed message content remains immutable when old scans replay');
assert.ok(accepted.assignmentId);
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n,1,
 'one explicitly approved candidate becomes one existing manual task');
assert.equal(sqlite.prepare('SELECT kind FROM academic_assignments').get().kind,'manual');
const acceptedTasks=await listAssignments(db,owner);
assert.equal(acceptedTasks.length,1);
assert.equal(acceptedTasks[0].id,accepted.assignmentId);
assert.equal(acceptedTasks[0].wechat_excerpt,detailed,
 'approved task must retain its reviewed message details');
assert.equal(acceptedTasks[0].source_provider,'wechat',
 'reviewed WeChat tasks must carry authenticated source provenance in the normal task API');
assert.equal(acceptedTasks[0].local_status,'todo');
assert.equal(acceptedTasks[0].revision,0);
const done=await patchPersonalTask(db,owner,accepted.assignmentId,
 {revision:0,localStatus:'done'},{now:()=> '2026-11-06T11:00:00.000Z'});
assert.equal(done.revision,1);
assert.deepEqual(
 (await listAssignments(db,owner)).map(t=>[t.source_provider,t.local_status,t.revision]),
 [['wechat','done',1]],
 'reviewed WeChat task personal completion persists without changing its source');
await assert.rejects(
 patchPersonalTask(db,owner,accepted.assignmentId,{revision:0,localStatus:'todo'}),
 e=>e instanceof WorkspaceError && e.code==='revision_conflict');
await assert.rejects(
 patchPersonalTask(db,'owner-b',accepted.assignmentId,{revision:1,localStatus:'todo'}),
 e=>e instanceof WorkspaceError && e.code==='not_found');
const undone=await patchPersonalTask(db,owner,accepted.assignmentId,
 {revision:1,localStatus:'todo'},{now:()=> '2026-11-06T11:01:00.000Z'});
assert.equal(undone.revision,2);
assert.equal((await listAssignments(db,owner))[0].local_status,'todo');
assert.equal(sqlite.prepare(
 'SELECT COUNT(*) AS n FROM academic_assignment_events WHERE assignment_id=?'
).get(accepted.assignmentId).n,2,
 'personal completion and undo must remain auditable');

assert.equal((await listAssignments(db,'owner-b')).length,0,
 'another owner must not see the approved WeChat assignment');
assert.equal((await listWechatCandidates(db,owner)).length,0,
 'approved candidates are removed from pending inbox');
await refused(reviewWechatCandidate(db,owner,pending.id,'accept'),'candidate_already_reviewed');
await refused(reviewWechatCandidate(db,owner,pending.id,'dismiss'),'candidate_already_reviewed');
await ingestWechatCandidates(db,device,{...batch,batchId:str('u'),records:[
 {...batch.records[0],candidateKey:str('n')}
]});
const otherPending=(await listWechatCandidates(db,owner))[0];
assert.equal((await reviewWechatCandidate(db,owner,otherPending.id,'dismiss')).status,'dismissed');
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n,1,
 'dismissing a candidate never creates a task');
assert.deepEqual((await listAssignments(db,owner)).map(t=>t.source_provider),['wechat'],
 'dismissing another candidate does not promote or reclassify any task');

await refused(revokeWechatDevice(db,'owner-b',device.id),'device_not_found');
assert.equal((await revokeWechatDevice(db,owner,device.id)).revoked,true);
await refused(authenticateWechatDevice(db,'Bearer '+paired.token),'device_unauthorized');
assert.ok((await listWechatDevices(db,owner))[0].revokedAt);
assert.equal(sqlite.prepare('PRAGMA quick_check').get().quick_check,'ok');
sqlite.close();
console.log('校序 WeChat: 设备授权/撤销、跨账户隔离、事务入库、批次重放、去重、隐私字段拒绝、未审核不入正式任务通过');
