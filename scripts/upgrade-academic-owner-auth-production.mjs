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
 // M20 is another forward-only table in the SAME owner-scoped production D1.
 // Never replace existing student data or session ciphertext.
 const existingBridge=rows(name,
  "SELECT name FROM sqlite_master WHERE type='table' AND name='academic_secondclass_browser_bridges'");
 if(!existingBridge.length){
  run(['d1','execute',name,'--remote','--yes','--json',
   '--file=./migrations/0013_academic_secondclass_browser_bridge.sql']);
  report('✅ 已创建 0013 本人浏览器二课同步配对表');
 }else report('✅ 0013 浏览器配对表已存在，无重复迁移');
 const bridgeColumns=rows(name,'PRAGMA table_info(academic_secondclass_browser_bridges)');
 const requiredBridge=['user_id','token_hash','created_at','expires_at','last_sync_at','last_status','last_error_code'];
 if(!requiredBridge.every(field=>bridgeColumns.some(column=>column.name===field)))
  throw Error('browser_bridge_schema_missing');
 // Keep browser-reported totals separate from independent school attestations.
 const personalTotalTable='academic_secondclass_personal_totals';
 const foundPersonal=rows(name,
  "SELECT name FROM sqlite_master WHERE type='table' AND name='"+personalTotalTable+"'");
 if(!foundPersonal.length){
  run(['d1','execute',name,'--remote','--yes','--json',
   '--file=./migrations/0014_academic_secondclass_personal_totals.sql']);
  report('✅ 已创建 0014 第二课堂本人浏览器合计表');
 }else report('✅ 0014 个人合计表已存在，无重复迁移');
 const totalColumns=rows(name,'PRAGMA table_info('+personalTotalTable+')');
 if(!['user_id','captured_at','quality_score','volunteer_hours','updated_at','source']
  .every(field=>totalColumns.some(column=>column.name===field)))
  throw Error('personal_totals_schema_missing');
 if(rows(name,'PRAGMA foreign_key_check').length)throw Error('foreign_key_violation');
 const check=rows(name,'PRAGMA quick_check');
 if(check.length!==1||check[0].quick_check!=='ok')throw Error('integrity_check_failed');
 report('✅ 0010–0014 表、外键和完整性均通过；现有学业数据未删除');
 report('ℹ️ 学校网页登录功能仍需配置独立加密密钥及真实授权适配器；本迁移不存储凭据，也不会启动学校连接');
}
if(process.argv[1]?.endsWith('upgrade-academic-owner-auth-production.mjs')){
 try{await upgrade();}
 catch(e){
  report('⛔ M12 未就绪：'+String(e?.message||'unknown').replace(/[^A-Za-z0-9_-]/g,'').slice(0,70));
  process.exitCode=1;
 }
}
