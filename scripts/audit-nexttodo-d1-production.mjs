/* Manual, read-only production D1 preflight for the NextTodo 0019 migration.
 * This is deliberately independent of the not-yet-merged migration branch.
 * No SQL writes, Pages deploy, task contents, or credential logging.
 */
import {appendFileSync} from 'node:fs';
import {api,remoteRows,findPagesBoundD1} from './bootstrap-academic-d1-production.mjs';
import {exactProductionDatabase} from './audit-academic-d1-production.mjs';

const REPO='07xh/dorm-os',MAIN='refs/heads/main';
const FIELDS=Object.freeze({
 academic_task_dependencies:['user_id','task_id','prerequisite_id','created_at'],
 academic_task_graph_versions:['user_id','revision','updated_at'],
 academic_task_graph_guards:['id','user_id','task_id','expected_graph_revision','expected_task_revision']
});
const REQUIRED_TRIGGERS=[
 'trg_academic_task_graph_guard','trg_academic_task_done_prerequisites'
];
function report(message){
 console.log(message);
 if(process.env.GITHUB_STEP_SUMMARY)
  appendFileSync(process.env.GITHUB_STEP_SUMMARY,'- '+message+'\n');
}
export function requireManualMain(repo,ref,event){
 if(repo!==REPO||ref!==MAIN||event!=='workflow_dispatch')
  throw Error('readonly_preflight_main_manual_only');
 return true;
}
export function classifyTaskDependencySchema(tables,columns={},triggers=[]){
 if(!Array.isArray(tables)||!Array.isArray(triggers))throw Error('invalid_schema_response');
 const names=new Set(tables.map(x=>typeof x==='string'?x:x?.name));
 if(names.has(undefined))throw Error('invalid_schema_response');
 const found=Object.keys(FIELDS).filter(name=>names.has(name));
 if(found.length===0)return 'migration_pending';
 if(found.length!==Object.keys(FIELDS).length)return 'partial_schema_manual_review';
 for(const [table,required] of Object.entries(FIELDS)){
  if(!Array.isArray(columns[table]))return 'partial_schema_manual_review';
  const got=new Set(columns[table].map(x=>typeof x==='string'?x:x?.name));
  if(required.some(col=>!got.has(col)))return 'partial_schema_manual_review';
 }
 const tr=new Set(triggers.map(x=>typeof x==='string'?x:x?.name));
 return REQUIRED_TRIGGERS.every(t=>tr.has(t))?
  'already_ready':'partial_schema_manual_review';
}
export function validateD1Bookmark(value){
 const bookmark=value?.bookmark;
 if(typeof bookmark!=='string'||!/^[a-f0-9-]{32,100}$/i.test(bookmark))
  throw Error('d1_time_travel_bookmark_unavailable');
 return true;
}
export async function auditNextTodoProduction(){
 requireManualMain(process.env.GITHUB_REPOSITORY,process.env.GITHUB_REF,
  process.env.GITHUB_EVENT_NAME);
 const account=process.env.CLOUDFLARE_ACCOUNT_ID,token=process.env.CLOUDFLARE_API_TOKEN;
 if(!/^[0-9a-f]{32}$/i.test(account||'')||!token)
  throw Error('cloudflare_credentials_unavailable');
 const project=await api('accounts/'+account+'/pages/projects/dorm-os',token);
 const boundId=findPagesBoundD1(project);
 const listing=await api('accounts/'+account+'/d1/database',token);
 const db=exactProductionDatabase(project,listing);
 report('✅ Pages 生产 ACADEMIC_DB 与 D1 UUID 一致');
 const tables=remoteRows(db,
  "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('academic_users','academic_assignments','academic_task_dependencies','academic_task_graph_versions','academic_task_graph_guards')");
 const names=new Set(tables.map(t=>t.name));
 if(!names.has('academic_users')||!names.has('academic_assignments'))
  throw Error('academic_core_schema_missing');
 const assignmentColumns=remoteRows(db,'PRAGMA table_info(academic_assignments)');
 if(!assignmentColumns.some(x=>x.name==='revision'))
  throw Error('academic_workspace_migration_missing');
 const columns={};
 for(const table of Object.keys(FIELDS)){
  if(names.has(table))columns[table]=remoteRows(db,'PRAGMA table_info('+table+')');
 }
 const triggers=remoteRows(db,
  "SELECT name FROM sqlite_master WHERE type='trigger' AND name IN ('trg_academic_task_graph_guard','trg_academic_task_done_prerequisites')");
 const state=classifyTaskDependencySchema(tables,columns,triggers);
 if(state==='partial_schema_manual_review')
  throw Error('task_graph_schema_partial_manual_review_required');
 if(remoteRows(db,'PRAGMA foreign_key_check').length)
  throw Error('foreign_key_check_failed');
 const health=remoteRows(db,'PRAGMA quick_check');
 if(health.length!==1||health[0]?.quick_check!=='ok')
  throw Error('sqlite_integrity_check_failed');
 report('✅ D1 外键和 SQLite quick_check 均通过');
 // Query a reproducible point at/before this UTC timestamp. Never emit the
 // returned bookmark into logs or artifacts.
 const checkedAt=new Date().toISOString();
 const bookmark=await api('accounts/'+account+'/d1/database/'+boundId+
  '/time_travel/bookmark?timestamp='+encodeURIComponent(checkedAt),token);
 validateD1Bookmark(bookmark);
 report('✅ Time Travel 恢复点已取得，核验 UTC：'+checkedAt+'（不输出书签）');
 report(state==='already_ready'?
  '✅ 0019 已具备预期表和触发器；仍需本人真实任务验收':
  'ℹ️ 0019 尚未迁移；此流程未执行任何写库或网站发布');
 return {status:state,checkedAt};
}
if(process.argv[1]?.endsWith('/audit-nexttodo-d1-production.mjs')){
 try{await auditNextTodoProduction();}
 catch(e){
  const category=String(e?.message||'unknown').replace(/[^a-zA-Z0-9_-]/g,'').slice(0,72);
  report('⛔ 只读预检未通过：'+category);
  process.exitCode=1;
 }
}
