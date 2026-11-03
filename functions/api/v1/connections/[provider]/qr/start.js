import {authorize,json} from '../../../assignments.js';
import {availableSchoolAdapter,checkSchoolMutation,sealChaoxingQrChallenge,SchoolLoginError}
 from '../../../../../../lib/academic-school-login.mjs';
// One authenticated owner initiates an upstream login challenge. No password,
// upstream Cookie or QR polling secret is returned in plaintext.
export async function onRequestPost(context){
 const auth=await authorize(context);if(auth.response)return auth.response;
 if(!checkSchoolMutation(context.request))return json({error:'forbidden_origin_or_format'},403);
 const adapter=availableSchoolAdapter(context,'chaoxing');
 if(typeof adapter?.beginQrLogin!=='function')return json({error:'school_login_unavailable'},503);
 try{
  const body=await context.request.text();
  if(body.length>100||body.trim()!=='{}')return json({error:'invalid_json'},400);
  const now=Date.now(),bucket='school-qr-start:'+auth.userId;
  const count=await auth.db.prepare(
   'INSERT INTO academic_auth_login_attempts(bucket,hits,window_end) VALUES(?,1,?) '+
   'ON CONFLICT(bucket) DO UPDATE SET hits=CASE WHEN window_end<=? THEN 1 ELSE hits+1 END, '+
   'window_end=CASE WHEN window_end<=? THEN excluded.window_end ELSE window_end END RETURNING hits')
   .bind(bucket,now+900000,now,now).first();
  if(!Number.isInteger(count?.hits))return json({error:'rate_limit_unavailable'},503);
  if(count.hits>8)return json({error:'too_many_attempts'},429);
  const result=await adapter.beginQrLogin();
  const ticket=await sealChaoxingQrChallenge({challenge:result.challenge,
   userId:auth.userId,secret:context.env?.SCHOOL_SESSION_KEY});
  return json({status:'pending',ticket,qrImageUrl:result.qrImageUrl,
   expiresAt:result.expiresAt},200);
 }catch(e){
  const code=e instanceof SchoolLoginError?e.code:
   ['upstream_unavailable','interactive_login_required','authorization_failed'].includes(e?.code)?e.code:'sync_failed';
  return json({error:code},code==='upstream_unavailable'?502:code==='interactive_login_required'?409:503);
 }
}
