import {authorize,json} from '../assignments.js';
import {patchPersonalTask,WorkspaceError} from '../../../../lib/academic-workspace.mjs';
// Same owner-protected task mutation. Android's HttpURLConnection does not
// reliably support PATCH; POST here accepts only personal completion or personal plan.
async function updatePersonalTask(context,statusOnly){
 const req=context.request, origin=req.headers.get('origin');
 if((origin&&origin!==new URL(req.url).origin)||
    req.headers.get('sec-fetch-site')==='cross-site')return json({error:'forbidden_origin'},403);
 const auth=await authorize(context);
 if(auth.response)return auth.response;
 if(!(req.headers.get('content-type')||'').toLowerCase().startsWith('application/json'))
   return json({error:'json_required'},415);
 try{
   const raw=await req.text();
   if(raw.length>16000)return json({error:'payload_too_large'},413);
   let body;try{body=JSON.parse(raw);}catch{return json({error:'invalid_json'},400);}
   // Existing POST compatibility route: exactly one personal field + revision.
   // Never allow cloud clients to change source title, official due date or
   // verified platform status through this Android-compatible endpoint.
   if(statusOnly){
     const keys=body&&typeof body==='object'&&!Array.isArray(body)
       ?Object.keys(body).sort().join(','):'';
     if(keys!=='localStatus,revision'&&keys!=='personalTargetAt,revision')
       return json({error:'personal_fields_only'},422);
   }
   return json({item:await patchPersonalTask(auth.db,auth.userId,context.params.id,body)});
 }catch(e){
   if(e instanceof WorkspaceError)return json({error:e.code},e.status);
   return json({error:'database_unavailable'},503);
 }
}

export async function onRequestPatch(context){return updatePersonalTask(context,false);}
export async function onRequestPost(context){return updatePersonalTask(context,true);}
