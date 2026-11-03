/* NCWU CAS server-side read-only transport.
 * Only the student's normal HTTPS login, no captcha or MFA bypass.
 * No passwords, tickets or cookies in source, client JSON or logs.
 */
import {NCWU_SECOND_CLASS_READS,makeNcwuSecondClassAdapter} from './academic-ncwu-second-class.mjs';
export class NcwuCasError extends Error {
 constructor(code,reason=null){
  super(code);this.name='NcwuCasError';this.code=code;this.reason=reason;
 }
}
const fail=(code,reason=null)=>{throw new NcwuCasError(code,reason);};
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
const fields=tag=>{
 const a={};
 for(const m of tag.matchAll(/([A-Za-z_][A-Za-z_0-9:-]*)\s*(?:=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?/g)){
  const key=m[1].toLowerCase();
  if(Object.hasOwn(a,key))fail('interactive_login_required');
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
export function inspectCasForm(html,base,expectedService=SERVICE){
 if(typeof html!=='string'||html.length>300000)
  fail('interactive_login_required','password_form_unavailable');
 // Other login tabs (SMS, face, slider) may be visible on the SAME official
 // CAS page. Their labels are not evidence that the password form requires
 // them. Still refuse password transformation anywhere on the page and
 // mandatory challenges within the password form below.
 if(/(?:rsaEncrypt|encryptPassword|sm2Encrypt)/i.test(html))
  fail('interactive_login_required','password_transform_required');
 for(const match of html.matchAll(/<form\b([^>]*)>([\s\S]*?)<\/form\s*>/gi)){
  const f=fields(match[1]),inputs=Array.from(match[2].matchAll(/<input\b([^>]*)>/gi),m=>fields(m[1]));
  if(!inputs.some(x=>x.type?.toLowerCase()==='password'))continue;
  if(f.method?.toLowerCase()!=='post'||f.onsubmit||
     /(?:captcha|geetest|slider|slideVerify|rsaEncrypt|encryptPassword|sm2Encrypt|滑块验证|图形验证码)/i.test(match[0]))
   fail('interactive_login_required','interactive_challenge_required');
  const account=inputs.find(x=>x.name==='username');
  const password=inputs.find(x=>x.name==='password'&&x.type?.toLowerCase()==='password');
  if(!account||!password)fail('interactive_login_required','password_form_unavailable');
  const action=safeUrl(f.action||base,base);
  if(action.hostname!=='authserver.ncwu.edu.cn'||
     action.searchParams.get('service')!==expectedService)
   fail('interactive_login_required','unexpected_cas_form_action');
  const hidden={};
  for(const x of inputs){
   if((x.type||'text').toLowerCase()!=='hidden'||!x.name)continue;
   if(!/^[A-Za-z_][\w.-]{0,70}$/.test(x.name)||String(x.value||'').length>1000)
    fail('interactive_login_required');
   hidden[x.name]=x.value||'';
  }
  if(!Object.hasOwn(hidden,'execution')||!Object.hasOwn(hidden,'_eventId'))
   fail('interactive_login_required','password_form_unavailable');
  return {action:action.href,hidden};
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
    res.headers.getSetCookie():[res.headers.get('set-cookie')].filter(Boolean);
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
export function makeNcwuQingguoSessionJar(initial=[]){
 return sessionJar(initial,new Set([...HOSTS,'jwmis.ncwu.edu.cn']));
}
export function makeNcwuCasServerAdapter({fetcher=fetch,now=()=>Date.now()}={}){
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
 async function start(jar){
  let page=await request(jar,ENTRY);
  if(page.status!==200)fail('upstream_unavailable');
  if(new URL(page.url).hostname==='dekt.ncwu.edu.cn'){
   const link=casLink(page.content,page.url);
   if(!link)fail('interactive_login_required','cas_handoff_unrecognized');
   page=await request(jar,link);
  }
  if(new URL(page.url).hostname!=='authserver.ncwu.edu.cn'||page.status!==200)
   fail('interactive_login_required','cas_login_page_unavailable');
  return inspectCasForm(page.content,page.url);
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
   fail('authorization_failed');
  try{
   const obj=JSON.parse(response.content);
   if(!obj||typeof obj!=='object')fail('authorization_failed');
   return obj;
  }catch{fail('authorization_failed');}
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
  if(seen.size!==1)fail('authorization_failed');
  return [...seen][0];
 }
 return Object.freeze({
  provider:'second_class',
  async preflight(){
   try{
    await start(sessionJar());
    return {loginSupported:true,mode:'password_form_verified'};
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
   const body=new URLSearchParams({...form.hidden,username,password}).toString();
   const res=await request(jar,form.action,{method:'POST',
    headers:{'content-type':'application/x-www-form-urlencoded'},body});
   if(res.status!==200||new URL(res.url).hostname==='authserver.ncwu.edu.cn')
    fail('authorization_failed');
   const studentCode=await identity(jar);
   return {session:{studentCode,cookies:jar.list},
    expiresAt:new Date(now()+7200000).toISOString()};
  },
  async forSession({session,userId}){
   if(!session||!/^[A-Za-z0-9_-]{1,50}$/.test(session.studentCode||'')||!userId)
    fail('authorization_failed');
   const jar=sessionJar(session.cookies);
   return makeNcwuSecondClassAdapter({
    verifySession:async()=>{
     const code=await identity(jar);
     if(code!==session.studentCode)fail('authorization_failed');
     return {authorized:true,scope:'self',school:'ncwu',provider:'second_class',
      identityBound:true,principalBoundTo:userId,
      session:{studentCode:code}};
    },
    readPost:async({path,form})=>post(jar,path,form)
   });
  }
 });
}
