// Owner-scoped, read-only monitoring of the existing WeChat ingest tables.
// A successful batch is evidence of cloud receipt, NOT PC online status.
export class WechatMonitorError extends Error {
 constructor(code,status=400){super(code);this.name='WechatMonitorError';this.code=code;this.status=status;}
}
const OWNER=/^[A-Za-z0-9_-]{1,128}$/;
const NINETY_MINUTES=90*60*1000;
function chinaMinutes(date){
 const fmt=new Intl.DateTimeFormat('en-GB',{
  timeZone:'Asia/Shanghai',hour:'2-digit',minute:'2-digit',hourCycle:'h23'
 });
 const parts=fmt.formatToParts(date);
 const hour=Number(parts.find(p=>p.type==='hour')?.value);
 const minute=Number(parts.find(p=>p.type==='minute')?.value);
 const time=hour*60+minute;
 return Number.isFinite(time)?time:-1;
}
export async function listWechatCloudMonitor(db,userId,{now=()=>new Date()}={}){
 if(!db?.prepare||typeof userId!=='string'||!OWNER.test(userId))
  throw new WechatMonitorError('not_configured',503);
 const current=now();
 if(!(current instanceof Date)||!Number.isFinite(current.getTime()))
  throw new WechatMonitorError('invalid_clock',500);
 const since=new Date(current.getTime()-24*60*60*1000).toISOString();
 // Only aggregates are returned: no message text, titles, chat names or credentials.
 const rows=await db.prepare(
  'SELECT d.id,d.display_name AS name,d.last_seen_at AS lastSeenAt,'+
  'd.revoked_at AS revokedAt,'+
  '(SELECT MAX(b.received_at) FROM academic_wechat_batches b WHERE b.device_id=d.id) AS lastConfirmedAt,'+
  '(SELECT COUNT(*) FROM academic_wechat_batches b WHERE b.device_id=d.id AND b.received_at>=?) AS confirmedBatches24h,'+
  "(SELECT COUNT(*) FROM academic_wechat_candidates c WHERE c.device_id=d.id AND c.user_id=d.user_id AND c.review_status='needs_review') AS pendingCount "+
  'FROM academic_wechat_devices d WHERE d.user_id=? ORDER BY d.created_at DESC LIMIT 20'
 ).bind(since,userId).all();
 const currentMinutes=chinaMinutes(current);
 const activeWindow=currentMinutes>=7*60&&currentMinutes<=23*60;
 return {asOf:current.toISOString(),devices:(rows?.results||[]).map(row=>{
  const seen=Date.parse(row.lastSeenAt??'');
  const age=Number.isFinite(seen)?Math.max(0,current.getTime()-seen):null;
  const state=row.revokedAt?'revoked':age===null?'not_verified':
   age<=NINETY_MINUTES?'recent':!activeWindow?'outside_window':
   currentMinutes<8*60+30?'starting_window':'overdue';
  return {
   id:row.id,name:row.name,lastSeenAt:row.lastSeenAt,
   lastConfirmedAt:row.lastConfirmedAt,
   confirmedBatches24h:Number(row.confirmedBatches24h)||0,
   pendingCount:Number(row.pendingCount)||0,
   cloudState:state
  };
 })};
}
