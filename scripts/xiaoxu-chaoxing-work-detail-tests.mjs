import assert from 'node:assert/strict';
import {parseChaoxingWorkDetailHtml,parseChaoxingWorkTimeWindow,ChaoxingWorkDetailError} from '../lib/academic-chaoxing-work-detail.mjs';
const page='<html><body><form id="submitForm">'+
 '<div class="questionLi"><h3 class="mark_name">1. (单选题) Python 列表从几开始索引？'+
 '<img src="https://p.ananas.chaoxing.com/object?token=SECRET"></h3>'+
 '<div class="num_option">A. 从 0 开始</div>'+
 '<div class="num_option">B. 从 1 开始</div>'+
 '<div class="answerBg">官方答案与私人解析不要泄漏</div>'+
 '</div><div class="questionLi"><h3 class="mark_name">2. (简答题) 解释循环</h3>'+
 '<textarea name="answer">草稿敏感内容</textarea>'+
 '<script>steal()</script></div></form></body></html>';
const parsed=parseChaoxingWorkDetailHtml(page);
assert.equal(parsed.schema,'xiaoxu/chaoxing-work-detail-v1');
assert.equal(parsed.coverage,'current_html_page_only');
assert.equal(parsed.questionCount,2);
assert.equal(parsed.imageCount,1);
assert.equal(parsed.questions[0].type,'single_choice');
assert.equal(parsed.questions[0].options.length,2);
assert.equal(parsed.questions[1].type,'essay');
assert.equal(parsed.questions[1].options.length,0);
assert.ok(!JSON.stringify(parsed).includes('token='));
assert.ok(!JSON.stringify(parsed).includes('SECRET'));
assert.ok(!JSON.stringify(parsed).includes('官方答案'));
assert.ok(!JSON.stringify(parsed).includes('草稿敏感内容'));
assert.ok(!JSON.stringify(parsed).includes('steal()'));
assert.ok(parsed.questions[0].stem.includes('[图片]'));
for(const html of [
 '<form id="submitForm"></form>',
 '<div>请登录后重试</div>',
 '<form id="submitForm"><div class="questionLi"><h3 class="mark_name"></h3></div></form>',
 '<form id="submitForm"></form><form id="submitForm"></form>'
 ])assert.throws(()=>parseChaoxingWorkDetailHtml(html),ChaoxingWorkDetailError);
assert.throws(()=>parseChaoxingWorkDetailHtml('x'.repeat(450001)),e=>e.code==='html_missing_or_oversized');
// The MIT xuexitong-cli supports this one explicit detail-page time label.
// Other times, hidden content, dates without a year, or ambiguous pages must
// never become a fabricated absolute deadline or alert.
assert.deepEqual(parseChaoxingWorkTimeWindow('<html><body><section>作答时间：11-03 08:15 至 12-01 23:59</section></body></html>'),
 {startTimeRaw:'11-03 08:15',deadlineRaw:'12-01 23:59'});
assert.deepEqual(parseChaoxingWorkTimeWindow('<body><p>作答时间: 2-9 8:05 至 3-1 9:15</p></body>'),
 {startTimeRaw:'02-09 08:05',deadlineRaw:'03-01 09:15'});
for (const h of [
 '<body><script>var leak="作答时间: 11-01 09:00 至 11-02 18:00"</script></body>',
 '<body><input type="hidden" value="作答时间: 11-01 09:00 至 11-02 18:00"></body>',
 '<body>作答时间: 02-30 09:00 至 03-01 18:00</body>',
 '<body>作答时间: 11-01 25:00 至 11-02 18:00</body>',
 '<body>作答时间: 11-01 09:00 至 11-02 18:00 作答时间: 11-03 10:00 至 11-04 18:00</body>',
 '<body>截止时间可能在课程通知，未给具体作答时间</body>'
])assert.equal(parseChaoxingWorkTimeWindow(h),null);
console.log('Chaoxing read-only work-page parser: question preview plus provenance-bound, yearless detail timing passed');
