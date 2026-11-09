/* Read-only NextTodo production D1 preflight.
 * This CLI never imports a write-capable helper or issues mutation SQL.
 * No owner tasks, sessions, database identifiers or bookmarks are logged.
 */
import {appendFileSync} from 'node:fs';
import {api,findPagesBoundD1} from './bootstrap-academic-d1-production.mjs';
import {exactProductionDatabase} from './audit-academic-d1-production.mjs';
import {
 inspectTaskGraphSchema,verifyTaskGraphIntegrity,validateRecoveryBookmark
} from './ensure-task-dependency-schema.mjs';

const REPO='07xh/dorm-os';
const BRANCH='refs/heads/main';
function report(line){
 console.log(line);
 if(process.env.GITHUB_STEP_SUMMARY)
  appendFileSync(process.env.GITHUB_STEP_SUMMARY,'- '+line+'\n');
}
export function classifyD1Preflight(schema){
 if(schema==='missing')return 'migration_pending';
 if(schema==='ready')return 'already_ready';
 throw Error('task_graph_schema_partial_manual_review_required');
}
export function requireReadonlyPreflightContext(repo,ref,event){
 if(repo!==REPO||ref!==BRANCH||event!=='workflow_dispatch')
  throw Error('readonly_preflight_main_manual_only');
 return true;
}
export async function auditTaskGraphD1Production(){
 // This workflow is manually dispatched from main only. Unlike deployment,
 // it never invokes the schema migration operation or Wrangler migration files.
 requireReadonlyPreflightContext(process.env.GITHUB_REPOSITORY,
  process.env.GITHUB_REF,process.env.GITHUB_EVENT_NAME);
 const account=process.env.CLOUDFLARE_ACCOUNT_ID;
 const token=process.env.CLOUDFLARE_API_TOKEN;
 if(!/^[a-f0-9]{32}$/i.test(account||'')||!token)
  throw Error('cloudflare_credentials_unavailable');
 const project=await api('accounts/'+account+'/pages/projects/dorm-os',token);
 const boundId=findPagesBoundD1(project);
 const databases=await api('accounts/'+account+'/d1/database',token);
 const db=exactProductionDatabase(project,databases);
 report('✅ 当前 Cloudflare Pages 生产 D1 绑定与数据库列表 UUID 一致');
 const state=classifyD1Preflight(inspectTaskGraphSchema(db));
 verifyTaskGraphIntegrity(db);
 report('✅ D1 foreign_key_check 与 quick_check 通过（只读）');
 // Verify an available recovery point at a reproducible UTC timestamp.
 const checkedAt=new Date().toISOString();
 const result=await api('accounts/'+account+'/d1/database/'+boundId+
  '/time_travel/bookmark?timestamp='+encodeURIComponent(checkedAt),token);
 validateRecoveryBookmark(result);
 report('✅ D1 Time Travel 恢复点查询成功，核验 UTC：'+checkedAt+
  '（不记录或上传书签本身）');
 report(state==='already_ready'?
  '✅ 0019 云端任务图结构就绪：仍需本人真实任务/两端验收':
  'ℹ️ 0019 尚未迁移；此预检未执行任何 SQL 写入或网站发布');
 return {schema:state,restorePointCheckedAt:checkedAt,
  databaseBindingVerified:true,integrityVerified:true};
}
if(process.argv[1]?.endsWith('/audit-task-graph-d1-production.mjs')){
 try{await auditTaskGraphD1Production();}
 catch(e){
  const code=String(e?.message||'unknown').replace(/[^a-zA-Z0-9_-]/g,'').slice(0,75);
  report('⛔ 生产 D1 只读预检未通过：'+code);
  process.exitCode=1;
 }
}
