/* Actual Cron liveness, not merely a D1 bearer record or green Pages CI.
 * Only the authorized internal route records a heartbeat, after the Worker
 * returned a schema-validated result. A rotated bearer invalidates old proof.
 */
const WINDOW_MS=150*60*1000; // hourly at minute 17, tolerate a delayed tick
const FUTURE_SKEW_MS=2*60*1000;
const PROVIDER='chaoxing-hourly';

export function isRecentCronHeartbeat(row,now=Date.now()){
 if(!row||typeof row.configured_at!=='string'||
    row.configured_at!==row.observed_configured_at||
    !Number.isFinite(now))return false;
 const configured=Date.parse(row.configured_at);
 const last=Date.parse(row.last_heartbeat_at);
 if(!Number.isFinite(configured)||!Number.isFinite(last))return false;
 return configured<=last&&last<=now+FUTURE_SKEW_MS&&
    now-last<=WINDOW_MS&&now-configured>=-FUTURE_SKEW_MS;
}
export async function readCronWorkerHealth(db,{now=Date.now()}={}){
 if(!db?.prepare)return {ready:false,lastHeartbeatAt:null};
 try{
  const row=await db.prepare(
   "SELECT i.configured_at,h.configured_at AS observed_configured_at,"+
   "h.last_heartbeat_at FROM academic_cron_identity i LEFT JOIN "+
   "academic_cron_health h ON h.name=i.name WHERE i.name=?"
  ).bind(PROVIDER).first();
  const ready=isRecentCronHeartbeat(row,now);
  return {ready,lastHeartbeatAt:ready?row.last_heartbeat_at:null};
 }catch{
  // Schema is not deployed, token missing, or D1 is unavailable: fail closed.
  return {ready:false,lastHeartbeatAt:null};
 }
}
export async function recordCronWorkerHeartbeat(db,now=new Date().toISOString()){
 if(!db?.prepare||typeof now!=='string'||!Number.isFinite(Date.parse(now)))
  throw Error('cron_health_unavailable');
 const result=await db.prepare(
  "INSERT INTO academic_cron_health (name,configured_at,last_heartbeat_at) "+
  "SELECT name,configured_at,? FROM academic_cron_identity "+
  "WHERE name=? AND configured_at<=? "+
  "ON CONFLICT(name) DO UPDATE SET configured_at=excluded.configured_at,"+
  "last_heartbeat_at=excluded.last_heartbeat_at"
 ).bind(now,PROVIDER,now).run();
 if((result?.meta?.changes??result?.changes??0)!==1)
  throw Error('cron_health_unavailable');
}
