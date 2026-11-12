import {verifyAcademicAccess} from '../../../lib/academic-access.mjs';
import {parseTaskBreakdownInput,runTaskBreakdownAI} from '../../../lib/academic-task-ai-breakdown.mjs';

const json=(body,status=200)=>new Response(JSON.stringify(body),{status,headers:{
 'content-type':'application/json; charset=utf-8',
 'cache-control':'private, no-store','vary':'Cookie',
 'x-content-type-options':'nosniff'
}});

/**
 * No caller-provided model URL/key. Only the existing owner session may request
 * suggestions, and the binding must be explicitly enabled by the operator.
 */
export async function handleBreakdownPost({request,env}, {verify=verifyAcademicAccess}={}) {
 const auth=await verify(request,env);
 if(!auth.ok)return json({error:auth.error},auth.status);
 const origin=request.headers.get('origin');
 if((origin&&origin!==new URL(request.url).origin)||
    request.headers.get('sec-fetch-site')==='cross-site')
  return json({error:'forbidden_origin'},403);
 if(env?.TASK_BREAKDOWN_AI_ENABLED!=='true'||typeof env?.AI?.run!=='function')
  return json({error:'ai_not_configured'},503);
 if(!(request.headers.get('content-type')||'').toLowerCase().startsWith('application/json'))
  return json({error:'json_required'},415);
 let raw;
 try {raw=await request.text();}
 catch{return json({error:'invalid_input'},400);}
 if(raw.length>1500)return json({error:'payload_too_large'},413);
 let body;try{body=JSON.parse(raw);}catch{return json({error:'invalid_json'},400);}
 const input=parseTaskBreakdownInput(body);
 if(!input)return json({error:'invalid_input'},422);
 try {
  const steps=await runTaskBreakdownAI(env.AI,input);
  // Suggestions are never saved server side; Android requires explicit approval.
  return json({steps,model:'workers_ai',saved:false});
 } catch {
  return json({error:'ai_unavailable'},503);
 }
}
export async function onRequestPost(context){return handleBreakdownPost(context);}
