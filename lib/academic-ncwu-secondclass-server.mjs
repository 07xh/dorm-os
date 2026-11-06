/* NCWU second-class official CAS read-only transport (M24).
 * Only the student's normal HTTPS login, no captcha or MFA bypass.
 * No passwords, tickets or cookies in source, client JSON or logs.
 */
import {NCWU_SECOND_CLASS_READS,makeNcwuSecondClassAdapter} from './academic-ncwu-second-class.mjs';
import {verifyNcwuSudySource,encodeNcwuSudyPassword} from './academic-ncwu-sudy-codec.mjs';
const SUDY_SOURCE_VERIFIED=Symbol('verified-school-sudy-source');
export class NcwuCasError extends Error {
 constructor(code,reason=null){
  super(code);this.name='NcwuCasError';this.code=code;this.reason=reason;
 }
}
const fail=(code,reason=null)=>{throw new NcwuCasError(code,reason);};
// Fixed phase labels only: never include school HTML, redirect URLs, tokens,
// cookies, form fields, identity values, or submitted credentials.
const CAS_RETURN_REASONS=new Set(['unexpected_response','login_page_remained',
 'service_callback_incomplete','unexpected_school_landing',
 'cas_request_rejected','cas_rate_limited','cas_server_failure']);
// Only exact, independently allowlisted school hosts may produce a coarse,
// fixed origin category. No URL, CAS ticket, Cookie or account is exported.
const CAS_RETURN_HOSTS=Object.freeze({
 'authserver.ncwu.edu.cn':'cas_auth_server',
 'sec.ncwu.edu.cn':'second_class_callback',
 'dekt.ncwu.edu.cn':'second_class_portal'
});
const failAt=(code,stage,reason=null,host=null)=>{
 const e=new NcwuCasError(code);e.failureStage=stage;
 if(code==='authorization_failed'&&stage==='cas_return'&&CAS_RETURN_REASONS.has(reason)){
  e.casReturnReason=reason;
  if(Object.hasOwn(CAS_RETURN_HOSTS,host))
   e.casReturnHost=CAS_RETURN_HOSTS[host];
 }
 throw e;
};
const ENTRY='https://dekt.ncwu.edu.cn/';
const SERVICE='https://sec.ncwu.edu.cn/rump_frontend/loginFromCas/';
const HOSTS=new Set(['dekt.ncwu.edu.cn','authserver.ncwu.edu.cn','sec.ncwu.edu.cn']);
const PATHS=new Set(Object.values(NCWU_SECOND_CLASS_READS));
const unescapeHtml=s=>s.replace(/&amp;/gi,'&').replace(/&quot;/gi,'"').replace(/&#39;/g,"'");
function safeUrl(value,base=ENTRY){
 let u;
 try{u=new URL(unescapeHtml(value),base);}catch{fail('interactive_login_required');}
 if(u.protocol!=='https:'||!HOSTS.has(u.hostname)||u.username||u.password||
    (u.port&&u.port!=='443'))fail('interactive_login_required');
 if(u.hostname==='authserver.ncwu.edu.cn'&&u.pathname!=='/authserver/login')
  fail('interactive_login_required');
 if(u.hostname==='sec.ncwu.edu.cn'&&!u.pathname.startsWith('/rump_frontend/'))
  fail('interactive_login_required');
 return u;
}
const fields=(tag,stage='form_attributes')=>{
 const a={};
 for(const m of tag.matchAll(/([A-Za-z_][A-Za-z_0-9:-]*)\s*(?:=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?/g)){
  const key=m[1].toLowerCase();
  if(Object.hasOwn(a,key)){
   const error=new NcwuCasError('interactive_login_required');
   error.failureStage=stage;throw error;
  }
  a[key]=unescapeHtml(m[2]??m[3]??m[4]??'');
 }
 return a;
};
/* Follow only static school-owned CAS handoffs. We do not execute the
 * university's JavaScript or accept arbitrary redirect targets. */
function casLink(html,base){
 const candidates=[];
 for(const match of html.matchAll(/<a\b([^>]*)>/gi)){
  try{candidates.push(fields(match[1]).href);}catch{}
 }
 for(const match of html.matchAll(/<meta\b([^>]*)>/gi)){
  let attrs;
  try{attrs=fields(match[1]);}catch{continue;}
  if(attrs['http-equiv']?.toLowerCase()!=='refresh')continue;
  const next=attrs.content?.match(/^\s*\d+(?:\.\d+)?\s*;\s*url\s*=\s*(.+)$/i);
  if(next)candidates.push(next[1].replace(/^['"]|['"]$/g,''));
 }
 // A literal navigation to the allowlisted school CAS is safe to recognize;
 // dynamic script expression remains unsupported (no code evaluation).
 for(const match of html.matchAll(/(?:window\.)?location(?:\.href|\.assign|\.replace)?\s*(?:=|\()\s*(['"])(https:\/\/[^'"\s]+)\1/g)){
  candidates.push(match[2]);
 }
 for(const value of candidates){
  let url;
  try{url=safeUrl(value,base);}catch{continue;}
  if(url.hostname==='authserver.ncwu.edu.cn'&&url.pathname==='/authserver/login'&&
     url.searchParams.get('service')===SERVICE)return url.href;
 }
 return null;
}
/* Return only fixed booleans about an unauthenticated school CAS form.
 * Never export HTML, field values, hidden tokens, QR tickets, or credentials.
 * This diagnostic does NOT authorize bypassing an interactive challenge. */
export function summarizeCasChallengeEvidence(html){
 const empty={passwordFormSeen:false,challengeControlSeen:false,
  challengeMarkupSeen:false,challengeScriptSeen:false,
  passwordTransformationSeen:false,submitHandlerSeen:false,submitHandlerKind:'none'};
 if(typeof html!=='string'||html.length>300000)return empty;
 const matches=Array.from(html.matchAll(/<form\b([^>]*)>([\s\S]*?)<\/form\s*>/gi));
 const found=matches.find(m=>/<input\b[^>]*type\s*=\s*(?:"password"|'password'|password)(?:\s|\/|>)/i.test(m[2]));
 if(!found)return {...empty,passwordTransformationSeen:
  /(?:rsaEncrypt|encryptPassword|sm2Encrypt)/i.test(html)};
 const fragment=found[0],body=found[2];
 // Inspect only the public, static onsubmit attribute and emit a bounded
 // function identifier when the shape is a simple zero-argument call.
 const handler=(found[1].match(/\bonsubmit\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))/i)||[]);
 const submitText=(handler[1]??handler[2]??handler[3]??'').trim();
 const simpleCall=submitText.match(/^(?:return\s+)?([A-Za-z_$][\w$]{0,60})\(\s*\)\s*;?$/);
 const handlerKind=!submitText?'none':/^return\s+true\s*;?$/i.test(submitText)?
  'constant_true':/^return\s+false\s*;?$/i.test(submitText)?
  'constant_false':simpleCall?'named_function_call':'complex_inline';
 const marker=/(?:captcha|geetest|slider|slideVerify|滑块验证|图形验证码)/i;
 const scripts=Array.from(body.matchAll(/<script\b[^>]*>[\s\S]*?<\/script\s*>/gi),m=>m[0]).join(' ');
 const sansScripts=fragment.replace(/<script\b[^>]*>[\s\S]*?<\/script\s*>/gi,' ');
 const fields=Array.from(body.matchAll(/<(?:input|select|textarea)\b[^>]*>/gi),m=>m[0]);
 return {passwordFormSeen:true,
  challengeControlSeen:fields.some(tag=>marker.test(tag)),
  challengeMarkupSeen:marker.test(sansScripts),
  challengeScriptSeen:marker.test(scripts),
  passwordTransformationSeen:/(?:rsaEncrypt|encryptPassword|sm2Encrypt)/i.test(html),
  submitHandlerSeen:/\bonSubmit\s*=/i.test(found[1]),
  submitHandlerKind:handlerKind,
  ...(simpleCall?{submitHandlerName:simpleCall[1]}:{})};
}
/* Inspect public school-owned JavaScript conservatively; never execute it.
 * Signals are heuristic evidence, NOT permission to skip school JS/verification. */
export function summarizeSchoolSubmitFunctionScript(source,handlerName){
 const none={definitionSeen:false,passwordProcessingSeen:false,
  verificationSeen:false,formSubmissionSeen:false,asyncSubmissionSeen:false};
 if(typeof source!=='string'||source.length>300000||
    typeof handlerName!=='string'||!/^[A-Za-z_$][\w$]{0,60}$/.test(handlerName))return none;
 const escaped=handlerName.split('$').join('\\$');
 // Match function DECLARATIONS/ASSIGNMENTS, not calls from an onsubmit
 // attribute. A bare "doPcMobilesubmit()" is never evidence of definition.
 const patterns=[
  new RegExp('\\b(?:async\\s+)?function\\s+'+escaped+'\\s*\\(','g'),
  new RegExp('\\b(?:const|let|var)\\s+'+escaped+'\\s*=\\s*(?:async\\s+)?function\\s*\\(','g'),
  new RegExp('\\b(?:const|let|var)\\s+'+escaped+'\\s*=\\s*(?:async\\s*)?\\([^)]*\\)\\s*=>\\s*\\{','g'),
  new RegExp('\\b(?:window|globalThis)\\.'+escaped+'\\s*=\\s*(?:async\\s+)?function\\s*\\(','g')
 ];
 const matches=patterns.map(p=>p.exec(source)).filter(Boolean)
  .sort((x,y)=>x.index-y.index);
 const match=matches[0];
 if(!match)return none;
 const start=source.indexOf('{',match.index+match[0].length);
 if(start<0||start-match.index>240)return {...none,definitionSeen:true};
 // Bounded, conservative lexical window. False positives are possible;
 // this evidence must NEVER authorize credential submission by itself.
 const window=source.slice(start,Math.min(source.length,start+6000));
 return {definitionSeen:true,
  passwordProcessingSeen:/(?:encrypt|decrypt|rsa|sm2|sm4|aes|sha256|md5|password|pwd)/i.test(window),
  verificationSeen:/(?:captcha|geetest|verify|slider|challenge|验证码|滑块|mfa|otp)/i.test(window),
  formSubmissionSeen:/(?:\.submit\s*\(|requestSubmit\s*\()/i.test(window),
  asyncSubmissionSeen:/(?:ajax\s*\(|fetch\s*\(|XMLHttpRequest)/i.test(window)};
}
async function inspectSchoolCasSubmitScripts(page,fetcher,handlerName){
 const none={schoolScriptDefinitionSeen:false,sourceType:'unknown',
  passwordProcessingSeen:false,verificationSeen:false,
  formSubmissionSeen:false,asyncSubmissionSeen:false,
  inlineScriptCount:0,externalScriptTagsSeen:0,
  eligibleExternalScripts:0,fetchedExternalScripts:0,failedExternalScripts:0};
 if(!/^[A-Za-z_$][\w$]{0,60}$/.test(handlerName||''))return none;
 const inline=Array.from(page.content.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script\s*>/gi))
  .filter(m=>!/\bsrc\s*=/i.test(m[1])).map(m=>m[2]);
 none.inlineScriptCount=Math.min(inline.length,99);
 for(const source of inline){
  const evidence=summarizeSchoolSubmitFunctionScript(source,handlerName);
  if(evidence.definitionSeen)return {...none,...evidence,schoolScriptDefinitionSeen:true,
   sourceType:'inline_school_js'};
 }
 const urls=[];
 for(const m of page.content.matchAll(/<script\b([^>]*)>/gi)){
  const attrs=fields(m[1]);
  if(typeof attrs.src!=='string'||!attrs.src)continue;
  none.externalScriptTagsSeen=Math.min(99,none.externalScriptTagsSeen+1);
  let u;
  try{u=new URL(attrs.src,page.url);}catch{continue;}
  if(u.protocol!=='https:'||u.hostname!=='authserver.ncwu.edu.cn'||
     !/\.js$/i.test(u.pathname)||u.pathname.length>190||
     u.search.length>120||u.username||u.password)continue;
  if(!urls.includes(u.href))urls.push(u.href);
  // The live school page has ten external scripts; its login handler is
  // after the vendor and alternative-login bundles. Keep a bounded budget
  // without silently omitting the actual submit implementation.
  if(urls.length>=12)break;
 }
 none.eligibleExternalScripts=urls.length;
 // Only fixed university-hosted public .js files. Never send CAS cookies,
 // browser identifiers, credentials, tickets or the password form itself.
 for(const url of urls){
  let response;
  try{
   response=await fetcher(url,{method:'GET',redirect:'manual',
    cache:'no-store',headers:{accept:'application/javascript,text/javascript'},
    signal:AbortSignal.timeout(4500)});
   if(response.status!==200||Number(response.headers.get('content-length')||0)>300000){
    none.failedExternalScripts++;continue;
   }
   const source=await response.text();
   if(source.length>300000){none.failedExternalScripts++;continue;}
   none.fetchedExternalScripts++;
   const evidence=summarizeSchoolSubmitFunctionScript(source,handlerName);
   if(evidence.definitionSeen)return {...none,...evidence,schoolScriptDefinitionSeen:true,
    sourceType:'school_static_js'};
  }catch{none.failedExternalScripts++;/* Unknown is not proof of safe login. */}
  finally{try{await response?.body?.cancel?.();}catch{}}
 }
 return none;
}

export function inspectCasForm(html,base,expectedService=SERVICE,sourceProof=null){
 if(typeof html!=='string'||html.length>300000)
  fail('interactive_login_required','password_form_unavailable');
 // Other login tabs (SMS, face, slider) may be visible on the SAME official
 // CAS page. Their labels are not evidence that the password form requires
 // them. Still refuse password transformation anywhere on the page and
 // mandatory challenges within the password form below.
 if(/(?:rsaEncrypt|encryptPassword|sm2Encrypt)/i.test(html))
  fail('interactive_login_required','password_transform_required');
 for(const match of html.matchAll(/<form\b([^>]*)>([\s\S]*?)<\/form\s*>/gi)){
  const f=fields(match[1]),inputs=Array.from(match[2].matchAll(/<input\b([^>]*)>/gi),m=>fields(m[1],'input_attributes'));
  if(!inputs.some(x=>x.type?.toLowerCase()==='password'))continue;
  // The school's checkPassLogin uses a document-wide getElementById,
  // including controls outside fm1. Other tabs' distinct IDs do not veto it.
  const globalAuthcode=sourceProof===SUDY_SOURCE_VERIFIED&&
   Array.from(html.matchAll(/<[a-z][\w:-]*\b([^>]*)>/gi))
    .some(m=>/(?:^|\s)id\s*=\s*(?:"authcode"|'authcode'|authcode(?=\s|\/?$))/i.test(m[1]));
  const sudy=sourceProof===SUDY_SOURCE_VERIFIED&&f.id==='fm1'&&
   /^return\s+doPcMobilesubmit\(\s*\);?$/.test(f.onsubmit||'')&&
   inputs.some(x=>x.id==='passwordShow'&&x.type==='password'&&!x.name)&&
   inputs.some(x=>x.id==='password'&&x.name==='password'&&x.type==='hidden')&&
   inputs.some(x=>x.id==='encrypted'&&x.name==='encrypted'&&x.type==='hidden')&&
   /\bonclick\s*=\s*["']return\s+checkPassLogin\(\s*\);?["']/i.test(match[2]);
  if(globalAuthcode||f.method?.toLowerCase()!=='post'||(f.onsubmit&&!sudy)||
     /(?:authcode|captcha|geetest|slider|slideVerify|rsaEncrypt|encryptPassword|sm2Encrypt|滑块验证|图形验证码)/i.test(match[0]))
   fail('interactive_login_required','interactive_challenge_required');
  const account=inputs.find(x=>x.name==='username');
  const password=inputs.find(x=>x.name==='password'&&x.type?.toLowerCase()===(sudy?'hidden':'password'));
  if(!account||!password)fail('interactive_login_required','password_form_unavailable');
  // CAS installations may submit to /authserver/login without repeating
  // the service query in the form action. Only restore the SAME service from
  // the independently verified page; never accept another CAS target.
  let page,action;
  try{page=safeUrl(base);}catch(e){e.failureStage='page_url';throw e;}
  try{action=safeUrl(f.action||base,base);}catch(e){
   e.failureStage='action_url';
   try{e.actionHasPathParameters=new URL(f.action||base,base).pathname.includes(';');}catch{}
   throw e;
  }
  if(page.hostname!=='authserver.ncwu.edu.cn'||
     page.searchParams.getAll('service').length!==1||
     page.searchParams.get('service')!==expectedService||
     action.hostname!=='authserver.ncwu.edu.cn'||
     action.pathname!=='/authserver/login'||
     action.searchParams.getAll('service').length>1||
     (action.searchParams.has('service')&&
      action.searchParams.get('service')!==expectedService))
   fail('interactive_login_required','unexpected_cas_form_action');
  if(!action.searchParams.has('service'))
   action.searchParams.set('service',expectedService);
  // The live school CAS form includes a hidden token above the old 1k
  // limit. Bound each opaque school-issued field and the aggregate budget;
  // they are POSTed only to the independently verified same-school CAS URL.
  // These size limits do not relax authentication or challenge checks.
  const hidden={};
  let hiddenBudget=0;
  for(const x of inputs){
   if((x.type||'text').toLowerCase()!=='hidden'||!x.name)continue;
   const validName=/^[A-Za-z_][\w.-]{0,70}$/.test(x.name);
   const value=String(x.value||'');
   hiddenBudget+=x.name.length+value.length;
   if(!validName||value.length>8192||hiddenBudget>16384){
    const e=new NcwuCasError('interactive_login_required');
    e.failureStage=validName?'hidden_length':'hidden_name';
    throw e;
   }
   hidden[x.name]=value;
  }
  if(!Object.hasOwn(hidden,'execution')||!Object.hasOwn(hidden,'_eventId'))
   fail('interactive_login_required','password_form_unavailable');
  return {action:action.href,hidden,...(sudy?{passwordEncoding:'ncwu_sudy_rsa'}:{})};
 }
 fail('interactive_login_required','password_form_unavailable');
}
const cookiePair=(raw,u,hosts=HOSTS)=>{
 const p=raw.split(';')[0],i=p.indexOf('=');
 if(i<1)return null;
 const name=p.slice(0,i).trim(),value=p.slice(i+1).trim();
 if(!/^[A-Za-z0-9_.-]{1,100}$/.test(name)||/[;\r\n]/.test(value)||value.length>2500)
  return null;
 const attr=raw.match(/(?:^|;)\s*domain=([^;\s]+)/i);
 const domain=(attr?.[1]||u.hostname).replace(/^\./,'').toLowerCase();
 if(!hosts.has(domain)&&domain!=='ncwu.edu.cn')return null;
 // Reject response hosts setting cookies for unrelated sibling domains.
 if(u.hostname!==domain&&!u.hostname.endsWith('.'+domain))return null;
 const path=(raw.match(/(?:^|;)\s*path=([^;\s]+)/i)?.[1]||'/');
 if(!path.startsWith('/')||path.length>150)return null;
 return {name,value,domain,path,hostOnly:!attr};
};
function sessionJar(initial=[],hosts=HOSTS){
 if(!Array.isArray(initial)||initial.length>32)fail('authorization_failed');
 const list=[];
 for(const c of initial){
  if(!c||!hosts.has(c.domain)&&c.domain!=='ncwu.edu.cn'||
     !/^[A-Za-z0-9_.-]{1,100}$/.test(c.name||'')||
     typeof c.value!=='string'||c.value.length>2500||
     typeof c.path!=='string'||!c.path.startsWith('/'))fail('authorization_failed');
  list.push({name:c.name,value:c.value,domain:c.domain,path:c.path,hostOnly:c.hostOnly===true});
 }
 return {list,
  header(u){return list.filter(c=>(c.hostOnly?u.hostname===c.domain:
    u.hostname===c.domain||u.hostname.endsWith('.'+c.domain))&&
    u.pathname.startsWith(c.path)).map(c=>c.name+'='+c.value).join('; ');},
  merge(res,u){
   const values=typeof res.headers.getSetCookie==='function'?
    res.headers.getSetCookie():typeof res.headers.getAll==='function'?
    res.headers.getAll('Set-Cookie'):[res.headers.get('set-cookie')].filter(Boolean);
   for(const raw of values){
    const c=cookiePair(raw,u,hosts);
    if(!c)continue;
    const idx=list.findIndex(x=>x.name===c.name&&x.domain===c.domain&&x.path===c.path);
    if(idx>=0)list.splice(idx,1);
    if(c.value&&!/(?:^|;)\s*max-age=0(?:;|$)/i.test(raw))list.push(c);
   }
   if(list.length>32)fail('authorization_failed');
  }
 };
}
/** Server-internal cookie jar shared with the official Qingguo CAS adapter.
 * Scope is fixed to verified NCWU hosts, never caller-provided domain lists. */
/* A visible official login-page label is evidence of an option, not of
 * any QR ticket, callback ownership, authenticated user or shared session.
 * Deliberately ignore hidden script content and never extract QR secrets. */
export function observeOfficialWeChatEntry(html){
 if(typeof html!=='string'||html.length>300000)return false;
 const readable=html.replace(/<script\b[^>]*>[\s\S]*?<\/script\s*>/gi,' ')
  .replace(/<style\b[^>]*>[\s\S]*?<\/style\s*>/gi,' ')
  .replace(/<[^>]*>/g,' ').replace(/&nbsp;|&#160;/gi,' ')
  .replace(/\s+/g,' ');
 return /(?:请使用微信扫码登录|微信扫码登录|微信登录)/.test(readable);
}
export function makeSecondClassSessionJar(initial=[]){
 return sessionJar(initial,new Set([...HOSTS,'jwmis.ncwu.edu.cn']));
}
export function makeNcwuSecondClassServerAdapter({fetcher=fetch,now=()=>Date.now()}={}){
 if(typeof fetcher!=='function')fail('not_configured');
 async function request(jar,url,opts={}){
  let u=safeUrl(url),method=opts.method||'GET',body=opts.body||null,headers=opts.headers||{};
  for(let step=0;step<10;step++){
   let res;
   const cookie=jar.header(u);
   try{
    res=await fetcher(u.href,{method,redirect:'manual',cache:'no-store',
     headers:{accept:'text/html,application/json',...headers,...(cookie?{cookie}:{})},
     ...(body!==null?{body}:{}),signal:AbortSignal.timeout(15000)});
   }catch{fail('upstream_unavailable');}
   jar.merge(res,u);
   if([301,302,303,307,308].includes(res.status)){
    const location=res.headers.get('location');
    if(!location)fail('upstream_unavailable');
    // Never replay a password-bearing POST across HTTP redirects.
    if([307,308].includes(res.status)&&method!=='GET')fail('interactive_login_required');
    u=safeUrl(location,u.href);
    if(![307,308].includes(res.status))method='GET',body=null,headers={};
    continue;
   }
   if(Number(res.headers.get('content-length')||0)>300000)fail('upstream_unavailable');
   let text;
   try{text=await res.text();}catch{fail('upstream_unavailable');}
   if(text.length>300000)fail('upstream_unavailable');
   return {status:res.status,content:text,url:u.href};
  }
  fail('interactive_login_required');
 }
 async function officialCasPage(jar){
  let page=await request(jar,ENTRY);
  if(page.status!==200)fail('upstream_unavailable');
  if(new URL(page.url).hostname==='dekt.ncwu.edu.cn'){
   const link=casLink(page.content,page.url);
   if(!link)fail('interactive_login_required','cas_handoff_unrecognized');
   page=await request(jar,link);
  }
  if(new URL(page.url).hostname!=='authserver.ncwu.edu.cn'||page.status!==200)
   fail('interactive_login_required','cas_login_page_unavailable');
  return page;
 }
 async function resolveForm(page){
  try{return inspectCasForm(page.content,page.url);}
  catch(original){
   // Only the current school theme with an independently pinned public
   // implementation can use the Sudy wire format. A named handler alone
   // or a heuristic script inventory never enables credential submission.
   const sourceUrl='https://authserver.ncwu.edu.cn/authserver/themes/sudy_ncwu/js/login.js?t=1';
   const sources=Array.from(page.content.matchAll(/<script\b([^>]*)>/gi),m=>{
    try{return new URL(fields(m[1]).src,page.url).href;}catch{return null;}
   });
   if(!sources.includes(sourceUrl)||!/<form\b[^>]*\bid=["']fm1["']/i.test(page.content))
    throw original;
   let source;
   try{
    const response=await fetcher(sourceUrl,{method:'GET',redirect:'manual',cache:'no-store',
     headers:{accept:'application/javascript,text/javascript'},signal:AbortSignal.timeout(4500)});
    if(response.status!==200||Number(response.headers.get('content-length')||0)>300000)throw original;
    source=await response.text();
    if(!await verifyNcwuSudySource(source))throw original;
   }catch{throw original;}
   return inspectCasForm(page.content,page.url,SERVICE,SUDY_SOURCE_VERIFIED);
  }
 }
 async function start(jar){
  return resolveForm(await officialCasPage(jar));
 }
 async function post(jar,path,form={}){
  if(!PATHS.has(path))fail('authorization_failed');
  const response=await request(jar,new URL(path,ENTRY).href,{
   method:'POST',headers:{accept:'application/json',
    'content-type':'application/x-www-form-urlencoded; charset=UTF-8',
    'x-requested-with':'XMLHttpRequest',
    origin:'https://dekt.ncwu.edu.cn',referer:ENTRY},
   body:new URLSearchParams(form).toString()
  });
  if(response.status!==200||new URL(response.url).hostname!=='dekt.ncwu.edu.cn'||
     /<\s*(?:html|form|!doctype)\b/i.test(response.content.slice(0,200)))
   failAt('authorization_failed','personal_data');
  try{
   const obj=JSON.parse(response.content);
   if(!obj||typeof obj!=='object')failAt('authorization_failed','personal_data');
   return obj;
  }catch{failAt('authorization_failed','personal_data');}
 }
 async function identity(jar){
  const row=await post(jar,NCWU_SECOND_CLASS_READS.scores,{});
  const seen=new Set();
  for(const record of row.data||[])
   if(typeof record?.student_code==='string'&&record.student_code.length<=50&&
      /^[A-Za-z0-9_-]+$/.test(record.student_code))seen.add(record.student_code);
  if(seen.size===0){
   const page=await post(jar,NCWU_SECOND_CLASS_READS.activities,{page:'1',pagesize:'50'});
   for(const record of page.data||[])
    if(typeof record?.student_code==='string'&&record.student_code.length<=50&&
       /^[A-Za-z0-9_-]+$/.test(record.student_code))seen.add(record.student_code);
  }
  if(seen.size!==1)failAt('authorization_failed','identity_evidence');
  return [...seen][0];
 }
 return Object.freeze({
  provider:'second_class',
  async preflight({inspectScripts=false}={}){
   // Probe only the school's OWN CAS page and its ordinary password form.
   // This never starts a QR challenge or makes a third-party session grant.
   let officialWeChatEntryObserved=false;
   try{
    const page=await officialCasPage(sessionJar());
    officialWeChatEntryObserved=observeOfficialWeChatEntry(page.content);
    const proof=officialWeChatEntryObserved?{officialWeChatEntryObserved:true}:{};
    try{
     await resolveForm(page);
     return {loginSupported:true,mode:'password_form_verified',...proof};
    }catch(e){
     const mode=e?.code==='upstream_unavailable'?'upstream_unavailable':'interactive_login_required';
     const reasons=new Set(['cas_handoff_unrecognized','cas_login_page_unavailable',
      'password_form_unavailable','interactive_challenge_required',
      'password_transform_required','unexpected_cas_form_action']);
     return {loginSupported:false,mode,
      ...(['form_attributes','input_attributes','page_url','action_url','hidden_name','hidden_length'].includes(e?.failureStage)?
       {failureStage:e.failureStage,...(typeof e.actionHasPathParameters==='boolean'?
        {actionHasPathParameters:e.actionHasPathParameters}:{})}:{}),
      reason:mode==='upstream_unavailable'?'school_unreachable':
       reasons.has(e?.reason)?e.reason:'unrecognized_auth_flow',...proof,
      ...(e?.reason==='interactive_challenge_required'?
       {challengeEvidence:{
        ...summarizeCasChallengeEvidence(page.content),
        ...(inspectScripts===true?
         await (async()=>{
          const evidence=summarizeCasChallengeEvidence(page.content);
          return evidence.submitHandlerKind==='named_function_call'?
           inspectSchoolCasSubmitScripts(page,fetcher,evidence.submitHandlerName):{};
         })():{})
       }}:{})};
    }
   }catch(e){
    const mode=e?.code==='upstream_unavailable'?'upstream_unavailable':'interactive_login_required';
    const reasons=new Set(['cas_handoff_unrecognized','cas_login_page_unavailable',
     'password_form_unavailable','interactive_challenge_required',
     'password_transform_required','unexpected_cas_form_action']);
    return {loginSupported:false,mode,
     reason:mode==='upstream_unavailable'?'school_unreachable':
      reasons.has(e?.reason)?e.reason:'unrecognized_auth_flow'};
   }
  },
  async login({username,password}){
   if(typeof username!=='string'||!username.trim()||username.length>128||
      typeof password!=='string'||!password||password.length>512)
    fail('invalid_credentials');
   const jar=sessionJar(),form=await start(jar);
   let encoded=password;
   if(form.passwordEncoding==='ncwu_sudy_rsa'){
    try{encoded=encodeNcwuSudyPassword(password);}catch{fail('invalid_credentials');}
   }
   const body=new URLSearchParams({...form.hidden,username,password:encoded}).toString();
   // Match ordinary same-origin CAS form submission metadata. Both values
   // are derived from the independently verified official form action, never
   // from caller input. This does not bypass school challenges or authorization.
   const casFormUrl=new URL(form.action);
   const res=await request(jar,form.action,{method:'POST',
    headers:{'content-type':'application/x-www-form-urlencoded',
     origin:casFormUrl.origin,referer:casFormUrl.href},body});
   const landing=new URL(res.url);
   // Fixed response classes only. Do not return raw school status, response
   // body, destination URL, CAS ticket, cookies, credentials or user identity.
   // An unsuccessful CAS POST is never proof of a wrong password.
   if(res.status!==200){
    const reason=res.status===429?'cas_rate_limited':
     res.status>=400&&res.status<500?'cas_request_rejected':
     res.status>=500&&res.status<600?'cas_server_failure':'unexpected_response';
    failAt('authorization_failed','cas_return',reason,landing.hostname);
   }
   // The known school-owned CAS service can finish with a 200 HTML page
   // instead of an HTTP 302. A successful school callback is NOT a login
   // grant: perform one bounded, allowlisted GET to the school entry first,
   // then require independent proof from the student's read-only endpoints.
   // Never execute school JS, submit forms, or replay the password.
   if(landing.hostname==='sec.ncwu.edu.cn'&&
      landing.pathname==='/rump_frontend/loginFromCas/'){
    const completed=await request(jar,ENTRY);
    if(completed.status!==200||
       new URL(completed.url).hostname!=='dekt.ncwu.edu.cn')
      failAt('authorization_failed','cas_return','service_callback_incomplete');
   }else if(landing.hostname==='authserver.ncwu.edu.cn'){
    failAt('authorization_failed','cas_return','login_page_remained');
   }else if(landing.hostname!=='dekt.ncwu.edu.cn'){
    failAt('authorization_failed','cas_return','unexpected_school_landing');
   }
   const studentCode=await identity(jar);
   return {session:{studentCode,cookies:jar.list},
    expiresAt:new Date(now()+7200000).toISOString()};
  },
  async forSession({session,userId}){
   if(!session||!/^[A-Za-z0-9_-]{1,50}$/.test(session.studentCode||'')||!userId)
    fail('authorization_failed');
   const jar=sessionJar(session.cookies);
   const read=makeNcwuSecondClassAdapter({
    verifySession:async()=>{
     const code=await identity(jar);
     if(code!==session.studentCode)fail('authorization_failed');
     return {authorized:true,scope:'self',school:'ncwu',provider:'second_class',
      identityBound:true,principalBoundTo:userId,
      session:{studentCode:code}};
    },
    readPost:async({path,form})=>post(jar,path,form)
   });
   // School can rotate cookies during an authenticated personal-data read.
   // Return them only to the trusted session vault after sync succeeds.
   return Object.freeze({...read,getRefreshedSession:()=>({
    studentCode:session.studentCode,cookies:jar.list.map(cookie=>({...cookie}))
   })});
  }
 });
}

