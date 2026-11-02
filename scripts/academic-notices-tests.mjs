import assert from 'node:assert/strict';
import {SOURCES,articleUrl,parseNotices,mergeNotices} from '../lib/notice-feed.mjs';
import {getFeed} from '../functions/api/notices.js';
assert.ok(Object.keys(SOURCES).length===2);

const noticeHTML='<html><title>华水</title><ul>'+
'<li><a href="../info/1008/3003.htm" title="关于2026-2027学年第二学期线上线下混合式课程申报的通知">标题</a><span>11-02</span></li>'+
'<li><a href="../info/1008/2995.htm" title="关于对2026-2027第一学期课程进行学生网上评教的通知">评教</a><span>10-26</span></li>'+
'<li><a href="../info/1008/3003.htm" title="重复链接">重复链接</a><span>11-02</span></li>'+
'<li><a href="https://evil.ncwu.edu.cn/jiaowu/info/1008/1111.htm" title="伪造的校外通知">无效</a></li>'+
'<li><a href="javascript:alert(1)" title="恶意脚本">无效</a></li>'+
'</ul></html>';
const examHTML='<html><ul>'+
'<li><a href="https://www2.ncwu.edu.cn/jiaowu/info/1034/2990.htm">2026-2027学年第一学期9-12周考试安排表</a><span>10-19</span></li>'+
'<li><a href="info/1034/2888.htm" title="2026级新生入学测试安排">入学测试</a><span>09-01</span></li>'+
'</ul></html>';
const notices=parseNotices(noticeHTML);
assert.equal(notices.length,2);
assert.equal(notices[0].url,'https://www2.ncwu.edu.cn/jiaowu/info/1008/3003.htm');
assert.equal(notices[0].dateLabel,'11-02');
assert.equal(notices[0].publishedAt,null,'Unknown year is not guessed');
const exams=parseNotices(examHTML,'exams');
assert.equal(exams.length,2);
assert.equal(exams[0].category,'exam');
assert.equal(mergeNotices([notices,notices,exams]).length,4);
assert.equal(articleUrl('https://evil.com/jiaowu/info/1008/3003.htm','notice'),null);
assert.equal(articleUrl('https://www2.ncwu.edu.cn/jiaowu/info/1008/3003.htm?redirect=bad','notice'),null);
assert.throws(()=>parseNotices('<html>Cloudflare Error</html>'),/不完整/);
assert.throws(()=>parseNotices('<html>'+('<p>未匹配条目</p>'.repeat(20))+'</html>'),/结构异常/);
const makeResponse=(url,html,status=200)=>({
 ok:status===200,status,url,
 headers:new Headers({'content-type':'text/html; charset=utf-8'}),
 arrayBuffer:async()=>new TextEncoder().encode(html).buffer,
});
const mocks={
 [SOURCES.notices.url]:noticeHTML,
 [SOURCES.exams.url]:examHTML
};
const validFetch=async url=>makeResponse(url,mocks[url]);
const ok=await getFeed(validFetch);
assert.equal(ok.success,true);
assert.equal(ok.partial,false);
assert.equal(ok.items.length,4);
assert.ok(ok.fetchedAt?.includes('T'));
const part=await getFeed(async url=>url===SOURCES.notices.url?validFetch(url):Promise.reject(Error('time out')));
assert.equal(part.success,true);
assert.equal(part.partial,true);
assert.deepEqual(part.failedSources,['exams']);
assert.equal(part.items.length,2);
const fail=await getFeed(async()=>{throw Error('site down')});
assert.equal(fail.success,false);
assert.equal(fail.items.length,0);
assert.equal(fail.fetchedAt,null);
const fakeBad=await getFeed(async url=>makeResponse('https://attacker.example/',mocks[url]));
assert.equal(fakeBad.success,false,'Off-host redirect must not be accepted');
console.log('校序官方通知：严格来源、重复、日期未知、双栏目、局部失败及安全响应校验通过');
