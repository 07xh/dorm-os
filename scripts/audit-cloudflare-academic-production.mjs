/* Read-only Cloudflare Pages Academic OS configuration audit.
 * Never log, serialize, or persist the token or environment variable values.
 * The production authorization gate selects ONE mode:
 *   owner_password -> signed owner session backed by D1
 *   access         -> verified Cloudflare Access JWT (legacy/default)
 */
import {appendFileSync} from 'node:fs';
const PROJECT='dorm-os';
const required=['d1Bound','authSettingsReady','productionBranchMain','pagesFunctionsPresent'];
const isObject=x=>x!==null&&typeof x==='object'&&!Array.isArray(x);
const own=(obj,key)=>isObject(obj)&&Object.prototype.hasOwnProperty.call(obj,key);

export function inspectAcademicPagesProject(project){
 if(!isObject(project)||project.name!==PROJECT)throw Error('unexpected_pages_project');
 const production=project.deployment_configs?.production||{};
 const vars=production.env_vars||{};
 const d1=production.d1_databases||{};
 // Values are used only to select a fixed, non-secret mode label.
 const rawMode=vars.ACADEMIC_AUTH_MODE?.value;
 const authMode=!own(vars,'ACADEMIC_AUTH_MODE')?'access':
   rawMode==='owner_password'?'owner_password':
   rawMode==='access'?'access':'unverified';
 const ownerSecretConfigured=own(vars,'OWNER_LOGIN_SECRET');
 const accessIssuerConfigured=own(vars,'ACCESS_TEAM_DOMAIN');
 const accessAudienceConfigured=own(vars,'ACCESS_AUD');
 const authSettingsReady=authMode==='owner_password'?ownerSecretConfigured:
   authMode==='access'?accessIssuerConfigured&&accessAudienceConfigured:false;
 return {
  d1Bound:own(d1,'ACADEMIC_DB')&&typeof d1.ACADEMIC_DB?.id==='string'&&d1.ACADEMIC_DB.id.length>0,
  authMode,authSettingsReady,ownerSecretConfigured,accessIssuerConfigured,
  accessAudienceConfigured,
  productionBranchMain:project.production_branch==='main',
  pagesFunctionsPresent:project.uses_functions===true
 };
}
const invoked=process.argv[1]&&process.argv[1].endsWith('/audit-cloudflare-academic-production.mjs');
if(invoked){
 const token=process.env.CLOUDFLARE_API_TOKEN;
 const account=process.env.CLOUDFLARE_ACCOUNT_ID;
 const report=[];
 try{
  if(!token||!account||!/^[a-f0-9]{32}$/i.test(account))
   throw Error('cloudflare_credentials_unavailable');
  const res=await fetch('https://api.cloudflare.com/client/v4/accounts/'+account+
   '/pages/projects/'+PROJECT,{
    headers:{authorization:'Bearer '+token,accept:'application/json'},
    signal:AbortSignal.timeout(20000)
   });
  if(!res.ok)throw Error('cloudflare_project_read_http_'+res.status);
  const body=await res.json();
  if(body.success!==true)throw Error('cloudflare_project_read_failed');
  const inspection=inspectAcademicPagesProject(body.result);
  report.push('### 校序生产 Cloudflare 配置检查（只读）');
  const names={owner_password:'校序单人口令',access:'Cloudflare Access',
   unverified:'无法从安全元数据确认'};
  report.push('- ℹ️ 当前认证模式：'+names[inspection.authMode]);
  const labels={
   d1Bound:'Pages 生产 ACADEMIC_DB 已绑定',
   authSettingsReady:'当前认证模式所需的环境变量已配置（不检查秘密值）',
   productionBranchMain:'Pages 生产分支为 main',
   pagesFunctionsPresent:'Pages Functions 已启用'
  };
  for(const key of required)report.push('- '+(inspection[key]?'✅':'⚠️')+' '+labels[key]);
  if(inspection.authMode==='owner_password')
   report.push('- ℹ️ 单人口令模式不要求 ACCESS_TEAM_DOMAIN 或 ACCESS_AUD');
  else if(inspection.authMode==='access')
   report.push('- ℹ️ Access Issuer '+(inspection.accessIssuerConfigured?'已配置':'缺少')+
    '；Access Audience '+(inspection.accessAudienceConfigured?'已配置':'缺少'));
  else
   report.push('- ⚠️ 无法确认 ACADEMIC_AUTH_MODE 的非敏感枚举值；需人工核对模式');
  report.push('- ℹ️ D1 实际迁移、有效登录会话和 Zero Trust 策略：**未在本脚本内检查**');
  report.push('- ℹ️ 学校真实账号授权和无人值守同步：**尚未验证**');
  if(!required.every(key=>inspection[key]))process.exitCode=1;
 }catch(err){
  report.push('### 校序生产 Cloudflare 配置检查（只读）');
  report.push('- ⚠️ 无法核验项目配置：'+String(err.message||'unknown').replace(/[^a-zA-Z0-9_-]/g,'').slice(0,72));
  report.push('- ℹ️ 不代表网站没有部署；需要检查现有 Cloudflare Pages 凭据/权限');
  process.exitCode=1;
 }
 const output=report.join('\n')+'\n';
 console.log(output);
 if(process.env.GITHUB_STEP_SUMMARY)appendFileSync(process.env.GITHUB_STEP_SUMMARY,output);
}
