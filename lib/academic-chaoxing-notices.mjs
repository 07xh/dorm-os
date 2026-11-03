/* Chaoxing notice inbox -> source-linked Academic OS tasks.
 * Private trusted adapter only; never accept a captured HAR/cookie as verified login.
 * Retain minimal message metadata, never raw HTML, signed links or credentials.
 */
import {runTrustedConnector} from './academic-connector-runner.mjs';

const MESSAGE_ID=/^[A-Za-z0-9_$:-]{1,100}$/;
const CURSOR=/^[A-Za-z0-9_-]{1,100}$/;
const USER=/^[A-Za-z0-9_-]{1,100}$/;
const MAX_MESSAGES=250;
export class ChaoxingInboxError extends Error {
 constructor(code){super(code);this.name='ChaoxingInboxError';this.code=code;}
}
const fail=code=>{throw new ChaoxingInboxError(code);};
const text=(value,max=160)=>typeof value==='string'
 ? value.replace(/<[^>]*>/g,'').replace(/[\u0000-\u0008\u000B-\u001F]/g,' ').trim().slice(0,max) : '';
function localTime(input){
 const value=text(input,40);
 const m=/^(20\d{2}-\d{2}-\d{2})[ T]([01]\d|2[0-3]):([0-5]\d)(?::([0-5]\d))?$/.exec(value);
 if(!m)return null;
 const instant=m[1]+'T'+m[2]+':'+m[3]+':'+(m[4]||'00')+'+08:00';
 const date=new Date(instant);
 if(!Number.isFinite(date.getTime())||
    new Date(date.getTime()+8*3600000).toISOString().slice(0,19)!==
      m[1]+'T'+m[2]+':'+m[3]+':'+(m[4]||'00'))return null;
 return {day:m[1],instant,minute:m[1]+'T'+m[2]+':'+m[3]+':00+08:00',
  utc:date.toISOString()};
}
function fieldsFromContent(value){
 const result={};
 for(const line of String(value||'').split(/\r\n|\r|\n/)){
  const m=/^\s*([^：:\s]{2,20})[：:]\s*(.*?)\s*$/.exec(line);
  if(m&&!Object.hasOwn(result,m[1]))result[m[1]]=text(m[2],240);
 }
 return result;
}
export function parseChaoxingNotice(raw){
 if(!raw||typeof raw!=='object'||Array.isArray(raw))fail('invalid_response');
 const noticeId=raw.idCode||raw.uuid;
 if(typeof noticeId!=='string'||!MESSAGE_ID.test(noticeId))fail('invalid_response');
 const title=text(raw.title,140),content=raw.content;
 if(!title||typeof content!=='string'||content.length>12000)fail('invalid_response');
 const sent=localTime(raw.sendTime);
 if(!sent)fail('invalid_response');
 const fields=fieldsFromContent(content);
 const tag=typeof raw.tag==='string'?raw.tag:'';
 const courseId=/^courseId(\d{1,25})$/.exec(tag)?.[1]||'';
 const course=text(fields['课程名称'],80);
 const workName=text(fields['作业名称'],100);
 const examName=text(fields['试卷名称']||fields['考试名称']||title,100);
 let category='notice',taskType='',taskTitle='',due=null;
 if(/互评/.test(title)&&workName){
  category='peer_review';taskType='peer_review';taskTitle='互评：'+workName;
  due=localTime(fields['互评结束时间']);
 }else if(/考试/.test(title)||fields['考试时间']){
  category='exam';taskType='exam';taskTitle='考试：'+examName;
  const match=(fields['考试时间']||'').match(/20\d{2}-\d{2}-\d{2}[ T]\d{2}:\d{2}(?::\d{2})?/);
  due=match?localTime(match[0]):null;
 }else if(workName&&(/作业/.test(title)||fields['结束时间'])){
  category=/提醒/.test(title)?'assignment_reminder':'assignment';
  taskType='assignment';taskTitle=workName;
  due=localTime(fields['结束时间']);
 }else if(/签到/.test(title)){
  category='checkin';
 }else if(/测验|测试/.test(title)){
  category='quiz';
 }else if(/活动|报名/.test(title)){
  category='activity';
 }
 // Without a stable course identity, two unrelated assignments could collapse.
 // Keep the notice but do not fabricate a unified task.
 const identity=taskType&&taskTitle&&(courseId||course||taskType==='exam')
  ?[taskType,courseId||course,taskTitle,taskType==='exam'?(due?.day||'unknown'):''].join('|'):'';
 return {noticeId,title,category,course,courseId,sentAt:sent.utc,
  dueAt:due?.utc||null,dueDay:due?.day||null,
  read:raw.isread===1||raw.isread===true,taskType,taskTitle,identity,
  dueLocal:due?.minute||'',dueDate:due?.day||''};
}
async function stableId(value){
 const bytes=new TextEncoder().encode(value);
 const digest=new Uint8Array(await crypto.subtle.digest('SHA-256',bytes));
 return 'cxn:'+Array.from(digest.slice(0,20),v=>v.toString(16).padStart(2,'0')).join('');
}
export async function normalizeChaoxingInbox(payload){
 if(!payload||payload.status!==true||!payload.notices||
    !Array.isArray(payload.notices.list)||payload.notices.list.length>100)
   fail('invalid_response');
 const notices=[],keys=new Set(),byTask=new Map();
 for(const raw of payload.notices.list){
  const notice=parseChaoxingNotice(raw);
  if(keys.has(notice.noticeId))continue;
  keys.add(notice.noticeId);
  let externalId=null;
  if(notice.identity){
   externalId=await stableId(notice.identity);
   const previous=byTask.get(externalId);
   const candidate={externalId,title:notice.taskTitle,course:notice.course,
    status:'unknown',due:notice.dueDate,dueAt:notice.dueLocal};
   // Later messages can revise an explicit deadline, but reminders without a
   // deadline must not erase the known deadline of the published assignment.
   if(!previous||((notice.dueLocal&&!previous.task.dueAt)||
      (notice.dueLocal&&notice.sentAt>=previous.sentAt)))
    byTask.set(externalId,{task:candidate,sentAt:notice.sentAt});
  }
  notices.push({...notice,externalId});
 }
 return {notices,tasks:[...byTask.values()].map(x=>x.task)};
}
export async function collectVerifiedChaoxingInbox(adapter,userId,{maxPages=30}={}){
 if(!USER.test(userId||'')||adapter?.provider!=='chaoxing'||
    typeof adapter.verifySession!=='function'||typeof adapter.readNoticePage!=='function'||
    !Number.isInteger(maxPages)||maxPages<1||maxPages>50)fail('connector_not_configured');
 let proof;
 try{proof=await adapter.verifySession();}catch{fail('authorization_failed');}
 if(!proof||proof.authorized!==true||proof.scope!=='self'||
    proof.school!=='ncwu'||proof.provider!=='chaoxing'||
    proof.identityBound!==true||proof.principalBoundTo!==userId||!proof.session)
   fail('authorization_failed');
 const notices=new Map(),tasks=new Map(),seenCursors=new Set();
 let cursor=null,complete=false,pages=0;
 for(let i=0;i<maxPages;i++){
  let page;
  try{page=await adapter.readNoticePage({cursor,session:proof.session});}
  catch{fail('upstream_unavailable');}
  if(!page||page.complete!==(page.nextCursor==null)||
     (page.nextCursor!=null&&!CURSOR.test(page.nextCursor)))
    fail('invalid_response');
  const parsed=await normalizeChaoxingInbox(page.payload);
  if(!parsed.notices.length&&!page.complete)fail('invalid_response');
  for(const row of parsed.notices){
   notices.set(row.noticeId,row);
   if(notices.size>MAX_MESSAGES)fail('invalid_response');
  }
  for(const task of parsed.tasks){
   const old=tasks.get(task.externalId);
   // Pages arrive newest-first. Preserve a known deadline from the newest
   // message, or backfill it from an older published notice.
   if(!old||(!old.dueAt&&task.dueAt))tasks.set(task.externalId,task);
  }
  pages++;
  if(page.complete){complete=true;break;}
  if(seenCursors.has(page.nextCursor))fail('invalid_response');
  seenCursors.add(page.nextCursor);cursor=page.nextCursor;
 }
 if(!complete||tasks.size>MAX_MESSAGES)fail('invalid_response');
 return {proof,pages,notices:[...notices.values()],tasks:[...tasks.values()]};
}
export async function runVerifiedChaoxingInboxSync(db,userId,adapter,options={}){
 if(!db?.prepare||typeof db.batch!=='function')fail('connector_not_configured');
 // Fail before changing tasks if production has not received the inbox migration.
 try{await db.prepare('SELECT notice_id FROM academic_chaoxing_notices WHERE user_id=? LIMIT 0')
   .bind(userId).all();}
 catch{fail('database_unavailable');}
 // All upstream pages are verified before any D1 changes. Neither browser
 // JSON nor a previously captured HAR can trigger this trusted path.
 const collected=await collectVerifiedChaoxingInbox(adapter,userId,options);
 const tasks=collected.tasks;
 const pageTasks=[];
 for(let i=0;i<tasks.length;i+=20)pageTasks.push(tasks.slice(i,i+20));
 if(!pageTasks.length)pageTasks.push([]);
 const trusted={
  provider:'chaoxing',
  verifySession:async()=>collected.proof,
  readPage:async({cursor})=>{
   const index=cursor==null?0:Number(cursor.slice(1));
   if(!Number.isInteger(index)||index<0||index>=pageTasks.length)fail('invalid_response');
   const next=index+1<pageTasks.length?'p'+(index+1):null;
   return {tasks:pageTasks[index],complete:next===null,nextCursor:next,
    verifiedEmpty:pageTasks.length===1&&tasks.length===0};
  }
 };
 const result=await runTrustedConnector(db,userId,trusted,options);
 const at=(options.now||(()=>new Date().toISOString()))();
 const statements=collected.notices.map(n=>db.prepare(
  'INSERT INTO academic_chaoxing_notices '+
  '(user_id,notice_id,category,title,course_title,source_course_id,task_external_id,'+
  'sent_at_utc,due_at_utc,platform_read,observed_at) VALUES (?,?,?,?,?,?,?,?,?,?,?) '+
  'ON CONFLICT(user_id,notice_id) DO UPDATE SET category=excluded.category,'+
  'title=excluded.title,course_title=excluded.course_title,'+
  'task_external_id=excluded.task_external_id,due_at_utc=excluded.due_at_utc,'+
  'platform_read=excluded.platform_read,observed_at=excluded.observed_at'
 ).bind(userId,n.noticeId,n.category,n.title,n.course,n.courseId,n.externalId,
   n.sentAt,n.dueAt,n.read?1:0,at));
 if(statements.length){
  try{await db.batch(statements);}
  catch{throw new ChaoxingInboxError('database_unavailable');}
 }
 return {...result,noticeCount:collected.notices.length,
  taskCount:tasks.length,noticePages:collected.pages,lastSuccessAt:at};
}
