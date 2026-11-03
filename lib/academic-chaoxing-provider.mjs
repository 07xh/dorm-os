/* Private server-side Chaoxing adapter. Adapted from MIT xuexitong-cli.
 * Uses normal official login and verified account identity; never bypasses a challenge.
 * Passwords exist only for the login request. Cookies stay in Academic OS AES-GCM vault.
 */
const LOGIN='https://passport2.chaoxing.com/fanyalogin';
const BASE='https://i.chaoxing.com/base';
const NOTICE='https://notice.chaoxing.com/pc/notice/getNoticeList';
const KEY=new TextEncoder().encode('u2oh6Vu^HWe4_AES');
const HOSTS=new Set(['passport2.chaoxing.com','i.chaoxing.com','notice.chaoxing.com']);
const UID=/^[0-9]{1,24}$/;
class SchoolError extends Error {constructor(code){super(code);this.code=code;}}
const fail=code=>{throw new SchoolError(code);};
function jarValid(jar){
 return Array.isArray(jar)&&jar.length<=50&&jar.every(c=>
  c&&typeof c.name==='string'&&/^[A-Za-z_][A-Za-z0-9_-]{0,100}$/.test(c.name)&&
  typeof c.value==='string'&&c.value.length<4000&&!/[\r\n;]/.test(c.value)&&
  (c.domain==='chaoxing.com'||HOSTS.has(c.domain))&&
  typeof c.path==='string'&&c.path.startsWith('/')&&typeof c.hostOnly==='boolean');
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
  if(u.protocol!=='https:'||!HOSTS.has(u.hostname)||u.username||u.password)fail('upstream_unavailable');
  const headers=new Headers({'accept':'*/*','cache-control':'no-store',
   'user-agent':'Mozilla/5.0 Chrome/125.0.0.0 Safari/537.36'});
  const cookie=jar.filter(c=>(u.hostname===c.domain||(!c.hostOnly&&u.hostname.endsWith('.'+c.domain)))&&
   u.pathname.startsWith(c.path)).map(c=>c.name+'='+c.value).join('; ');
  if(cookie)headers.set('cookie',cookie);
  if(body)headers.set('content-type','application/x-www-form-urlencoded; charset=UTF-8');
  let r;
  try{r=await fetchImpl(u.href,{method,body,headers,redirect:'manual',
   cache:'no-store',signal:AbortSignal.timeout(12000)});}catch{fail('upstream_unavailable');}
  takeCookies(r,u.hostname,jar);
  if([301,302,303,307,308].includes(r.status)){
   const location=r.headers.get('location');
   if(!location)fail('authorization_failed');
   url=new URL(location,u).href;method='GET';body=undefined;continue;
  }
  if(r.status===401||r.status===403)fail('authorization_failed');
  if(r.status===429||r.status>=500)fail('upstream_unavailable');
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
async function profile(fetchImpl,jar){
 const p=await request(fetchImpl,BASE,jar);
 if(new URL(p.url).hostname!=='i.chaoxing.com')fail('authorization_failed');
 const s=p.text;
 const uid=/(?:var\s+uid\s*=\s*['"]?|"uid"\s*:\s*["]?)(\d{1,24})/.exec(s)?.[1];
 const name=/aria-label=["']账号：([^"'<>]{1,45})["']/.exec(s)?.[1];
 const school=/<[^>]*id=["']siteName["'][^>]*title=["']([^"'<>]{1,90})["']/.exec(s)?.[1];
 if(!uid||!UID.test(uid)||!name||!school||!school.includes('华北水利水电大学'))
  fail('authorization_failed');
 return uid;
}
export function createChaoxingSchoolAdapter({fetchImpl=fetch,clock=()=>Date.now()}={}){
 if(typeof fetchImpl!=='function')throw Error('invalid_adapter_configuration');
 return Object.freeze({
  provider:'chaoxing',
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
    expiresAt:new Date(clock()+3*86400000).toISOString()};
  },
  async forSession({session,userId}){
   if(!session||session.schema!=='chaoxing-session-v1'||!UID.test(session.uid||'')||
      !jarValid(session.jar)||typeof userId!=='string'||!userId)fail('authorization_failed');
   const jar=session.jar,principal=session.uid;
   let proof=null;
   return {
    provider:'chaoxing',
    async verifySession(){
     const current=await profile(fetchImpl,jar);
     if(current!==principal)fail('authorization_failed');
     proof=current;
     return {authorized:true,scope:'self',school:'ncwu',provider:'chaoxing',
      identityBound:true,principalBoundTo:userId,session:{uid:current}};
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
     let data;try{data=JSON.parse(result.text)}catch{fail('invalid_response');}
     if(data?.status!==true||!data.notices||!Array.isArray(data.notices.list)||
        data.notices.list.length>100)fail('invalid_response');
     const n=data.notices;
     const complete=n.lastPage===1||n.lastPage===true||n.list.length<20;
     const nextCursor=complete?null:n.lastGetId;
     if(!complete&&!/^[A-Za-z0-9_-]{1,100}$/.test(nextCursor||''))fail('invalid_response');
     // Complete means the visible inbox window was scanned, not every school task.
     return {payload:data,complete,nextCursor,
      verifiedEmpty:complete&&n.list.length===0};
    }
   };
  }
 });
}
