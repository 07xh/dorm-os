/* Production-only additive D1 migration for cloud task dependencies.
 * Inspects schema names / PRAGMA only, never student tasks or credentials.
 * Does not touch existing assignment rows; no second database is created.
 */
import {api,remoteRows,localWrangler} from './bootstrap-academic-d1-production.mjs';
import {exactProductionDatabase} from './audit-academic-d1-production.mjs';
import {findPagesBoundD1} from './bootstrap-academic-d1-production.mjs';

const TABLES={
 academic_task_dependencies:['user_id','task_id','prerequisite_id','created_at'],
 academic_task_graph_versions:['user_id','revision','updated_at'],
 academic_task_graph_guards:['id','user_id','task_id','expected_graph_revision','expected_task_revision']
};
const GUARD='trg_academic_task_graph_guard';
const COMPLETION_GUARD='trg_academic_task_done_prerequisites';

// Compare aggregate counts from one consistent SQLite SELECT before and after
// the additive migration. Names are hard-coded, never constructed from user
// input; values and identity of students/tasks never enter CI logs.
const CORE_COUNT_TABLES=[
 'academic_users','academic_connections','academic_courses',
 'academic_assignments','academic_assignment_sources',
 'academic_assignment_events','academic_verified_records'
];
const CORE_COUNT_SQL='SELECT '+CORE_COUNT_TABLES.map(name=>
 '(SELECT COUNT(*) FROM '+name+') AS '+name).join(', ');
function coreCounts(database){
 const rows=remoteRows(database,CORE_COUNT_SQL);
 if(rows.length!==1)throw Error('core_count_snapshot_invalid');
 return rows[0];
}
export function assertCoreCountsUnchanged(before,after){
 if(!before||!after||typeof before!=='object'||typeof after!=='object')
  throw Error('core_count_snapshot_invalid');
 for(const name of CORE_COUNT_TABLES){
  if(!Number.isSafeInteger(before[name])||before[name]<0||
     !Number.isSafeInteger(after[name])||after[name]<0)
    throw Error('core_count_snapshot_invalid');
  if(before[name]!==after[name])
   throw Error('core_data_changed_during_migration_manual_review');
 }
 return true;
}

// D1 Time Travel is supported by the production storage backend. Refuse an
// incremental write unless a pre-migration recovery bookmark is obtainable.
// Do NOT print, persist or upload its value; a UTC time remains in CI logs.
export function validateRecoveryBookmark(result){
 const value=result?.bookmark;
 if(typeof value!=='string'||!/^[a-fA-F0-9-]{32,100}$/.test(value))
  throw Error('d1_time_travel_bookmark_unavailable');
 return true;
}

// A new production schema must never be written by the normal CI-triggered
// Pages deployment. Only an explicitly approved manual workflow dispatch can
// apply the one-time migration. Once the schema is ready, ordinary deployments
// remain unaffected.
export function requireTaskGraphMigrationApproval(eventName,approval){
 if(eventName!=='workflow_dispatch'||approval!=='APPLY-0019')
  throw Error('task_graph_migration_manual_approval_required');
 return true;
}

export function classifyTaskGraphSchema(tableRows,columnRows={},triggerRows=[]){
 if(!Array.isArray(tableRows)||!Array.isArray(triggerRows))throw Error('invalid_schema');
 const names=new Set(tableRows.map(x=>typeof x==='string'?x:x?.name));
 if(names.has(undefined))throw Error('invalid_schema');
 const found=Object.keys(TABLES).filter(name=>names.has(name));
 if(!found.length)return 'missing';
 if(found.length!==Object.keys(TABLES).length)return 'partial';
 for(const [name,columns] of Object.entries(TABLES)){
  if(!Array.isArray(columnRows[name]))return 'partial';
  const existing=new Set(columnRows[name].map(x=>typeof x==='string'?x:x?.name));
  if(columns.some(column=>!existing.has(column)))return 'partial';
 }
 const triggers=new Set(triggerRows.map(x=>typeof x==='string'?x:x?.name));
 return triggers.has(GUARD)&&triggers.has(COMPLETION_GUARD)?'ready':'partial';
}
function inspect(database){
 const tables=remoteRows(database,
  "SELECT name FROM sqlite_master WHERE type='table' AND (name IN ('academic_users','academic_assignments','academic_task_dependencies','academic_task_graph_versions','academic_task_graph_guards'))");
 const existing=new Set(tables.map(x=>x.name));
 if(!existing.has('academic_users')||!existing.has('academic_assignments'))
  throw Error('academic_core_schema_missing');
 const assignments=remoteRows(database,'PRAGMA table_info(academic_assignments)');
 if(!assignments.some(x=>x.name==='revision'))
  throw Error('academic_workspace_migration_missing');
 const columns={};
 for(const name of Object.keys(TABLES)){
  if(existing.has(name))columns[name]=remoteRows(database,'PRAGMA table_info('+name+')');
 }
 const triggers=remoteRows(database,
  "SELECT name FROM sqlite_master WHERE type='trigger' AND name IN ('trg_academic_task_graph_guard','trg_academic_task_done_prerequisites')");
 return classifyTaskGraphSchema(tables,columns,triggers);
}
function integrity(database){
 if(remoteRows(database,'PRAGMA foreign_key_check').length)
  throw Error('foreign_key_check_failed');
 const rows=remoteRows(database,'PRAGMA quick_check');
 if(rows.length!==1||rows[0]?.quick_check!=='ok')
  throw Error('sqlite_integrity_check_failed');
}
export async function ensureTaskGraphSchema(){
 if(process.env.GITHUB_REPOSITORY!=='07xh/dorm-os'||
    process.env.GITHUB_REF!=='refs/heads/main')throw Error('production_only');
 const account=process.env.CLOUDFLARE_ACCOUNT_ID,token=process.env.CLOUDFLARE_API_TOKEN;
 if(!/^[a-f0-9]{32}$/i.test(account||'')||!token)
  throw Error('cloudflare_credentials_unavailable');
 const project=await api('accounts/'+account+'/pages/projects/dorm-os',token);
 const databases=await api('accounts/'+account+'/d1/database',token);
 const db=exactProductionDatabase(project,databases);
 const before=inspect(db);
 if(before==='partial')throw Error('task_graph_schema_partial_manual_review_required');
 integrity(db);
 if(before==='missing'){
  const countsBefore=coreCounts(db);
  // A Time Travel checkpoint is a non-destructive recovery point; exporting
  // the full private database into an ephemeral CI runner is unnecessary.
  const boundId=findPagesBoundD1(project);
  const checkpoint=await api('accounts/'+account+'/d1/database/'+boundId+
   '/time_travel/bookmark',token);
  validateRecoveryBookmark(checkpoint);
  requireTaskGraphMigrationApproval(process.env.GITHUB_EVENT_NAME,
   process.env.XIAOXU_TASK_D1_0019_APPLY);
  const checkpointTime=new Date().toISOString();
  console.log('✅ 已核验 D1 Time Travel 迁移前恢复点，UTC：'+checkpointTime+
   '（不输出恢复书签）');
  // Additive migration creates only namespaced tables and guard triggers.
  // Existing task data, sessions and source attribution are unchanged.
  localWrangler(['d1','execute',db,'--remote','--yes','--json',
   '--file=./migrations/0019_academic_task_dependencies.sql']);
  assertCoreCountsUnchanged(countsBefore,coreCounts(db));
  console.log('✅ 迁移前后核心业务表记录数量一致（不记录数量或个人数据）');
 }
 if(inspect(db)!=='ready')throw Error('task_graph_schema_not_ready');
 integrity(db);
 console.log('✅ 校序生产 D1 云端任务先后关系表就绪（只检查结构，无本人任务内容读取）');
 return {ready:true,migrated:before==='missing'};
}
if(process.argv[1]?.endsWith('/ensure-task-dependency-schema.mjs')){
 try{await ensureTaskGraphSchema();}
 catch(e){
  const code=String(e?.message||'unknown').replace(/[^a-zA-Z0-9_-]/g,'').slice(0,70);
  console.error('⚠️ 校序任务图数据库迁移未通过：'+code);
  process.exitCode=1;
 }
}
