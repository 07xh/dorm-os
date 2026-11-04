/* Owner-only device pairing. Reuses existing Academic OS login and D1. */
import {authorize,json} from '../assignments.js';
import {createWechatDevice,listWechatDevices,WechatDeviceError} from '../../../../lib/academic-wechat-device-sync.mjs';
const safe=req=>req.headers.get('origin')===new URL(req.url).origin&&
 req.headers.get('sec-fetch-site')!=='cross-site'&&
 (req.headers.get('content-type')||'').toLowerCase().startsWith('application/json');
export async function onRequestGet(context){
 const auth=await authorize(context);if(auth.response)return auth.response;
 try{return json({devices:await listWechatDevices(auth.db,auth.userId)});}
 catch{return json({error:'database_unavailable'},503);}
}
export async function onRequestPost(context){
 const auth=await authorize(context);if(auth.response)return auth.response;
 if(!safe(context.request))return json({error:'forbidden_origin_or_format'},403);
 try{
  const raw=await context.request.text();
  if(raw.length>1000)return json({error:'payload_too_large'},413);
  let input;try{input=JSON.parse(raw);}catch{return json({error:'invalid_json'},400);}
  return json(await createWechatDevice(auth.db,auth.userId,input),201);
 }catch(e){
  if(e instanceof WechatDeviceError)return json({error:e.code},e.status);
  return json({error:'database_unavailable'},503);
 }
}
