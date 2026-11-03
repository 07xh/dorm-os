/* Private deployment readiness for the currently authenticated owner.
 * No student records, Cloudflare binding IDs, session tokens or secrets.
 * This is not evidence of a live NCWU school-session connection.
 */
import {verifyAcademicAccess} from '../../../lib/academic-access.mjs';

const HEADERS={
 'content-type':'application/json; charset=utf-8',
 'cache-control':'private, no-store',
 'x-content-type-options':'nosniff',
 'referrer-policy':'no-referrer',
 'vary':'Cf-Access-Jwt-Assertion'
};
const json=(data,status=200)=>new Response(JSON.stringify(data),{status,headers:HEADERS});
const REQUIRED=[
 'academic_users',
 'academic_verified_batches',
 'academic_verified_records',
 'academic_second_class_sync_audit',
 'academic_second_class_official_scores'
];

export async function onRequestGet({request,env}){
 const auth=await verifyAcademicAccess(request,env);
 if(!auth.ok)return json({status:auth.error},auth.status);
 const db=env?.ACADEMIC_DB;
 if(!db?.prepare)return json({status:'database_not_configured',databaseReady:false},503);
 try{
  const {results}=await db.prepare(
   'SELECT name FROM sqlite_master WHERE type=? AND name IN (?,?,?,?,?)'
  ).bind('table',...REQUIRED).all();
  const found=new Set((results||[]).map(x=>x.name));
  const missing=REQUIRED.filter(x=>!found.has(x));
  let hasProvenance=false;
  if(found.has('academic_verified_batches')){
   const columns=await db.prepare('PRAGMA table_info(academic_verified_batches)').all();
   hasProvenance=Array.isArray(columns?.results)&&columns.results.some(c=>c.name==='provenance');
  }
  const ready=missing.length===0&&hasProvenance;
  return json({
   status:ready?'database_ready':'database_schema_incomplete',
   databaseReady:ready,
   accessAuthenticated:true,
   missingTables:missing,
   sourceProvenanceColumn:hasProvenance,
   schoolConnection:'not_verified',
   automaticSync:false
  },ready?200:503);
 }catch{
  return json({status:'database_unavailable',databaseReady:false},503);
 }
}
