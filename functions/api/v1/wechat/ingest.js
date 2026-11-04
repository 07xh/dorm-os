// Private computer-to-cloud ingest: device token, never a user's school cookie.
import {json} from '../assignments.js';
import {authenticateWechatDevice,ingestWechatCandidates,WechatDeviceError}
 from '../../../../lib/academic-wechat-device-sync.mjs';
export async function onRequestPost(context){
 const req=context.request;
 if(req.headers.get('origin')||
    !(req.headers.get('content-type')||'').toLowerCase().startsWith('application/json'))
  return json({error:'forbidden_origin_or_format'},403);
 try{
  const device=await authenticateWechatDevice(context.env?.ACADEMIC_DB,
   req.headers.get('authorization'));
  const raw=await req.text();
  if(raw.length>60000)return json({error:'payload_too_large'},413);
  let batch;try{batch=JSON.parse(raw);}catch{return json({error:'invalid_json'},400);}
  return json(await ingestWechatCandidates(context.env.ACADEMIC_DB,device,batch),202);
 }catch(e){
  if(e instanceof WechatDeviceError)return json({error:e.code},e.status);
  return json({error:'database_unavailable'},503);
 }
}