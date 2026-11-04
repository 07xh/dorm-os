/* Atomic, owner-scoped NCWU second-class activities + official score sync.
 * Requires a trusted user-authorized adapter with verified school identity.
 * No public route receives credentials or untrusted user-supplied source flags.
 */
import {collectAuthorizedSecondClass} from './academic-second-class-connector.mjs';
import {NcwuSecondClassError} from './academic-ncwu-second-class.mjs';

const ID=/^[A-Za-z0-9:_-]{1,120}$/;
const fail=code=>{throw new NcwuSecondClassError(code);};
export async function runVerifiedNcwuSecondClassSync(db,userId,adapter,{
 now=()=>new Date().toISOString(),id=()=>crypto.randomUUID(),
 maxPages=25,maxRecords=250
}={}){
 if(!db?.prepare||typeof db.batch!=='function'||!ID.test(userId||'')||
  !adapter||adapter.provider!=='second_class'||
  typeof adapter.readOfficialScores!=='function')fail('connector_not_configured');
 let activities,official;
 try{
  // Phase 1: check ALL pages and official scores without mutating D1.
  activities=await collectAuthorizedSecondClass(adapter,{maxPages,maxRecords,userId});
  official=await adapter.readOfficialScores();
  if(!official||official.school!=='ncwu'||
   !Array.isArray(official.semesters)||
   typeof official.isSchoolReportedAggregate!=='boolean'||
   official.isSchoolReportedAggregate!==(official.aggregate!==null))fail('invalid_response');
 }catch(error){
  if(error instanceof NcwuSecondClassError)throw error;
  fail(error?.code==='authorization_failed'?'authorization_failed':
   error?.code==='invalid_response'?'invalid_response':'upstream_unavailable');
 }
 // An intermittent empty upstream result must not hide an already trusted
 // activity/score snapshot. A first-time genuinely empty owner remains valid
 // after the usual full identity and source verification.
 if(activities.records.length===0||
    (official.aggregate===null&&official.semesters.length===0)){
  let previous;
  try{
   previous=await db.prepare(
    "SELECT batch_id FROM academic_verified_batches WHERE user_id=? AND kind='second-class' AND source='second_class' AND provenance='trusted_readonly'"
   ).bind(userId).first();
  }catch{fail('database_unavailable');}
  if(previous)fail('unverified_empty_page');
 }
 const capturedAt=now(),batchId=id(),auditId=id();
 if(!ID.test(batchId)||!ID.test(auditId)||!Number.isFinite(Date.parse(capturedAt)))
  fail('connector_not_configured');
 const payload=JSON.stringify(official);
 if(payload.length>25000)fail('invalid_response');
 const rows=[
  db.prepare('INSERT OR IGNORE INTO academic_users(id,created_at) VALUES(?,?)')
   .bind(userId,capturedAt)
 ];
 for(const record of activities.records){
  rows.push(db.prepare(
   'INSERT INTO academic_verified_records(user_id,kind,source,external_id,term,payload,batch_id,updated_at) VALUES(?,?,?,?,?,?,?,?) '+
   'ON CONFLICT(user_id,kind,source,external_id,term) DO UPDATE SET payload=excluded.payload,batch_id=excluded.batch_id,updated_at=excluded.updated_at'
  ).bind(userId,'second-class','second_class',record.externalId,
   record.term,JSON.stringify(record),batchId,capturedAt));
 }
 rows.push(db.prepare(
  'INSERT INTO academic_verified_batches(user_id,kind,source,last_success_at,batch_id,updated_at,provenance) VALUES(?,?,?,?,?,?,?) '+
  'ON CONFLICT(user_id,kind) DO UPDATE SET source=excluded.source,last_success_at=excluded.last_success_at,batch_id=excluded.batch_id,updated_at=excluded.updated_at,provenance=excluded.provenance'
 ).bind(userId,'second-class','second_class',capturedAt,batchId,capturedAt,'trusted_readonly'));
 rows.push(db.prepare(
  "INSERT INTO academic_second_class_official_scores(user_id,batch_id,captured_at,payload,updated_at) VALUES(?,?,?,?,?) "+
  "ON CONFLICT(user_id) DO UPDATE SET batch_id=excluded.batch_id,captured_at=excluded.captured_at,payload=excluded.payload,updated_at=excluded.updated_at"
 ).bind(userId,batchId,capturedAt,payload,capturedAt));
 rows.push(db.prepare(
  "INSERT INTO academic_second_class_sync_audit(id,user_id,source,status,observed_at,item_count) VALUES(?,?,'second_class','success',?,?)"
 ).bind(auditId,userId,capturedAt,activities.records.length));
 try{await db.batch(rows);}
 catch{fail('database_unavailable');}
 return {kind:'second-class',source:'second_class',provenance:'trusted_readonly',
  activityCount:activities.records.length,activityPages:activities.pages,
  hasOfficialScoreAggregate:official.isSchoolReportedAggregate,
  capturedAt};
}
