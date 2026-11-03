/* A deliberate, owner-authenticated alternative to password login:
 * import a domain-scoped Chaoxing browser export, then PROVE the self profile
 * and sync actual inbox data before saving it in the existing AES-GCM vault.
 * No cross-origin browser cookie access, new identity system or secret echo.
 */
import {authorize,json} from '../../assignments.js';
import {availableSchoolAdapter,connectImportedChaoxingSession,
 schoolConnectAttemptStatus,checkSchoolMutation,SchoolLoginError}
 from '../../../../../lib/academic-school-login.mjs';

export async function onRequestPost(context){
 const auth=await authorize(context);if(auth.response)return auth.response;
 if(!checkSchoolMutation(context.request))
  return json({error:'forbidden_origin_or_format'},403);
 const adapter=availableSchoolAdapter(context,'chaoxing');
 if(!adapter||typeof adapter.importSession!=='function')
  return json({error:'school_login_unavailable'},503);
 const limit=await schoolConnectAttemptStatus(auth.db,auth.userId,'chaoxing');
 if(limit!=='ok')return json({error:limit},limit==='too_many_attempts'?429:503);
 const raw=await context.request.text();
 if(raw.length>15500)return json({error:'payload_too_large'},413);
 let payload;
 try{payload=JSON.parse(raw);}catch{return json({error:'invalid_json'},400);}
 if(!payload||!Array.isArray(payload.cookies)||payload.cookies.length>40||
    payload.cookies.length===0)return json({error:'invalid_cookie_format'},400);
 try{
  const result=await connectImportedChaoxingSession({db:auth.db,
   userId:auth.userId,adapter,cookies:payload.cookies,
   secret:context.env?.SCHOOL_SESSION_KEY});
  // Never return a cookie, session id, principal or other secret.
  return json(result,200);
 }catch(error){
  const code=error instanceof SchoolLoginError?error.code:'sync_failed';
  const status=code==='invalid_cookie_format'?400:
   code==='authorization_failed'?422:
   ['sync_failed','upstream_unavailable'].includes(code)?502:503;
  return json({error:code},status);
 }
}
