/* Zero-disclosure, idempotent production D1 upgrade for second-class
 * encrypted sessions. The legacy 0011 migration exists in source but was not
 * part of the earlier 0001-0010 production bootstrap/audit.
 *
 * NEVER read or print credentials, Cookie values, session rows, or ciphertext.
 * Preserve legacy NCWU/Chaoxing sessions when upgrading the provider CHECK.
 */
import {spawnSync} from 'node:child_process';
import {findPagesBoundD1,queryRows} from './bootstrap-academic-d1-production.mjs';
const API='https://api.cloudflare.com/client/v4/';
const PROJECT='dorm-os';
const NAME=/^[A-Za-z0-9._-]{1,120}$/;
const UUID=/^[a-f0-9-]{32,40}$/i;
const COLUMNS=['user_id','provider','ciphertext','nonce','expires_at','updated_at'];

export function classifySecondClassVault({tableSql=null,columns=[],nextTableExists=false}={}){
 if(nextTableExists)return 'unsafe_partial_migration';
 if(tableSql===null)return 'missing';
 if(typeof tableSql!=='string'||!Array.isArray(columns))return 'unexpected_schema';
 const found=columns.map(x=>typeof x==='string'?x:x?.name);
 if(found.length!==COLUMNS.length||!COLUMNS.every(x=>found.includes(x)))return 'unexpected_schema';
 const old=/CHECK\s*\(\s*provider\s+IN\s*\(\s*'ncwu'\s*,\s*'chaoxing'\s*\)\s*\)/i;
 const full=/CHECK\s*\(\s*provider\s+IN\s*\(\s*'ncwu'\s*,\s*'chaoxing'\s*,\s*'second_class'\s*\)\s*\)/i;
 if(full.test(tableSql))return 'ready';
 if(old.test(tableSql))return 'legacy_0011';
 return 'unexpected_schema';
}

async function cloudApi(path,token){
 const response=await fetch(API+path,{headers:{authorization:'Bearer '+token,accept:'application/json'},
  signal:AbortSignal.timeout(25000)});
 if(!response.ok)throw Error('cloudflare_project_api_unavailable');
 const payload=await response.json();
 if(payload?.success!==true)throw Error('cloudflare_project_api_unavailable');
 return payload.result;
}
function wrangler(name,command){
 const out=spawnSync('npx',['--yes','wrangler@4','d1','execute',name,
  '--remote','--yes','--json',...command],{
  encoding:'utf8',cwd:process.cwd(),maxBuffer:2*1024*1024,timeout:180000,
  env:{...process.env,CI:'true',WRANGLER_SEND_METRICS:'false'}
 });
 // Do NOT print stderr or stdout: the D1 CLI may include sensitive metadata.
 if(out.error||out.status!==0)throw Error('remote_migration_failed');
 return queryRows(out.stdout);
}
const query=(name,sql)=>wrangler(name,['--command',sql]);
function inspect(name){
 const table=query(name,
  "SELECT name,sql FROM sqlite_master WHERE type='table' AND name IN ('academic_provider_sessions','academic_provider_sessions_next')");
 const present=table.find(x=>x.name==='academic_provider_sessions');
 const next=table.some(x=>x.name==='academic_provider_sessions_next');
 const columns=present?query(name,'PRAGMA table_info(academic_provider_sessions)'):[];
 return classifySecondClassVault({tableSql:present?.sql??null,columns,nextTableExists:next});
}
function integrity(name){
 const foreignKeys=query(name,'PRAGMA foreign_key_check');
 if(foreignKeys.length)throw Error('remote_foreign_keys_invalid');
 const result=query(name,'PRAGMA quick_check');
 if(result.length!==1||result[0].quick_check!=='ok')
  throw Error('remote_sqlite_quick_check_failed');
}
function migrate(name,file){
 if(!/^(0011_academic_provider_sessions|0012_academic_second_class_sessions)\.sql$/.test(file))
  throw Error('unexpected_migration');
 wrangler(name,['--file=./migrations/'+file]);
}

export async function ensureSecondClassSessionSchema(){
 if(process.env.GITHUB_REPOSITORY!=='07xh/dorm-os'||
    process.env.GITHUB_REF!=='refs/heads/main')throw Error('production_only');
 const account=process.env.CLOUDFLARE_ACCOUNT_ID,token=process.env.CLOUDFLARE_API_TOKEN;
 if(!UUID.test(account||'')||!token)throw Error('cloudflare_credentials_unavailable');
 const project=await cloudApi('accounts/'+account+'/pages/projects/'+PROJECT,token);
 const target=findPagesBoundD1(project);
 const listing=await cloudApi('accounts/'+account+'/d1/database',token);
 const rows=Array.isArray(listing)?listing:listing?.databases;
 const matches=(rows||[]).filter(item=>(item.uuid||item.id)===target);
 if(matches.length!==1||!NAME.test(matches[0].name||''))
  throw Error('production_database_identity_unverified');
 const name=matches[0].name;
 const accounts=query(name,"SELECT name FROM sqlite_master WHERE type='table' AND name='academic_users'");
 if(accounts.length!==1)throw Error('academic_core_not_ready');
 const state=inspect(name);
 if(state==='unexpected_schema'||state==='unsafe_partial_migration')
  throw Error('unknown_school_session_schema');
 integrity(name);
 if(state==='missing'){
  migrate(name,'0011_academic_provider_sessions.sql');
  if(inspect(name)!=='legacy_0011')throw Error('session_schema_0011_failed');
 }
 if(state==='missing'||state==='legacy_0011'){
  // The checked-in 0012 migration copies user_id/provider/ciphertext/
  // nonce/expires_at/updated_at without transforming or logging any value.
  migrate(name,'0012_academic_second_class_sessions.sql');
 }
 if(inspect(name)!=='ready')throw Error('school_session_schema_not_ready');
 integrity(name);
 console.log('✅ Pages 生产 D1 第二课堂会话表 0011/0012 就绪（无会话内容读取）');
 return {schemaReady:true,migrated:state!=='ready'};
}
if(process.argv[1]?.endsWith('/ensure-second-class-session-schema.mjs')){
 try{await ensureSecondClassSessionSchema();}
 catch(error){
  console.error('⚠️ 第二课堂生产会话表升级失败：'+
   String(error?.message||'unknown').replace(/[^a-zA-Z0-9_-]/g,'').slice(0,70));
  process.exitCode=1;
 }
}
