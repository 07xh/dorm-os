/* Cloudflare Pages Function: public NCWU academic notice aggregator.
 * Borrowed architecture (not code): notice-radar (MIT), rss-worker (MIT).
 * Only fixed official URLs; no user-provided destination and no credentials.
 */
import {SOURCES,parseNotices,mergeNotices} from '../../lib/notice-feed.mjs';

const HEADERS={'content-type':'application/json; charset=utf-8',
  'x-content-type-options':'nosniff','x-xiaoxu-public-notices':'1'};
const encode=(body,status=200,cache=false)=>new Response(JSON.stringify(body),{
  status,headers:{...HEADERS,'cache-control':cache?'public, max-age=600, s-maxage=1800':'no-store'}
});
async function fetchSource(source,fetcher){
  const info=SOURCES[source];
  const response=await fetcher(info.url,{redirect:'follow',headers:{
    accept:'text/html,application/xhtml+xml',
    'user-agent':'Xiaoxu-Personal-Academic-Notices/1.0'
  },signal:AbortSignal.timeout(18000)});
  if(!response.ok)throw new Error('NCWU request failed');
  const final=new URL(response.url||info.url);
  if(final.protocol!=='https:'||final.hostname!=='www2.ncwu.edu.cn')
    throw new Error('NCWU source redirect rejected');
  if(Number(response.headers.get('content-length')||0)>1200000)
    throw new Error('NCWU HTML is too large');
  const bytes=await response.arrayBuffer();
  if(bytes.byteLength>1200000)throw new Error('NCWU HTML is too large');
  const h=(response.headers.get('content-type')||'').toLowerCase();
  const snippet=new TextDecoder('latin1').decode(bytes.slice(0,1600));
  const found=/charset\s*=\s*["']?(gbk|gb2312|utf-8)/i.exec(h+' '+snippet);
  const charset=found&&/^gb/.test(found[1].toLowerCase())?'gbk':'utf-8';
  const html=new TextDecoder(charset).decode(bytes);
  return parseNotices(html,source);
}
export async function getFeed(fetcher=fetch){
 const names=['notices','exams'];
 const settled=await Promise.allSettled(names.map(n=>fetchSource(n,fetcher)));
 const available=settled.filter(r=>r.status==='fulfilled').map(r=>r.value);
 const failedSources=settled.flatMap((r,i)=>r.status==='rejected'?[names[i]]:[]);
 const partial=failedSources.length>0;
 if(available.length===0)return {success:false,partial:false,items:[],
  source:'华北水利水电大学教务处官网',fetchedAt:null,
  failedSources,warnings:['官方栏目暂时无法读取；请直接打开学校官网。']};
 return {success:true,partial,items:mergeNotices(available),
  source:'华北水利水电大学教务处官网',fetchedAt:new Date().toISOString(),
  failedSources,warnings:partial?['部分栏目读取失败，数据暂不完整。']:[]};
}
export async function onRequestGet({request,waitUntil}){
 const cache=globalThis.caches?.default;
 const key=new Request(new URL('/api/notices',request.url).href);
 if(cache){
  try{const hit=await cache.match(key);if(hit)return hit;}catch{}
 }
 const result=await getFeed();
 const response=encode(result,result.success?200:503,result.success);
 if(cache&&result.success&&waitUntil)waitUntil(cache.put(key,response.clone()));
 return response;
}
