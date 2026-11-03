import assert from 'node:assert/strict';
import {parseChaoxingWorkDetailHtml,ChaoxingWorkDetailError} from '../lib/academic-chaoxing-work-detail.mjs';
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
console.log('Chaoxing read-only work-page parser: HTML, question types, options and secret stripping passed');
