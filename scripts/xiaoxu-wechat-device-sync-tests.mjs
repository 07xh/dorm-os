import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {webcrypto} from 'node:crypto';
import {DatabaseSync} from 'node:sqlite';
import {
 createWechatDevice,authenticateWechatDevice,ingestWechatCandidates,
 listWechatCandidates,listWechatDevices,revokeWechatDevice,WechatDeviceError
} from '../lib/academic-wechat-device-sync.mjs';
import {WECHAT_CANDIDATE_SCHEMA} from '../lib/academic-wechat-candidate-contract.mjs';
import {onRequestPost as ingestRoute} from '../functions/api/v1/wechat/ingest.js';

globalThis.crypto??=webcrypto;
const sqlite=new DatabaseSync(':memory:');
sqlite.exec('PRAGMA foreign_keys=ON');
for(const name of ['0001_academic_core.sql','0017_academic_wechat_candidates.sql'])
 sqlite.exec(readFileSync(new URL('../migrations/'+name,import.meta.url),'utf8'));
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
await refused(ingestWechatCandidates(db,device,{...batch,deviceId:str('x')}),'device_mismatch');
await refused(ingestWechatCandidates(db,device,{...batch,batchId:str('z'),records:[
 {...batch.records[0],cookie:'do not persist'}
]}),'record_invalid');
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
await refused(revokeWechatDevice(db,'owner-b',device.id),'device_not_found');
assert.equal((await revokeWechatDevice(db,owner,device.id)).revoked,true);
await refused(authenticateWechatDevice(db,'Bearer '+paired.token),'device_unauthorized');
assert.ok((await listWechatDevices(db,owner))[0].revokedAt);
assert.equal(sqlite.prepare('PRAGMA quick_check').get().quick_check,'ok');
sqlite.close();
console.log('校序 WeChat: 设备授权/撤销、跨账户隔离、事务入库、批次重放、去重、隐私字段拒绝、未审核不入正式任务通过');
