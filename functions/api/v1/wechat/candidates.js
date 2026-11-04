// Owner-only candidate inbox. Never conflates messages with verified coursework.
import {authorize,json} from '../assignments.js';
import {listWechatCandidates,WechatDeviceError} from '../../../../lib/academic-wechat-device-sync.mjs';
export async function onRequestGet(context){
 const auth=await authorize(context);if(auth.response)return auth.response;
 try{
  const q=new URL(context.request.url).searchParams;
  const rows=await listWechatCandidates(auth.db,auth.userId,{
   limit:q.has('limit')?Number(q.get('limit')):30,
   offset:q.has('offset')?Number(q.get('offset')):0,
   status:q.get('status')||'needs_review'
  });
  return json({status:'personal_candidates',items:rows});
 }catch(e){
  if(e instanceof WechatDeviceError)return json({error:e.code},e.status);
  return json({error:'database_unavailable'},503);
 }
}