import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { canonicalLink, dateFromText, stableId, parseHtmlList, mergeArchive, collect, collectFromPublishedApi, normalizeUpstreamSnapshot, isVerifiedArchiveItem, SCHEMA, SCHOOL_ID } from './ncwu-notices.mjs';

const cfg = JSON.parse(readFileSync(new URL('../config/ncwu-notices.json', import.meta.url), 'utf8'));
const seed = JSON.parse(readFileSync(new URL('../public/data/notices.json', import.meta.url), 'utf8'));
assert.equal(seed.schema, SCHEMA);
assert.equal(seed.school, SCHOOL_ID);
assert.ok(seed.items.length >= 4);
for (const item of seed.items) {
  assert.ok(canonicalLink(item.url,item.url), 'must be a verified official HTTPS article');
  assert.ok(isVerifiedArchiveItem(item), 'Published archive must not contain seed or unproven source identifiers');
  assert.ok(item.date === null || /^\d{4}-\d\d-\d\d$/.test(item.date), 'Unknown publication dates must remain null, not invented');
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
const contaminated={...seed,items:[...seed.items,{
 id:'seed-jwc-23331',title:'早期未验证样本通知',url:'https://www.ncwu.edu.cn/info/1063/23331.htm',
 date:'2026-11-02',sourceId:'school',sourceName:'教学',category:'校园'
}]};
const preserved=seed.items.find(item=>item.url!==found[0].url&&item.url!==found[1].url);
assert.ok(preserved,'Need a real previously verified item for the partial failure assertion');
const result=mergeArchive(contaminated,[{source,items:found},{source:cfg.sources[1],error:'failed'}], '2026-11-03T02:00:00.000Z');
assert.equal(result.mode,'auto');
assert.equal(result.lastSuccessAt,'2026-11-03T02:00:00.000Z');
assert.equal(result.sources[1].status,'error');
assert.equal(result.items.find(n=>n.url===preserved.url)?.id,preserved.id,'preserve genuine history on partial failures');
assert.equal(result.items.some(n=>n.id.startsWith('seed-')),false,'Never publish initial placeholder records');
assert.ok(result.items.some(n=>n.url.endsWith('/23285.htm')));
assert.equal(result.items.find(n=>n.url.endsWith('/23331.htm')).id,stableId(found[0].url),'Newly fetched real article replaces any seed marker');
if (seed.mode === 'seed') assert.equal(seed.lastSuccessAt, null, 'Initial snapshot cannot claim auto success');
else { assert.equal(seed.mode, 'auto'); assert.ok(/^20\d\d-\d\d-\d\dT/.test(seed.lastSuccessAt), 'Live archive must contain a real update timestamp'); }
const loaded=await collect({config:{sources:[source]},previous:seed,fetcher:async()=>new Response(sample,{status:200,headers:{'content-type':'text/html; charset=utf-8'}}),at:'2026-11-03T02:00:00.000Z'});
assert.equal(loaded.mode,'auto');
await assert.rejects(collect({config:{sources:[source]},previous:seed,fetcher:async()=>new Response('JavaScript challenge',{status:202})}),/所有公开通知源均失败/);
await assert.rejects(collect({config:{sources:[source]},previous:seed,fetcher:async()=>new Response('<html><body>空白</body></html>',{status:200})}),/所有公开通知源均失败/);
// Verified Cloudflare Pages API is only a fallback if all 3 direct official sites fail.
// The API supplies article URLs and MM-DD labels, never a verified publication year.
const officialApi = {
  success:true,
  items:[
    {category:'notice',title:'2026学年公开课程安排的通知',url:'https://www2.ncwu.edu.cn/jiaowu/info/1008/3003.htm',publishedAt:null,dateLabel:'11-02'},
    {category:'exam',title:'2026学年第一学期考试安排表',url:'https://www2.ncwu.edu.cn/jiaowu/info/1034/2990.htm',publishedAt:null,dateLabel:'10-19'}
  ]
};
const apiReply=(x, {status=200, header='1'}={})=>new Response(JSON.stringify(x),{
 status,headers:{'content-type':'application/json','x-xiaoxu-public-notices':header}
});
const apiFetcher=async url=>{
  assert.equal(url,'https://dorm-os-dk9.pages.dev/api/notices');
  return apiReply(officialApi);
};
const backup=await collectFromPublishedApi(apiFetcher);
assert.equal(backup.items.length,2);
assert.equal(backup.items[0].date,null,'MM-DD without year may not be converted to ISO');
assert.equal(backup.items[1].category,'考试');
const brokenOfficial=async()=>new Response('Temporary unavailable',{status:503});
const fallback=await collect({config:{sources:[source]},previous:seed,
 fetcher:brokenOfficial,apiFetcher,at:'2026-11-03T03:00:00.000Z'});
assert.equal(fallback.mode,'auto');
assert.equal(fallback.sources.find(x=>x.id==='jwc').status,'error');
assert.equal(fallback.sources.find(x=>x.id==='jwc-api').status,'ok');
assert.ok(fallback.items.some(x=>x.url.endsWith('/jiaowu/info/1008/3003.htm')));
const malicious={...officialApi,items:[{...officialApi.items[0],url:'https://attack.example/info/1008/3003.htm'},officialApi.items[1]]};
await assert.rejects(collectFromPublishedApi(async()=>apiReply(malicious)),/来源/);
await assert.rejects(collectFromPublishedApi(async()=>apiReply(officialApi,{header:'0'})),/未命中/);
await assert.rejects(collectFromPublishedApi(async()=>apiReply({...officialApi,success:false})),/没有通过/);


// Pinned upstream provides only candidates: our existing collector must validate
// each official source prefix and preserve history when a source fails.
const collegeSnapshot={sources:{
  dianqi:{items:[
    {url:'https://www2.ncwu.edu.cn/dianqi/info/1001/5001.htm',title:'关于电气工程学院2026年度实验安排的通知',date:'2026-11-09'},
    {url:'https://www2.ncwu.edu.cn/dianqi/info/1001/5002.htm',title:'关于组织电气工程学院学业讲座的通知',date:null}
  ]},
  gcxlzx:{items:[
    {url:'https://www2.ncwu.edu.cn/gcxlzx/info/1001/6001.htm',title:'关于2026年度学生创新实践项目的通知',date:'11-02'},
    {url:'https://www2.ncwu.edu.cn/gcxlzx/info/1001/6002.htm',title:'关于实践创新中心课程训练的通知',date:null}
  ]},
  qysy:{error:'official list temporarily unavailable'}
}};
const collegeBatches=normalizeUpstreamSnapshot(collegeSnapshot);
assert.equal(collegeBatches.length,3);
assert.equal(collegeBatches[0].items.length,2);
assert.equal(collegeBatches[0].items[0].id,stableId(collegeBatches[0].items[0].url));
assert.equal(collegeBatches[1].items[0].date,null,'Month and day without year must stay null');
assert.equal(collegeBatches[2].error,'source_unavailable');
const maliciousCollege={sources:{...collegeSnapshot.sources,dianqi:{items:[
  {...collegeSnapshot.sources.dianqi.items[0],url:'https://www2.ncwu.edu.cn.evil.example/dianqi/info/1001/5001.htm'},
  collegeSnapshot.sources.dianqi.items[1]
]}}};
assert.ok(normalizeUpstreamSnapshot(maliciousCollege)[0].error,'Reject fake official domain');
const incorrectSection={sources:{...collegeSnapshot.sources,dianqi:{items:[
  {...collegeSnapshot.sources.dianqi.items[0],url:'https://www2.ncwu.edu.cn/qysy/info/1001/5001.htm'},
  collegeSnapshot.sources.dianqi.items[1]
]}}};
assert.ok(normalizeUpstreamSnapshot(incorrectSection)[0].error,'Reject cross-department article reuse');
const supplemented=await collect({config:{sources:[source]},previous:seed,
  fetcher:async()=>new Response(sample,{status:200,headers:{'content-type':'text/html; charset=utf-8'}}),
  upstreamSnapshot:collegeSnapshot,at:'2026-11-10T10:00:00.000Z'});
assert.equal(supplemented.sources.length,4,'Existing source and three optional sources share ONE archive');
assert.equal(supplemented.sources.find(x=>x.id==='qysy').status,'error');
assert.ok(supplemented.items.some(x=>x.sourceId==='dianqi'));
assert.ok(supplemented.items.some(x=>x.sourceId==='gcxlzx'));
assert.ok(supplemented.items.some(x=>x.url===preserved.url),
  'Existing verified history must survive optional-source failures');

const html=readFileSync(new URL('../public/index.html',import.meta.url),'utf8');
assert.ok(html.includes('function loadPublicNotices('),'preserve existing /api/notices functionality');
assert.ok(html.includes("getJson('/api/notices')")||html.includes("fetch('/api/notices'"),'current Cloudflare Pages API stays in use');
assert.ok(html.includes("getJson(NOTICE_ARCHIVE_URL)"),'scheduled notice archive must be read and merged, not only embedded as static UI');
assert.ok(html.includes('function academicPage()'),'preserve existing academic UI');
console.log('NCWU notice fixture, source whitelist, stable archive and fail-safe tests passed');
