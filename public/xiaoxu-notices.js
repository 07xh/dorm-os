/* 校序官方公开通知统一数据层。
 * Reuse the existing notice-radar (MIT) scheduled archive; do not duplicate
 * scraping or embed another platform UI. Browser + Node (CommonJS) compatible.
 */
(function(root){
 'use strict';
 const READ_STORAGE='xiaoxu-notice-reading-v1';
 const OFFICIAL=new Set(['www.ncwu.edu.cn','www2.ncwu.edu.cn']);
 const validDate=s=>typeof s==='string'&&/^20\d\d-\d\d-\d\d$/.test(s)&&!Number.isNaN(Date.parse(s));
 function officialUrl(value) {
  try{
   const u=new URL(value);
   if(u.protocol!=='https:'||!OFFICIAL.has(u.hostname)||u.port||u.username||u.password||u.search||u.hash)return '';
   if(!/^\/(?:(?:xsc|jiaowu)\/)?info\/\d{3,6}\/\d{1,8}\.htm$/.test(u.pathname))return '';
   return u.origin+u.pathname;
  }catch{return '';}
 }
 function validTitle(v){return typeof v==='string'&&v.trim().length>=5&&v.trim().length<=220;}
 function fromArchive(data){
  if(!data||data.schema!=='xiaoxu/notices-v1'||data.mode!=='auto'||
   !Array.isArray(data.items)||data.items.length<2||data.items.length>240||
   !Number.isFinite(Date.parse(data.lastSuccessAt||''))) throw Error('定时归档尚未经过有效采集验收');
  const seen=new Set(),items=[];
  for(const raw of data.items) {
   // A later archive mode=auto never certifies the original seed-* placeholder rows.
   if(typeof raw?.id==='string'&&/^seed[-:]/i.test(raw.id))continue;
   const url=officialUrl(raw.url);
   if(!url||!validTitle(raw.title)||!['教学','考试','校园','学生事务'].includes(raw.category))throw Error('通知归档来源或字段异常');
   if(seen.has(url))continue;
   seen.add(url);
   const date=validDate(raw.date)?raw.date:null;
   items.push({
    id:url,url,title:raw.title.trim(),category:raw.category==='考试'?'exam':'notice',
    group:raw.category,sourceName:String(raw.sourceName||'华水官网').slice(0,35),
    dateLabel:date||'',publishedAt:date,origin:'archive'
   });
  }
  if(items.length<2)throw Error('通知归档有效条目不足');
  return {items,fetchedAt:data.lastSuccessAt,mode:'archive'};
 }
 function fromApi(data){
  if(!data||data.success!==true||!Array.isArray(data.items)||
      data.items.length<2||data.items.length>45)throw Error('官网动态通知未获得有效响应');
  const seen=new Set(),items=[];
  for(const raw of data.items){
   const url=officialUrl(raw.url);
   if(!url||!validTitle(raw.title)||!['notice','exam'].includes(raw.category)||
      !/^https:\/\/www2\.ncwu\.edu\.cn\/jiaowu\/info\/(?:1008|1034)\/\d{1,8}\.htm$/.test(url))
    throw Error('官网动态通知包含非法来源');
   if(seen.has(url))continue;seen.add(url);
   const date=validDate(raw.publishedAt)?raw.publishedAt:null;
   items.push({id:url,url,title:raw.title.trim(),category:raw.category,
     group:raw.category==='exam'?'考试':'教学',sourceName:raw.category==='exam'?'教务处考试安排':'教务处通知',
     dateLabel:date||String(raw.dateLabel||'').slice(0,10),publishedAt:date,origin:'api'});
  }
  return {items,fetchedAt:data.fetchedAt||null,mode:'api',partial:data.partial===true};
 }
 function merge(api,archive){
  const combined=new Map();
  // Verified API order is the page's recent list; archive fills historical gaps.
  for(const group of [api,archive]){
   for(const item of group?.items||[]){
    const existing=combined.get(item.url);
    if(existing){
     if(!existing.publishedAt&&item.publishedAt)combined.set(item.url,{...existing,
      publishedAt:item.publishedAt,dateLabel:item.publishedAt});
     continue;
    }
    combined.set(item.url,item);
   }
  }
  return [...combined.values()].slice(0,240);
 }
 function normalizeReading(raw){
  const source=raw&&typeof raw==='object'?raw:{};
  const urls=a=>Array.isArray(a)?[...new Set(a.filter(x=>!!officialUrl(x)))].slice(-350):[];
  const seen=urls(source.seen),unread=urls(source.unread);
  return {initialized:source.initialized===true,seen,unread:unread.filter(x=>seen.includes(x))};
 }
 function reconcileReading(current,items){
  const next=normalizeReading(current);
  const urls=[...new Set(items.map(x=>x.url))];
  if(!next.initialized)return {next:{initialized:true,seen:urls,unread:[]},added:0};
  const seen=new Set(next.seen),unread=new Set(next.unread);
  let added=0;
  for(const url of urls)if(!seen.has(url)){seen.add(url);unread.add(url);added++;}
  return {next:{initialized:true,seen:[...seen].slice(-350),unread:[...unread].slice(-350)},added};
 }
 function markRead(current,url){
  const state=normalizeReading(current);
  return {...state,unread:state.unread.filter(v=>v!==url)};
 }
 function markAllRead(current){return {...normalizeReading(current),unread:[]};}
 const api={READ_STORAGE,officialUrl,fromArchive,fromApi,merge,normalizeReading,reconcileReading,markRead,markAllRead};
 root.XiaoxuNotices=api;
 if(typeof module==='object'&&module.exports)module.exports=api;
})(typeof globalThis!=='undefined'?globalThis:this);
