import {authorize,json} from '../../../assignments.js';
import {availableSchoolAdapter,checkSchoolMutation,sealChaoxingQrChallenge,
 unsealChaoxingQrChallenge,connectAuthenticatedChaoxingQr,SchoolLoginError}
 from '../../../../../../lib/academic-school-login.mjs';
export async function onRequestPost(context){
 const auth=await authorize(context);if(auth.response)return auth.response;
 if(!checkSchoolMutation(context.request))return json({error:'forbidden_origin_or_format'},403);
 const adapter=availableSchoolAdapter(context,'chaoxing');
 if(typeof adapter?.pollQrLogin!=='function')return json({error:'school_login_unavailable'},503);
 try{
  const raw=await context.request.text();
  if(raw.length>25000)return json({error:'payload_too_large'},413);
  let input;try{input=JSON.parse(raw)}catch{return json({error:'invalid_json'},400);}
  if(typeof input?.ticket!=='string')return json({error:'invalid_ticket'},400);
  // Bound polling per owner; an old encrypted ticket cannot create unbounded
  // upstream requests. No account identifiers or upstream QR tokens in logs.
  const now=Date.now(),bucket='school-qr-poll:'+auth.userId;
  const count=await auth.db.prepare(
   'INSERT INTO academic_auth_login_attempts(bucket,hits,window_end) VALUES(?,1,?) '+
   'ON CONFLICT(bucket) DO UPDATE SET hits=CASE WHEN window_end<=? THEN 1 ELSE hits+1 END, '+
   'window_end=CASE WHEN window_end<=? THEN excluded.window_end ELSE window_end END RETURNING hits')
   .bind(bucket,now+900000,now,now).first();
  if(!Number.isInteger(count?.hits))return json({error:'rate_limit_unavailable'},503);
  if(count.hits>75)return json({error:'too_many_attempts'},429);
  const challenge=await unsealChaoxingQrChallenge({ticket:input.ticket,
   userId:auth.userId,secret:context.env?.SCHOOL_SESSION_KEY});
  const result=await adapter.pollQrLogin({challenge});
  if(result.status==='approved'){
   // Existing trusted read verifies THIS owner's NCWU-bound account and
   // synchronizes verified inbox entries before D1 stores the session.
   const linked=await connectAuthenticatedChaoxingQr({db:auth.db,userId:auth.userId,
    adapter,session:result.session,expiresAt:result.expiresAt,
    secret:context.env?.SCHOOL_SESSION_KEY});
   return json({status:'connected',lastSuccessAt:linked.lastSuccessAt,expiresAt:linked.expiresAt});
  }
  if(result.status==='cancelled')return json({status:'cancelled'});
  if(!['pending','scanned'].includes(result.status))return json({error:'invalid_response'},502);
  const ticket=await sealChaoxingQrChallenge({challenge:result.challenge,
   userId:auth.userId,secret:context.env?.SCHOOL_SESSION_KEY});
  return json({status:result.status,ticket});
 }catch(e){
  const code=e instanceof SchoolLoginError?e.code:
   ['session_expired','authorization_failed','upstream_unavailable','interactive_login_required'].includes(e?.code)
    ?e.code:'sync_failed';
  const status=code==='session_expired'?410:code==='authorization_failed'?403:
   code==='upstream_unavailable'||code==='sync_failed'?502:503;
  return json({error:code},status);
 }
}
