import {authorize,json} from '../../assignments.js';
import {availableSchoolAdapter,schoolSessionVaultReady}
 from '../../../../../lib/academic-school-login.mjs';
import {probeNcwuLogin} from '../../../../../lib/academic-ncwu-login-probe.mjs';

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
  let nativeLoginReady=false,mode=result.mode;
  if(adapter&&typeof adapter.preflight==='function'&&result.loginSupported===true){
   try{
    const readiness=await adapter.preflight();
    nativeLoginReady=readiness.loginSupported===true;
    mode=readiness.mode;
   }catch{mode='upstream_unavailable';}
  }
  return json({status:mode,loginSupported:result.loginSupported===true,
   nativeLoginReady,sessionVaultReady:vaultReady,
   schoolAuthorized:false,automaticSync:false},200);
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
