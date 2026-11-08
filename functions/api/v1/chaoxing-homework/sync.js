/* Same owner and same encrypted Chaoxing school session as GET /homework.
 * A verified course-scoped read writes official status, not local completion.
 */
import {authorize,json} from '../assignments.js';
import {availableSchoolAdapter,readConnectedChaoxing,checkSchoolMutation}
 from '../../../../lib/academic-school-login.mjs';
import {syncVerifiedChaoxingHomework}
 from '../../../../lib/academic-chaoxing-homework-sync.mjs';
const NUM=/^\d{1,24}$/;
export async function onRequestPost(context){
 const auth=await authorize(context);if(auth.response)return auth.response;
 if(!checkSchoolMutation(context.request))
  return json({error:'forbidden_origin_or_format'},403);
 let input;
 try{
  const raw=await context.request.text();
  if(raw.length>1000)return json({error:'invalid_query'},400);
  input=JSON.parse(raw);
 }catch{return json({error:'invalid_query'},400);}
 if(!input||typeof input!=='object'||Array.isArray(input)||
    Object.keys(input).sort().join(',')!=='classId,courseId,cpi'||
    ![input.courseId,input.classId,input.cpi].every(v=>typeof v==='string'&&NUM.test(v)))
  return json({error:'invalid_query'},400);
 const adapter=availableSchoolAdapter(context,'chaoxing');
 if(!adapter)return json({error:'school_login_unavailable'},503);
 try{
  const data=await readConnectedChaoxing({
   db:auth.db,userId:auth.userId,adapter,
   secret:context.env?.SCHOOL_SESSION_KEY,
   reader:async(source,proof)=>syncVerifiedChaoxingHomework(
    auth.db,auth.userId,source,proof,input)
  });
  return json({schema:'xiaoxu/chaoxing-work-browser-v1',
   view:'list',live:true,data});
 }catch(e){
  const code=e?.code;
  // Deliberately expose only code-owned read stages to the authenticated owner.
  const safeStages=new Set(['course_catalog','course_identity','course_entry',
    'list_legacy','list_official','list_integrity']);
  const stage=safeStages.has(e?.schoolReadStage)?e.schoolReadStage:null;
  const safeError=(error,status)=>json(
    stage?{error,readStage:stage}:{error},status);
  if(code==='session_expired'||code==='authorization_failed')
   return safeError('session_expired',409);
  if(code==='interactive_login_required')return safeError(code,409);
  if(code==='work_not_available'||code==='courses_unverified'||
     code==='work_list_incomplete'||code==='invalid_response')
   return safeError(code,422);
  if(code==='invalid_query')return safeError(code,400);
  if(code==='not_configured'||code==='database_unavailable'||
     code==='connector_not_configured')return safeError('database_unavailable',503);
  // Never expose source HTML, secrets, signed task links or upstream stack.
  return safeError('upstream_unavailable',502);
 }
}
