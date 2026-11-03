/* One schema contract for the existing Academic OS D1. Read-only checks only.
 * Never query student rows, passwords, tokens, or login attempts.
 */
export const REQUIRED_CORE_TABLES=Object.freeze([
 'academic_users','academic_connections','academic_courses','academic_assignments',
 'academic_assignment_sources','academic_sync_runs','academic_assignment_events',
 'academic_legacy_links','academic_verified_batches','academic_verified_records',
 'academic_verified_ingests','academic_sync_jobs','academic_reminder_preferences',
 'academic_reminder_receipts','academic_second_class_sync_audit',
 'academic_second_class_official_scores'
]);
export const REQUIRED_OWNER_TABLES=Object.freeze([
 'academic_auth_identities','academic_auth_sessions','academic_auth_login_attempts'
]);
export const REQUIRED_COLUMNS=Object.freeze({
 academic_connections:Object.freeze(['id','user_id','provider','mode','status','last_success_at']),
 academic_verified_batches:Object.freeze(['user_id','kind','source','batch_id','last_success_at','provenance']),
 academic_verified_records:Object.freeze(['user_id','kind','source','external_id','term','payload','batch_id']),
 academic_second_class_official_scores:Object.freeze(['user_id','batch_id','payload']),
 academic_auth_identities:Object.freeze(['id','user_id','login_identifier']),
 academic_auth_sessions:Object.freeze(['id','identity_id','token_hash','expires_at','revoked_at'])
});
const names=(rows)=>{
 if(!Array.isArray(rows))throw Error('invalid_schema_rows');
 const out=rows.map(r=>typeof r==='string'?r:r?.name);
 if(out.some(x=>typeof x!=='string'||!/^[A-Za-z_][A-Za-z0-9_]*$/.test(x)))
  throw Error('invalid_schema_rows');
 return new Set(out);
};
/* columns is a fixed schema-name -> PRAGMA table_info result map,
 * NOT an externally supplied table name or arbitrary SQL string. */
export function inspectAcademicSchema(tables,columns,{authMode='owner_password'}={}){
 if(!['owner_password','access'].includes(authMode)||
    columns===null||typeof columns!=='object'||Array.isArray(columns))
  throw Error('invalid_schema_input');
 const found=names(tables);
 const required=authMode==='owner_password'
   ?[...REQUIRED_CORE_TABLES,...REQUIRED_OWNER_TABLES]:REQUIRED_CORE_TABLES;
 const missingTables=required.filter(table=>!found.has(table));
 const missingColumns=[];
 for(const [table,requiredFields] of Object.entries(REQUIRED_COLUMNS)){
  if(!found.has(table)||authMode==='access'&&REQUIRED_OWNER_TABLES.includes(table))continue;
  const present=names(columns[table]);
  for(const field of requiredFields)if(!present.has(field))
   missingColumns.push(table+'.'+field);
 }
 return {
  status:missingTables.length===0&&missingColumns.length===0?'ready':'incomplete',
  databaseReady:missingTables.length===0&&missingColumns.length===0,
  authMode,missingTables,missingColumns,
  coreSchemaReady:REQUIRED_CORE_TABLES.every(table=>found.has(table))&&
   missingColumns.every(x=>REQUIRED_OWNER_TABLES.some(t=>x.startsWith(t+'.'))),
  ownerAuthSchemaReady:REQUIRED_OWNER_TABLES.every(table=>found.has(table))&&
   missingColumns.every(x=>!REQUIRED_OWNER_TABLES.some(t=>x.startsWith(t+'.')))
 };
}
export async function readAcademicSchema(db,{authMode='owner_password'}={}){
 if(!db?.prepare)throw Error('database_unavailable');
 const rows=await db.prepare("SELECT name FROM sqlite_master WHERE type='table'").all();
 if(!rows||!Array.isArray(rows.results))throw Error('invalid_schema_rows');
 const available=names(rows.results);
 const columns={};
 for(const table of Object.keys(REQUIRED_COLUMNS)){
  if(!available.has(table))continue;
  // Name comes only from checked-in, static REQUIRED_COLUMNS.
  const fields=await db.prepare('PRAGMA table_info('+table+')').all();
  if(!fields||!Array.isArray(fields.results))throw Error('invalid_schema_rows');
  columns[table]=fields.results;
 }
 return inspectAcademicSchema(rows.results,columns,{authMode});
}
