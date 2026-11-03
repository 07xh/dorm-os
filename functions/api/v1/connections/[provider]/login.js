import {authorize,json} from '../../assignments.js';
import {availableSchoolAdapter,connectSchoolAccount,checkSchoolMutation,validSchoolLoginInput,SchoolLoginError} from '../../../../../lib/academic-school-login.mjs';
const responseCode=e=>e.code==='invalid_credentials'?400:e.code==='authorization_failed'?422:
 e.code==='interactive_login_required'?409:e.code==='sync_failed'?502:503;
export async function onRequestPost(context){
 const auth=await authorize(context);if(auth.response)return auth.response;
 if(!checkSchoolMutation(context.request))return json({error:'forbidden_origin_or_format'},403);
 const provider=context.params?.provider;
 // A school CAS round trip is only attempted through the trusted server
 // adapter. The adapter rejects interactive/unsupported challenges and only
 // saves sessions after a verified read of this owner's school records.
 const adapter=availableSchoolAdapter(context,provider);
 // Never read or accept a password when the school adapter is unavailable.
 if(!adapter)return json({error:'school_login_unavailable'},503);
 // Reuse the existing owner-login rate-limit table. This is an atomic,
 // owner-and-provider-scoped 15-minute bucket, not an additional user system.
 try{
  const now=Date.now(),bucket='school-login:'+auth.userId+':'+provider;
  const row=await auth.db.prepare(
   'INSERT INTO academic_auth_login_attempts(bucket,hits,window_end) VALUES(?,1,?) '+
   'ON CONFLICT(bucket) DO UPDATE SET hits=CASE WHEN window_end<=? THEN 1 ELSE hits+1 END, '+
   'window_end=CASE WHEN window_end<=? THEN excluded.window_end ELSE window_end END '+
   'RETURNING hits'
  ).bind(bucket,now+900000,now,now).first();
  if(!row||!Number.isInteger(row.hits))return json({error:'rate_limit_unavailable'},503);
  if(row.hits>6)return json({error:'too_many_attempts'},429);
 }catch{return json({error:'rate_limit_unavailable'},503);}
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
