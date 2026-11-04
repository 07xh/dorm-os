import {authorize,json} from '../../assignments.js';
import {availableSchoolAdapter,connectSchoolAccount,prepareNcwuAccountReview,checkSchoolMutation,validSchoolLoginInput,schoolConnectAttemptStatus,SchoolLoginError} from '../../../../../lib/academic-school-login.mjs';
const responseCode=e=>e.code==='invalid_credentials'?400:e.code==='authorization_failed'?422:
 e.code==='interactive_login_required'?409:['sync_failed','upstream_unavailable'].includes(e.code)?502:503;
export async function onRequestPost(context){
 const auth=await authorize(context);if(auth.response)return auth.response;
 if(!checkSchoolMutation(context.request))return json({error:'forbidden_origin_or_format'},403);
 const provider=context.params?.provider;
 // Only a registered server-side official adapter may accept credentials.
 // A school CAS round trip is only attempted through the trusted server
 // adapter. The adapter rejects interactive/unsupported challenges and only
 // saves sessions after a verified read of this owner's school records.
 const adapter=availableSchoolAdapter(context,provider);
 // Never read or accept a password when the school adapter is unavailable.
 if(!adapter)return json({error:'school_login_unavailable'},503);
 // Use one owner/provider bucket for password login and imported sessions.
 const limit=await schoolConnectAttemptStatus(auth.db,auth.userId,provider);
 if(limit!=='ok')return json({error:limit},limit==='too_many_attempts'?429:503);
 const raw=await context.request.text();
 if(raw.length>2400)return json({error:'payload_too_large'},413);
 let payload;try{payload=JSON.parse(raw);}catch{return json({error:'invalid_json'},400);}
 if(!validSchoolLoginInput(payload))return json({error:'invalid_credentials'},400);
 try{
  if(provider==='ncwu'){
   // Do not run an unbound preflight before a second fresh login GET.
   // The native adapter itself inspects the current school form WITH the
   // cookie jar used for its only credential POST; unsupported challenges
   // fail closed before any password leaves the server. The encrypted vault,
   // school adapter and per-owner rate limit were checked above.
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
  // Emit only fixed, non-sensitive failure phases for the owner's own
  // second-class attempt. Never return CAS URLs, form values or credentials.
  const stages=new Set(['cas_return','personal_data','identity_evidence']);
  const reasons=new Set(['unexpected_response','login_page_remained',
   'service_callback_incomplete','unexpected_school_landing']);
  const secondFailure=provider==='second_class'&&
   code==='authorization_failed'&&stages.has(e?.failureStage);
  return json({error:code,...(secondFailure?{failureStage:e.failureStage}:{}),
   ...(secondFailure&&e.failureStage==='cas_return'&&reasons.has(e?.casReturnReason)?
    {casReturnReason:e.casReturnReason}:{})},responseCode({code}));
 }
}
