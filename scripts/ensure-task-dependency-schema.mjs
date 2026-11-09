/* Production-only additive D1 migration for cloud task dependencies.
 * Inspects schema names / PRAGMA only, never student tasks or credentials.
 * Does not touch existing assignment rows; no second database is created.
 */
import {api,remoteRows,localWrangler} from './bootstrap-academic-d1-production.mjs';
import {exactProductionDatabase} from './audit-academic-d1-production.mjs';

const TABLES={
 academic_task_dependencies:['user_id','task_id','prerequisite_id','created_at'],
 academic_task_graph_versions:['user_id','revision','updated_at'],
 academic_task_graph_guards:['id','user_id','task_id','expected_graph_revision','expected_task_revision']
};
const GUARD='trg_academic_task_graph_guard';

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
 return triggers.has(GUARD)?'ready':'partial';
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
  "SELECT name FROM sqlite_master WHERE type='trigger' AND name='trg_academic_task_graph_guard'");
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
  // Additive migration creates only namespaced tables and a guard trigger.
  // Existing task data, sessions and source attribution are unchanged.
  localWrangler(['d1','execute',db,'--remote','--yes','--json',
   '--file=./migrations/0019_academic_task_dependencies.sql']);
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
