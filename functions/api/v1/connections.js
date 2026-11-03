/* Consolidated private connections endpoint. A provider is only login-capable
 * when a real server-side adapter is installed. Never accepts client credentials.
 */
import {authorize,json} from './assignments.js';
import {readConnectionCenter} from '../../../lib/academic-connection-center.mjs';
import {availableSchoolAdapter,schoolSessionStates,schoolSessionVaultReady} from '../../../lib/academic-school-login.mjs';
export async function onRequestGet(context){
 const auth=await authorize(context);
 if(auth.response)return auth.response;
 try{
  const result=await readConnectionCenter(auth.db,auth.userId);
  const sessions=await schoolSessionStates(auth.db,auth.userId);
  const vaultReady=await schoolSessionVaultReady(auth.db,context.env?.SCHOOL_SESSION_KEY);
  result.platforms=result.platforms.map(row=>{
   const available=vaultReady&&!!availableSchoolAdapter(context,row.id);
   const sessionState=sessions[row.id]||'not_connected';
   return {...row,loginMode:available?'password':'unavailable',sessionState,
    accountAuthorization:available&&sessionState==='active'&&row.status==='connected'?
     'verified_session':'not_verified'};
  });
  return json(result);
 }catch{return json({error:'database_unavailable'},503);}
}
