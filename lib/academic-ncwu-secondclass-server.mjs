/* NCWU second-class existing official-session, owner-bound read-only transport.
 * Official authentication happens on the user's device, not this server.
 * Never accept passwords or return school cookies to client responses.
 */
import {NCWU_SECOND_CLASS_READS,makeNcwuSecondClassAdapter} from './academic-ncwu-second-class.mjs';
export class NcwuCasError extends Error {
 constructor(code,reason=null){
  super(code);this.name='NcwuCasError';this.code=code;this.reason=reason;
 }
}
const fail=(code,reason=null)=>{throw new NcwuCasError(code,reason);};
// Only fixed personal-read stages leave this adapter. Official device
// authentication never transports a password through this server.
const failAt=(code,stage)=>{
 const e=new NcwuCasError(code);e.failureStage=stage;throw e;
};
const ENTRY='https://dekt.ncwu.edu.cn/';
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
// Cookie jar used for private, owner-bound second-class session refresh.
export function makeSecondClassSessionJar(initial=[]){
 return sessionJar(initial,new Set([...HOSTS,'jwmis.ncwu.edu.cn']));
}
export function makeNcwuSecondClassServerAdapter({fetcher=fetch}={}){
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
  // Permanently retire the cloud password transport. Keep only the existing
  // encrypted-session readonly adapter; the App owns official authentication.
  async login(){
   fail('interactive_login_required','official_app_authorization_required');
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

