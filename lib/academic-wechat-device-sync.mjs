// Existing Academic OS owner login stays authoritative for pairing and review.
// Only an explicitly paired Windows collector can ingest candidates; the PC
// never receives an Academic OS owner cookie or a school's authentication data.
import {WechatCandidateError,normalizeWechatCandidateBatch} from './academic-wechat-candidate-contract.mjs';

export class WechatDeviceError extends Error {
 constructor(code,status=400){super(code);this.name='WechatDeviceError';this.code=code;this.status=status;}
}
const fail=(code,status)=>{throw new WechatDeviceError(code,status);};
const toHex=bytes=>Array.from(new Uint8Array(bytes),x=>x.toString(16).padStart(2,'0')).join('');
const base64url=bytes=>btoa(String.fromCharCode(...bytes)).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
const randomBytes=count=>crypto.getRandomValues(new Uint8Array(count));
const nowIso=()=>new Date().toISOString();
async function hash(value){return toHex(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(value)));}
const validOwner=id=>typeof id==='string'&&/^[A-Za-z0-9_-]{1,128}$/.test(id);

export async function createWechatDevice(db,userId,input,{now=nowIso,bytes=randomBytes}={}){
 if(!db?.prepare||typeof db.batch!=='function'||!validOwner(userId))fail('not_configured',503);
 if(!input||typeof input!=='object'||Array.isArray(input)||
    Object.keys(input).join(',')!=='name'||typeof input.name!=='string'||
    input.name.trim()!==input.name||input.name.length<1||input.name.length>48||
    /[\x00-\x1f\x7f]/.test(input.name))fail('invalid_device_name',422);
 const total=await db.prepare('SELECT COUNT(*) AS n FROM academic_wechat_devices WHERE user_id=? AND revoked_at IS NULL')
  .bind(userId).first();
 if((total?.n??0)>=3)fail('device_limit_reached',409);
 const id='wxd_'+base64url(bytes(18)),token='wxu_'+base64url(bytes(32));
 const createdAt=now();
 await db.batch([
  db.prepare('INSERT OR IGNORE INTO academic_users(id,created_at) VALUES (?,?)').bind(userId,createdAt),
  db.prepare('INSERT INTO academic_wechat_devices (id,user_id,display_name,token_sha256,created_at) VALUES (?,?,?,?,?)')
   .bind(id,userId,input.name,await hash(token),createdAt)
 ]);
 // This plaintext token is shown exactly once; only its SHA-256 is stored in D1.
 return {device:{id,name:input.name,createdAt},token};
}
export async function listWechatDevices(db,userId){
 if(!db?.prepare||!validOwner(userId))fail('not_configured',503);
 const rows=await db.prepare(
  'SELECT id,display_name AS name,created_at AS createdAt,last_seen_at AS lastSeenAt,revoked_at AS revokedAt '+
  'FROM academic_wechat_devices WHERE user_id=? ORDER BY created_at DESC LIMIT 20'
 ).bind(userId).all();
 return rows?.results||[];
}
export async function revokeWechatDevice(db,userId,id,{now=nowIso}={}){
 if(!db?.prepare||!validOwner(userId)||typeof id!=='string'||!/^[A-Za-z0-9_-]{12,128}$/.test(id))
  fail('invalid_device',400);
 const result=await db.prepare(
  'UPDATE academic_wechat_devices SET revoked_at=? WHERE id=? AND user_id=? AND revoked_at IS NULL'
 ).bind(now(),id,userId).run();
 if((result?.meta?.changes??result?.changes??0)!==1)fail('device_not_found',404);
 return {revoked:true,id};
}
export async function authenticateWechatDevice(db,header){
 const m=typeof header==='string'&&/^Bearer (wxu_[A-Za-z0-9_-]{43})$/.exec(header);
 if(!m||!db?.prepare)fail('device_unauthorized',401);
 const row=await db.prepare(
  'SELECT id,user_id FROM academic_wechat_devices WHERE token_sha256=? AND revoked_at IS NULL'
 ).bind(await hash(m[1])).first();
 if(!row)fail('device_unauthorized',401);
 return {id:row.id,userId:row.user_id};
}
export async function ingestWechatCandidates(db,device,input,{now=nowIso,id=()=>crypto.randomUUID()}={}){
 if(!db?.prepare||typeof db.batch!=='function'||!device?.id||!device?.userId)
  fail('not_configured',503);
 let batch;
 try{batch=normalizeWechatCandidateBatch(input);}
 catch(e){if(e instanceof WechatCandidateError)fail(e.code,422);throw e;}
 if(batch.deviceId!==device.id)fail('device_mismatch',403);
 if(batch.records.length>25)fail('batch_size_invalid',422);
 const receivedAt=now(),digest=await hash(JSON.stringify(batch));
 const previous=await db.prepare(
  'SELECT content_sha256 FROM academic_wechat_batches WHERE device_id=? AND batch_id=?'
 ).bind(device.id,batch.batchId).first();
 if(previous){
  if(previous.content_sha256!==digest)fail('batch_id_conflict',409);
  return {received:batch.records.length,inserted:0,duplicateBatch:true,reviewStatus:'needs_review'};
 }
 // All inserts are guarded within the D1 transaction: a revoke racing with
 // the initial bearer lookup cannot import new candidates.
 const guard=' WHERE EXISTS (SELECT 1 FROM academic_wechat_devices '+
  'WHERE id=? AND user_id=? AND revoked_at IS NULL)';
 const writes=[
  db.prepare('INSERT INTO academic_wechat_batches (device_id,batch_id,content_sha256,captured_at,received_at) '+
   'SELECT ?,?,?,?,?'+guard)
   .bind(device.id,batch.batchId,digest,batch.capturedAt,receivedAt,device.id,device.userId)
 ];
 for(const rec of batch.records){
  writes.push(db.prepare(
   'INSERT OR IGNORE INTO academic_wechat_candidates '+
   '(id,user_id,device_id,message_key,candidate_key,chat_key,chat_label,kind,title,excerpt,sent_at,'+
   'due_precision,due_local_date,due_at_utc,created_at) '+
   'SELECT ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?'+guard
  ).bind(id(),device.userId,device.id,rec.messageKey,rec.candidateKey,rec.chatKey,
   rec.chatLabel,rec.kind,rec.title,rec.excerpt,rec.sentAt,rec.deadline.precision,
   rec.deadline.date,rec.deadline.atUtc,receivedAt,device.id,device.userId));
 }
 writes.push(db.prepare('UPDATE academic_wechat_devices SET last_seen_at=? WHERE id=? AND user_id=? AND revoked_at IS NULL')
  .bind(receivedAt,device.id,device.userId));
 let outcome;
 try{outcome=await db.batch(writes);}
 catch{
  // A concurrent/reused batch is never allowed to partially import records.
  fail('batch_conflict_or_storage_unavailable',409);
 }
 if((outcome[0]?.meta?.changes??outcome[0]?.changes??0)!==1||
    (outcome.at(-1)?.meta?.changes??outcome.at(-1)?.changes??0)!==1)
  fail('device_unauthorized',401);
 const inserted=outcome.slice(1,-1).reduce((sum,r)=>sum+(r?.meta?.changes??r?.changes??0),0);
 return {received:batch.records.length,inserted,duplicateBatch:false,reviewStatus:'needs_review'};
}
export async function listWechatCandidates(db,userId,{limit=30,offset=0,status='needs_review'}={}){
 if(!db?.prepare||!validOwner(userId))fail('not_configured',503);
 if(!Number.isSafeInteger(limit)||limit<1||limit>50||
    !Number.isSafeInteger(offset)||offset<0||offset>10000||
    !['needs_review','accepted','dismissed'].includes(status))fail('invalid_query',400);
 const result=await db.prepare(
  'SELECT id,kind,title,excerpt,chat_label AS chatLabel,sent_at AS sentAt,'+
  'due_precision AS duePrecision,due_local_date AS dueLocalDate,due_at_utc AS dueAtUtc,'+
  'review_status AS reviewStatus,created_at AS receivedAt '+
  'FROM academic_wechat_candidates WHERE user_id=? AND review_status=? '+
  'ORDER BY sent_at DESC,id DESC LIMIT ? OFFSET ?'
 ).bind(userId,status,limit,offset).all();
 return result?.results||[];
}


// Only the signed-in Academic OS owner may approve a candidate. Approval
// inserts a regular manual task into the EXISTING tasks store in one D1 batch;
// no unreviewed chat content enters the official coursework sync pipeline.
export async function reviewWechatCandidate(db,userId,candidateId,decision,{
 now=nowIso,id=()=>crypto.randomUUID()
}={}){
 if(!db?.prepare||typeof db.batch!=='function'||!validOwner(userId))
  fail('not_configured',503);
 if(typeof candidateId!=='string'||!/^[A-Za-z0-9_-]{12,128}$/.test(candidateId)||
    !['accept','dismiss'].includes(decision))fail('invalid_review',422);
 const row=await db.prepare(
  'SELECT review_status FROM academic_wechat_candidates WHERE id=? AND user_id=?'
 ).bind(candidateId,userId).first();
 if(!row)fail('candidate_not_found',404);
 if(row.review_status!=='needs_review')fail('candidate_already_reviewed',409);
 const at=now();
 if(decision==='dismiss'){
  const updated=await db.prepare(
   "UPDATE academic_wechat_candidates SET review_status='dismissed',reviewed_at=? "+
   "WHERE id=? AND user_id=? AND review_status='needs_review'"
  ).bind(at,candidateId,userId).run();
  if((updated?.meta?.changes??updated?.changes??0)!==1)fail('candidate_already_reviewed',409);
  return {id:candidateId,status:'dismissed',assignmentId:null};
 }
 const assignmentId=id();
 const outcome=await db.batch([
  db.prepare(
   'INSERT INTO academic_assignments '+
   '(id,user_id,course_id,title,kind,due_at_utc,due_local_date,due_timezone,'+
   'due_precision,personal_target_at,local_status,created_at,updated_at) '+
   "SELECT ?,user_id,NULL,title,'manual',due_at_utc,due_local_date,"+
   "'Asia/Shanghai',due_precision,NULL,'todo',?,? "+
   "FROM academic_wechat_candidates WHERE id=? AND user_id=? AND review_status='needs_review'"
  ).bind(assignmentId,at,at,candidateId,userId),
  db.prepare(
   "UPDATE academic_wechat_candidates SET review_status='accepted',reviewed_at=?,assignment_id=? "+
   "WHERE id=? AND user_id=? AND review_status='needs_review'"
  ).bind(at,assignmentId,candidateId,userId)
 ]);
 if(outcome.some(x=>(x?.meta?.changes??x?.changes??0)!==1))
  fail('candidate_already_reviewed',409);
 return {id:candidateId,status:'accepted',assignmentId};
}
