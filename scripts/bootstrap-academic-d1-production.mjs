/* One-shot GitHub Actions bootstrap for a newly created, empty production D1.
 * NO secrets, school sessions or private user rows are ever printed/stored.
 * Never modify a database with existing non-internal tables, even when partial.
 * Retry safe after full success; a partial migration requires manual review.
 */
import {spawnSync} from 'node:child_process';
import {appendFileSync} from 'node:fs';

const PAGES_PROJECT='dorm-os';
const BINDING='ACADEMIC_DB';
const SQL_MIGRATIONS=[
 '0001_academic_core.sql','0002_academic_legacy_links.sql',
 '0003_academic_verified_snapshots.sql','0004_academic_source_course.sql',
 '0005_academic_workspace.sql','0006_academic_scheduler.sql',
 '0007_academic_reminder_inbox.sql','0008_academic_second_class_source.sql',
 '0009_ncwu_second_class_official_scores.sql'
];
const REQUIRED_TABLES=[
 'academic_users','academic_connections','academic_courses',
 'academic_assignments','academic_assignment_sources','academic_sync_runs',
 'academic_assignment_events','academic_legacy_links',
 'academic_verified_batches','academic_verified_records','academic_verified_ingests',
 'academic_sync_jobs','academic_reminder_preferences','academic_reminder_receipts',
 'academic_second_class_sync_audit','academic_second_class_official_scores'
];
const UUID=/^[a-f0-9-]{32,40}$/i,NAME=/^[A-Za-z0-9._-]{1,120}$/;
const own=(o,k)=>o&&typeof o==='object'&&Object.prototype.hasOwnProperty.call(o,k);

export function findPagesBoundD1(project){
 if(!project||project.name!==PAGES_PROJECT)throw Error('unexpected_pages_project');
 const item=project.deployment_configs?.production?.d1_databases?.[BINDING];
 const id=item?.id;
 if(typeof id!=='string'||!UUID.test(id))throw Error('production_d1_binding_missing');
 return id;
}
export function classifyProductionSchema(tables,columns){
 if(!Array.isArray(tables)||!Array.isArray(columns))throw Error('invalid_schema_response');
 const names=tables.map(x=>typeof x==='string'?x:x?.name);
 if(names.some(n=>typeof n!=='string'||n.length>140))throw Error('invalid_schema_response');
 const userTables=names.filter(x=>!x.startsWith('sqlite_')&&!x.startsWith('_cf_'));
 if(userTables.length===0)return 'empty';
 const visible=new Set(userTables);
 const columnNames=new Set(columns.map(x=>typeof x==='string'?x:x?.name));
 if(REQUIRED_TABLES.every(x=>visible.has(x))&&columnNames.has('provenance'))return 'ready';
 return 'partial_existing_schema';
}
function readJson(text){
 const trimmed=text.trim();
 try{return JSON.parse(trimmed);}
 catch{throw Error('wrangler_output_not_json');}
}
export function queryRows(raw){
 const parsed=typeof raw==='string'?readJson(raw):raw;
 const blocks=Array.isArray(parsed)?parsed:[parsed];
 if(blocks.length===0||blocks.some(x=>x?.success===false))throw Error('d1_read_failed');
 const all=blocks.flatMap(x=>Array.isArray(x?.results)?x.results:
  Array.isArray(x?.result?.results)?x.result.results:[]);
 if(!Array.isArray(all))throw Error('d1_read_failed');
 return all;
}
function localWrangler(args){
 const out=spawnSync('npx',['--yes','wrangler@4',...args],{
  cwd:process.cwd(),encoding:'utf8',maxBuffer:3*1024*1024,timeout:180000,
  env:{...process.env,CI:'true',WRANGLER_SEND_METRICS:'false'}
 });
 if(out.error||out.status!==0)throw Error('wrangler_'+args[1]+'_failed');
 return out.stdout;
}
function remoteRows(name,command){
 return queryRows(localWrangler([
  'd1','execute',name,'--remote','--yes','--json','--command',command
 ]));
}
async function api(path,token){
 const res=await fetch('https://api.cloudflare.com/client/v4/'+path,{
  headers:{authorization:'Bearer '+token,accept:'application/json'},
  signal:AbortSignal.timeout(25000)
 });
 if(!res.ok)throw Error('cloudflare_api_http_'+res.status);
 const body=await res.json();
 if(body.success!==true)throw Error('cloudflare_api_unsuccessful');
 return body.result;
}
function report(line){
 const text='- '+line+'\n';
 console.log(text.trim());
 if(process.env.GITHUB_STEP_SUMMARY)appendFileSync(process.env.GITHUB_STEP_SUMMARY,text);
}
async function bootstrap(){
 if(process.env.GITHUB_REPOSITORY!=='07xh/dorm-os'||
  process.env.GITHUB_REF!=='refs/heads/main')throw Error('repository_or_branch_not_allowed');
 const account=process.env.CLOUDFLARE_ACCOUNT_ID;
 const token=process.env.CLOUDFLARE_API_TOKEN;
 if(!token||!account||!/^[a-f0-9]{32}$/i.test(account))throw Error('cloudflare_token_not_configured');
 const project=await api('accounts/'+account+'/pages/projects/'+PAGES_PROJECT,token);
 const boundId=findPagesBoundD1(project);
 report('✅ 已确认 Pages 生产 ACADEMIC_DB 绑定存在（不输出数据库 ID）');
 const list=await api('accounts/'+account+'/d1/database',token);
 const rows=Array.isArray(list)?list:Array.isArray(list?.databases)?list.databases:null;
 if(!rows)throw Error('d1_list_unavailable');
 const matches=rows.filter(x=>(x.uuid||x.id)===boundId);
 if(matches.length!==1||!NAME.test(matches[0].name||''))throw Error('bound_database_not_verified');
 const name=matches[0].name;
 report('✅ D1 已与 Pages 生产绑定 ID 严格匹配（不输出数据库名称）');
 const tables=remoteRows(name,
  "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name");
 const columns=remoteRows(name,'PRAGMA table_info(academic_verified_batches)');
 const status=classifyProductionSchema(tables,columns);
 if(status==='ready'){
  report('✅ 生产 D1 已具备 0001–0009 必需表和 provenance 字段；未重复执行迁移');
  return;
 }
 if(status!=='empty'){
  report('⛔ 生产库存在既有表或部分迁移，已停止，无任何 SQL 写入；必须先备份并人工核对');
  throw Error('existing_schema_requires_manual_review');
 }
 report('✅ 远程生产库目前无业务表，允许首次初始化 0001–0009');
 for(const migration of SQL_MIGRATIONS){
  // Restrict all file names to the nine checked-in SQL files.
  localWrangler([
   'd1','execute',name,'--remote','--yes','--json',
   '--file=./migrations/'+migration
  ]);
  report('✅ 已执行 '+migration.slice(0,4)+' 数据库迁移');
 }
 const nextTables=remoteRows(name,
  "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name");
 const nextColumns=remoteRows(name,'PRAGMA table_info(academic_verified_batches)');
 if(classifyProductionSchema(nextTables,nextColumns)!=='ready')
  throw Error('post_migration_schema_incomplete');
 const fk=remoteRows(name,'PRAGMA foreign_key_check');
 if(fk.length)throw Error('post_migration_foreign_key_check_failed');
 report('✅ 生产 D1 必需表、provenance 与外键约束均已通过检查');
 report('ℹ️ 仍须单独验收 Cloudflare Access 本人策略和学校授权读取；Cron 继续关闭');
}
const invoked=process.argv[1]?.endsWith('bootstrap-academic-d1-production.mjs');
if(invoked){
 if(process.env.GITHUB_STEP_SUMMARY)appendFileSync(process.env.GITHUB_STEP_SUMMARY,
  '### 校序 · 生产 D1 一次性初始化\n');
 try{await bootstrap();}
 catch(e){
  const code=String(e?.message||'unknown').replace(/[^a-zA-Z0-9_-]/g,'').slice(0,75);
  report('⚠️ 初始化未完成：'+code+'。未验证成功前不得宣称数据库已就绪');
  process.exitCode=1;
 }
}
