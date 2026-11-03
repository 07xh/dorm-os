/* Consolidated private connections endpoint. A provider is only login-capable
 * when a real server-side adapter is installed. Never accepts client credentials.
 */
import {authorize,json} from './assignments.js';
import {readConnectionCenter} from '../../../lib/academic-connection-center.mjs';
import {availableSchoolAdapter,schoolSessionStates,schoolSessionVaultReady} from '../../../lib/academic-school-login.mjs';
import {describeSecondClassAuthorization} from '../../../lib/academic-ncwu-secondclass-sso.mjs';
import {bridgeStatus} from '../../../lib/academic-secondclass-browser-bridge.mjs';
export function isActiveSchoolAuthorization(row,sessionState,available){
 // Historical snapshots cannot prove current owner-bound authorization.
 if(!available||sessionState!=='active'||!row||!Array.isArray(row.trustedKinds))return false;
 if(row.id==='second_class')return row.trustedKinds.includes('second-class');
 if(row.id==='ncwu')return ['partial','connected'].includes(row.status)&&
  row.trustedKinds.includes('schedule');
 return row.id==='chaoxing'&&row.status==='connected';
}
export async function onRequestGet(context){
 const auth=await authorize(context);
 if(auth.response)return auth.response;
 try{
  const result=await readConnectionCenter(auth.db,auth.userId);
  const sessions=await schoolSessionStates(auth.db,auth.userId,context.env?.SCHOOL_SESSION_KEY);
  const vaultReady=await schoolSessionVaultReady(auth.db,context.env?.SCHOOL_SESSION_KEY);
  const browserBridge=await bridgeStatus(auth.db,auth.userId).catch(()=>({state:'unavailable',lastSyncAt:null,expiresAt:null}));
  result.platforms=result.platforms.map(row=>{
   const available=vaultReady&&!!availableSchoolAdapter(context,row.id);
   const sessionState=sessions[row.id]||'not_connected';
   // The second-class verified ledger is intentionally independent of the
   // legacy academic_connections table (whose provider CHECK excludes it).
   const verified=isActiveSchoolAuthorization(row,sessionState,available);
   // University CAS returns to a SCHOOL-owned service. Do not expose
   // an Academic OS password form or imply consent to a third-party app.
   if(row.id==='second_class'){
    const flow=describeSecondClassAuthorization({sessionState,verifiedSession:verified});
    return {...row,status:verified?'connected':row.status,sessionState,
     loginMode:flow.loginMode,authFlow:flow,browserBridge,
     accountAuthorization:flow.xiaoxuAuthorization};
   }
   return {...row,loginMode:available?'password':'unavailable',sessionState,
    accountAuthorization:verified?'verified_session':'not_verified'};
  });
  return json(result);
 }catch{return json({error:'database_unavailable'},503);}
}
