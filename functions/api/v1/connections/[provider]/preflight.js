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
  // A real GET probe is deliberately separate from the adapter registration.
  // It must not imply school authentication, successful timetable read, or
  // permission to submit credentials to an unconfigured service.
  const result=await probeNcwuLogin();
  return json({status:result.mode,loginSupported:result.loginSupported===true,
   sessionVaultReady:vaultReady,schoolAuthorized:false,automaticSync:false},200);
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
