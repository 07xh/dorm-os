/* Explicit single-course read-only sync. Only verified server-side Chaoxing
 * sessions may write official homework status to Academic OS D1.
 * Notification-derived IDs are intentionally NOT guessed or title-matched.
 */
import {runTrustedConnector} from './academic-connector-runner.mjs';

const NUM=/^\d{1,24}$/;
const STATUS=new Set(['todo','submitted','graded','returned','unknown']);
const fail=code=>{const err=new Error(code);err.code=code;throw err;};

export function normalizeVerifiedChaoxingHomework(keys,result){
 if(!keys||![keys.courseId,keys.classId,keys.cpi].every(x=>typeof x==='string'&&NUM.test(x)))
  fail('invalid_query');
 if(!result||!result.course||!Array.isArray(result.items)||
    result.items.length>200||typeof result.verifiedEmpty!=='boolean')
  fail('invalid_response');
 const course=result.course;
 if(course.courseId!==keys.courseId||course.classId!==keys.classId||
    course.cpi!==keys.cpi||typeof course.title!=='string'||
    !course.title.trim()||course.title.length>80)fail('invalid_response');
 if(result.progress!==undefined&&result.progress!==null){
  const progress=result.progress;
  if(!progress||typeof progress!=='object'||Array.isArray(progress)||
     Object.keys(progress).sort().join(',')!=='completed,total'||
     !Number.isSafeInteger(progress.completed)||!Number.isSafeInteger(progress.total)||
     progress.completed<0||progress.total<0||progress.completed>progress.total||
     progress.total>200)fail('invalid_response');
  if(progress.total!==result.items.length)fail('work_list_incomplete');
 }
 if(!result.items.length&&!result.verifiedEmpty)fail('work_not_available');
 if(result.items.length&&result.verifiedEmpty)fail('invalid_response');
 const seen=new Set(),tasks=[];
 for(const item of result.items){
  if(!item||typeof item.workId!=='string'||!NUM.test(item.workId)||
     typeof item.title!=='string'||!item.title.trim()||item.title.trim()!==item.title||
     item.title.length>100||typeof item.status!=='string'||
     item.status.length>60||item.platformVerified!==true||
     !STATUS.has(item.statusCode))fail('invalid_response');
  // No title matching: the official (course, class, work) tuple is the only
  // stable identity. Never overwrite a notice/CLI task based on a similar name.
  const externalId='work:'+keys.courseId+':'+keys.classId+':'+item.workId;
  if(externalId.length>80||seen.has(externalId))fail('invalid_response');
  seen.add(externalId);
  tasks.push({externalId,title:item.title,course:course.title,
   status:item.statusCode,due:'',dueAt:''});
 }
 return {tasks,course,items:result.items,verifiedEmpty:result.verifiedEmpty,
  progress:result.progress??null};
}

export async function syncVerifiedChaoxingHomework(db,userId,source,proof,keys,{
 now,id
}={}){
 if(!db?.prepare||typeof db.batch!=='function'||
    typeof userId!=='string'||!userId||!source||
    typeof source.readHomeworkList!=='function'||!proof||
    proof.authorized!==true||proof.scope!=='self'||
    proof.provider!=='chaoxing'||proof.school!=='ncwu'||
    proof.principalBoundTo!==userId||proof.identityBound!==true||
    !proof.session)fail('authorization_failed');
 const live=await source.readHomeworkList(keys);
 const normalized=normalizeVerifiedChaoxingHomework(keys,live);
 const pages=[];
 for(let i=0;i<normalized.tasks.length;i+=20)pages.push(normalized.tasks.slice(i,i+20));
 if(!pages.length)pages.push([]);
 const trusted={
  provider:'chaoxing',
  verifySession:async()=>proof,
  readPage:async({cursor})=>{
   const n=cursor==null?0:Number(cursor.slice(1));
   if(!Number.isInteger(n)||n<0||n>=pages.length)fail('invalid_response');
   const next=n+1<pages.length?'p'+(n+1):null;
   return {tasks:pages[n],complete:next===null,nextCursor:next,
    verifiedEmpty:pages.length===1&&normalized.verifiedEmpty};
  }
 };
 const sync=await runTrustedConnector(db,userId,trusted,{
  coverage:'partial',maxPages:11,maxRecords:200,
  ...(now?{now}:{}),...(id?{id}:{})
 });
 return {course:normalized.course,items:normalized.items,
  verifiedEmpty:normalized.verifiedEmpty,progress:normalized.progress,sync};
}
