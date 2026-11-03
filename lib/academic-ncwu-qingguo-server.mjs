/**
 * Native, server-only Qingguo CAS transport. The authorized student uses the
 * official NCWU login form, never a HAR replay or a forged CAS ticket.
 *
 * The university can require a second, independent Qingguo password after
 * campus SSO. In that case we require explicit secondary credentials rather
 * than silently trying the campus password. Challenges always fail closed.
 */
import {load} from 'cheerio';
import {inspectCasForm,makeNcwuQingguoSessionJar} from './academic-ncwu-qingguo-cas-shared.mjs';
import {readNcwuTimetableCandidate} from './academic-ncwu-page-reader.mjs';

const SCHOOL='https://jwmis.ncwu.edu.cn';
const SERVICE=SCHOOL+'/hsjw/caslogin';
const CAS='https://authserver.ncwu.edu.cn/authserver/login?service='+encodeURIComponent(SERVICE);
const ENTRY=SCHOOL+'/hsjw/student/xkjg.wdkb.jsp';
const ALLOWED=new Set(['authserver.ncwu.edu.cn','jwmis.ncwu.edu.cn']);
const STUDENT=/^[A-Za-z0-9_-]{3,50}$/;
const TERM=/^20\d{2}$/;
const CHALLENGE=/(captcha|geetest|slideVerify|smsCode|otp|rsaEncrypt|encryptPassword|sm2Encrypt|验证码|滑块|短信验证|人脸识别|动态口令)/i;
export class NcwuQingguoError extends Error{
 constructor(code,reason=null){super(code);this.code=code;this.name='NcwuQingguoError';
  this.reason=['password_transform_required','interactive_challenge_required',
   'password_form_unavailable','unexpected_cas_form_action'].includes(reason)?reason:null;
 }
}
const fail=(code,reason=null)=>{throw new NcwuQingguoError(code,reason);};
function safeUrl(value,base=CAS){
 let u;try{u=new URL(value,base);}catch{fail('interactive_login_required');}
 if(u.protocol!=='https:'||!ALLOWED.has(u.hostname)||u.username||u.password||
   (u.port&&u.port!=='443'))fail('interactive_login_required');
 if(u.hostname==='authserver.ncwu.edu.cn'&&u.pathname!=='/authserver/login')
  fail('interactive_login_required');
 if(u.hostname==='jwmis.ncwu.edu.cn'&&!u.pathname.startsWith('/hsjw/'))
  fail('interactive_login_required');
 return u;
}
const clean=v=>String(v??'').trim();
function schoolForm(html,base){
 if(CHALLENGE.test(html))fail('interactive_login_required');
 const $=load(html);
 const choices=$('form').toArray().filter(el=>{
  const form=$(el),action=form.attr('action')||'';
  try{return safeUrl(action,base).href.split('?')[0]===SCHOOL+'/hsjw/cas/logon.action';}
  catch{return false;}
 });
 if(choices.length===0)return null;
 if(choices.length!==1)fail('interactive_login_required');
 const el=$(choices[0]),inputs=el.find('input');
 if(clean(el.attr('method')).toLowerCase()!=='post'||el.attr('onsubmit'))
  fail('interactive_login_required');
 const names=new Set(),hidden={};
 inputs.each((_,input)=>{
  const name=$(input).attr('name'),type=clean($(input).attr('type')||'text').toLowerCase();
  if(!name)return;
  if(names.has(name)||!(/^[A-Za-z_][\w.-]{0,70}$/).test(name))
   fail('interactive_login_required');
  names.add(name);
  if(type==='hidden'){
   const value=$(input).attr('value')||'';
   if(value.length>800)fail('interactive_login_required');
   hidden[name]=value;
  }
 });
 if(!names.has('username')||!names.has('password')||
    inputs.filter('[name="password"][type="password"]').length!==1)
   fail('interactive_login_required');
 const url=safeUrl(el.attr('action'),base);
 return {url:url.href,hidden};
}
function parsePrincipal(html){
 if(typeof html!=='string'||CHALLENGE.test(html))fail('authorization_failed');
 const $=load(html),fields=$('input#xh,input[name="xh"]').toArray()
  .map(el=>clean($(el).val())).filter(Boolean);
 if(!fields.length||new Set(fields).size!==1||!STUDENT.test(fields[0]))
  fail('authorization_failed');
 const xn=clean($('#xn').first().val()),term=clean($('#xq_m').first().val());
 if(!TERM.test(xn)||!['1','2','3'].includes(term))
  fail('authorization_failed');
 return {studentId:fields[0],semester:xn+'-'+(Number(xn)+1)+'-'+term};
}
function htmlResponse(res){
 if(!res||res.status!==200)return false;
 const type=res.headers?.get?.('content-type')||'';
 return !type||/text\/html|application\/xhtml\+xml/i.test(type);
}
export function makeNcwuQingguoServerAdapter({
 fetcher=fetch,now=()=>Date.now(),trustedSyncEnabled=false
}={}){
 if(typeof fetcher!=='function')fail('not_configured');
 async function request(jar,raw,{method='GET',headers={},body=null}={}){
  let url=safeUrl(raw);
  for(let step=0;step<9;step++){
   const cookie=jar.header(url);
   let res;
   try{
    res=await fetcher(url.href,{
     method,redirect:'manual',cache:'no-store',signal:AbortSignal.timeout(15000),
     headers:{accept:'text/html',...headers,...(cookie?{cookie}:{})},
     ...(body===null?{}:{body})
    });
   }catch{fail('upstream_unavailable');}
   jar.merge(res,url);
   if([301,302,303,307,308].includes(res.status)){
    if([307,308].includes(res.status)&&method!=='GET')
     fail('interactive_login_required');
    const loc=res.headers.get('location');
    if(!loc)fail('upstream_unavailable');
    url=safeUrl(loc,url.href);
    if(![307,308].includes(res.status)){method='GET';body=null;headers={};}
    continue;
   }
   if(!htmlResponse(res))fail(res.status===401||res.status===403?
     'authorization_failed':'upstream_unavailable');
   if(Number(res.headers.get('content-length')||0)>650000)
    fail('upstream_unavailable');
   let buffer;try{buffer=await res.arrayBuffer();}catch{fail('upstream_unavailable');}
   if(buffer.byteLength<20||buffer.byteLength>650000)fail('upstream_unavailable');
   const ct=res.headers.get('content-type')||'';
   const charset=/charset\s*=\s*["']?([a-z0-9_-]+)/i.exec(ct)?.[1]||'utf-8';
   let html;try{html=new TextDecoder(/^(gbk|gb2312|gb18030)$/i.test(charset)?
    'gb18030':charset,{fatal:true}).decode(buffer);}catch{fail('upstream_unavailable');}
   return {html,url:url.href};
  }
  fail('interactive_login_required');
 }
 async function inspect(jar){
  const entry=await request(jar,CAS);
  if(safeUrl(entry.url).hostname!=='authserver.ncwu.edu.cn')
   fail('interactive_login_required');
  try{return inspectCasForm(entry.html,entry.url,SERVICE);}
  catch(e){fail('interactive_login_required',e?.reason);}
 }
 async function protectedPrincipal(jar){
  const response=await request(jar,ENTRY);
  if(safeUrl(response.url).origin!==SCHOOL)fail('authorization_failed');
  return parsePrincipal(response.html);
 }
 return Object.freeze({
  provider:'ncwu',
  async preflight(){
   try{
    await inspect(makeNcwuQingguoSessionJar());
    // Do not solicit passwords until a real owner schedule has been reviewed
    // against the official portal, including source-key stability.
    return trustedSyncEnabled?
     {loginSupported:true,mode:'password_form_verified'}:
     {loginSupported:false,mode:'schedule_review_pending'};
   }catch(e){return {loginSupported:false,mode:e?.code==='upstream_unavailable'?
    'upstream_unavailable':'interactive_login_required',
    ...(e?.reason?{reason:e.reason}:{})};}
  },
  async login({username,password,academicUsername,academicPassword}){
   if(!trustedSyncEnabled)fail('interactive_login_required');
   if(!STUDENT.test(username||'')||typeof password!=='string'||
      password.length<1||password.length>512)fail('invalid_credentials');
   const jar=makeNcwuQingguoSessionJar();
   const form=await inspect(jar);
   const response=await request(jar,form.action,{method:'POST',
    headers:{'content-type':'application/x-www-form-urlencoded'},
    body:new URLSearchParams({...form.hidden,username,password}).toString()});
   if(safeUrl(response.url).origin!==SCHOOL)fail('authorization_failed');
   // Some school SSO paths require a second campus-controlled Qingguo login.
   const next=schoolForm(response.html,response.url);
   if(next){
    if(!STUDENT.test(academicUsername||'')||
       typeof academicPassword!=='string'||academicPassword.length<1||
       academicPassword.length>512)fail('interactive_login_required');
    await request(jar,next.url,{method:'POST',
     headers:{'content-type':'application/x-www-form-urlencoded'},
     body:new URLSearchParams({...next.hidden,
      username:academicUsername,password:academicPassword}).toString()});
   }
   const principal=await protectedPrincipal(jar);
   // School login account and authenticated student ID must refer to the
   // same student. A campus-only SSO login does not establish course ownership.
   const expected=next?academicUsername:username;
   if(principal.studentId!==expected)fail('authorization_failed');
   return {session:{studentId:principal.studentId,
    semester:principal.semester,cookies:jar.list},
    expiresAt:new Date(now()+2*3600000).toISOString()};
  },
  async forSession({session,userId}){
   if(!userId||!session||!STUDENT.test(session.studentId||'')||
      typeof session.semester!=='string'||!Array.isArray(session.cookies))
    fail('authorization_failed');
   const jar=makeNcwuQingguoSessionJar(session.cookies);
   let lastPrincipal=null;
   return {
    provider:'ncwu',
    async verifySession(){
     const actual=await protectedPrincipal(jar);
     if(actual.studentId!==session.studentId||
        actual.semester!==session.semester)fail('authorization_failed');
     lastPrincipal=actual;
     return {authorized:true,scope:'self',school:'ncwu',provider:'ncwu',
      officialOrigin:SCHOOL,identityBound:true,principalBoundTo:userId,
      session:{studentId:actual.studentId,semester:actual.semester}};
    },
    async readSection({kind,session:proof}){
     if(kind!=='schedule'||!lastPrincipal||proof?.studentId!==lastPrincipal.studentId||
       proof?.semester!==lastPrincipal.semester)fail('authorization_failed');
     // This single-site double-render check supports a candidate audit, but
     // it is not a human comparison with the official timetable. Until that
     // is completed in production, the explicit release gate stays disabled.
     if(!trustedSyncEnabled)fail('schedule_review_pending');
     const candidate=await readNcwuTimetableCandidate({
      cookie:jar.header(new URL(ENTRY)),semester:lastPrincipal.semester,
      fetchImpl:async(url,opts)=>{
       const target=safeUrl(url);
       if(target.hostname!=='jwmis.ncwu.edu.cn')fail('authorization_failed');
       const cookie=jar.header(target);
       const res=await fetcher(target.href,{...opts,redirect:'manual',
        cache:'no-store',signal:AbortSignal.timeout(15000),
        headers:{...opts.headers,Cookie:cookie}});
       jar.merge(res,target);
       return res;
      }
     });
     const {gridComparison,gridCoverage,sourceClassCodes}=candidate;
     if(candidate.parseCoverage!=='syntactically_complete'||
        !gridComparison?.syntacticMatch||!gridCoverage?.available||
        gridCoverage.blockerCodes.length||gridCoverage.gridItems!==gridCoverage.parsedItems||
        !Array.isArray(candidate.candidate?.records)||
        !candidate.candidate.records.length||
        sourceClassCodes?.length!==candidate.candidate.records.length)
      fail('schedule_review_pending');
     const records=[];
     for(let i=0;i<sourceClassCodes.length;i++){
      const course=candidate.candidate.records[i],code=clean(sourceClassCodes[i]);
      if(!code||code.length>90)fail('schedule_review_pending');
      const identifier=JSON.stringify([lastPrincipal.semester,code,
       course.weekday,course.startSection,course.endSection]);
      const digest=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(identifier));
      const short=Array.from(new Uint8Array(digest)).slice(0,16)
       .map(x=>x.toString(16).padStart(2,'0')).join('');
      records.push({externalId:'qg-'+short,term:lastPrincipal.semester,
       title:course.title,teacher:course.teacher,location:course.location,
       weekday:course.weekday,startSection:course.startSection,
       endSection:course.endSection,weeks:course.weeks});
     }
     if(new Set(records.map(r=>r.externalId)).size!==records.length)
      fail('schedule_review_pending');
     return {kind:'schedule',complete:true,records};
    },
    // Server-internal snapshot after verified principal. Callers persist it
    // ONLY after a complete trusted schedule sync or owner-reviewed re-read.
    // The encrypted vault receives rotated cookies, never browser JSON.
    getRefreshedSession(){
     if(!lastPrincipal)fail('authorization_failed');
     return {studentId:lastPrincipal.studentId,semester:lastPrincipal.semester,
      cookies:jar.list.map(cookie=>({...cookie}))};
    }
   };
  }
 });
}
