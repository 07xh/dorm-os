/**
 * Read-only reachability check for the three public university service entry
 * points. This is NOT authentication, a session probe, or private-data sync.
 * Fixed URLs only, no credentials, cookies, supplied domains or user data.
 */
export const SOURCES=Object.freeze([
 Object.freeze({id:'kingo',name:'华水青果教务',url:'https://jwmis.ncwu.edu.cn/',adapter:'KINGO / 拾光'}),
 Object.freeze({id:'chaoxing',name:'学习通（华水学堂）',url:'https://ncwu.fanya.chaoxing.com/portal',adapter:'xuexitong-cli'}),
 Object.freeze({id:'second',name:'第二课堂',url:'https://dekt.ncwu.edu.cn/',adapter:'官方数据待核对'})
]);
async function probeSource(source,fetcher=fetch){
 let response;
 try{
  // Not following cross-origin login redirects avoids treating login pages as data.
  const options={method:'HEAD',redirect:'manual',credentials:'omit',
   headers:{accept:'text/html,application/xhtml+xml'},signal:AbortSignal.timeout(4500)};
  response=await fetcher(source.url,options);
  // Certain academic sites do not support HEAD; one bounded GET fallback.
  if(response.status===405||response.status===501)
   response=await fetcher(source.url,{...options,method:'GET'});
  const origin=new URL(response.url||source.url).origin;
  if(origin!==new URL(source.url).origin)throw Error('Off-origin response');
  const status=Number(response.status);
  if(!Number.isInteger(status)||status<100||status>599)throw Error('Unexpected HTTP status');
  return {
   id:source.id,name:source.name,url:source.url,adapter:source.adapter,
   reachability:status>=200&&status<500?'responded':'unavailable',
   httpStatus:status,accountStatus:'not-connected'
  };
 }catch{
  return {id:source.id,name:source.name,url:source.url,adapter:source.adapter,
   reachability:'unavailable',httpStatus:null,accountStatus:'not-connected'};
 }finally{
  try{await response?.body?.cancel?.();}catch{}
 }
}
export async function checkSources(fetcher=fetch){
 const sources=await Promise.all(SOURCES.map(source=>probeSource(source,fetcher)));
 return {schema:'xiaoxu/academic-readiness-v1',checkedAt:new Date().toISOString(),
  kind:'public-entry-reachability-only',personalData:false,
  accountConnected:false,fullyVerified:false,sources};
}
const HEADERS={'content-type':'application/json; charset=utf-8','x-content-type-options':'nosniff',
 'x-xiaoxu-academic-readiness':'1','cache-control':'public, max-age=300, s-maxage=900'};
export async function onRequestGet({request,waitUntil}){
 const edge=globalThis.caches?.default;
 const key=new Request(new URL('/api/academic/status',request.url).href);
 if(edge){try{const hit=await edge.match(key);if(hit)return hit;}catch{}}
 const payload=await checkSources();
 const response=new Response(JSON.stringify(payload),{status:200,headers:HEADERS});
 if(edge&&waitUntil)waitUntil(edge.put(key,response.clone()).catch(()=>{}));
 return response;
}
