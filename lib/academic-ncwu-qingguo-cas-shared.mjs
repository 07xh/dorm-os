/* Qingguo-only, strictly scoped school CAS form + cookie utilities.
 * No second-class password submission or server-owned second-class login.
 */
export class NcwuQingguoCasError extends Error{
 constructor(code){super(code);this.code=code;this.name='NcwuQingguoCasError';}
}
const fail=code=>{throw new NcwuQingguoCasError(code);};
const HOSTS=new Set(['authserver.ncwu.edu.cn','jwmis.ncwu.edu.cn']);
const unescapeHtml=s=>s.replace(/&amp;/gi,'&').replace(/&quot;/gi,'"').replace(/&#39;/g,"'");
function safeUrl(value,base){
 let u;try{u=new URL(unescapeHtml(value),base);}catch{fail('interactive_login_required');}
 if(u.protocol!=='https:'||u.hostname!=='authserver.ncwu.edu.cn'||
  u.pathname!=='/authserver/login'||u.username||u.password||
  (u.port&&u.port!=='443'))fail('interactive_login_required');
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
export function inspectCasForm(html,base,expectedService){
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
