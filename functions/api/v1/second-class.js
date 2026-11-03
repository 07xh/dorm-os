/* Own-school second-class read-only dashboard API.
 * This route NEVER fetches the university, accepts credentials or starts sync.
 * It reads the current owner-scoped trusted/user-imported D1 snapshot only.
 */
import {verifyAcademicAccess} from '../../../lib/academic-access.mjs';
import {projectSecondClass} from '../../../lib/academic-second-class-view.mjs';
const HEADERS={'content-type':'application/json; charset=utf-8',
 'cache-control':'private, no-store','x-content-type-options':'nosniff',
 'referrer-policy':'no-referrer','vary':'Cf-Access-Jwt-Assertion'};
const json=(body,status=200)=>new Response(JSON.stringify(body),{status,headers:HEADERS});
const base={kind:'second-class',source:'second_class',automaticSync:false,
 syncMode:'awaiting_school_authorized_adapter'};
export async function onRequestGet({request,env}){
 const gate=await verifyAcademicAccess(request,env);
 if(!gate.ok)return json({status:gate.error},gate.status);
 if(!env?.ACADEMIC_DB?.prepare)return json({status:'database_not_configured'},503);
 const db=env.ACADEMIC_DB,userId=gate.userId;
 try{
  const batch=await db.prepare(
   "SELECT batch_id,last_success_at,provenance FROM academic_verified_batches WHERE user_id=? AND kind='second-class'"
  ).bind(userId).first();
  if(!batch)return json({...base,status:'not_connected',provenance:null,
   records:[],summary:null,officialScore:null,lastSuccessAt:null,lastReadOnlyAt:null});
  const {results}=await db.prepare(
   "SELECT payload FROM academic_verified_records WHERE user_id=? AND kind='second-class' AND batch_id=? ORDER BY external_id LIMIT 250"
  ).bind(userId,batch.batch_id).all();
  const mapped=projectSecondClass((results||[]).map(x=>JSON.parse(x.payload)));
  const attested=batch.provenance==='trusted_readonly';
  const audit=attested?await db.prepare(
   "SELECT observed_at FROM academic_second_class_sync_audit WHERE user_id=? AND status='success' ORDER BY observed_at DESC LIMIT 1"
  ).bind(userId).first():null;
  // Official school totals are separate from activity-level score candidates
  // and must belong to the SAME trusted batch as displayed activities.
  const officialRow=attested?await db.prepare(
   'SELECT batch_id,payload FROM academic_second_class_official_scores WHERE user_id=?'
  ).bind(userId).first():null;
  const officialScore=officialRow?.batch_id===batch.batch_id?
   JSON.parse(officialRow.payload):null;
  const dateMs=Date.parse(batch.last_success_at);
  if(!Number.isFinite(dateMs))throw Error('invalid_last_success');
  return json({...base,
   status:Date.now()-dateMs>36*3600_000?'stale':'ready',
   provenance:attested?'trusted_readonly':'user_import',
   records:mapped.records,summary:mapped.summary,officialScore,
   lastSuccessAt:batch.last_success_at,lastReadOnlyAt:audit?.observed_at||null});
 }catch{return json({status:'database_unavailable'},503);}
}
