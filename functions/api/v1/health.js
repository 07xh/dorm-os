/* Private, read-only Academic OS readiness. Requires the same owner gate
 * used by assignments, verified snapshots, reminders and connections.
 * Never returns private rows, secrets, student IDs or session values.
 */
import {verifyAcademicAccess} from '../../../lib/academic-access.mjs';
import {readAcademicSchema} from '../../../lib/academic-schema-readiness.mjs';
const HEADERS={
 'content-type':'application/json; charset=utf-8',
 'cache-control':'private, no-store',
 'x-content-type-options':'nosniff',
 'referrer-policy':'no-referrer',
 'vary':'Cookie, Cf-Access-Jwt-Assertion'
};
const json=(data,status=200)=>new Response(JSON.stringify(data),{status,headers:HEADERS});
export async function onRequestGet({request,env}){
 const auth=await verifyAcademicAccess(request,env);
 if(!auth.ok)return json({status:auth.error},auth.status);
 const db=env?.ACADEMIC_DB;
 if(!db?.prepare)return json({status:'database_not_configured',databaseReady:false},503);
 const mode=env?.ACADEMIC_AUTH_MODE==='owner_password'?'owner_password':'access';
 try{
  const checked=await readAcademicSchema(db,{authMode:mode});
  return json({
   status:checked.databaseReady?'database_ready':'database_schema_incomplete',
   databaseReady:checked.databaseReady,loginAuthenticated:true,accessAuthenticated:true,
   authMode:mode,
   coreSchemaReady:checked.coreSchemaReady,ownerAuthSchemaReady:checked.ownerAuthSchemaReady,
   missingTables:checked.missingTables,missingColumns:checked.missingColumns,
   sourceProvenanceColumn:!checked.missingColumns.includes('academic_verified_batches.provenance')&&
     !checked.missingTables.includes('academic_verified_batches'),
   schoolConnection:'not_verified',automaticSync:false
  },checked.databaseReady?200:503);
 }catch{
  return json({status:'database_unavailable',databaseReady:false},503);
 }
}
