/* Private server-side Chaoxing adapter. Adapted from MIT xuexitong-cli.
 * Uses normal official login and verified account identity; never bypasses a challenge.
 * Passwords exist only for the login request. Cookies stay in Academic OS AES-GCM vault.
 */
import {parseChaoxingCourseList,parseChaoxingWorkCredentials,
 parseChaoxingWorkList,safeChaoxingWorkUrl,classifyChaoxingWorkStatus} from './academic-chaoxing-work-discovery.mjs';
import {parseChaoxingWorkDetailHtml,parseChaoxingWorkTimeWindow} from './academic-chaoxing-work-detail.mjs';
import {load} from 'cheerio';
import qrcode from './vendor/qrcode-generator.mjs';
import {CHAOXING_SESSION_MAX_AGE_MS} from './academic-chaoxing-session-policy.mjs';
const LOGIN='https://passport2.chaoxing.com/fanyalogin';
const QR_LOGIN='https://passport2.chaoxing.com/login?fid=&newversion=true&refer=https%3A%2F%2Fi.chaoxing.com';
const QR_STATUS='https://passport2.chaoxing.com/getauthstatus';
const QR_TIMEOUT_MS=165000;
const QR_FIELD=/^[A-Za-z0-9_+\/=:%-]{8,256}$/;
const COURSES='https://mooc2-ans.chaoxing.com/mooc2-ans/visit/courselistdata';
const MIDDLE='https://mooc1.chaoxing.com/visit/stucoursemiddle';
// Both endpoints are documented in read-only Chaoxing clients. Preserve
// the working legacy route and retry the modern route only on template failure.
const WORKLIST='https://mooc1.chaoxing.com/mooc2/work/list';
const WORKLIST_ALT='https://mooc1.chaoxing.com/mooc-ans/mooc2/work/list';
const BASE='https://i.chaoxing.com/base';
const NOTICE='https://notice.chaoxing.com/pc/notice/getNoticeList';
const KEY=new TextEncoder().encode('u2oh6Vu^HWe4_AES');
const HOSTS=new Set(['passport2.chaoxing.com','i.chaoxing.com','notice.chaoxing.com','mooc1.chaoxing.com','mooc2-ans.chaoxing.com']);
const UID=/^[0-9]{1,24}$/;
class SchoolError extends Error {constructor(code){super(code);this.code=code;}}
const fail=code=>{throw new SchoolError(code);};
// Only these non-sensitive, code-owned stage labels may cross an authenticated
// API boundary. No remote HTML, signed URLs, status text or request params.
const SAFE_READ_STAGES=new Set(['course_catalog','course_identity',
  'course_entry','list_legacy','list_official','list_integrity']);
function atSchoolStage(stage,error){
 const safe=SAFE_READ_STAGES.has(stage)?stage:'list_integrity';
 const e=error&&typeof error==='object'?error:
   new SchoolError('upstream_unavailable');
 e.schoolReadStage=safe;
 throw e;
}

function jarValid(jar){
 return Array.isArray(jar)&&jar.length<=50&&jar.every(c=>
  c&&typeof c.name==='string'&&/^[A-Za-z_][A-Za-z0-9_-]{0,100}$/.test(c.name)&&
  typeof c.value==='string'&&c.value.length<4000&&!/[\r\n;]/.test(c.value)&&
  (c.domain==='chaoxing.com'||HOSTS.has(c.domain))&&
  typeof c.path==='string'&&c.path.startsWith('/')&&typeof c.hostOnly==='boolean');
}
function cookieHeader(u,jar){
 return jar.filter(c=>(u.hostname===c.domain||
  (!c.hostOnly&&u.hostname.endsWith('.'+c.domain)))&&
  u.pathname.startsWith(c.path)).map(c=>c.name+'='+c.value).join('; ');
}
function validQrChallenge(state,clock){
 if(state?.schema!=='chaoxing-qr-v1'||!QR_FIELD.test(state.uuid||'')||
    !QR_FIELD.test(state.enc||'')||!jarValid(state.jar)||
    !Number.isSafeInteger(state.issuedAt)||state.issuedAt>clock()+5000||
    clock()-state.issuedAt>QR_TIMEOUT_MS)fail('session_expired');
 return state;
}
function takeCookies(response,host,jar){
 const h=response.headers;
 const values=typeof h.getAll==='function'?h.getAll('Set-Cookie'):
  typeof h.getSetCookie==='function'?h.getSetCookie():
  h.get('set-cookie')?[h.get('set-cookie')]:[];
 for(const line of values){
  if(typeof line!=='string'||line.length>5000)continue;
  const pieces=line.split(';').map(x=>x.trim()),first=pieces.shift()||'',eq=first.indexOf('=');
  if(eq<=0)continue;
  const name=first.slice(0,eq),value=first.slice(eq+1);
  if(!/^[A-Za-z_][A-Za-z0-9_-]{0,100}$/.test(name)||value.length>=4000||/[\r\n;]/.test(value))continue;
  let domain=host,path='/',hostOnly=true,drop=false,illegal=false;
  for(const piece of pieces){
   const i=piece.indexOf('='),key=(i<0?piece:piece.slice(0,i)).toLowerCase();
   const val=i<0?'':piece.slice(i+1);
   if(key==='domain'){
    const candidate=val.toLowerCase().replace(/^\./,'');
    if((candidate!=='chaoxing.com'&&!HOSTS.has(candidate))||
       (host!==candidate&&!host.endsWith('.'+candidate))){illegal=true;break;}
    domain=candidate;hostOnly=false;
   }else if(key==='path'&&val.startsWith('/')&&!val.startsWith('//'))path=val.slice(0,100);
   else if(key==='max-age'&&Number(val)<=0)drop=true;
  }
  if(illegal)continue;
  const at=jar.findIndex(c=>c.name===name&&c.domain===domain&&c.path===path);
  if(at>=0)jar.splice(at,1);
  if(!drop)jar.push({name,value,domain,path,hostOnly});
  if(!jarValid(jar))fail('authorization_failed');
 }
}
async function request(fetchImpl,start,jar,{method='GET',body}={}){
 let url=start;
 for(let i=0;i<5;i++){
  const u=new URL(url);
  if(u.protocol!=='https:'||!HOSTS.has(u.hostname)||(u.port&&u.port!=='443')||
     u.username||u.password)fail('upstream_unavailable');
  const headers=new Headers({'accept':'*/*','cache-control':'no-store',
   'user-agent':'Mozilla/5.0 Chrome/125.0.0.0 Safari/537.36'});
  const cookie=cookieHeader(u,jar);
  if(cookie)headers.set('cookie',cookie);
  if(body)headers.set('content-type','application/x-www-form-urlencoded; charset=UTF-8');
  if(u.hostname==='passport2.chaoxing.com'&&u.pathname==='/getauthstatus'&&method==='POST'){
   headers.set('origin','https://passport2.chaoxing.com');
   headers.set('referer','https://passport2.chaoxing.com/mlogin');
   headers.set('x-requested-with','XMLHttpRequest');
  }
  if(u.hostname==='notice.chaoxing.com'&&method==='POST'){
   headers.set('origin','https://notice.chaoxing.com');
   headers.set('referer','https://notice.chaoxing.com/pc/notice/myNotice');
   headers.set('x-requested-with','XMLHttpRequest');
  }
  let r;
  try{r=await fetchImpl(u.href,{method,body,headers,redirect:'manual',
   cache:'no-store',signal:AbortSignal.timeout(12000)});}catch{fail('upstream_unavailable');}
  takeCookies(r,u.hostname,jar);
  if([301,302,303,307,308].includes(r.status)){
   const location=r.headers.get('location');
   if(!location)fail('authorization_failed');
   // A 307/308 must not silently downgrade an upstream password POST to GET.
   // Never replay a password-bearing POST to a redirected destination.
   if([307,308].includes(r.status)&&method!=='GET')fail('interactive_login_required');
   url=new URL(location,u).href;method='GET';body=undefined;continue;
  }
  if(r.status===401)fail('authorization_failed');
  // A 403 may be an upstream access/rate/challenge decision; it does not
  // establish that the verified owner's login is no longer valid.
  if(r.status===403||r.status===429||r.status>=500)fail('upstream_unavailable');
  if(!r.ok)fail('invalid_response');
  if(Number(r.headers.get('content-length')||0)>420000)fail('invalid_response');
  const text=await r.text();
  if(text.length>420000)fail('invalid_response');
  return {text,url:u.href};
 }
 fail('authorization_failed');
}
async function encrypt(value){
 if(typeof value!=='string'||!value||value.length>512)fail('invalid_credentials');
 const key=await crypto.subtle.importKey('raw',KEY,{name:'AES-CBC'},false,['encrypt']);
 const encrypted=await crypto.subtle.encrypt({name:'AES-CBC',iv:KEY},key,new TextEncoder().encode(value));
 return btoa(String.fromCharCode(...new Uint8Array(encrypted)));
}
async function profileDetails(fetchImpl,jar){
 const p=await request(fetchImpl,BASE,jar);
 if(new URL(p.url).hostname!=='i.chaoxing.com')fail('authorization_failed');
 const s=p.text;
 const uid=/(?:var\s+uid\s*=\s*['"]?|"uid"\s*:\s*["]?)(\d{1,24})/.exec(s)?.[1];
 const name=/aria-label=["']账号：([^"'<>]{1,45})["']/.exec(s)?.[1];
 const school=/<[^>]*id=["']siteName["'][^>]*title=["']([^"'<>]{1,90})["']/.exec(s)?.[1];
 if(!uid||!UID.test(uid)||!name||!school||!school.includes('华北水利水电大学'))
  fail('authorization_failed');
 // The official course list needs a school/course Fid from this same
 // verified owner profile. Never substitute someone else's default Fid.
 const fid=/(?:["']fid["']\s*:\s*["']?|\bvar\s+fid\s*=\s*["']?)(\d{1,24})/i.exec(s)?.[1]||null;
 return {uid,fid};
}
async function profile(fetchImpl,jar){
 return (await profileDetails(fetchImpl,jar)).uid;
}
/* An explicit export of the owner's official Chaoxing browser cookies.
 * Keep the original domain + path restriction. Never read browser cookies
 * across origins, expand a host-only cookie or accept caller-supplied URLs.
 */
export function validateChaoxingCookieExport(input){
 if(!Array.isArray(input)||input.length<1||input.length>40)
  fail('invalid_cookie_format');
 const jar=[],seen=new Set();
 for(const cookie of input){
  if(!cookie||typeof cookie!=='object'||Array.isArray(cookie))
   fail('invalid_cookie_format');
  const rawDomain=cookie.domain;
  if(typeof rawDomain!=='string'||rawDomain.length>80)
   fail('invalid_cookie_format');
  const domain=rawDomain.replace(/^\./,'').toLowerCase();
  const hostOnly=cookie.hostOnly===undefined?!rawDomain.startsWith('.'):
   cookie.hostOnly===true;
  if(!['chaoxing.com',...HOSTS].includes(domain)||
     (domain==='chaoxing.com'&&hostOnly)||
     (rawDomain.startsWith('.')&&hostOnly)||
     (cookie.hostOnly!==undefined&&typeof cookie.hostOnly!=='boolean')||
     typeof cookie.name!=='string'||!/^[A-Za-z_][A-Za-z0-9_-]{0,100}$/.test(cookie.name)||
     typeof cookie.value!=='string'||!cookie.value||cookie.value.length>3900||
     /[\r\n;\x00-\x1f\x7f]/.test(cookie.value)||
     (cookie.path!==undefined&&typeof cookie.path!=='string'))
   fail('invalid_cookie_format');
  const path=cookie.path||'/';
  if(!path.startsWith('/')||path.startsWith('//')||path.length>120)
   fail('invalid_cookie_format');
  if(cookie.name.startsWith('__Host-')&&(!hostOnly||path!=='/'))
   fail('invalid_cookie_format');
  const identity=[domain,path,cookie.name].join('|');
  if(seen.has(identity))fail('invalid_cookie_format');
  seen.add(identity);
  jar.push({name:cookie.name,value:cookie.value,domain,path,hostOnly});
 }
 if(!jarValid(jar))fail('invalid_cookie_format');
 return jar;
}

export function createChaoxingSchoolAdapter({fetchImpl=fetch,clock=()=>Date.now()}={}){
 if(typeof fetchImpl!=='function')throw Error('invalid_adapter_configuration');
 return Object.freeze({
  provider:'chaoxing',
  // QR login reuses the exact same verified Chaoxing session shape as
  // password login. A short-lived encrypted challenge is carried by the
  // owner browser, never an upstream Cookie or plaintext QR status token.
  async beginQrLogin(){
   const jar=[];
   const page=await request(fetchImpl,QR_LOGIN,jar);
   if(new URL(page.url).hostname!=='passport2.chaoxing.com')
    fail('interactive_login_required');
   const $=load(page.text);
   const uuid=String($('#uuid').val()||$('input[name="uuid"]').first().val()||'');
   const enc=String($('#enc').val()||$('input[name="enc"]').first().val()||'');
   if(!QR_FIELD.test(uuid)||!QR_FIELD.test(enc)||!jarValid(jar))
    fail('interactive_login_required');
   const image=new URL('https://passport2.chaoxing.com/createqr');
   image.searchParams.set('uuid',uuid);
   image.searchParams.set('fid','-1');
   return {qrImageUrl:image.href,expiresAt:new Date(clock()+QR_TIMEOUT_MS).toISOString(),
    challenge:{schema:'chaoxing-qr-v1',uuid,enc,jar,issuedAt:clock()}};
  },
  async readQrImage({challenge}){
   const state=validQrChallenge(challenge,clock);
   // Official endpoint may reject an image request from a cloud worker even
   // when /login produced a valid challenge. Never proxy HTML/redirects.
   // On failure, encode the *official* toauthlogin link with the same
   // uuid/enc in a self-hosted, MIT QR renderer; no invented login ticket.
   const url=new URL('https://passport2.chaoxing.com/createqr');
   url.searchParams.set('uuid',state.uuid);
   url.searchParams.set('fid','-1');
   const headers=new Headers({'accept':'image/png, image/jpeg, image/*',
    'referer':'https://passport2.chaoxing.com/mlogin',
    'user-agent':'Mozilla/5.0 Chrome/125.0.0.0 Safari/537.36',
    'cache-control':'no-store'});
   const cookie=cookieHeader(url,state.jar);
   if(cookie)headers.set('cookie',cookie);
   try{
    const response=await fetchImpl(url.href,{method:'GET',headers,
     redirect:'manual',cache:'no-store',signal:AbortSignal.timeout(12000)});
    if(response.status===200&&
       Number(response.headers.get('content-length')||0)<=180000){
     const bytes=new Uint8Array(await response.arrayBuffer());
     const png=bytes.length>=64&&[137,80,78,71,13,10,26,10].every((v,i)=>bytes[i]===v);
     const jpeg=bytes.length>=64&&bytes[0]===255&&bytes[1]===216&&
      bytes[bytes.length-2]===255&&bytes[bytes.length-1]===217;
     if(bytes.length<=180000&&(png||jpeg))
      return {contentType:png?'image/png':'image/jpeg',body:bytes,source:'official'};
    }
   }catch{} // Official QR image is optional; auth polling remains server-bound.
   const destination=new URL('https://passport2.chaoxing.com/toauthlogin');
   destination.searchParams.set('uuid',state.uuid);
   destination.searchParams.set('enc',state.enc);
   destination.searchParams.set('type','0');
   try{
    const qr=qrcode(0,'M');
    qr.addData(destination.href,'Byte');
    qr.make();
    const svg=qr.createSvgTag(4,4);
    if(typeof svg!=='string'||svg.length<500||svg.length>220000||
       !svg.includes('<svg')||!svg.includes('</svg>'))fail('upstream_unavailable');
    return {contentType:'image/svg+xml',body:svg,source:'local'};
   }catch{fail('upstream_unavailable');}
  },
  async pollQrLogin({challenge}){
   const state=validQrChallenge(challenge,clock);
   const jar=state.jar;
   const body=new URLSearchParams({enc:state.enc,uuid:state.uuid});
   const response=await request(fetchImpl,QR_STATUS,jar,{method:'POST',body:body.toString()});
   if(new URL(response.url).hostname!=='passport2.chaoxing.com')
    fail('authorization_failed');
   let parsed;try{parsed=JSON.parse(response.text)}catch{fail('interactive_login_required');}
   if(!parsed||typeof parsed!=='object'||Array.isArray(parsed))
    fail('interactive_login_required');
   const data=parsed.data&&typeof parsed.data==='object'?parsed.data:parsed;
   if(data.status===true){
    // The official QR flow completes its refer hop before the account profile
    // becomes readable. Follow it with the same server-side cookie jar.
    await request(fetchImpl,'https://i.chaoxing.com/',jar);
    const uid=await profile(fetchImpl,jar);
    if(!jar.length||!jarValid(jar))fail('authorization_failed');
    return {status:'approved',session:{schema:'chaoxing-session-v1',uid,jar},
     expiresAt:new Date(clock()+CHAOXING_SESSION_MAX_AGE_MS).toISOString()};
   }
   if(data.type===6)return {status:'cancelled'};
   if(data.type===4)return {status:'scanned',challenge:{...state,jar}};
   return {status:'pending',challenge:{...state,jar}};
  },
  async login({username,password}){
   if(typeof username!=='string'||!username.trim()||typeof password!=='string'||!password)
    fail('invalid_credentials');
   const jar=[];
   const form=new URLSearchParams({fid:'-1',uname:await encrypt(username),
    password:await encrypt(password),refer:'https%3A%2F%2Fi.chaoxing.com',
    t:'true',forbidotherlogin:'0',validate:'',doubleFactorLogin:'0',independentId:'0'});
   const result=await request(fetchImpl,LOGIN,jar,{method:'POST',body:form.toString()});
   let data;try{data=JSON.parse(result.text)}catch{fail('interactive_login_required');}
   if(data.status!==true)fail('interactive_login_required');
   const uid=await profile(fetchImpl,jar);
   if(!jar.length||!jarValid(jar))fail('authorization_failed');
   return {session:{schema:'chaoxing-session-v1',uid,jar},
    expiresAt:new Date(clock()+CHAOXING_SESSION_MAX_AGE_MS).toISOString()};
  },
  async importSession({cookies}){
   const jar=validateChaoxingCookieExport(cookies);
   // The official profile binds the UID; browser-provided identity is ignored.
   const uid=await profile(fetchImpl,jar);
   return {session:{schema:'chaoxing-session-v1',uid,jar},
    expiresAt:new Date(clock()+CHAOXING_SESSION_MAX_AGE_MS).toISOString()};
  },
  async forSession({session,userId}){
   if(!session||session.schema!=='chaoxing-session-v1'||!UID.test(session.uid||'')||
      !jarValid(session.jar)||typeof userId!=='string'||!userId)fail('authorization_failed');
   const jar=session.jar,principal=session.uid;
   let proof=null,verifiedCourseFid=null;
   const NUM=/^\d{1,24}$/;
   async function courses(){
    if(proof!==principal)fail('authorization_failed');
    if(!verifiedCourseFid)fail('courses_unverified');
    const url=new URL(COURSES);
    for(const [key,value] of Object.entries({courseType:'1',
      courseFid:verifiedCourseFid,courseTeacherid:'0',courseOrderBy:'0'}))
     url.searchParams.set(key,value);
    const page=await request(fetchImpl,url.href,jar);
    if(new URL(page.url).hostname!=='mooc2-ans.chaoxing.com')fail('authorization_failed');
    return parseChaoxingCourseList(page.text);
   }
   async function workItems(input){
    if(proof!==principal)fail('authorization_failed');
    const {courseId,classId,cpi}=input||{};
    if(![courseId,classId,cpi].every(x=>typeof x==='string'&&NUM.test(x)))
     fail('invalid_response');
    const owned=(await courses().catch(e=>
      atSchoolStage('course_catalog',e))).find(c=>c.courseId===courseId&&
      c.classId===classId&&c.cpi===cpi);
    if(!owned)atSchoolStage('course_identity',new SchoolError('authorization_failed'));
    const m=new URL(MIDDLE);
    for(const [k,v] of Object.entries({courseid:courseId,clazzid:classId,cpi,
     ismooc2:'1',v:'2'}))m.searchParams.set(k,v);
    const page=await request(fetchImpl,m.href,jar).catch(e=>
      atSchoolStage('course_entry',e));
    if(new URL(page.url).hostname==='passport2.chaoxing.com')
      atSchoolStage('course_entry',new SchoolError('authorization_failed'));
    // Reference mobile clients can discover official homework directly on
    // the signed-in course entry (.bottomList li[data]) without workEnc.
    // Keep this as bounded, individual verified evidence, not a fabricated
    // complete course count, and prefer the normal paginated route when valid.
    let embedded=null;
    if(page.text.includes('bottomList')){
     try{
      const result=parseChaoxingWorkList(page.text,{courseId,classId},
        {embedded:true});
      if(result.items.length && result.totalPages===1 &&
          (!result.progress||result.progress.total===result.items.length))
       embedded={owned,items:result.items,progress:result.progress,
         verifiedEmpty:false};
     }catch(error){
      // A partial course panel may have a separate paginated list. Continue
      // to that list rather than pretending the embedded panel is complete.
      if(error?.code!=='work_list_incomplete')
       atSchoolStage('course_entry',error);
     }
    }
    let tokens;
    try{tokens=parseChaoxingWorkCredentials(page.text,{finalUrl:page.url});}
    catch(e){
     // A legitimate course-specific task panel can exist without workEnc.
     if(embedded)return embedded;
     atSchoolStage('course_entry',e);
    }
    async function readFromListEndpoint(listEndpoint,{compact=false}={}){
    const items=[],seen=new Set();
    let pageNumber=1,totalPages=1,progress=null,verifiedEmpty=false;
    // The signed course URL remains fixed. Only the official bounded pageNum
    // changes; no user-provided upstream URL, filter or authentication bypass.
    do{
     const u=new URL(listEndpoint);
     // The newer /mooc-ans/ route is documented with a compact query:
     // courseId, classId, cpi, enc and pageNum. Legacy templates instead
     // require ut/t/isdisplaytable and sometimes stuenc/openc.
     // Never alter the working default path; compact mode is only a bounded
     // same-course fallback after the existing signed requests fail.
     const params=compact
       ? {courseId,classId,cpi,enc:tokens.workEnc,pageNum:String(pageNumber)}
       : {courseId,classId,cpi,ut:'s',enc:tokens.workEnc,t:String(clock()),
         isdisplaytable:'2',pageNum:String(pageNumber)};
     if(!compact){
      if(tokens.openc)params.openc=tokens.openc;
      if(tokens.pageEnc)params.stuenc=tokens.pageEnc;
     }
     for(const [k,v] of Object.entries(params))u.searchParams.set(k,v);
     const list=await request(fetchImpl,u.href,jar);
     if(new URL(list.url).hostname==='passport2.chaoxing.com')
      fail('authorization_failed');
     const parsed=parseChaoxingWorkList(list.text,{courseId,classId},
       {pageNumber});
     totalPages=Math.max(totalPages,parsed.totalPages);
     if(totalPages>10)fail('work_list_incomplete');
     if(pageNumber===1)verifiedEmpty=parsed.verifiedEmpty;
     if(pageNumber===1&&totalPages>1&&!parsed.items.length)fail('work_list_incomplete');
     if(parsed.progress){
      if(progress&&(progress.completed!==parsed.progress.completed||
          progress.total!==parsed.progress.total))fail('work_list_incomplete');
      progress=parsed.progress;
     }
     for(const item of parsed.items){
      if(seen.has(item.workId))fail('work_list_incomplete');
      seen.add(item.workId);items.push(item);
      if(items.length>200)fail('work_list_incomplete');
     }
     pageNumber++;
    }while(pageNumber<=totalPages);
    // No partial results are returned, persisted, or marked submitted.
    if(progress&&progress.total!==items.length)fail('work_list_incomplete');

     // An unverified empty HTML shell must not count as full work coverage.
     if(!items.length&&!verifiedEmpty)fail('work_not_available');
     return {owned,items,progress,verifiedEmpty};
    }
    // Same verified owner, course and signing token for both routes.
    // Never retry identity, authorization or human verification failures.
    let first=null,firstError=null;
    try{
     first=await readFromListEndpoint(WORKLIST);
     if(first.items.length)return first;
    }catch(error){
     if(!['invalid_response','work_not_available','work_list_incomplete',
       'upstream_unavailable'].includes(error?.code))
       atSchoolStage(error?.code==='work_list_incomplete'?
         'list_integrity':'list_legacy',error);
     if(!error||typeof error!=='object')
       atSchoolStage('list_legacy',error);
     error.schoolReadStage=error.code==='work_list_incomplete'?
       'list_integrity':'list_legacy';
     firstError=error;
    }
    try{
     // The newer route may expose completed work omitted by a legacy view.
     return await readFromListEndpoint(WORKLIST_ALT);
    }catch(error){
     // One additional documented query shape for the SAME official endpoint,
     // verified owner and signed work token. Strict parser/pagination/identity
     // checks are unchanged. Never retry a login, challenge or ownership error.
     if(['invalid_response','work_not_available','work_list_incomplete',
         'upstream_unavailable'].includes(error?.code)){
      try{
       return await readFromListEndpoint(WORKLIST_ALT,{compact:true});
      }catch(compactError){
       // Do not turn a new login denial into stale data or an empty list.
       if(!['invalid_response','work_not_available','work_list_incomplete',
           'upstream_unavailable'].includes(compactError?.code))
        atSchoolStage('list_official',compactError);
       error=compactError;
      }
     }
     if(first?.verifiedEmpty &&
        !['authorization_failed','interactive_login_required',
          'session_expired'].includes(error?.code))return first;
     if(firstError&&error?.code==='upstream_unavailable'){
      if(embedded&&firstError.code!=='authorization_failed')return embedded;
      throw firstError;
     }
     // A validated owner/course panel is useful partial status evidence if
     // neither list route was available. Never downgrade official auth failure.
     if(embedded&&['work_not_available','invalid_response',
       'work_list_incomplete','upstream_unavailable'].includes(error?.code))
      return embedded;
     atSchoolStage(error?.code==='work_list_incomplete'?
       'list_integrity':'list_official',error);
    }
   }
   return {
    provider:'chaoxing',
    // Expose a fresh copy only after the official profile bound this reader
    // to its owner. The caller keeps it in the encrypted server-side vault.
    getRefreshedSession(){
     if(proof!==principal||!jarValid(jar))fail('authorization_failed');
     return {schema:'chaoxing-session-v1',uid:principal,
      jar:jar.map(cookie=>({...cookie}))};
    },
    async verifySession(){
     const current=await profileDetails(fetchImpl,jar);
     if(current.uid!==principal)fail('authorization_failed');
     proof=current.uid;verifiedCourseFid=current.fid;
     return {authorized:true,scope:'self',school:'ncwu',provider:'chaoxing',
      identityBound:true,principalBoundTo:userId,session:{uid:current.uid}};
    },
    async readCourses(){
     return (await courses()).map(x=>({...x}));
    },
    async readHomeworkList(keys){
     const data=await workItems(keys);
     return {course:data.owned,verifiedEmpty:data.verifiedEmpty,
      progress:data.progress,
      items:data.items.map(({workId,title,status})=>({
       workId,title,status,statusCode:classifyChaoxingWorkStatus(status),
       platformVerified:true}))};
    },
    async readHomeworkDetail(keys){
     const id=keys?.workId;
     if(typeof id!=='string'||!NUM.test(id))fail('invalid_response');
     const data=await workItems(keys);
     const item=data.items.find(x=>x.workId===id);
     if(!item)fail('not_found');
     const response=await request(fetchImpl,item.url,jar);
     let html=response.text;
     let $=load(html);
     if(!$('form#submitForm .questionLi').length){
      const href=$('a[href*="/work/dowork"]').first().attr('href');
      if(href){
       const work=safeChaoxingWorkUrl(href,{courseId:keys.courseId,classId:keys.classId});
       if(work.workId!==id)fail('invalid_response');
       html=(await request(fetchImpl,work.url,jar)).text;
       $=load(html);
      }
     }
     let preview=null;
     if($('form#submitForm .questionLi').length)
      preview=parseChaoxingWorkDetailHtml(html);
     const body=$('body').text().replace(/\s+/g,' ');
     const count=/题量[:：]\s*(\d{1,3})/.exec(body);
     const timing=parseChaoxingWorkTimeWindow(html);
     return {schema:'xiaoxu/chaoxing-work-preview-v1',source:'chaoxing',
      title:item.title,course:data.owned.title,status:item.status,
      statusCode:classifyChaoxingWorkStatus(item.status),workId:id,
      declaredQuestionCount:count?Number(count[1]):null,
      startTimeRaw:timing?.startTimeRaw??null,
      deadlineRaw:timing?.deadlineRaw??null,
      questionsAvailable:!!preview,questions:preview?.questions||[],
      note:preview?'仅展示本页可读取的题目，图片需到学习通查看':'该页面未提供可读取的题目表单'};
    },
    async readNoticePage({cursor}){
     if(proof!==principal)fail('authorization_failed');
     if(cursor!=null&&!/^[A-Za-z0-9_-]{1,100}$/.test(cursor))fail('invalid_response');
     const year=new Intl.DateTimeFormat('en-US',{year:'numeric',timeZone:'Asia/Shanghai'})
      .format(new Date(clock()));
     const form=new URLSearchParams({type:'2',notice_type:'',lastValue:cursor||'',
      sort:'',folderUUID:'',kw:'',startTime:'',endTime:'',gKw:'',gName:'',year,
      tag:'',fidsCode:'',queryFolderNoticePrevYear:'0',filterSenderPuids:'',filterTags:''});
     const result=await request(fetchImpl,NOTICE,jar,{method:'POST',body:form.toString()});
     if(new URL(result.url).hostname!=='notice.chaoxing.com')fail('authorization_failed');
     let data;try{data=JSON.parse(result.text)}catch{fail('invalid_response');}
     if(data?.status!==true)fail('invalid_response');
     // Captured HAR: after a 12-item page with lastPage=0 the next page may
     // return only {status:true}. Accept only as the END of a prior nonempty
     // page, never as a verified empty inbox for the first request.
     if(!data.notices&&cursor){
      return {payload:{status:true,notices:{list:[],lastPage:true,lastGetId:''}},
       complete:true,nextCursor:null,verifiedEmpty:false,terminalInferred:true};
     }
     if(!data.notices||!Array.isArray(data.notices.list)||
        data.notices.list.length>100)fail('invalid_response');
     const n=data.notices;
     // Do not infer completion from a short page: real HAR has 12 items
     // but lastPage=0 and a nonempty lastGetId.
     const complete=n.lastPage===1||n.lastPage===true;
     const nextCursor=complete?null:n.lastGetId;
     if(!complete&&!/^[A-Za-z0-9_-]{1,100}$/.test(nextCursor||''))fail('invalid_response');
     return {payload:data,complete,nextCursor,
      verifiedEmpty:complete&&n.list.length===0};
    }
   };
  }
 });
}
