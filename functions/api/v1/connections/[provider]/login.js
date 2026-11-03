import {authorize,json} from '../../assignments.js';
import {availableSchoolAdapter,connectSchoolAccount,checkSchoolMutation,validSchoolLoginInput,SchoolLoginError} from '../../../../../lib/academic-school-login.mjs';
const responseCode=e=>e.code==='invalid_credentials'?400:e.code==='authorization_failed'?422:
 e.code==='interactive_login_required'?409:e.code==='sync_failed'?502:503;
export async function onRequestPost(context){
 const auth=await authorize(context);if(auth.response)return auth.response;
 if(!checkSchoolMutation(context.request))return json({error:'forbidden_origin_or_format'},403);
 const provider=context.params?.provider,adapter=availableSchoolAdapter(context,provider);
 // Never read or accept a password when the school adapter is unavailable.
 if(!adapter)return json({error:'school_login_unavailable'},503);
 const raw=await context.request.text();
 if(raw.length>2400)return json({error:'payload_too_large'},413);
 let payload;try{payload=JSON.parse(raw);}catch{return json({error:'invalid_json'},400);}
 if(!validSchoolLoginInput(payload))return json({error:'invalid_credentials'},400);
 try{
  const result=await connectSchoolAccount({db:auth.db,userId:auth.userId,provider,adapter,
   username:payload.username,password:payload.password,secret:context.env?.SCHOOL_SESSION_KEY});
  return json(result,200);
 }catch(e){
  const code=e instanceof SchoolLoginError?e.code:'sync_failed';
  return json({error:code},responseCode({code}));
 }
}
