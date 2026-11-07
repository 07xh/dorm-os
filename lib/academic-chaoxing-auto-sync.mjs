/* Owner-scoped, hourly Chaoxing batch runner.
 * The Cron identity is checked separately. This code runs only on Pages,
 * where the existing encrypted school session and D1 are available.
 * At most one owner and three courses per invocation (bounded costs).
 */
import {readConnectedChaoxing} from './academic-school-login.mjs';
import {syncVerifiedChaoxingHomework} from './academic-chaoxing-homework-sync.mjs';
import {nextAttempt} from './academic-scheduler.mjs';

const LIMIT_COURSES=3;
const fail=code=>{const error=new Error(code);error.code=code;throw error;};
export async function runChaoxingAutoCycle(db,adapter,secret,{
 now=()=>new Date().toISOString(),
 readOwner=readConnectedChaoxing,
 syncCourse=syncVerifiedChaoxingHomework
}={}){
 if(!db?.prepare||adapter?.provider!=='chaoxing'||
    typeof adapter.forSession!=='function'||typeof secret!=='string'||!secret||
    typeof readOwner!=='function'||typeof syncCourse!=='function')
  fail('autosync_not_configured');
 const current=now();
 if(!Number.isFinite(Date.parse(current)))fail('autosync_clock_invalid');
 const due=await db.prepare(
  "SELECT user_id,interval_minutes,lease_until,failure_count,next_at "+
  "FROM academic_sync_jobs WHERE provider='chaoxing' AND enabled=1 "+
  "AND next_at<=? AND (lease_until IS NULL OR lease_until<=?) "+
  "ORDER BY next_at ASC LIMIT 1"
 ).bind(current,current).first();
 if(!due)return {schema:'xiaoxu/chaoxing-hourly-v1',status:'idle',coursesChecked:0};
 const lease=new Date(Date.parse(current)+10*60000).toISOString();
 const claim=await db.prepare(
  "UPDATE academic_sync_jobs SET lease_until=? WHERE user_id=? "+
  "AND provider='chaoxing' AND enabled=1 AND next_at<=? "+
  "AND (lease_until IS NULL OR lease_until<=?)"
 ).bind(lease,due.user_id,current,current).run();
 if((claim?.meta?.changes??claim?.changes??0)!==1)
  return {schema:'xiaoxu/chaoxing-hourly-v1',status:'idle',coursesChecked:0};
 let status='failed',count=0,succeeded=0,updated=0,failures=0,checkedAt=now();
 try{
  const result=await readOwner({db,userId:due.user_id,adapter,secret,
   reader:async (reader,proof)=>{
    const courses=await reader.readCourses();
    if(!Array.isArray(courses)||!courses.length||courses.length>120)
     fail('courses_unverified');
    const state=await db.prepare(
     'SELECT next_course FROM academic_chaoxing_sync_cursor WHERE user_id=?'
    ).bind(due.user_id).first();
    const start=(state?.next_course??0)%courses.length;
    const max=Math.min(LIMIT_COURSES,courses.length);
    let ok=0,changes=0;
    for(let i=0;i<max;i++){
     const course=courses[(start+i)%courses.length];
     count++;
     try{
      const data=await syncCourse(db,due.user_id,reader,proof,{
       courseId:course.courseId,classId:course.classId,cpi:course.cpi
      });
      if(data?.sync?.coverage!=='partial')fail('invalid_response');
      ok++;changes+=(data.sync.inserted||0)+(data.sync.updated||0);
     }catch{
      // Failures must not turn old verified statuses into guesses.
      // Continue to a different course, but report a partial or failed run.
     }
    }
    // readConnectedChaoxing commits rotated cookies after this trusted read
    // succeeds, using the original vault row as a concurrency guard.
    await db.prepare(
     'INSERT INTO academic_chaoxing_sync_cursor (user_id,next_course,updated_at,last_success_at) '+
     'VALUES (?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET '+
     'next_course=excluded.next_course,updated_at=excluded.updated_at,'+
     'last_success_at=COALESCE(excluded.last_success_at,academic_chaoxing_sync_cursor.last_success_at)'
    ).bind(due.user_id,(start+max)%courses.length,now(),ok?now():null).run();
    return {ok,changes,total:max};
   }});
  succeeded=result.ok;updated=result.changes;
  status=succeeded===count?'success':succeeded>0?'partial':'failed';
 }catch(error){
  status=['session_expired','authorization_failed'].includes(error?.code)?'expired':'failed';
 }
 checkedAt=now();
 failures=status==='success'?0:Math.min(20,(due.failure_count||0)+1);
 const enabled=status==='expired'?0:1;
 const next=enabled?nextAttempt(checkedAt,due.interval_minutes,failures,
  status==='success'?'success':'failed'):null;
 // A user disabling auto-sync while this invocation was running wins: the
 // completion update cannot silently re-enable their job.
 await db.prepare(
  'UPDATE academic_sync_jobs SET lease_until=NULL,last_run_at=?,last_result=?,'+
  'failure_count=?,next_at=? WHERE user_id=? AND provider=? AND enabled=1 AND lease_until=?'
 ).bind(checkedAt,status,failures,next,due.user_id,'chaoxing',lease).run();
 if(!enabled)await db.prepare(
  'UPDATE academic_sync_jobs SET enabled=0,next_at=NULL WHERE user_id=? '+
  "AND provider='chaoxing' AND last_result='expired' AND lease_until IS NULL"
 ).bind(due.user_id).run();
 return {schema:'xiaoxu/chaoxing-hourly-v1',status,coursesChecked:count,
  coursesSucceeded:succeeded,changes:updated};
}
