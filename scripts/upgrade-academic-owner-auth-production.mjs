// M12 forward-only production D1 upgrade. Existing 0001-0009 data is never cleared.
import {spawnSync} from 'node:child_process';
import {appendFileSync} from 'node:fs';
import {findPagesBoundD1,classifyProductionSchema,queryRows} from './bootstrap-academic-d1-production.mjs';
const projectName='dorm-os',binding='ACADEMIC_DB';
const authTables=['academic_auth_identities','academic_auth_sessions','academic_auth_login_attempts'];
const UUID=/^[a-f0-9-]{32,40}$/i,DB_NAME=/^[A-Za-z0-9._-]{1,120}$/;
function report(msg){
 console.log(msg);
 if(process.env.GITHUB_STEP_SUMMARY)appendFileSync(process.env.GITHUB_STEP_SUMMARY,'- '+msg+'\n');
}
function run(args){
 const x=spawnSync('npx',['--yes','wrangler@4',...args],{cwd:process.cwd(),encoding:'utf8',
  env:{...process.env,CI:'true',WRANGLER_SEND_METRICS:'false'},timeout:180000,maxBuffer:3*1024*1024});
 if(x.error||x.status!==0)throw Error('wrangler_execution_failed');
 return x.stdout;
}
function rows(name,sql){return queryRows(run(['d1','execute',name,'--remote','--yes','--json','--command',sql]));}
async function api(path,token){
 const res=await fetch('https://api.cloudflare.com/client/v4/'+path,{headers:{
  authorization:'Bearer '+token,accept:'application/json'},signal:AbortSignal.timeout(25000)});
 if(!res.ok)throw Error('cloudflare_api_http_'+res.status);
 const out=await res.json();
 if(out?.success!==true)throw Error('cloudflare_api_unsuccessful');
 return out.result;
}
export function classifyAuthUpgrade(tables,columns){
 const names=new Set(tables.map(x=>x.name));
 if(classifyProductionSchema(tables,columns)!=='ready')return 'core_not_ready';
 const present=authTables.filter(x=>names.has(x)).length;
 if(present===0)return 'apply';
 if(present===authTables.length)return 'verify';
 return 'partial_auth_schema';
}
export function classifySessionVaultUpgrade(tables,columns){
 if(!Array.isArray(tables)||!Array.isArray(columns))throw Error('invalid_session_schema');
 const present=new Set(tables.map(x=>x?.name));
 if(!present.has('academic_provider_sessions'))return 'apply';
 const fields=new Set(columns.map(x=>x?.name));
 const required=['user_id','provider','ciphertext','nonce','expires_at','updated_at'];
 return required.every(x=>fields.has(x))?'verify':'partial_session_schema';
}
export function classifySecondClassSessionUpgrade(definition){
 if(typeof definition!=='string')return 'unexpected_session_constraint';
 const normalized=definition.toLowerCase().replace(/\s+/g,'');
 if(normalized.includes("check(providerin('ncwu','chaoxing','second_class'))"))
  return 'verify';
 if(normalized.includes("check(providerin('ncwu','chaoxing'))"))
  return 'apply';
 return 'unexpected_session_constraint';
}
export function classifyChaoxingInboxSchema(tables,columns){
 if(!Array.isArray(tables)||!Array.isArray(columns))throw Error('invalid_chaoxing_inbox_schema');
 const present=tables.some(row=>row?.name==='academic_chaoxing_notices');
 if(!present)return 'apply';
 const fields=new Set(columns.map(row=>row?.name));
 const expected=['user_id','notice_id','category','title','course_title',
  'source_course_id','task_external_id','sent_at_utc','due_at_utc',
  'platform_read','observed_at'];
 return expected.every(col=>fields.has(col))?'verify':'partial_inbox_schema';
}
export async function upgrade(){
 if(process.env.GITHUB_REPOSITORY!=='07xh/dorm-os'||process.env.GITHUB_REF!=='refs/heads/main')
  throw Error('repository_or_branch_not_allowed');
 const token=process.env.CLOUDFLARE_API_TOKEN,account=process.env.CLOUDFLARE_ACCOUNT_ID;
 if(!token||!account||!/^[a-f0-9]{32}$/i.test(account))throw Error('cloudflare_config_missing');
 const pages=await api('accounts/'+account+'/pages/projects/'+projectName,token);
 const boundId=findPagesBoundD1(pages);
 if(!UUID.test(boundId))throw Error('invalid_binding');
 const list=await api('accounts/'+account+'/d1/database',token);
 const found=Array.isArray(list)?list:(list?.databases||[]);
 const matches=found.filter(x=>(x.uuid||x.id)===boundId);
 if(matches.length!==1||!DB_NAME.test(matches[0].name||''))
  throw Error('production_db_identity_mismatch');
 const name=matches[0].name;
 report('✅ 已核对 Pages 生产 D1 绑定与数据库 UUID');
 const tableRows=rows(name,"SELECT name FROM sqlite_master WHERE type='table'");
 const cols=rows(name,'PRAGMA table_info(academic_verified_batches)');
 const action=classifyAuthUpgrade(tableRows,cols);
 if(action==='core_not_ready'||action==='partial_auth_schema')
  throw Error('existing_database_requires_manual_review');
 if(action==='apply'){
  run(['d1','execute',name,'--remote','--yes','--json','--file=./migrations/0010_academic_owner_auth.sql']);
  report('✅ 已执行 0010 身份、会话与限流表迁移');
 }else report('✅ 0010 已存在，无重复迁移');
 const next=rows(name,"SELECT name FROM sqlite_master WHERE type='table'");
 if(classifyAuthUpgrade(next,cols)!=='verify')throw Error('auth_tables_missing');
 for(const t of authTables){
  const c=rows(name,'PRAGMA table_info('+t+')');
  if(!Array.isArray(c)||c.length<3)throw Error('auth_columns_missing');
 }
 // Extend the SAME existing academic D1. No new service account or database.
 const sessionColumns=rows(name,'PRAGMA table_info(academic_provider_sessions)');
 const sessionAction=classifySessionVaultUpgrade(next,sessionColumns);
 if(sessionAction==='partial_session_schema')throw Error('partial_session_schema_manual_review');
 if(sessionAction==='apply'){
  run(['d1','execute',name,'--remote','--yes','--json','--file=./migrations/0011_academic_provider_sessions.sql']);
  report('✅ 已执行 0011 校序本人学校会话加密保险库迁移');
 }else report('✅ 0011 已存在，无重复迁移');
 const vaultTables=rows(name,"SELECT name FROM sqlite_master WHERE type='table'");
 const vaultColumns=rows(name,'PRAGMA table_info(academic_provider_sessions)');
 if(classifySessionVaultUpgrade(vaultTables,vaultColumns)!=='verify')
  throw Error('session_vault_columns_missing');
 // Check the actual SQLite CHECK definition, not merely columns. Existing
 // encrypted sessions must survive the one-time forward migration.
 const definition=()=>rows(name,
  "SELECT sql FROM sqlite_master WHERE type='table' AND name='academic_provider_sessions'")[0]?.sql;
 const secondAction=classifySecondClassSessionUpgrade(definition());
 if(secondAction==='unexpected_session_constraint')
  throw Error('unknown_school_session_constraint_manual_review');
 if(secondAction==='apply'){
  run(['d1','execute',name,'--remote','--yes','--json',
   '--file=./migrations/0012_academic_second_class_sessions.sql']);
  report('✅ 已执行 0012 第二课堂会话范围升级（保留旧会话）');
 }else report('✅ 0012 已存在，无重复迁移');
 if(classifySecondClassSessionUpgrade(definition())!=='verify')
  throw Error('second_class_session_schema_missing');
 // Legacy 0013/0014 tables are left intact in databases that already have them.
 // They are no longer an active dependency and are never dropped here.
 // Reuse the SAME production D1 and existing authenticated-session vault.
 // Apply the new inbox metadata table only after 0010-0014 are verified.
 const existingInbox=rows(name,
  "SELECT name FROM sqlite_master WHERE type='table' AND name='academic_chaoxing_notices'");
 const inboxCols=rows(name,'PRAGMA table_info(academic_chaoxing_notices)');
 const inboxAction=classifyChaoxingInboxSchema(existingInbox,inboxCols);
 if(inboxAction==='partial_inbox_schema')
  throw Error('partial_chaoxing_inbox_schema_manual_review');
 if(inboxAction==='apply'){
  run(['d1','execute',name,'--remote','--yes','--json',
   '--file=./migrations/0015_academic_chaoxing_notices.sql']);
  report('✅ 已创建 0015 学习通收件箱元数据表（不保存消息正文）');
 }else report('✅ 0015 学习通收件箱表已存在，无重复迁移');
 const afterInbox=rows(name,"SELECT name FROM sqlite_master WHERE type='table' AND name='academic_chaoxing_notices'");
 const verifiedInboxCols=rows(name,'PRAGMA table_info(academic_chaoxing_notices)');
 if(classifyChaoxingInboxSchema(afterInbox,verifiedInboxCols)!=='verify')
  throw Error('chaoxing_inbox_schema_missing');
 if(rows(name,'PRAGMA foreign_key_check').length)throw Error('foreign_key_violation');
 const check=rows(name,'PRAGMA quick_check');
 if(check.length!==1||check[0].quick_check!=='ok')throw Error('integrity_check_failed');
 report('✅ 0010–0012 表、外键和完整性均通过；现有学业数据未删除');
 report('ℹ️ 学校网页登录功能仍需配置独立加密密钥及真实授权适配器；本迁移不存储凭据，也不会启动学校连接');
}
if(process.argv[1]?.endsWith('upgrade-academic-owner-auth-production.mjs')){
 try{await upgrade();}
 catch(e){
  report('⛔ M12 未就绪：'+String(e?.message||'unknown').replace(/[^A-Za-z0-9_-]/g,'').slice(0,70));
  process.exitCode=1;
 }
}
