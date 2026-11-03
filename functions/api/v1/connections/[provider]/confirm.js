/* Confirm only the owner's pending NCWU timetable, after a fresh school read.
 * Never accept course records, cookies or principal assertions from the browser.
 */
import {authorize,json} from '../../assignments.js';
import {availableSchoolAdapter,checkSchoolMutation,confirmNcwuAccountReview,SchoolLoginError}
 from '../../../../../lib/academic-school-login.mjs';

export async function onRequestPost(context){
 const auth=await authorize(context);
 if(auth.response)return auth.response;
 if(!checkSchoolMutation(context.request))
  return json({error:'forbidden_origin_or_format'},403);
 if(context.params?.provider!=='ncwu')
  return json({error:'unknown_provider'},404);
 const adapter=availableSchoolAdapter(context,'ncwu');
 if(!adapter)return json({error:'school_login_unavailable'},503);
 const raw=await context.request.text();
 if(raw.length>250)return json({error:'payload_too_large'},413);
 let payload;
 try{payload=JSON.parse(raw);}catch{return json({error:'invalid_json'},400);}
 if(!payload||payload.reviewed!==true||
    typeof payload.reviewHash!=='string'||
    !/^[A-Za-z0-9_-]{43}$/.test(payload.reviewHash))
  return json({error:'invalid_review'},400);
 try{
  const result=await confirmNcwuAccountReview({db:auth.db,userId:auth.userId,
   adapter,secret:context.env?.SCHOOL_SESSION_KEY,reviewHash:payload.reviewHash});
  return json(result,200);
 }catch(error){
  const code=error instanceof SchoolLoginError?error.code:'sync_failed';
  const status=code==='schedule_changed'||code==='review_pending'||code==='session_expired'?409:
   code==='authorization_failed'?422:code==='database_unavailable'?503:502;
  return json({error:code},status);
 }
}
