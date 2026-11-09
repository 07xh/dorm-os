import {authorize,json} from '../assignments.js';
import {WorkspaceError} from '../../../../lib/academic-workspace.mjs';
import {
 readCloudTaskGraph,setCloudTaskPrerequisites
} from '../../../../lib/academic-task-dependencies.mjs';

// Same owner login / D1 binding as the existing assignment API.
export async function onRequestGet(context){
 const auth=await authorize(context);
 if(auth.response)return auth.response;
 try{return json(await readCloudTaskGraph(auth.db,auth.userId));}
 catch(e){
  if(e instanceof WorkspaceError)return json({error:e.code},e.status);
  return json({error:'database_unavailable'},503);
 }
}
async function write(context){
 const request=context.request;
 const origin=request.headers.get('origin');
 if((origin&&origin!==new URL(request.url).origin)||
    request.headers.get('sec-fetch-site')==='cross-site')
  return json({error:'forbidden_origin'},403);
 const auth=await authorize(context);
 if(auth.response)return auth.response;
 if(!(request.headers.get('content-type')||'').toLowerCase().startsWith('application/json'))
  return json({error:'json_required'},415);
 let body;
 try{
  const raw=await request.text();
  if(raw.length>12000)return json({error:'payload_too_large'},413);
  body=JSON.parse(raw);
 }catch{return json({error:'invalid_json'},400);}
 if(!body||typeof body!=='object'||Array.isArray(body)||
    Object.keys(body).sort().join(',')!=='graphRevision,prerequisiteIds,taskId,taskRevision')
  return json({error:'invalid_graph_patch'},422);
 try{
  const item=await setCloudTaskPrerequisites(auth.db,auth.userId,body);
  return json({item});
 }catch(e){
  if(e instanceof WorkspaceError)return json({error:e.code},e.status);
  return json({error:'database_unavailable'},503);
 }
}
export const onRequestPut=write;
// POST compatibility for Android clients whose HTTP stack may not support PUT.
export const onRequestPost=write;
