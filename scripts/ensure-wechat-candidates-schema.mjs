/* Idempotent, fail-closed production migration of the existing Academic OS D1.
 * Reuses the Cloudflare project binding verifier and remote query helpers.
 * Never reads task rows, device credentials, message contents, or school sessions.
 */
import {api,remoteRows,localWrangler} from './bootstrap-academic-d1-production.mjs';
import {exactProductionDatabase} from './audit-academic-d1-production.mjs';

const TABLES=['academic_wechat_devices','academic_wechat_batches','academic_wechat_candidates'];
const COLUMNS={
 academic_wechat_devices:['id','user_id','token_sha256','revoked_at'],
 academic_wechat_batches:['device_id','batch_id','content_sha256'],
 academic_wechat_candidates:['id','user_id','device_id','candidate_key','review_status','assignment_id']
};

export function classifyWechatSchema(tableNames,columnNames={}){
 if(!Array.isArray(tableNames))throw Error('invalid_schema');
 const existing=new Set(tableNames.map(t=>typeof t==='string'?t:t?.name));
 if(existing.has(undefined))throw Error('invalid_schema');
 const present=TABLES.filter(t=>existing.has(t));
 if(!present.length)return 'missing';
 if(present.length!==TABLES.length)return 'partial';
 for(const name of TABLES){
  const columns=columnNames[name];
  if(!Array.isArray(columns))return 'partial';
  const found=new Set(columns.map(x=>typeof x==='string'?x:x?.name));
  if(COLUMNS[name].some(c=>!found.has(c)))return 'partial';
 }
 return 'ready';
}
const LINK_TABLE='academic_wechat_candidate_links';
const LINK_COLUMNS=['candidate_id','user_id','position','url'];
export function classifyWechatLinksSchema(tableNames,columnNames={}){
 if(!Array.isArray(tableNames))throw Error('invalid_schema');
 const existing=new Set(tableNames.map(t=>typeof t==='string'?t:t?.name));
 if(existing.has(undefined))throw Error('invalid_schema');
 if(!existing.has(LINK_TABLE))return 'missing';
 const columns=columnNames[LINK_TABLE];
 if(!Array.isArray(columns))return 'partial';
 const found=new Set(columns.map(x=>typeof x==='string'?x:x?.name));
 return LINK_COLUMNS.every(c=>found.has(c))?'ready':'partial';
}
function audit(db){
 const names=remoteRows(db,
  "SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'academic_%'");
 const columns={};
 for(const name of [...TABLES,LINK_TABLE]){
  if(names.some(x=>x.name===name))columns[name]=remoteRows(db,'PRAGMA table_info('+name+')');
 }
 return {state:classifyWechatSchema(names,columns),
  linksState:classifyWechatLinksSchema(names,columns),
  hasUsers:names.some(x=>x.name==='academic_users'),
  hasAssignments:names.some(x=>x.name==='academic_assignments')};
}
function integrity(db){
 if(remoteRows(db,'PRAGMA foreign_key_check').length)throw Error('foreign_key_check_failed');
 const checked=remoteRows(db,'PRAGMA quick_check');
 if(checked.length!==1||checked[0]?.quick_check!=='ok')
  throw Error('sqlite_integrity_check_failed');
}
export async function ensureWechatSchema(){
 if(process.env.GITHUB_REPOSITORY!=='07xh/dorm-os'||
    process.env.GITHUB_REF!=='refs/heads/main')throw Error('production_only');
 const account=process.env.CLOUDFLARE_ACCOUNT_ID,token=process.env.CLOUDFLARE_API_TOKEN;
 if(!/^[a-f0-9]{32}$/i.test(account||'')||!token)
  throw Error('cloudflare_credentials_unavailable');
 const project=await api('accounts/'+account+'/pages/projects/dorm-os',token);
 const listing=await api('accounts/'+account+'/d1/database',token);
 const db=exactProductionDatabase(project,listing);
 const before=audit(db);
 if(!before.hasUsers||!before.hasAssignments)
  throw Error('academic_core_schema_missing');
 if(before.state==='partial')
  throw Error('wechat_schema_partial_requires_manual_review');
 integrity(db);
 if(before.state==='missing'){
  // Create only new namespaced tables. No existing rows are read or rewritten.
  localWrangler(['d1','execute',db,'--remote','--yes','--json',
   '--file=./migrations/0017_academic_wechat_candidates.sql']);
 }
 // Incremental V1 upgrade: existing candidates keep working while the links
 // table is created. Only new namespaced objects; nothing is rewritten.
 if(before.linksState!=='ready'){
  localWrangler(['d1','execute',db,'--remote','--yes','--json',
   '--file=./migrations/0018_academic_wechat_candidate_links.sql']);
 }
 const after=audit(db);
 if(after.state!=='ready')throw Error('wechat_schema_not_ready');
 if(after.linksState!=='ready')throw Error('wechat_links_schema_not_ready');
 integrity(db);
 console.log('✅ 校序生产 D1 微信设备和候选任务表就绪（无聊天内容、密钥或账号数据读取）');
 return {ready:true,migrated:before.state==='missing'};
}
if(process.argv[1]?.endsWith('/ensure-wechat-candidates-schema.mjs')){
 try{await ensureWechatSchema();}
 catch(e){
  // Known error code only. Do not print remote CLI output or network secrets.
  const code=String(e?.message||'unknown').replace(/[^a-zA-Z0-9_-]/g,'').slice(0,70);
  console.error('⚠️ 微信候选任务数据库迁移失败：'+code);
  process.exitCode=1;
 }
}
