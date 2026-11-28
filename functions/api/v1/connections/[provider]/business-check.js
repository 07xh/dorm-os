import {authorize,json} from '../../assignments.js';
import {availableSchoolAdapter,checkSchoolMutation,ncwuBusinessAttemptStatus,
 checkNcwuBusiness,SchoolLoginError} from '../../../../../lib/academic-school-login.mjs';

export async function onRequestPost(context){
 const auth=await authorize(context);if(auth.response)return auth.response;
 if(!checkSchoolMutation(context.request))return json({error:'forbidden_origin_or_format'},403);
 if(context.params?.provider!=='ncwu')return json({error:'unknown_provider'},404);
 const adapter=availableSchoolAdapter(context,'ncwu');
 if(!adapter)return json({error:'school_login_unavailable'},503);
 const raw=await context.request.text();
 if(raw.length>128)return json({error:'payload_too_large'},413);
 let input;try{input=JSON.parse(raw);}catch{return json({error:'invalid_json'},400);}
 if(!input||typeof input!=='object'||Array.isArray(input)||
    Object.keys(input).length!==1||!['grades','exams'].includes(input.kind))
  return json({error:'invalid_kind'},400);
 const rate=await ncwuBusinessAttemptStatus(auth.db,auth.userId);
 if(rate!=='ok')return json({error:rate},rate==='too_many_attempts'?429:503);
 try{
  return json(await checkNcwuBusiness({db:auth.db,userId:auth.userId,
   adapter,secret:context.env?.SCHOOL_SESSION_KEY,kind:input.kind}));
 }catch(e){
  const code=e instanceof SchoolLoginError?e.code:'sync_failed';
  return json({error:code},['session_expired','review_pending','authorization_failed'].includes(code)?409:
   ['sync_failed','upstream_unavailable'].includes(code)?502:503);
 }
}
