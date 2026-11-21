import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {listWechatCloudMonitor,WechatMonitorError} from '../lib/academic-wechat-monitor.mjs';
const sqlite=new DatabaseSync(':memory:');
sqlite.exec(`
 CREATE TABLE academic_wechat_devices (
  id TEXT PRIMARY KEY,user_id TEXT NOT NULL,display_name TEXT NOT NULL,
  created_at TEXT NOT NULL,last_seen_at TEXT,revoked_at TEXT
 );
 CREATE TABLE academic_wechat_batches (
  device_id TEXT NOT NULL,batch_id TEXT NOT NULL,received_at TEXT NOT NULL
 );
 CREATE TABLE academic_wechat_candidates (
  id TEXT PRIMARY KEY,device_id TEXT NOT NULL,user_id TEXT NOT NULL,
  review_status TEXT NOT NULL
 );
`);
const db={
 prepare(sql){let args=[];return{
  bind(...values){args=values;return this;},
  all(){return {results:sqlite.prepare(sql).all(...args)};}
 };}
};
const now=()=>new Date('2026-11-19T12:00:00.000Z'); // 20:00 Beijing time
for(const [id,owner,seen,revoked] of [
 ['a','owner-a','2026-11-19T11:55:00.000Z',null],
 ['b','owner-a','2026-11-19T08:00:00.000Z',null],
 ['c','owner-a',null,null],
 ['d','owner-a',null,'2026-11-19T06:00:00.000Z'],
 ['e','owner-b','2026-11-19T11:58:00.000Z',null]
]){
 sqlite.prepare('INSERT INTO academic_wechat_devices VALUES (?,?,?,?,?,?)')
  .run(id,owner,'Windows '+id,'2026-11-01T00:00:00.000Z',seen,revoked);
}
for(const [device,batch,received] of [
 ['a','new','2026-11-19T11:55:00.000Z'],
 ['a','old','2026-11-17T12:00:00.000Z'],
 ['e','private','2026-11-19T11:58:00.000Z']
]){
 sqlite.prepare('INSERT INTO academic_wechat_batches VALUES (?,?,?)').run(device,batch,received);
}
for(const [id,device,owner,status] of [
 ['one','a','owner-a','needs_review'],
 ['two','a','owner-a','accepted'],
 ['private','e','owner-b','needs_review']
]){
 sqlite.prepare('INSERT INTO academic_wechat_candidates VALUES (?,?,?,?)').run(id,device,owner,status);
}
const result=await listWechatCloudMonitor(db,'owner-a',{now});
assert.equal(result.devices.length,4);
const byId=Object.fromEntries(result.devices.map(d=>[d.id,d]));
assert.deepEqual(
 [byId.a.cloudState,byId.b.cloudState,byId.c.cloudState,byId.d.cloudState],
 ['recent','overdue','not_verified','revoked']
);
assert.equal(byId.a.confirmedBatches24h,1);
assert.equal(byId.a.pendingCount,1);
assert.equal(byId.a.lastConfirmedAt,'2026-11-19T11:55:00.000Z');
assert.ok(!JSON.stringify(result).includes('private'));
const night=await listWechatCloudMonitor(db,'owner-a',{
 now:()=>new Date('2026-11-19T17:00:00.000Z')
});
assert.equal(night.devices.find(d=>d.id==='b').cloudState,'outside_window');
const morning=await listWechatCloudMonitor(db,'owner-a',{
 now:()=>new Date('2026-11-19T23:00:00.000Z') // next day 07:00 Beijing
});
assert.equal(morning.devices.find(d=>d.id==='b').cloudState,'starting_window');
assert.equal((await listWechatCloudMonitor(db,'owner-b',{now})).devices.length,1);
await assert.rejects(listWechatCloudMonitor(db,'owner/a',{now}),
 e=>e instanceof WechatMonitorError&&e.code==='not_configured');
sqlite.close();
console.log('校序微信云端监控：属主隔离、确认批次、待审核聚合、时段状态及隐私字段通过');
