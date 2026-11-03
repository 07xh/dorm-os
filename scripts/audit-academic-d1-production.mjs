/* Read-only production D1 schema audit. Only introspects sqlite_master and
 * checked-in PRAGMA table_info names; never reads a student or session row.
 * The exact production Pages D1 UUID must match the Cloudflare D1 listing.
 * This script does NOT migrate, reset, create accounts or mutate anything.
 */
import {spawnSync} from 'node:child_process';
import {appendFileSync} from 'node:fs';
import {findPagesBoundD1,queryRows} from './bootstrap-academic-d1-production.mjs';
import {inspectAcademicPagesProject} from './audit-cloudflare-academic-production.mjs';
import {REQUIRED_COLUMNS,inspectAcademicSchema} from '../lib/academic-schema-readiness.mjs';
const PROJECT='dorm-os';
const UUID=/^[a-f0-9-]{32,40}$/i;
const NAME=/^[A-Za-z0-9._-]{1,120}$/;
const API='https://api.cloudflare.com/client/v4/';
export function exactProductionDatabase(project,listing){
 const uuid=findPagesBoundD1(project);
 if(!UUID.test(uuid))throw Error('invalid_production_binding');
 const entries=Array.isArray(listing)?listing:listing?.databases;
 if(!Array.isArray(entries))throw Error('d1_list_unavailable');
 const matches=entries.filter(item=>(item.uuid||item.id)===uuid);
 if(matches.length!==1||!NAME.test(matches[0].name||''))
  throw Error('production_db_identity_mismatch');
 return matches[0].name;
}
async function api(path,token){
 const r=await fetch(API+path,{headers:{authorization:'Bearer '+token,accept:'application/json'},
  signal:AbortSignal.timeout(25000)});
 if(!r.ok)throw Error('cloudflare_api_unavailable');
 const j=await r.json();
 if(j?.success!==true)throw Error('cloudflare_api_unavailable');
 return j.result;
}
function remoteRows(db,sql){
 const x=spawnSync('npx',['--yes','wrangler@4','d1','execute',db,
  '--remote','--yes','--json','--command',sql],{
  cwd:process.cwd(),encoding:'utf8',timeout:180000,maxBuffer:1024*1024,
  env:{...process.env,CI:'true',WRANGLER_SEND_METRICS:'false'}
 });
 // Never print Wrangler stdout/stderr: they may contain environment details.
 if(x.error||x.status!==0)throw Error('remote_schema_query_unavailable');
 return queryRows(x.stdout);
}
function report(text){
 console.log(text);
 if(process.env.GITHUB_STEP_SUMMARY)
  appendFileSync(process.env.GITHUB_STEP_SUMMARY,'- '+text+'\n');
}
export async function audit(){
 if(process.env.GITHUB_REPOSITORY!=='07xh/dorm-os'||
    process.env.GITHUB_REF!=='refs/heads/main')
  throw Error('repository_or_branch_not_allowed');
 const account=process.env.CLOUDFLARE_ACCOUNT_ID,token=process.env.CLOUDFLARE_API_TOKEN;
 if(!/^[a-f0-9]{32}$/i.test(account||'')||!token)throw Error('cloudflare_credentials_unavailable');
 const project=await api('accounts/'+account+'/pages/projects/'+PROJECT,token);
 const auth=inspectAcademicPagesProject(project);
 if(auth.authMode==='unverified'||!auth.authSettingsReady)
  throw Error('authentication_mode_unverified');
 const name=exactProductionDatabase(project,await api('accounts/'+account+'/d1/database',token));
 report('✅ 已匹配 Pages 当前生产 D1 绑定及 Cloudflare D1 数据库身份');
 const tables=remoteRows(name,"SELECT name FROM sqlite_master WHERE type='table'");
 const known=new Set(tables.map(x=>x?.name));
 const columns={};
 for(const table of Object.keys(REQUIRED_COLUMNS)){
  if(known.has(table))columns[table]=remoteRows(name,'PRAGMA table_info('+table+')');
 }
 const result=inspectAcademicSchema(tables,columns,{authMode:auth.authMode});
 if(!result.databaseReady){
  if(result.missingTables.length)report('⚠️ 缺少迁移表：'+result.missingTables.join('、'));
  if(result.missingColumns.length)report('⚠️ 缺少必要字段：'+result.missingColumns.join('、'));
  throw Error('production_academic_schema_incomplete');
 }
 report('✅ 生产 D1 当前模式必需表和字段完整（0001–0009'+
  (auth.authMode==='owner_password'?'+0010':'' )+'，仅读结构）');
 const foreignKeyIssues=remoteRows(name,'PRAGMA foreign_key_check');
 if(foreignKeyIssues.length)throw Error('production_foreign_keys_inconsistent');
 const health=remoteRows(name,'PRAGMA quick_check');
 if(health.length!==1||health[0].quick_check!=='ok')
  throw Error('production_d1_quick_check_failed');
 report('✅ 生产 D1 外键与 SQLite quick_check 通过（没有读取个人记录）');
 report('ℹ️ 本检查不能证明本人当前已登录，也不能证明学校平台已有第三方授权');
}
if(process.argv[1]?.endsWith('/audit-academic-d1-production.mjs')){
 try{await audit();}
 catch(err){
  const code=String(err?.message||'unknown').replace(/[^a-zA-Z0-9_-]/g,'').slice(0,65);
  report('⚠️ 生产 D1 结构验收未通过或无法查询：'+code);
  process.exitCode=1;
 }
}
