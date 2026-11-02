import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { canonicalLink, dateFromText, stableId, parseHtmlList, mergeArchive, collect, validatePagesFeed, fetchPagesFallback, SCHEMA, SCHOOL_ID } from './ncwu-notices.mjs';

const cfg = JSON.parse(readFileSync(new URL('../config/ncwu-notices.json', import.meta.url), 'utf8'));
const seed = JSON.parse(readFileSync(new URL('../public/data/notices.json', import.meta.url), 'utf8'));
assert.equal(seed.schema, SCHEMA);
assert.equal(seed.school, SCHOOL_ID);
assert.ok(seed.items.length >= 4);
for (const item of seed.items) {
  assert.ok(canonicalLink(item.url,item.url), 'must be a verified official HTTPS article');
  assert.ok(/^\d{4}-\d\d-\d\d$/.test(item.date));
}
assert.equal(dateFromText('发布日期：2026年11月2日'), '2026-11-02');
assert.equal(dateFromText('发布日期：2026-02-30'), null);
assert.equal(canonicalLink('javascript:alert(1)', 'https://www.ncwu.edu.cn/xxtz/jwc.htm'), null);
assert.equal(canonicalLink('https://www.ncwu.edu.cn.evil.example/info/1063/23331.htm', 'https://www.ncwu.edu.cn/'), null);
const source=cfg.sources[0];
const sample='<html><body><ul class="article-list">' +
  '<li><span class="date">2026-11-02</span><a href="/info/1063/23331.htm" title="关于2026年度课程申报的通知">查看</a></li>' +
  '<li><time datetime="2026-10-26">2026-10-26</time><a href="/info/1063/23285.htm">关于学生网上评教的通知</a></li>' +
  '<li><a href="/info/1063/23331.htm">关于2026年度课程申报的通知</a></li>' +
  '<li><a href="https://malicious.example/info/1000/1000.htm">恶意文章</a></li>' +
  '<li><a href="/xxtz/jwc.htm">栏目首页</a></li></ul></body></html>';
const found=parseHtmlList(sample,source);
assert.equal(found.length,2,'must dedupe URL, reject nav and external links');
assert.equal(found[0].date,'2026-11-02');
assert.equal(found[0].id,stableId(found[0].url));
const result=mergeArchive(seed,[{source,items:found},{source:cfg.sources[1],error:'failed'}], '2026-11-03T02:00:00.000Z');
assert.equal(result.mode,'auto');
assert.equal(result.lastSuccessAt,'2026-11-03T02:00:00.000Z');
assert.equal(result.sources[1].status,'error');
assert.equal(result.items.find(n=>n.url.endsWith('/23333.htm')).date,'2026-10-29','preserve archive on partial failures');
assert.ok(result.items.some(n=>n.url.endsWith('/23285.htm')));
assert.equal(result.items.find(n=>n.url.endsWith('/23331.htm')).id,'seed-jwc-23331','seed IDs remain stable');
assert.equal(seed.lastSuccessAt,null,'fixture must not lie about auto success');
const loaded=await collect({config:{sources:[source]},previous:seed,fetcher:async()=>new Response(sample,{status:200,headers:{'content-type':'text/html; charset=utf-8'}}),at:'2026-11-03T02:00:00.000Z'});
assert.equal(loaded.mode,'auto');
await assert.rejects(collect({config:{sources:[source]},previous:seed,fetcher:async()=>new Response('JavaScript challenge',{status:202})}),/均不可达/);
await assert.rejects(collect({config:{sources:[source]},previous:seed,fetcher:async()=>new Response('<html><body>空白</body></html>',{status:200})}),/均不可达/);

const edgeData={success:true,fetchedAt:'2026-11-03T02:20:00.000Z',items:[
 {source:'notices',category:'notice',title:'关于开展本学期课程教学评估的通知',
  url:'https://www2.ncwu.edu.cn/jiaowu/info/1008/3003.htm',dateLabel:'11-03',publishedAt:null},
 {source:'exams',category:'exam',title:'关于期末考试安排的通知及注意事项',
  url:'https://www2.ncwu.edu.cn/jiaowu/info/1034/3004.htm',dateLabel:'11-02',publishedAt:null}
]};
const verified=validatePagesFeed(edgeData);
assert.equal(verified.length,2,'two recognized official source types');
assert.equal(verified[0].items[0].date,null,'MM-DD does not imply an ISO date');
assert.equal(verified[1].items[0].category,'考试');
assert.throws(()=>validatePagesFeed({...edgeData,items:[{...edgeData.items[0],url:'https://evil.example/jiaowu/info/1008/3003.htm'},edgeData.items[1]]}),/URL rejected/);
assert.throws(()=>validatePagesFeed({...edgeData,items:[{...edgeData.items[0],url:'https://www2.ncwu.edu.cn/jiaowu/info/1034/3003.htm'},edgeData.items[1]]}),/URL rejected/);
assert.throws(()=>validatePagesFeed({...edgeData,success:false}),/invalid/);
const edgeResponse=()=>new Response(JSON.stringify(edgeData),{status:200,headers:{
 'content-type':'application/json','x-xiaoxu-public-notices':'1'
}});
const edge=await fetchPagesFallback(async()=>edgeResponse());
assert.equal(edge.length,2);
const fallbackUsed=await collect({
 config:{sources:[source]},previous:seed,at:'2026-11-03T02:25:00.000Z',
 fetcher:async(url)=>url.includes('/api/notices')?edgeResponse():new Response('JavaScript challenge',{status:202})
});
assert.equal(fallbackUsed.mode,'auto');
assert.equal(fallbackUsed.lastSuccessAt,'2026-11-03T02:25:00.000Z');
assert.ok(fallbackUsed.items.some(item=>item.url.endsWith('/3003.htm')));
assert.ok(fallbackUsed.sources.some(x=>x.id==='jwc-edge'&&x.status==='ok'));
assert.ok(fallbackUsed.sources.some(x=>x.id===source.id&&x.status==='error'));

const html=readFileSync(new URL('../public/index.html',import.meta.url),'utf8');
assert.ok(html.includes('function loadPublicNotices('),'preserve existing /api/notices functionality');
assert.ok(html.includes("fetch('/api/notices'"),'current Cloudflare Pages API stays in use');
assert.ok(html.includes('function academicPage()'),'preserve existing academic UI');
console.log('NCWU notice fixture, source whitelist, stable archive and fail-safe tests passed');
