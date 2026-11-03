/* Read-only Cloudflare Pages project configuration audit.
 * A Pages API token is supplied by the existing GitHub Actions secrets.
 * Never log, serialize or persist the token or any Pages env var values.
 */
import {appendFileSync} from 'node:fs';
const PROJECT='dorm-os';
const props=['d1Bound','accessIssuerConfigured','accessAudienceConfigured','productionBranchMain','pagesFunctionsPresent'];
const isObject=x=>x!==null&&typeof x==='object'&&!Array.isArray(x);
const own=(obj,key)=>isObject(obj)&&Object.prototype.hasOwnProperty.call(obj,key);

export function inspectAcademicPagesProject(project){
 if(!isObject(project)||project.name!==PROJECT)throw Error('unexpected_pages_project');
 const production=project.deployment_configs?.production||{};
 const vars=production.env_vars||{};
 const d1=production.d1_databases||{};
 return {
  d1Bound:own(d1,'ACADEMIC_DB')&&typeof d1.ACADEMIC_DB?.id==='string'&&d1.ACADEMIC_DB.id.length>0,
  accessIssuerConfigured:own(vars,'ACCESS_TEAM_DOMAIN'),
  accessAudienceConfigured:own(vars,'ACCESS_AUD'),
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
  const labels={
   d1Bound:'Pages 生产 ACADEMIC_DB 已绑定',
   accessIssuerConfigured:'生产 ACCESS_TEAM_DOMAIN 已配置',
   accessAudienceConfigured:'生产 ACCESS_AUD 已配置',
   productionBranchMain:'Pages 生产分支为 main',
   pagesFunctionsPresent:'Pages Functions 已启用'
  };
  for(const key of props)report.push('- '+(inspection[key]?'✅':'⚠️')+' '+labels[key]);
  report.push('- ℹ️ D1 0001–0009 实际迁移：**未在本脚本内检查**');
  report.push('- ℹ️ Zero Trust Access 应用规则及本人身份：**未在本脚本内检查**');
  report.push('- ℹ️ 华水第二课堂真实授权和无人值守更新：**尚未验证**');
  if(!Object.values(inspection).every(Boolean))process.exitCode=1;
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
