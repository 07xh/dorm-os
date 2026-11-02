/**
 * 校序公开教务通知解析器。仅使用华北水利水电大学教务处公开的文章页面。
 * No credentials, no scraping of private academic records.
 * Pure ESM for Cloudflare Pages Functions and Node tests.
 */
const BASE='https://www2.ncwu.edu.cn';
const SOURCES=Object.freeze({
 notices:{label:'通知公告',url:BASE+'/jiaowu/jwdt/tzgg.htm',category:'notice'},
 exams:{label:'考试安排',url:BASE+'/jiaowu/ksap.htm',category:'exam'}
});
function decodeText(value){
 return String(value||'')
  .replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi,'')
  .replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi,'')
  .replace(/<[^>]*>/g,'')
  .replace(/&nbsp;|&#160;/gi,' ')
  .replace(/&quot;|&#34;/gi,'"')
  .replace(/&amp;|&#38;/gi,'&')
  .replace(/&lt;|&#60;/gi,'<')
  .replace(/&gt;|&#62;/gi,'>')
  .replace(/&#(\d+);/g,(_,d)=>{const n=+d;return n>0&&n<=0x10ffff?String.fromCodePoint(n):'';})
  .replace(/&#x([\da-f]+);/gi,(_,h)=>{const n=parseInt(h,16);return n>0&&n<=0x10ffff?String.fromCodePoint(n):'';})
  .replace(/\s+/g,' ').trim();
}
function attribute(attrs,key) {
 const match=new RegExp('(?:^|\\s)'+key+'\\s*=\\s*(?:"([^"]*)"|\\x27([^\\x27]*)\\x27|([^\\s>]+))','i').exec(attrs);
 return match?match[1]??match[2]??match[3]:'';
}
function articleUrl(href,category){
 if(!href||href.length>500) return null;
 let u;
 try{u=new URL(href,BASE+'/jiaowu/jwdt/');}catch{return null;}
 const pattern=category==='exam'?/^\/jiaowu\/info\/1034\/(\d{1,8})\.htm$/:
   /^\/jiaowu\/info\/1008\/(\d{1,8})\.htm$/;
 const id=pattern.exec(u.pathname);
 if(u.protocol!=='https:'||u.hostname!=='www2.ncwu.edu.cn'||u.port||u.username||u.password||
    u.search||u.hash||!id)return null;
 return {url:u.href,id:id[1]};
}
function parseNotices(html,source='notices'){
 if(typeof html!=='string'||html.length>1500000||html.length<120)throw Error('官方公告列表 HTML 不完整');
 const conf=SOURCES[source];if(!conf)throw Error('Unsupported notice source');
 const seen=new Set(),items=[];
 const anchor=/<a\b([^>]*?)>([\s\S]*?)<\/a\s*>/gi;
 for(let match; (match=anchor.exec(html))!==null;){
  const attrs=match[1],href=attribute(attrs,'href');
  const record=articleUrl(href,conf.category);
  if(!record||seen.has(record.url))continue;
  let title=decodeText(attribute(attrs,'title')||match[2]);
  if(title.length<5||title.length>220||/\bjavascript\b/i.test(title))continue;
  const tail=html.slice(anchor.lastIndex,anchor.lastIndex+260);
  // VSB site index shows MM-DD; without full article year do not invent an ISO date.
  const label=/\b(?:0?[1-9]|1[0-2])-(?:0?[1-9]|[12]\d|3[01])\b/.exec(decodeText(tail));
  seen.add(record.url);
  items.push({id:source+':'+record.id,source,category:conf.category,
   sourceLabel:conf.label,title,url:record.url,
   dateLabel:label?label[0]:'',publishedAt:null});
 }
 // Never report zero notifications on an upstream shape change.
 if(items.length<2)throw Error('教务处列表结构异常或返回的文章过少');
 return items.slice(0,35);
}
function mergeNotices(groups){
 const found=new Set(),all=[];
 for(const group of groups)for(const item of group){
  if(found.has(item.url))continue;
  found.add(item.url);all.push(item);
 }
 return all.slice(0,45);
}
export {SOURCES,parseNotices,mergeNotices,articleUrl};
