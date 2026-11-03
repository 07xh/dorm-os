import {authorize,json} from '../../assignments.js';
import {availableSchoolAdapter,connectSchoolAccount,prepareNcwuAccountReview,checkSchoolMutation,validSchoolLoginInput,schoolConnectAttemptStatus,SchoolLoginError} from '../../../../../lib/academic-school-login.mjs';
const responseCode=e=>e.code==='invalid_credentials'?400:e.code==='authorization_failed'?422:
 e.code==='interactive_login_required'?409:['sync_failed','upstream_unavailable'].includes(e.code)?502:503;
export async function onRequestPost(context){
 const auth=await authorize(context);if(auth.response)return auth.response;
 if(!checkSchoolMutation(context.request))return json({error:'forbidden_origin_or_format'},403);
 const provider=context.params?.provider;
 // Retired second-class password submission: reject BEFORE reading any body.
 if(provider==='second_class')return json({error:'official_sso_only'},410);
 // A school CAS round trip is only attempted through the trusted server
 // adapter. The adapter rejects interactive/unsupported challenges and only
 // saves sessions after a verified read of this owner's school records.
 const adapter=availableSchoolAdapter(context,provider);
 // Never read or accept a password when the school adapter is unavailable.
 if(!adapter)return json({error:'school_login_unavailable'},503);
 // Password and imported-session paths share one owner/provider limiter.
 const limit=await schoolConnectAttemptStatus(auth.db,auth.userId,provider);
 if(limit!=='ok')return json({error:limit},limit==='too_many_attempts'?429:503);
 const raw=await context.request.text();
 if(raw.length>2400)return json({error:'payload_too_large'},413);
 let payload;try{payload=JSON.parse(raw);}catch{return json({error:'invalid_json'},400);}
 if(!validSchoolLoginInput(payload))return json({error:'invalid_credentials'},400);
 try{
  if(provider==='ncwu'){
   // Confirm a live ordinary official form before accepting credentials.
   const form=await adapter.preflight();
   if(form?.loginSupported!==true)return json({error:'interactive_login_required'},409);
   const review=await prepareNcwuAccountReview({db:auth.db,userId:auth.userId,adapter,
    username:payload.username,password:payload.password,
    academicUsername:payload.academicUsername,academicPassword:payload.academicPassword,
    secret:context.env?.SCHOOL_SESSION_KEY});
   return json(review,200);
  }
  const result=await connectSchoolAccount({db:auth.db,userId:auth.userId,provider,adapter,
   username:payload.username,password:payload.password,
   secret:context.env?.SCHOOL_SESSION_KEY});
  return json(result,200);
 }catch(e){
  const code=e instanceof SchoolLoginError?e.code:'sync_failed';
  return json({error:code},responseCode({code}));
 }
}
