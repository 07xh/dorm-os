// An owner may revoke a PC immediately without changing the Academic OS login.
import {authorize,json} from '../../assignments.js';
import {revokeWechatDevice,WechatDeviceError} from '../../../../../lib/academic-wechat-device-sync.mjs';
export async function onRequestDelete(context){
 const auth=await authorize(context);if(auth.response)return auth.response;
 const req=context.request;
 if(req.headers.get('origin')!==new URL(req.url).origin||
    req.headers.get('sec-fetch-site')==='cross-site')
  return json({error:'forbidden_origin'},403);
 try{return json(await revokeWechatDevice(auth.db,auth.userId,context.params.id));}
 catch(e){if(e instanceof WechatDeviceError)return json({error:e.code},e.status);
  return json({error:'database_unavailable'},503);}
}