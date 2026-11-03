/* Private read-only view of the verified Chaoxing inbox metadata.
 * Uses the same owner authorization and D1 database as existing tasks.
 */
import {authorize,json} from './assignments.js';
const VALID_CATEGORY=new Set(['assignment','assignment_reminder','peer_review',
 'exam','checkin','quiz','activity','notice']);
export async function onRequestGet(context){
 const auth=await authorize(context);
 if(auth.response)return auth.response;
 const q=new URL(context.request.url).searchParams;
 const requested=q.get('category'),limit=Number(q.get('limit')||50);
 if((requested&&!VALID_CATEGORY.has(requested))||
    !Number.isInteger(limit)||limit<1||limit>100)
   return json({error:'invalid_filters'},400);
 const params=[auth.userId];
 let where='WHERE user_id=?';
 if(requested){where+=' AND category=?';params.push(requested);}
 try{
  const rows=await auth.db.prepare(
   'SELECT notice_id,category,title,course_title,source_course_id,task_external_id,'+
   'sent_at_utc,due_at_utc,platform_read,observed_at '+
   'FROM academic_chaoxing_notices '+where+
   ' ORDER BY sent_at_utc DESC,notice_id DESC LIMIT ?')
   .bind(...params,limit).all();
  return json({schema:'xiaoxu/chaoxing-inbox-v1',source:'chaoxing',
   messages:(rows.results||[]).map(x=>({
    id:x.notice_id,category:x.category,title:x.title,course:x.course_title,
    courseId:x.source_course_id,taskExternalId:x.task_external_id,
    sentAt:x.sent_at_utc,dueAt:x.due_at_utc,platformRead:x.platform_read===1,
    observedAt:x.observed_at})),live:false});
 }catch{return json({error:'database_not_ready'},503);}
}
