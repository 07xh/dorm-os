/* Owner-only Chaoxing courses/homework/details; no raw upstream URLs.
 * Uses the existing encrypted school session, not a second login mechanism.
 */
import {authorize,json} from './assignments.js';
import {availableSchoolAdapter,readConnectedChaoxing} from '../../../lib/academic-school-login.mjs';
const NUM=/^\d{1,24}$/;
const MODES=new Set(['courses','list','detail']);
export async function onRequestGet(context){
 const auth=await authorize(context);
 if(auth.response)return auth.response;
 const req=context.request;
 if(req.headers.get('sec-fetch-site')==='cross-site')
  return json({error:'forbidden_origin'},403);
 const q=new URL(req.url).searchParams,mode=q.get('view')||'courses';
 if(!MODES.has(mode))return json({error:'invalid_query'},400);
 const params={courseId:q.get('courseId'),classId:q.get('classId'),
   cpi:q.get('cpi'),workId:q.get('workId')};
 if(mode!=='courses'&&![params.courseId,params.classId,params.cpi].every(x=>
   typeof x==='string'&&NUM.test(x)))return json({error:'invalid_query'},400);
 if(mode==='detail'&&!(typeof params.workId==='string'&&NUM.test(params.workId)))
  return json({error:'invalid_query'},400);
 const adapter=availableSchoolAdapter(context,'chaoxing');
 if(!adapter)return json({error:'school_login_unavailable'},503);
 try{
  const data=await readConnectedChaoxing({db:auth.db,userId:auth.userId,adapter,
    secret:context.env?.SCHOOL_SESSION_KEY,reader:async source=>
      mode==='courses'?{courses:await source.readCourses()}:
      mode==='list'?await source.readHomeworkList(params):
      await source.readHomeworkDetail(params)});
  // Signed links, cookies and source HTML cannot be selected by query or emitted.
  return json({schema:'xiaoxu/chaoxing-work-browser-v1',view:mode,live:true,data});
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
  if(code==='not_found')return safeError('not_found',404);
  if(code==='work_not_available'||code==='courses_unverified'||
     code==='work_list_incomplete'||code==='invalid_response')
   return safeError(code,422);
  if(code==='not_configured'||code==='database_unavailable')
   return safeError(code,503);
  return safeError('upstream_unavailable',502);
 }
}
