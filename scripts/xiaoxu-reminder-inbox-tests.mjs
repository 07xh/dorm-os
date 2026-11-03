import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import {DatabaseSync} from 'node:sqlite';
import {webcrypto} from 'node:crypto';
import {buildReminderInbox,shanghaiDay} from '../lib/academic-reminder-inbox.mjs';
import {normalizeRecord} from '../lib/academic-contract.mjs';
import {verifyAcademicAccess} from '../lib/academic-access.mjs';
import {onRequestGet,onRequestPatch} from '../functions/api/v1/reminders.js';

const second=normalizeRecord('second-class',{
 externalId:'activity-1',title:'二课合成记录',credit:1.5,hours:2,points:8,
 activityStatus:'completed'
});
assert.equal(second.credit,1.5);
assert.equal(second.hours,2);
assert.equal(second.points,8);
assert.equal(second.activityStatus,'completed');
const review=normalizeRecord('second-class',{
 externalId:'activity-2',title:'二课候选',reviewStatus:'approved'
});
assert.equal(review.reviewStatus,'approved');
assert.throws(()=>normalizeRecord('second-class',{
 externalId:'activity-3',title:'二课候选',reviewStatus:'bypassed'
}),/未知二课审核状态/);
assert.throws(()=>normalizeRecord('second-class',{externalId:'x',title:'活动',activityStatus:'secret'}),/未知二课/);
assert.equal(normalizeRecord('exams',{
 externalId:'exam-2',term:'2026-1',title:'考试',date:'2026-11-20',startTime:'10:30'
}).startTime,'10:30');
assert.throws(()=>normalizeRecord('exams',{
 externalId:'exam-2',term:'2026-1',title:'考试',date:'2026-11-20',startTime:'25:00'
}),/无效/);
const today=shanghaiDay(Date.now());
const move=(d,n)=>new Date(Date.parse(d+'T00:00:00Z')+n*86400000).toISOString().slice(0,10);
const exam={externalId:'exam-1',title:'考试（合成测试）',date:move(today,2),term:'test-term'};
const task={id:'task-1',title:'作业（合成测试）',due_precision:'date',due_local_date:today,local_status:'todo'};
let pure=buildReminderInbox({exams:[exam],tasks:[task],now:Date.now()});
assert.equal(pure.items.length,2);
assert.equal(pure.preferences.enabled,false,'opt-in off by default');
assert.equal(pure.items.find(x=>x.kind==='task').precision,'date');
assert.ok(pure.items.find(x=>x.kind==='task').when.includes('时刻未知'));
assert.equal(buildReminderInbox({exams:[{...exam,date:'2026-02-30'}]}).items.length,0,'invalid date excluded');
const originalExamKey=pure.items.find(x=>x.kind==='exam').key;
const originalTaskKey=pure.items.find(x=>x.kind==='task').key;
assert.match(originalExamKey,/^exam-v2:/);
assert.match(originalTaskKey,/^task-v2:/);
assert.equal(buildReminderInbox({exams:[exam],tasks:[{...task,local_status:'done'}],readKeys:[originalExamKey]}).unread,0);
const rescheduledExam={...exam,date:move(today,3)};
const examMoved=buildReminderInbox({exams:[rescheduledExam],now:Date.now(),readKeys:[originalExamKey]});
assert.equal(examMoved.items.length,1);
assert.notEqual(examMoved.items[0].key,originalExamKey,'changed exam date gets a new receipt');
assert.equal(examMoved.unread,1,'past read may not silence rescheduled exam');
const sameExam=buildReminderInbox({exams:[{...exam}],now:Date.now(),readKeys:[originalExamKey]});
assert.equal(sameExam.unread,0,'unchanged exam remains read');
const timedExam=buildReminderInbox({exams:[{...exam,startTime:'10:30'}],now:Date.now(),readKeys:[originalExamKey]});
assert.equal(timedExam.unread,1,'newly verified exam hour must alert again');
const endTimeExam=buildReminderInbox({exams:[{...exam,startTime:'10:30',endTime:'12:30'}],now:Date.now(),readKeys:[timedExam.items[0].key]});
assert.equal(endTimeExam.unread,1,'exam end-time correction must alert again');
const shiftedTask=buildReminderInbox({tasks:[{...task,due_local_date:move(today,1)}],now:Date.now(),readKeys:[originalTaskKey]});
assert.equal(shiftedTask.unread,1);
assert.notEqual(shiftedTask.items[0].key,originalTaskKey);
const exactTask={...task,due_precision:'exact',
  due_at_utc:new Date(move(today,1)+'T10:30:00+08:00').toISOString(),due_local_date:move(today,1)};
const exactOnce=buildReminderInbox({tasks:[exactTask],now:Date.now(),readKeys:[originalTaskKey]});
assert.equal(exactOnce.unread,1,'date-only becoming exact should re-alert');
const exactSame=buildReminderInbox({tasks:[exactTask],now:Date.now(),readKeys:[exactOnce.items[0].key]});
assert.equal(exactSame.unread,0);
const exactChanged=buildReminderInbox({tasks:[{...exactTask,due_at_utc:new Date(move(today,1)+'T11:00:00+08:00').toISOString()}],now:Date.now(),readKeys:[exactOnce.items[0].key]});
assert.equal(exactChanged.unread,1,'exact clock change must not inherit old read');
assert.equal(buildReminderInbox({exams:[{...exam,externalId:'exam-2',title:exam.title}],now:Date.now(),readKeys:[originalExamKey]}).unread,1,
 'same-title different external ID is a different event');
assert.equal(buildReminderInbox({exams:[exam],preferences:{examLeadDays:0}}).items.length,0);

const sql=new DatabaseSync(':memory:');
sql.exec('PRAGMA foreign_keys=ON');
for(const f of ['0001_academic_core.sql','0003_academic_verified_snapshots.sql','0007_academic_reminder_inbox.sql'])
 sql.exec(readFileSync(new URL('../migrations/'+f,import.meta.url),'utf8'));
const db={
 prepare(statement){
  let args=[];
  return {bind(...values){args=values;return this;},
   first(){return sql.prepare(statement).get(...args)??null;},
   all(){return {results:sql.prepare(statement).all(...args)};},
   run(){return sql.prepare(statement).run(...args);}};
 },
 async batch(statements){
  sql.exec('BEGIN');
  try{const out=statements.map(x=>x.run());sql.exec('COMMIT');return out;}
  catch(e){sql.exec('ROLLBACK');throw e;}
 }
};

const domain='test-team.cloudflareaccess.com',aud='reminder-test-aud';
const pair=await webcrypto.subtle.generateKey({name:'RSASSA-PKCS1-v1_5',modulusLength:2048,
 publicExponent:new Uint8Array([1,0,1]),hash:'SHA-256'},true,['sign','verify']);
const jwk=await webcrypto.subtle.exportKey('jwk',pair.publicKey);
Object.assign(jwk,{kid:'reminder-test-key',alg:'RS256',use:'sig'});
const base64=x=>Buffer.from(JSON.stringify(x)).toString('base64url');
const now=Math.floor(Date.now()/1000);
async function sign(sub){
 const payload={iss:'https://'+domain,aud:[aud],sub,iat:now-10,exp:now+3600};
 const content=base64({alg:'RS256',kid:jwk.kid})+'.'+base64(payload);
 const sig=await webcrypto.subtle.sign('RSASSA-PKCS1-v1_5',pair.privateKey,new TextEncoder().encode(content));
 return content+'.'+Buffer.from(sig).toString('base64url');
}
const a=await sign('user-a'),b=await sign('user-b');
const env={ACADEMIC_DB:db,ACCESS_TEAM_DOMAIN:domain,ACCESS_AUD:aud};
const oldFetch=globalThis.fetch;
globalThis.fetch=async()=>new Response(JSON.stringify({keys:[jwk]}),{status:200});
function ctx(jwt,method='GET',body,origin='https://academic.test'){
 const headers={'Cf-Access-Jwt-Assertion':jwt};
 if(body){headers['Content-Type']='application/json';headers.Origin=origin;}
 return {env,request:new Request('https://academic.test/api/v1/reminders',{
  method,headers,body:body?JSON.stringify(body):undefined})};
}
try{
 const id=(await verifyAcademicAccess(ctx(a).request,env)).userId;
 sql.prepare('INSERT INTO academic_users(id,created_at) VALUES (?,?)').run(id,new Date().toISOString());
 sql.prepare('INSERT INTO academic_verified_batches(user_id,kind,source,last_success_at,batch_id,updated_at) VALUES (?,?,?,?,?,?)')
  .run(id,'exams','ncwu',new Date().toISOString(),'exam-batch',new Date().toISOString());
 sql.prepare('INSERT INTO academic_verified_records(user_id,kind,source,external_id,term,payload,batch_id,updated_at) VALUES (?,?,?,?,?,?,?,?)')
  .run(id,'exams','ncwu',exam.externalId,exam.term,JSON.stringify(exam),'exam-batch',new Date().toISOString());
 sql.prepare('INSERT INTO academic_assignments(id,user_id,title,kind,due_local_date,due_precision,local_status,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)')
  .run(task.id,id,task.title,'manual',today,'date','todo',new Date().toISOString(),new Date().toISOString());
 const denied=await onRequestGet({request:ctx('').request,env});
 assert.equal(denied.status,401);
 assert.equal((await onRequestGet({request:ctx(a).request,env:{}})).status,503);
 let result=await onRequestGet(ctx(a));
 assert.equal(result.status,200);
 assert.equal(result.headers.get('cache-control'),'private, no-store');
 let data=await result.json();
 assert.equal(data.items.length,2);
 assert.equal(data.source.exams,'personal_snapshot','self-upload is not falsely attested as platform sync');
 assert.equal(data.delivery,'in_app_only');
 assert.equal(data.preferences.enabled,false);
 assert.equal((await (await onRequestGet(ctx(b))).json()).items.length,0,'cross-account isolation');
 const pref={operation:'settings',enabled:true,examLeadDays:7,taskLeadDays:3};
 assert.equal((await onRequestPatch(ctx(a,'PATCH',pref,'https://evil.test'))).status,403,'same-origin PATCH only');
 assert.equal((await onRequestPatch(ctx(a,'PATCH',{...pref,examLeadDays:300}))).status,422);
 result=await onRequestPatch(ctx(a,'PATCH',pref));
 assert.equal(result.status,200);
 data=await result.json();
 assert.equal(data.preferences.enabled,true);
 const key=data.items.find(x=>x.kind==='exam').key;
 data=await (await onRequestPatch(ctx(a,'PATCH',{operation:'read',key,read:true}))).json();
 assert.equal(data.unread,1);
 assert.equal(data.items.find(x=>x.key===key).read,true);
 assert.equal((await (await onRequestGet(ctx(a))).json()).unread,1,'read state persisted');
 // A corrected exam schedule must not inherit the old read receipt.
 const changedExam={...exam,date:move(today,3)};
 sql.prepare('UPDATE academic_verified_records SET payload=? WHERE user_id=? AND kind=? AND external_id=?')
  .run(JSON.stringify(changedExam),id,'exams',exam.externalId);
 const moved=await (await onRequestGet(ctx(a))).json();
 const newerKey=moved.items.find(x=>x.kind==='exam').key;
 assert.notEqual(newerKey,key);
 assert.equal(moved.items.find(x=>x.kind==='exam').read,false);
 assert.equal(moved.unread,2);
 assert.equal((await onRequestPatch(ctx(a,'PATCH',{operation:'read',key,read:true})).status,404,
   'old event key cannot be marked when it is no longer in the inbox');
 await onRequestPatch(ctx(a,'PATCH',{operation:'read',key:newerKey,read:true}));
 // Moving a date-only assignment also reopens the reminder.
 const beforeTask=(await (await onRequestGet(ctx(a))).json()).items.find(x=>x.kind==='task').key;
 await onRequestPatch(ctx(a,'PATCH',{operation:'read',key:beforeTask,read:true}));
 sql.prepare('UPDATE academic_assignments SET due_local_date=? WHERE id=? AND user_id=?')
  .run(move(today,1),task.id,id);
 const movedTask=(await (await onRequestGet(ctx(a))).json());
 assert.equal(movedTask.items.find(x=>x.kind==='task').read,false);
 assert.equal(movedTask.unread,1);
 assert.equal((await onRequestPatch(ctx(a,'PATCH',{operation:'read',key:'exam:fake',read:true}))).status,404);
 data=await (await onRequestPatch(ctx(a,'PATCH',{operation:'read',key:newerKey,read:false}))).json();
 assert.equal(data.unread,2);
 data=await (await onRequestPatch(ctx(a,'PATCH',{...pref,enabled:false}))).json();
 assert.equal(data.preferences.enabled,false);
 assert.equal((await (await onRequestGet(ctx(b))).json()).preferences.enabled,false);
}finally{globalThis.fetch=oldFetch;sql.close();}

const ui=readFileSync(new URL('../public/xiaoxu-reminders.js',import.meta.url),'utf8');
const html=readFileSync(new URL('../public/index.html',import.meta.url),'utf8');
const sw=readFileSync(new URL('../public/sw.js',import.meta.url),'utf8');
assert.ok(html.includes('<script src="/xiaoxu-reminders.js"></script>'));
assert.ok(html.includes('XiaoxuReminders.panel()'));
assert.ok(html.includes('XiaoxuReminders.disconnect()'));
assert.ok(sw.includes('/xiaoxu-reminders.js'));
const calls=[];
let mock={delivery:'in_app_only',items:[{key:'exam:test',kind:'exam',
 title:'<script>alert(1)</script>',when:'2026-11-05（时刻未验证）',
 subtitle:'个人导入考试快照',read:false}],
 preferences:{enabled:false,examLeadDays:7,taskLeadDays:3},unread:1,
 source:{exams:'personal_snapshot'}};
const vm={fetch:async (url,opts={})=>{
 calls.push(opts.method||'GET');
 if(opts.body){
  const body=JSON.parse(opts.body);
  if(body.operation==='settings')mock={...mock,preferences:{...mock.preferences,enabled:body.enabled}};
  if(body.operation==='read')mock={...mock,unread:0,items:mock.items.map(x=>({...x,read:body.read}))};
 }
 return new Response(JSON.stringify(mock),{status:200,headers:{'content-type':'application/json'}});
},Response};
runInNewContext(ui,vm);
const client=vm.XiaoxuReminders;
assert.equal(calls.length,0,'no private API before consent');
assert.ok(client.panel().includes('未连接'));
await client.connect();
assert.ok(!client.panel().includes('<script>'),'escapes untrusted academic titles');
await client.settings(true);
assert.ok(client.panel().includes('已启用站内提醒'));
await client.mark('exam:test',true);
assert.ok(client.panel().includes('恢复未读'));
client.disconnect();
assert.equal(client.isConnected(),false);
console.log('校序 M5 站内提醒：有效真实日期、个人 Access 验签、D1 用户隔离、同源写入、订阅与已读跨设备持久化、UI 防注入/默认无请求通过');
