/* Internal scheduler. Never exposes a public trigger or creates active jobs. */
const ALLOWED=new Set(['ncwu','chaoxing']);
const ISO=/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{3})?Z$/;
const date=s=>typeof s==='string'&&ISO.test(s)&&Number.isFinite(Date.parse(s));
const stamp=ms=>new Date(ms).toISOString();
export function nextAttempt(now,intervalMinutes,failures=0,result='success'){
 if(!date(now)||!Number.isInteger(intervalMinutes)||intervalMinutes<30||intervalMinutes>1440||
 !Number.isInteger(failures)||failures<0||failures>20)throw Error('invalid_scheduler_values');
 const minutes=result==='success'?intervalMinutes:Math.min(1440,Math.max(15,15*2**Math.min(failures,7)));
 return stamp(Date.parse(now)+minutes*60_000);
}
export function dueJob(row,now){
 return !!row&&ALLOWED.has(row.provider)&&row.enabled===1&&date(now)&&
 date(row.next_at)&&Date.parse(row.next_at)<=Date.parse(now)&&
 (!row.lease_until||(date(row.lease_until)&&Date.parse(row.lease_until)<=Date.parse(now)));
}
export async function runDueJobs(db,adapters,{now=()=>new Date().toISOString(),run,limit=3,leaseMinutes=10}={}){
 if(!db?.prepare||typeof run!=='function'||!adapters||typeof adapters!=='object'||
 !Number.isInteger(limit)||limit<1||limit>5||leaseMinutes<5||leaseMinutes>30)throw Error('scheduler_not_configured');
 const time=now();if(!date(time))throw Error('invalid_scheduler_clock');
 const sql='SELECT j.user_id,j.provider,j.enabled,j.interval_minutes,j.next_at,j.lease_until,j.failure_count '+
  'FROM academic_sync_jobs j JOIN academic_connections c ON c.user_id=j.user_id AND c.provider=j.provider '+
  "AND c.mode='readonly' AND c.status IN ('connected','partial') "+
  'WHERE j.enabled=1 AND j.next_at<=? AND (j.lease_until IS NULL OR j.lease_until<=?) '+
  'ORDER BY j.next_at ASC LIMIT ?';
 const rows=(await db.prepare(sql).bind(time,time,limit).all()).results||[], report=[];
 for(const job of rows){
  if(!dueJob(job,time))continue;
  const adapter=adapters[job.provider];
  if(!adapter||adapter.provider!==job.provider||typeof adapter.verifySession!=='function'||
  typeof adapter.readPage!=='function'){report.push({provider:job.provider,status:'not_configured'});continue;}
  const lease=stamp(Date.parse(time)+leaseMinutes*60_000);
  const claim=await db.prepare('UPDATE academic_sync_jobs SET lease_until=? WHERE user_id=? AND provider=? '+
   'AND enabled=1 AND next_at<=? AND (lease_until IS NULL OR lease_until<=?)')
   .bind(lease,job.user_id,job.provider,time,time).run();
  if(claim?.meta?.changes!==1){report.push({provider:job.provider,status:'already_running'});continue;}
  let state='failed',failures=0;
  try{
   const outcome=await run(db,job.user_id,adapter);
   state=outcome?.coverage==='complete'?'success':'partial';
   failures=state==='success'?0:Math.min(20,job.failure_count+1);
  }catch(error){
   state=error?.code==='authorization_failed'?'expired':'failed';
   failures=Math.min(20,job.failure_count+1);
  }
  const finished=now();
  await db.prepare('UPDATE academic_sync_jobs SET lease_until=NULL,last_run_at=?,last_result=?,'+
   'failure_count=?,next_at=? WHERE user_id=? AND provider=? AND lease_until=?')
   .bind(finished,state,failures,nextAttempt(finished,job.interval_minutes,failures,state),
    job.user_id,job.provider,lease).run();
  report.push({provider:job.provider,status:state});
 }
 return report;
}
