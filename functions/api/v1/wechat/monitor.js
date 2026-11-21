// Private owner-only cloud receipt monitor; this is not a remote PC control API.
import {authorize,json} from '../assignments.js';
import {listWechatCloudMonitor,WechatMonitorError} from '../../../../lib/academic-wechat-monitor.mjs';
export async function onRequestGet(context){
 const auth=await authorize(context);
 if(auth.response)return auth.response;
 try{return json(await listWechatCloudMonitor(auth.db,auth.userId));}
 catch(error){
  if(error instanceof WechatMonitorError)return json({error:error.code},error.status);
  return json({error:'database_unavailable'},503);
 }
}
