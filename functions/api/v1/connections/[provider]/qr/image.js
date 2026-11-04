import {authorize,json} from '../../../assignments.js';
import {availableSchoolAdapter,checkSchoolMutation,
 unsealChaoxingQrChallenge,SchoolLoginError}
 from '../../../../../../lib/academic-school-login.mjs';

// A same-origin binary QR response preserves the server-owned Chaoxing cookie
// jar. The short-lived encrypted challenge stays in the POST body, never URL,
// browser history, referrer, persistent storage or third-party image request.
export async function onRequestPost(context){
 const auth=await authorize(context);if(auth.response)return auth.response;
 if(!checkSchoolMutation(context.request))
  return json({error:'forbidden_origin_or_format'},403);
 const adapter=availableSchoolAdapter(context,'chaoxing');
 if(typeof adapter?.readQrImage!=='function')
  return json({error:'school_login_unavailable'},503);
 try{
  const raw=await context.request.text();
  if(raw.length>25000)return json({error:'payload_too_large'},413);
  let input;try{input=JSON.parse(raw);}catch{return json({error:'invalid_json'},400);}
  if(typeof input?.ticket!=='string')return json({error:'invalid_ticket'},400);
  // Limit repeat image pulls just as we limit QR start/poll requests.
  const now=Date.now(),bucket='school-qr-image:'+auth.userId;
  const count=await auth.db.prepare(
   'INSERT INTO academic_auth_login_attempts(bucket,hits,window_end) VALUES(?,1,?) '+
   'ON CONFLICT(bucket) DO UPDATE SET hits=CASE WHEN window_end<=? THEN 1 ELSE hits+1 END, '+
   'window_end=CASE WHEN window_end<=? THEN excluded.window_end ELSE window_end END RETURNING hits')
   .bind(bucket,now+900000,now,now).first();
  if(!Number.isInteger(count?.hits))return json({error:'rate_limit_unavailable'},503);
  if(count.hits>20)return json({error:'too_many_attempts'},429);
  const challenge=await unsealChaoxingQrChallenge({ticket:input.ticket,
   userId:auth.userId,secret:context.env?.SCHOOL_SESSION_KEY});
  const image=await adapter.readQrImage({challenge});
  if(!['image/png','image/jpeg','image/svg+xml'].includes(image?.contentType)||
     !['official','local'].includes(image?.source)||
     !(typeof image.body==='string'||image.body instanceof Uint8Array))
   return json({error:'upstream_unavailable'},502);
  return new Response(image.body,{status:200,headers:{
   'content-type':image.contentType,'cache-control':'private, no-store',
   'x-xu-qr-source':image.source,
   'x-content-type-options':'nosniff','content-security-policy':"default-src 'none'",
   'referrer-policy':'no-referrer'
  }});
 }catch(e){
  const code=e instanceof SchoolLoginError?e.code:
   ['session_expired','authorization_failed','upstream_unavailable'].includes(e?.code)
    ?e.code:'upstream_unavailable';
  return json({error:code},code==='session_expired'?410:
   code==='authorization_failed'?403:502);
 }
}
