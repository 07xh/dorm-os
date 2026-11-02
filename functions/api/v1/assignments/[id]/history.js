import {authorize,json} from '../../assignments.js';
import {readTaskHistory,WorkspaceError} from '../../../../../lib/academic-workspace.mjs';
export async function onRequestGet(context){
 const auth=await authorize(context);
 if(auth.response)return auth.response;
 try{
   const q=new URL(context.request.url).searchParams;
   const limit=q.has('limit')?Number(q.get('limit')):30;
   const events=await readTaskHistory(auth.db,auth.userId,context.params.id,{limit});
   return json({events});
 }catch(e){
   if(e instanceof WorkspaceError)return json({error:e.code},e.status);
   return json({error:'database_unavailable'},503);
 }
}
