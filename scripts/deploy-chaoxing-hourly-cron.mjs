/* Provision hourly Cloudflare Cron Worker using EXISTING deployment credentials.
 * We NEVER copy the Pages SCHOOL_SESSION_KEY into the Cron Worker. The Worker
 * only knows a random bearer, whose SHA-256 digest is stored in existing D1.
 * The bearer is passed to Wrangler via stdin, not command arguments/logs.
 */
import {randomBytes,createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {findPagesBoundD1,queryRows} from './bootstrap-academic-d1-production.mjs';

const API='https://api.cloudflare.com/client/v4/';
const CONFIG='workers/academic-cron/wrangler.jsonc';
const UUID=/^[a-f0-9-]{32,40}$/i,NAME=/^[a-zA-Z0-9._-]{1,120}$/;

async function api(url,token){
 const response=await fetch(API+url,{
  headers:{authorization:'Bearer '+token,accept:'application/json'},
  signal:AbortSignal.timeout(25000)});
 if(!response.ok)throw Error('cloudflare_api_unavailable');
 const data=await response.json();
 if(data?.success!==true)throw Error('cloudflare_api_unavailable');
 return data.result;
}
/**
 * Only diagnostic categories leave this process; Wrangler output may contain
 * Cloudflare account identifiers, URLs or deployment metadata.
 */
export function classifySafeWranglerFailure(stdout='',stderr=''){
 const message=(String(stderr)+'\n'+String(stdout)).slice(0,30000);
 if(/(?:10000|10001|not authorized|insufficient permissions|permission denied|authentication error|forbidden|access denied)/i.test(message))
   return 'cloudflare_workers_permission_or_auth';
 if(/(?:429|rate.?limit|too many requests)/i.test(message))
   return 'cloudflare_rate_limited';
 if(/(?:workers_dev|workers\.dev|subdomain|cron trigger|custom domain)/i.test(message))
   return 'cloudflare_worker_account_configuration';
 if(/(?:could not resolve|entry.?point|not found|no such file|cannot find module|main field)/i.test(message))
   return 'worker_build_or_config';
 if(/(?:timeout|timed out|network|connection refused|econnreset|etimedout)/i.test(message))
   return 'temporary_deploy_network_failure';
 return 'uncategorized';
}
function wrangler(args,input){
 const out=spawnSync('npx',['--yes','wrangler@4',...args],{
  cwd:process.cwd(),encoding:'utf8',timeout:180000,maxBuffer:3*1024*1024,
  ...(input===undefined?{}:{input:input+'\n'}),
  env:{...process.env,CI:'true',WRANGLER_SEND_METRICS:'false'}
 });
 // stdout/stderr can contain Cloudflare internals. Do not echo them in CI.
 if(out.error||out.status!==0){
  // Stage name alone is safe; do not log shell stdout/stderr, database names,
  // Cloudflare IDs, secret values, response bodies or URLs.
  const stage=args[0]==='deploy'?'worker_deploy':
   args[0]==='secret'?'worker_secret':
   args.some(v=>String(v).startsWith('--file='))?'d1_migration':'d1_query';
  const category=classifySafeWranglerFailure(out.stdout,out.stderr);
  throw Error('wrangler_'+stage+'_failed_'+category);
 }
 return out.stdout;
}
function rows(dbName,sql){
 return queryRows(wrangler(['d1','execute',dbName,'--remote','--yes','--json','--command',sql]));
}
export function cronHash(token){
 if(!/^[A-Za-z0-9_-]{43}$/.test(token||''))throw Error('invalid_cron_token');
 return createHash('sha256').update(token,'utf8').digest('hex');
}
export async function deployHourlyChaoxingCron(){
 if(process.env.GITHUB_REPOSITORY!=='07xh/dorm-os'||
    process.env.GITHUB_REF!=='refs/heads/main')throw Error('production_only');
 const account=process.env.CLOUDFLARE_ACCOUNT_ID,token=process.env.CLOUDFLARE_API_TOKEN;
 if(!UUID.test(account||'')||!token)throw Error('cloudflare_credentials_missing');
 const project=await api('accounts/'+account+'/pages/projects/dorm-os',token);
 const uuid=findPagesBoundD1(project);
 const databases=await api('accounts/'+account+'/d1/database',token);
 const list=Array.isArray(databases)?databases:databases?.databases;
 const matched=(list||[]).filter(x=>(x.uuid||x.id)===uuid);
 if(matched.length!==1||!NAME.test(matched[0]?.name||''))
  throw Error('bound_database_not_verified');
 const name=matched[0].name;
 if(rows(name,"SELECT name FROM sqlite_master WHERE type='table' AND name='academic_sync_jobs'").length!==1)
  throw Error('scheduler_schema_missing');
 // 0016 is additive and idempotent. Existing school sessions are untouched.
 wrangler(['d1','execute',name,'--remote','--yes','--json',
  '--file=./migrations/0016_academic_chaoxing_cron.sql']);
 if(rows(name,"SELECT name FROM sqlite_master WHERE type='table' AND name='academic_cron_identity'").length!==1)
  throw Error('cron_schema_not_created');
 // Deliver the scheduled-only Worker before activating the D1 digest.
 wrangler(['deploy','--config',CONFIG]);
 const secret=randomBytes(32).toString('base64url');
 const digest=cronHash(secret);
 wrangler(['secret','put','CRON_SYNC_TOKEN','--config',CONFIG],secret);
 const at=new Date().toISOString();
 if(!/^[\dTZ:.-]+$/.test(at))throw Error('clock_invalid');
 rows(name,"INSERT INTO academic_cron_identity(name,token_sha256,configured_at) "+
  "VALUES ('chaoxing-hourly','"+digest+"','"+at+"') "+
  "ON CONFLICT(name) DO UPDATE SET token_sha256=excluded.token_sha256,"+
  "configured_at=excluded.configured_at");
 const check=rows(name,"SELECT token_sha256 FROM academic_cron_identity WHERE name='chaoxing-hourly'");
 if(check.length!==1||check[0].token_sha256!==digest)
  throw Error('cron_token_digest_verification_failed');
 // No bearer, hash, D1 ID, user ID or session contents in Actions summary.
 console.log('✅ Chaoxing hourly Cron Worker deployed; existing D1 owner-gated endpoint authenticated');
 console.log('ℹ️ Auto-sync remains disabled until each owner explicitly enables it');
}
if(process.argv[1]?.endsWith('/deploy-chaoxing-hourly-cron.mjs')){
 try{await deployHourlyChaoxingCron();}
 catch(error){
  console.error('⛔ Chaoxing Cron deployment failed: '+
   String(error?.message||'unknown').replace(/[^A-Za-z0-9_-]/g,'').slice(0,65));
  process.exitCode=1;
 }
}
