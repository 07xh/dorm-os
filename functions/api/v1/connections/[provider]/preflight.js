import {authorize,json} from '../../assignments.js';
import {availableSchoolAdapter,schoolSessionVaultReady}
 from '../../../../../lib/academic-school-login.mjs';
import {probeNcwuLogin} from '../../../../../lib/academic-ncwu-login-probe.mjs';

/* The public probe is informational only; it must not veto the server's
 * independent, stricter CAS adapter readiness check. Neither probe sends
 * credentials nor grants authorization.
 */
export async function resolveNcwuPreflight(publicProbe,adapter,vaultReady){
 // Seeing an ordinary form on the official site does not mean that the
 // server-side adapter can complete that login and bind the owner's data.
 const officialFormDetected=publicProbe?.loginSupported===true;
 let nativeLoginReady=false,status=publicProbe?.mode||'upstream_unavailable';
 let safeReason=null;
 if(adapter&&typeof adapter.preflight==='function'){
  try{
   const readiness=await adapter.preflight();
   nativeLoginReady=readiness?.loginSupported===true;
   if(['password_transform_required','interactive_challenge_required',
     'password_form_unavailable','unexpected_cas_form_action'].includes(readiness?.reason))
    safeReason=readiness.reason;
   // Prefer the native adapter's actual state when it can inspect the school.
   if(nativeLoginReady||readiness?.mode==='interactive_login_required'||
      readiness?.mode==='schedule_review_pending')
    status=readiness.mode;
  }catch{status='upstream_unavailable';}
 }
 if(!vaultReady)status='server_session_vault_unavailable';
 return {status,loginSupported:nativeLoginReady&&vaultReady===true,
  officialFormDetected,nativeLoginReady,sessionVaultReady:vaultReady===true,
  ...(status==='interactive_login_required'&&vaultReady===true&&safeReason?{reason:safeReason}:{}),
  schoolAuthorized:false,automaticSync:false};
}

// Only the authenticated, trusted native adapter can authorize credential
// submission. The informational public probe is a fallback, not a second
// mandatory round trip on every button click.
export async function checkNcwuPreflight({
 vaultReady,adapter,publicProbe=probeNcwuLogin
}={}){
 if(vaultReady!==true)
  return resolveNcwuPreflight(null,null,false);
 if(adapter&&typeof adapter.preflight==='function')
  return resolveNcwuPreflight(null,adapter,true);
 return resolveNcwuPreflight(await publicProbe(),null,true);
}

export async function onRequestGet(context){
 const auth=await authorize(context);
 if(auth.response)return auth.response;
 const provider=context.params?.provider;
 if(provider==='second_class')return json({
  status:'official_app_required',loginSupported:false,nativeLoginReady:false,
  officialLoginUrl:'https://dekt.ncwu.edu.cn/',qrSessionLinked:false,
  schoolAuthorized:false,automaticSync:false});
 if(provider!=='ncwu')return json({status:'unknown_provider',loginSupported:false},404);
 const vaultReady=await schoolSessionVaultReady(auth.db,context.env?.SCHOOL_SESSION_KEY);
 const adapter=vaultReady&&availableSchoolAdapter(context,'ncwu');
 return json(await checkNcwuPreflight({vaultReady,adapter}),200);
}
