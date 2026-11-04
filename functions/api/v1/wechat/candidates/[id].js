// Existing owner session, same-origin mutation and existing manual-task ledger.
import {authorize,json} from '../../assignments.js';
import {reviewWechatCandidate,WechatDeviceError} from '../../../../../lib/academic-wechat-device-sync.mjs';
export async function onRequestPost(context){
 const auth=await authorize(context);if(auth.response)return auth.response;
 const req=context.request;
 if(req.headers.get('origin')!==new URL(req.url).origin||
    req.headers.get('sec-fetch-site')==='cross-site'||
    !(req.headers.get('content-type')||'').toLowerCase().startsWith('application/json'))
  return json({error:'forbidden_origin_or_format'},403);
 try{
  const raw=await req.text();
  if(raw.length>300)return json({error:'payload_too_large'},413);
  let body;try{body=JSON.parse(raw);}catch{return json({error:'invalid_json'},400);}
  if(!body||typeof body!=='object'||Array.isArray(body)||
     Object.keys(body).join(',')!=='decision')return json({error:'invalid_review'},422);
  return json(await reviewWechatCandidate(auth.db,auth.userId,context.params.id,body.decision));
 }catch(e){
  if(e instanceof WechatDeviceError)return json({error:e.code},e.status);
  return json({error:'database_unavailable'},503);
 }
}
