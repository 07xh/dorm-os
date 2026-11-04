/* Private scheduled trigger. This is not an owner login/API endpoint.
 * Without the unguessable Worker secret, it does not execute school traffic.
 */
import {json} from '../assignments.js';
import {verifyCronToken} from '../../../../lib/academic-cron-auth.mjs';
import {runChaoxingAutoCycle} from '../../../../lib/academic-chaoxing-auto-sync.mjs';
import {recordCronWorkerHeartbeat} from '../../../../lib/academic-chaoxing-cron-health.mjs';
import {createChaoxingSchoolAdapter} from '../../../../lib/academic-chaoxing-provider.mjs';
export async function onRequestPost(context){
 const request=context.request;
 if(!context.env?.ACADEMIC_DB||!context.env?.SCHOOL_SESSION_KEY)
  return json({error:'service_unavailable'},503);
 if(request.headers.get('origin')||
    !(request.headers.get('content-type')||'').startsWith('application/json'))
  return json({error:'not_found'},404);
 const key=request.headers.get('x-xu-cron-key');
 if(!await verifyCronToken(context.env.ACADEMIC_DB,key))
  return json({error:'not_found'},404);
 try{
  const result=await runChaoxingAutoCycle(context.env.ACADEMIC_DB,
   createChaoxingSchoolAdapter(),context.env.SCHOOL_SESSION_KEY);
  // Reaching this point proves this deployment could contact its private
  // endpoint with the CURRENT bearer. Idle checks also count as liveness.
  await recordCronWorkerHeartbeat(context.env.ACADEMIC_DB);
  return json(result);
 }catch{
  // Never serialize school URLs, encrypted sessions, course names or raw errors.
  return json({error:'autosync_unavailable'},503);
 }
}
