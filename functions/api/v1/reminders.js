import {verifyAcademicAccess} from '../../../lib/academic-access.mjs';
import {buildReminderInbox} from '../../../lib/academic-reminder-inbox.mjs';

const HEADERS={'content-type':'application/json; charset=utf-8',
 'cache-control':'private, no-store','x-content-type-options':'nosniff',
 'referrer-policy':'no-referrer','vary':'Cf-Access-Jwt-Assertion'};
const response=(body,status=200)=>new Response(JSON.stringify(body),{status,headers:HEADERS});
async function authorize({request,env}){
 const auth=await verifyAcademicAccess(request,env);
 if(!auth.ok)return {failure:response({status:auth.error},auth.status)};
 if(!env?.ACADEMIC_DB?.prepare||!env?.ACADEMIC_DB?.batch)
  return {failure:response({status:'database_not_configured'},503)};
 return {userId:auth.userId,db:env.ACADEMIC_DB};
}
async function load(db,userId){
 const pref=await db.prepare('SELECT enabled,exam_lead_days,task_lead_days FROM academic_reminder_preferences WHERE user_id=?')
  .bind(userId).first();
 const batches=await db.prepare("SELECT kind,batch_id,last_success_at FROM academic_verified_batches WHERE user_id=? AND kind='exams'")
  .bind(userId).all();
 const batch=batches.results?.[0]||null;
 let exams=[];
 if(batch){
  const rows=await db.prepare("SELECT payload FROM academic_verified_records WHERE user_id=? AND kind='exams' AND batch_id=? LIMIT 250")
   .bind(userId,batch.batch_id).all();
  exams=(rows.results||[]).flatMap(x=>{try{return [JSON.parse(x.payload)];}catch{return [];}});
 }
 const taskRows=await db.prepare('SELECT id,title,due_precision,due_local_date,due_at_utc,local_status FROM academic_assignments WHERE user_id=? AND due_local_date IS NOT NULL ORDER BY due_local_date,id LIMIT 500')
  .bind(userId).all();
 const receipts=await db.prepare('SELECT event_key FROM academic_reminder_receipts WHERE user_id=? LIMIT 1000')
  .bind(userId).all();
 const result=buildReminderInbox({exams,tasks:taskRows.results||[],
  preferences:{enabled:pref?.enabled===1,examLeadDays:pref?.exam_lead_days??7,
   taskLeadDays:pref?.task_lead_days??3},
  readKeys:(receipts.results||[]).map(x=>x.event_key)});
 return {...result,source:{exams:batch?'personal_snapshot':'not_connected',
  examLastReceivedAt:batch?.last_success_at||null,
  tasks:'personal_cloud_workspace'},
  delivery:'in_app_only',crossDeviceReads:true};
}
export async function onRequestGet(context){
 const auth=await authorize(context);if(auth.failure)return auth.failure;
 try{return response(await load(auth.db,auth.userId));}
 catch{return response({status:'database_unavailable'},503);}
}
export async function onRequestPatch(context){
 const origin=context.request.headers.get('origin');
 if((origin&&origin!==new URL(context.request.url).origin)||
    context.request.headers.get('sec-fetch-site')==='cross-site')
  return response({status:'forbidden_origin'},403);
 const auth=await authorize(context);if(auth.failure)return auth.failure;
 if(!(context.request.headers.get('content-type')||'').toLowerCase().startsWith('application/json'))
  return response({status:'json_required'},415);
 if(Number(context.request.headers.get('content-length')||0)>2048)
  return response({status:'payload_too_large'},413);
 let input;
 try{
  const raw=await context.request.text();
  if(raw.length>2048)return response({status:'payload_too_large'},413);
  input=JSON.parse(raw);
 }catch{return response({status:'invalid_json'},400);}
 if(!input||typeof input!=='object'||Array.isArray(input))return response({status:'invalid_input'},422);
 const {db,userId}=auth,now=new Date().toISOString();
 try{
  if(input.operation==='settings'){
   if(Object.keys(input).sort().join(',')!=='enabled,examLeadDays,operation,taskLeadDays'||
    typeof input.enabled!=='boolean'||!Number.isInteger(input.examLeadDays)||
    !Number.isInteger(input.taskLeadDays)||
    input.examLeadDays<0||input.examLeadDays>30||input.taskLeadDays<0||input.taskLeadDays>30)
    return response({status:'invalid_settings'},422);
   await db.batch([
    db.prepare('INSERT OR IGNORE INTO academic_users(id,created_at) VALUES (?,?)').bind(userId,now),
    db.prepare('INSERT INTO academic_reminder_preferences(user_id,enabled,exam_lead_days,task_lead_days,updated_at) VALUES(?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET enabled=excluded.enabled,exam_lead_days=excluded.exam_lead_days,task_lead_days=excluded.task_lead_days,updated_at=excluded.updated_at')
     .bind(userId,input.enabled?1:0,input.examLeadDays,input.taskLeadDays,now)
   ]);
  }else if(input.operation==='read'){
   if(Object.keys(input).sort().join(',')!=='key,operation,read'||
    typeof input.key!=='string'||input.key.length>220||typeof input.read!=='boolean')
    return response({status:'invalid_receipt'},422);
   const existing=await load(db,userId);
   if(!existing.items.some(row=>row.key===input.key))
    return response({status:'unknown_event'},404);
   if(input.read){
    await db.batch([
     db.prepare('INSERT OR IGNORE INTO academic_users(id,created_at) VALUES (?,?)').bind(userId,now),
     db.prepare('INSERT INTO academic_reminder_receipts(user_id,event_key,read_at) VALUES(?,?,?) ON CONFLICT(user_id,event_key) DO UPDATE SET read_at=excluded.read_at')
      .bind(userId,input.key,now)
    ]);
   }else await db.prepare('DELETE FROM academic_reminder_receipts WHERE user_id=? AND event_key=?')
    .bind(userId,input.key).run();
  }else return response({status:'invalid_operation'},422);
  return response(await load(db,userId));
 }catch{return response({status:'database_unavailable'},503);}
}
