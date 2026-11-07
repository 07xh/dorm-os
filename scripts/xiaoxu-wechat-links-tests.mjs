// WeChat candidate links V1: synthetic end-to-end flow from ingest to the
// task query. No real messages, no real device token, no link is ever fetched.
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {webcrypto} from 'node:crypto';
import {DatabaseSync} from 'node:sqlite';
import {
 createWechatDevice,authenticateWechatDevice,ingestWechatCandidates,
 listWechatCandidates,revokeWechatDevice,reviewWechatCandidate,WechatDeviceError
} from '../lib/academic-wechat-device-sync.mjs';
import {WECHAT_CANDIDATE_SCHEMA} from '../lib/academic-wechat-candidate-contract.mjs';
import {listAssignments} from '../lib/academic-assignments.mjs';

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
const owner='owner-links',str=n=>String(n).repeat(20);
const refused=async(promise,code)=>assert.rejects(promise,
 e=>e instanceof WechatDeviceError&&e.code===code);
const paired=await createWechatDevice(db,owner,{name:'链接同步测试电脑'});
const device=await authenticateWechatDevice(db,'Bearer '+paired.token);
const record={
 messageKey:str('m'),candidateKey:str('c'),chatKey:str('g'),
 kind:'homework',title:'完成电路实验记录 [链接]',
 sentAt:'2026-11-07T11:59:00+08:00',
 deadline:{precision:'date',date:'2026-11-10'},
 links:['https://course.example/hw/template.zip',
        'https://course.example/hw/参考表','https://course.example/hw/submit']
};
const batch={
 schema:WECHAT_CANDIDATE_SCHEMA,deviceId:paired.device.id,batchId:str('q'),
 capturedAt:'2026-11-07T12:00:00+08:00',records:[record]
};
const first=await ingestWechatCandidates(db,device,batch);
assert.deepEqual([first.received,first.inserted,first.duplicateBatch],[1,1,false]);
const pending=(await listWechatCandidates(db,owner))[0];
assert.deepEqual(pending.links,record.links,
 'all three links survive ingest in their original order');
assert.equal((await listWechatCandidates(db,owner,{status:'accepted'})).length,0);

// Idempotent retry with the same candidateKey but a fresh batch id: no
// duplicate link rows and no duplicate candidates.
const repeat=await ingestWechatCandidates(db,device,{...batch,batchId:str('r')});
assert.equal(repeat.inserted,0);
assert.equal(sqlite.prepare(
 'SELECT COUNT(*) AS n FROM academic_wechat_candidate_links').get().n,3,
 'retries must not duplicate stored links');
const retriedPending=(await listWechatCandidates(db,owner))[0];
assert.deepEqual(retriedPending.links,record.links);

// Old collector batches without any links field keep syncing unchanged.
const legacy=await ingestWechatCandidates(db,device,{...batch,batchId:str('l'),
 records:[{...record,candidateKey:str('o'),title:'纯文本任务',links:undefined}]});
assert.equal(legacy.inserted,1);
const legacyPending=(await listWechatCandidates(db,owner,{status:'needs_review'}))
 .find(x=>x.id!==pending.id);
assert.deepEqual(legacyPending.links,[],'legacy records expose an empty link list');
assert.equal(sqlite.prepare(
 'SELECT COUNT(*) AS n FROM academic_wechat_candidate_links').get().n,3);

// V1.1 safe historical backfill: the same message re-synced by an upgraded
// collector backfills links ONLY while the candidate is still pending review.
const backfilled=await ingestWechatCandidates(db,device,{...batch,batchId:str('b2'),
 records:[{...record,candidateKey:str('o'),title:'纯文本任务',
           links:['https://course.example/late-link']}]});
assert.equal(backfilled.inserted,0,'backfill never duplicates the candidate');
assert.equal(sqlite.prepare(
 'SELECT COUNT(*) AS n FROM academic_wechat_candidate_links').get().n,4);
const backfilledRow=(await listWechatCandidates(db,owner,{status:'needs_review'}))
 .find(x=>x.id===legacyPending.id);
assert.deepEqual(backfilledRow.links,['https://course.example/late-link'],
 'a pending legacy candidate gains its links on re-sync');
// Repeat backfill is idempotent.
await ingestWechatCandidates(db,device,{...batch,batchId:str('b3'),
 records:[{...record,candidateKey:str('o'),title:'纯文本任务',
           links:['https://course.example/late-link']}]});
assert.equal(sqlite.prepare(
 'SELECT COUNT(*) AS n FROM academic_wechat_candidate_links').get().n,4);

// Accepting the candidate keeps its links bound to the created task.
const accepted=await reviewWechatCandidate(db,owner,pending.id,'accept');
assert.equal(accepted.status,'accepted');
const tasks=await listAssignments(db,owner);
assert.equal(tasks.length,1);
assert.equal(tasks[0].id,accepted.assignmentId);
assert.deepEqual(tasks[0].links,record.links,
 'accepted candidate links are preserved on the official task query');
assert.deepEqual((await listWechatCandidates(db,owner,{status:'accepted'}))[0].links,
 record.links,'links stay consistent with the accepted review status');
assert.equal((await listAssignments(db,'owner-b')).length,0,
 'another owner never sees the task or its links');

// Already reviewed candidates never change: a re-sync cannot append links
// after acceptance, keeping review status and link data consistent.
const reingest=await ingestWechatCandidates(db,device,{...batch,batchId:str('b4'),
 records:[{...record,candidateKey:str('c'),
           links:['https://course.example/post-review-link']}]});
assert.equal(reingest.inserted,0);
assert.equal(sqlite.prepare(
 'SELECT COUNT(*) AS n FROM academic_wechat_candidate_links').get().n,4,
 'reviewed candidates never gain links after the fact');
assert.deepEqual((await listWechatCandidates(db,owner,{status:'accepted'}))[0].links,
 record.links);

// Dismissed candidates never create tasks, and their links never leak into
// any task query result.
const dismissedPending=(await listWechatCandidates(db,owner,{status:'needs_review'}))
 .find(x=>x.id!==pending.id);
await reviewWechatCandidate(db,owner,dismissedPending.id,'dismiss');
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n,1);
assert.deepEqual((await listAssignments(db,owner)).map(t=>t.links),[record.links]);

// Revoked-device write protection (V1.1 review P0): a device whose
// credentials were revoked between authentication and the batch write must
// not persist anything — including a pending-candidate link backfill. The
// API call fails and the database is left untouched.
const beforeRevoke=await ingestWechatCandidates(db,device,{...batch,batchId:str('u'),
 records:[{...record,candidateKey:str('p'),title:'撤销前旧采集任务',links:undefined}]});
assert.equal(beforeRevoke.inserted,1,
 'a fresh pending candidate exists before the device is revoked');
const linksBeforeRevoke=sqlite.prepare(
 'SELECT COUNT(*) AS n FROM academic_wechat_candidate_links').get().n;
await revokeWechatDevice(db,owner,paired.device.id);
await refused(ingestWechatCandidates(db,device,{...batch,batchId:str('v'),
 records:[{...record,candidateKey:str('p'),title:'撤销前旧采集任务',
           links:['https://course.example/revoked-leak']}]}),
 'device_unauthorized');
assert.equal(sqlite.prepare(
 'SELECT COUNT(*) AS n FROM academic_wechat_candidate_links').get().n,
 linksBeforeRevoke,'a revoked device can never persist links, not even backfills');
assert.equal(sqlite.prepare(
 'SELECT COUNT(*) AS n FROM academic_wechat_candidate_links '+
 'WHERE url LIKE \'%revoked-leak%\'').get().n,0);

// Restart resilience: the same data read again after "closing the app"
// (a fresh query) still returns the same links.
const again=await listAssignments(db,owner);
assert.deepEqual(again[0].links,record.links);
assert.equal(sqlite.prepare('PRAGMA foreign_key_check').all().length,0);
assert.equal(sqlite.prepare('PRAGMA quick_check').get().quick_check,'ok');
sqlite.close();
console.log('校序微信链接 V1：三链接有序入库、重试不重复、旧端兼容、审核后任务查询保留链接、owner 隔离通过');
