/* Configure the existing Cloudflare Pages SECOND CLASS session vault once.
 * Uses the deployment's existing Cloudflare credentials and project metadata.
 * NEVER rotate an existing key, publish a value, or modify login/session rows.
 */
import {randomBytes} from 'node:crypto';
import {spawnSync} from 'node:child_process';
const PROJECT='dorm-os';
const API='https://api.cloudflare.com/client/v4/';

export function needsSchoolSessionKey(project){
 if(!project||project.name!==PROJECT)throw Error('unexpected_pages_project');
 const production=project.deployment_configs?.production;
 const vars=production?.env_vars;
 if(!vars||typeof vars!=='object'||Array.isArray(vars))
  throw Error('production_environment_unavailable');
 // The secret's presence is sufficient for safe idempotence. Never overwrite
 // existing ciphertext keys, including when the underlying value is hidden.
 return !Object.prototype.hasOwnProperty.call(vars,'SCHOOL_SESSION_KEY');
}

export function generateSchoolSessionKey(){
 return randomBytes(32).toString('base64url'); // exactly 32 AES bytes, 43 chars
}

async function readPagesProject(account,token){
 const res=await fetch(API+'accounts/'+account+'/pages/projects/'+PROJECT,{
  headers:{authorization:'Bearer '+token,accept:'application/json'},
  signal:AbortSignal.timeout(20000)
 });
 if(!res.ok)throw Error('cloudflare_project_read_unavailable');
 const body=await res.json();
 if(body?.success!==true||!body.result)throw Error('cloudflare_project_read_failed');
 return body.result;
}

export async function ensureSchoolSessionKey(){
 if(process.env.GITHUB_REPOSITORY!=='07xh/dorm-os'||
    process.env.GITHUB_REF!=='refs/heads/main')
  throw Error('production_only');
 const account=process.env.CLOUDFLARE_ACCOUNT_ID,token=process.env.CLOUDFLARE_API_TOKEN;
 if(!/^[a-f0-9]{32}$/i.test(account||'')||!token)
  throw Error('cloudflare_credentials_unavailable');
 const before=await readPagesProject(account,token);
 if(!needsSchoolSessionKey(before)){
  console.log('Second-class session vault secret: existing production binding preserved');
  return {created:false};
 }
 // Secrets are never passed as CLI command args, shell source, logs or Git
 // objects. Wrangler accepts the value on stdin without printing it.
 const value=generateSchoolSessionKey();
 const out=spawnSync('npx',['--yes','wrangler@4','pages','secret','put',
  'SCHOOL_SESSION_KEY','--project-name='+PROJECT,'--env=production'],{
  encoding:'utf8',input:value+'\n',
  env:{...process.env,CI:'true',WRANGLER_SEND_METRICS:'false'},
  timeout:120000,maxBuffer:256*1024
 });
 if(out.error||out.status!==0)throw Error('cloudflare_secret_write_unavailable');
 // Confirm only the NAME, not the secret value; allow metadata propagation.
 for(let attempt=0;attempt<4;attempt++){
  const current=await readPagesProject(account,token);
  if(!needsSchoolSessionKey(current)){
   console.log('Second-class session vault secret: new production binding confirmed');
   return {created:true};
  }
  await new Promise(resolve=>setTimeout(resolve,1500));
 }
 throw Error('cloudflare_secret_readback_unavailable');
}

if(process.argv[1]?.endsWith('/ensure-school-session-key.mjs')){
 try{await ensureSchoolSessionKey();}
 catch(e){
  // No upstream stdout/stderr or sensitive values appear in CI logs.
  const code=String(e?.message||'unknown').replace(/[^a-zA-Z0-9_-]/g,'').slice(0,70);
  console.error('Second-class production session vault setup failed: '+code);
  process.exitCode=1;
 }
}
