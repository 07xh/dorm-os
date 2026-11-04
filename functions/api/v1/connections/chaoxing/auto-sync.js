/* Opt-in controller for the EXISTING encrypted Chaoxing school connection.
 * Disabling is supported even after the session has expired.
 */
import {authorize,json} from '../../assignments.js';
import {checkSchoolMutation,schoolSessionStates}
 from '../../../../../lib/academic-school-login.mjs';
export async function onRequestPost(context){
 const auth=await authorize(context);if(auth.response)return auth.response;
 if(!checkSchoolMutation(context.request))
  return json({error:'forbidden_origin_or_format'},403);
 let input;
 try{
  const raw=await context.request.text();
  if(raw.length>500)return json({error:'invalid_query'},400);
  input=JSON.parse(raw);
 }catch{return json({error:'invalid_query'},400);}
 if(!input||typeof input!=='object'||Array.isArray(input)||
    Object.keys(input).join(',')!=='enabled'||typeof input.enabled!=='boolean')
  return json({error:'invalid_query'},400);
 try{
  if(input.enabled){
   const tokenReady=await auth.db.prepare(
    "SELECT name FROM academic_cron_identity WHERE name='chaoxing-hourly'"
   ).first();
   if(!tokenReady)return json({error:'autosync_not_ready'},503);
   const states=await schoolSessionStates(auth.db,auth.userId,
    context.env?.SCHOOL_SESSION_KEY);
   if(states.chaoxing!=='active')return json({error:'session_expired'},409);
  }
  const at=new Date().toISOString();
  await auth.db.prepare(
   'INSERT INTO academic_sync_jobs (user_id,provider,enabled,interval_minutes,next_at,'+
   'lease_until,failure_count,last_result) VALUES (?,\'chaoxing\',?,60,?,NULL,0,NULL) '+
   'ON CONFLICT(user_id,provider) DO UPDATE SET enabled=excluded.enabled,'+
   'interval_minutes=60,next_at=excluded.next_at,lease_until=NULL,'+
   'failure_count=0,last_result=NULL'
  ).bind(auth.userId,input.enabled?1:0,input.enabled?at:null).run();
  return json({enabled:input.enabled,intervalMinutes:60});
 }catch{return json({error:'database_unavailable'},503);}
}
