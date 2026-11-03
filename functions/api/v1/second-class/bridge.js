/* Owner-only pairing controls. Pairing key is returned once on POST.
 * It is NOT a university credential and grants only uploading personal
 * second-class snapshots through a narrowly scoped ingest endpoint.
 */
import {authorize,json} from '../assignments.js';
import {createBrowserBridge,revokeBrowserBridge,bridgeStatus,checkOwnerMutation,
 BridgeError} from '../../../../lib/academic-secondclass-browser-bridge.mjs';
const format=(r,s=200)=>json(r,s);
export async function onRequestGet(context){
 const auth=await authorize(context);
 if(auth.response)return auth.response;
 try{return format(await bridgeStatus(auth.db,auth.userId));}
 catch{return format({error:'bridge_unavailable'},503);}
}
export async function onRequestPost(context){
 const auth=await authorize(context);
 if(auth.response)return auth.response;
 if(!checkOwnerMutation(context.request))return format({error:'forbidden_origin_or_format'},403);
 if(Number(context.request.headers.get('content-length')||0)>100)
  return format({error:'payload_too_large'},413);
 const raw=await context.request.text();
 if(raw.length>100)return format({error:'payload_too_large'},413);
 if(raw!=='{}')return format({error:'invalid_json'},400);
 try{return format(await createBrowserBridge(auth.db,auth.userId),201);}
 catch(e){return format({error:e instanceof BridgeError?e.code:'database_unavailable'},503);}
}
export async function onRequestDelete(context){
 const auth=await authorize(context);
 if(auth.response)return auth.response;
 if(!checkOwnerMutation(context.request))return format({error:'forbidden_origin_or_format'},403);
 try{return format(await revokeBrowserBridge(auth.db,auth.userId));}
 catch{return format({error:'database_unavailable'},503);}
}
