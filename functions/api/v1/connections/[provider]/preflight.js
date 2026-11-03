import {authorize,json} from '../../assignments.js';
import {availableSchoolAdapter,schoolSessionVaultReady}
 from '../../../../../lib/academic-school-login.mjs';
export async function onRequestGet(context){
 const auth=await authorize(context);
 if(auth.response)return auth.response;
 if(context.params?.provider!=='second_class')
  return json({status:'unknown_provider',loginSupported:false},404);
 if(!await schoolSessionVaultReady(auth.db,context.env?.SCHOOL_SESSION_KEY))
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
