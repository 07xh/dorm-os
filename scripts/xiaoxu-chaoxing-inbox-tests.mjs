import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
import {parseChaoxingNotice,normalizeChaoxingInbox,collectVerifiedChaoxingInbox,
 ChaoxingInboxError} from '../lib/academic-chaoxing-notices.mjs';
globalThis.crypto??=webcrypto;
const base={sendTime:'2026-11-03 19:00:00',isread:0,tag:'courseId123456'};
const notice=(id,title,content,extra={})=>({
 ...base,idCode:id.repeat(32),uuid:'$fake$'+id.repeat(36),title,content,...extra
});
const homework=notice('A','作业:Python实验一',
 '课程名称：Python程序设计\r作业名称：实验一\r开始时间：2026-11-01 09:00\r结束时间：2026-11-10 23:59');
const reminder=notice('B','作业结束提醒',
 '课程名称：Python程序设计\r作业名称：实验一\r你的作业还有24小时就要截止了',
 {sendTime:'2026-11-09 10:00:00'});
const peer=notice('C','互评开始通知',
 '课程名称：Python程序设计\r作业名称：实验一\r互评开始时间：2026-11-11 00:00\r互评结束时间：2026-11-14 21:00');
const exam=notice('D','大学英语期末考试通知',
 '试卷名称：大学英语期末\r考试时间：2026-11-16 10:35 至 2026-11-16 11:35',
 {tag:null});
const announcement=notice('E','课程教材通知','本周课程需要自带教材',{tag:null});
const checkin=notice('F','课堂签到提醒','请按课堂要求签到');
const quiz=notice('G','测验安排','新测验已发布');
const activity=notice('H','活动报名通知','活动时间待定');
const parsed=parseChaoxingNotice(homework);
assert.equal(parsed.noticeId,'A'.repeat(32));
assert.equal(parsed.category,'assignment');
assert.equal(parsed.courseId,'123456');
assert.equal(parsed.dueAt,'2026-11-10T15:59:00.000Z');
assert.equal(parseChaoxingNotice(reminder).category,'assignment_reminder');
assert.equal(parseChaoxingNotice(peer).category,'peer_review');
assert.equal(parseChaoxingNotice(peer).dueDay,'2026-11-14');
assert.equal(parseChaoxingNotice(exam).category,'exam');
assert.equal(parseChaoxingNotice(exam).dueDay,'2026-11-16');
assert.equal(parseChaoxingNotice(announcement).category,'notice');
assert.equal(parseChaoxingNotice(checkin).category,'checkin');
assert.equal(parseChaoxingNotice(quiz).category,'quiz');
assert.equal(parseChaoxingNotice(activity).category,'activity');
const payload=(list)=>({status:true,notices:{lastPage:1,lastGetId:'cursor',list}});
const normalized=await normalizeChaoxingInbox(payload([reminder,homework,peer,exam,announcement,checkin,quiz,activity]));
assert.equal(normalized.notices.length,8);
assert.equal(normalized.tasks.length,3,'homework and reminder must be one task; other notifications are not assignments');
assert.equal(normalized.tasks.find(t=>t.title==='实验一').due,'2026-11-10');
assert.ok(normalized.tasks.every(t=>t.status==='unknown'),
 'inbox does not prove submitted or unsubmitted');
assert.ok(normalized.notices.every(n=>!('content' in n)&&!('uuid' in n)),
 'raw message body and sender metadata must never enter DB rows');
const proof={authorized:true,scope:'self',school:'ncwu',provider:'chaoxing',
 identityBound:true,principalBoundTo:'owner1',session:{opaque:'private'}};
const adapter={provider:'chaoxing',verifySession:async()=>proof,
 readNoticePage:async({cursor})=>cursor==null?
  {payload:payload([reminder,announcement]),complete:false,nextCursor:'p1'}:
  {payload:payload([homework,peer,exam]),complete:true,nextCursor:null}};
const collected=await collectVerifiedChaoxingInbox(adapter,'owner1');
assert.equal(collected.pages,2);
assert.equal(collected.notices.length,5);
assert.equal(collected.tasks.length,3);
assert.equal(collected.tasks.find(t=>t.title==='实验一').due,'2026-11-10');
await assert.rejects(()=>collectVerifiedChaoxingInbox(adapter,'somebodyelse'),
 e=>e instanceof ChaoxingInboxError&&e.code==='authorization_failed');
await assert.rejects(()=>normalizeChaoxingInbox({status:true}),
 e=>e instanceof ChaoxingInboxError&&e.code==='invalid_response');
await assert.rejects(()=>collectVerifiedChaoxingInbox({...adapter,
 readNoticePage:async()=>({payload:payload([]),complete:false,nextCursor:'p1'})},'owner1'),
 e=>e instanceof ChaoxingInboxError&&e.code==='invalid_response');
console.log('Chaoxing inbox: classification, deadlines, pagination, authorized identity and safe dedup passed');
