import {authorize,json} from '../../assignments.js';
import {availableSchoolAdapter,schoolSessionVaultReady}
 from '../../../../../lib/academic-school-login.mjs';
import {probeNcwuLogin} from '../../../../../lib/academic-ncwu-login-probe.mjs';

/* The public probe is informational only; it must not veto the server's
 * independent, stricter CAS adapter readiness check. Neither probe sends
 * credentials nor grants authorization.
 */
export async function resolveNcwuPreflight(publicProbe,adapter,vaultReady){
 const plain=publicProbe?.loginSupported===true;
 let nativeLoginReady=false,status=publicProbe?.mode||'upstream_unavailable';
 if(adapter&&typeof adapter.preflight==='function'){
  try{
   const readiness=await adapter.preflight();
   nativeLoginReady=readiness?.loginSupported===true;
   // Prefer the native adapter's actual state when it can inspect the school.
   if(nativeLoginReady||readiness?.mode==='interactive_login_required'||
      readiness?.mode==='schedule_review_pending')
    status=readiness.mode;
  }catch{status='upstream_unavailable';}
 }
 if(!vaultReady)status='server_session_vault_unavailable';
 return {status,loginSupported:plain||nativeLoginReady,
  nativeLoginReady,sessionVaultReady:vaultReady===true,
  schoolAuthorized:false,automaticSync:false};
}

export async function onRequestGet(context){
 const auth=await authorize(context);
 if(auth.response)return auth.response;
 const provider=context.params?.provider;
 if(!['ncwu','second_class'].includes(provider))
  return json({status:'unknown_provider',loginSupported:false},404);
 const vaultReady=await schoolSessionVaultReady(auth.db,context.env?.SCHOOL_SESSION_KEY);
 if(provider==='ncwu'){
  // Keep the existing credential-free official-site probe. An ordinary
  // form is NOT the same as a verified, configured account connector.
  const result=await probeNcwuLogin();
  const adapter=vaultReady&&availableSchoolAdapter(context,'ncwu');
  return json(await resolveNcwuPreflight(result,adapter,vaultReady),200);
 }
 if(!vaultReady)
  return json({status:'server_session_vault_unavailable',loginSupported:false},503);
 const adapter=availableSchoolAdapter(context,'second_class');
 if(!adapter||typeof adapter.preflight!=='function')
  return json({status:'adapter_unavailable',loginSupported:false},503);
 try{
  const result=await adapter.preflight();
  return json({status:result.mode,loginSupported:result.loginSupported===true},200);
 }catch{
  return json({status:'upstream_unavailable',loginSupported:false},502);
 }
}
