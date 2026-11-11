import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';

const root='apps/xiaoxu-android/app/src/main/';
const source=readFileSync(root+'assets/ncwu_elective_source_probe.js','utf8');
const android=readFileSync(root+'java/io/github/x07xh/xiaoxu/LocalQingguoImportActivity.kt','utf8');
const makeRow=values=>({querySelectorAll:selector=>selector==='th,td'?
 values.map(textContent=>({textContent})):[]});
const makeTable=rows=>({querySelectorAll:selector=>selector==='tr'?
 rows.map(makeRow):[]});
const makeDoc=(tables=[],frames=[])=>({querySelectorAll:selector=>
 selector==='table'?tables:selector==='iframe,frame'?frames:[]});

let requests=0;
const privateID='SECRET_STUDENT_ID_2026';
const privatePhone='SECRET_STUDENT_PHONE_2026';
const catalog=makeTable([
 ['课程名称','课程代码','教学班代码','任课教师','剩余名额','学号','手机号'],
 ['自动控制','C101','A1','王老师','2',privateID,privatePhone],
 ['自动控制','C101','A1','王老师','2',privateID,privatePhone],
 ['电路原理','C102','A2','李老师','0',privateID,privatePhone],
 ['坏列','INVALID CODE','A3','教师','1',privateID,privatePhone]
]);
const unrelated=makeTable([
 ['姓名','学号','手机号'],['张同学',privateID,privatePhone]
]);
const brokenFrame={get contentDocument(){throw Error('cross-origin');}};
const context={document:makeDoc([unrelated],[{contentDocument:makeDoc([catalog])},brokenFrame]),
 window:{},fetch(){requests++;throw Error('unexpected request');},
 XMLHttpRequest:function(){requests++;throw Error('unexpected request');}};
runInNewContext(source,context,{timeout:2000});
const result=context.window.__xiaoxuPreviewElectiveCatalog();
assert.equal(result.schema,'xiaoxu/ncwu-elective-local-preview-v1');
assert.equal(result.sourceState,'unverified');
assert.equal(result.readOnly,true);
assert.equal(result.submissionAllowed,false);
assert.equal(result.officialAvailabilityVerified,false);
assert.equal(result.blockedFrames,1);
assert.equal(result.classes.length,2,'duplicate and invalid course codes must be rejected');
assert.equal(result.classes[0].courseName,'自动控制');
assert.equal(result.classes[0].remaining,2);
assert.equal(result.classes[1].remaining,0);
for(const secret of [privateID,privatePhone,'张同学','学号','手机号']){
 assert.equal(JSON.stringify(result).includes(secret),false,'preview leaked '+secret);
}
assert.equal(requests,0,'preview must not issue school requests');
const ambiguous=makeTable([
 ['课程名称','课程代码','课程代码','教学班代码'],
 ['混乱班','C1','C2','A1']
]);
const other={document:makeDoc([ambiguous]),window:{}};
runInNewContext(source,other,{timeout:2000});
assert.equal(other.window.__xiaoxuPreviewElectiveCatalog().classes.length,0);
assert.ok(android.includes('previewButton.setOnClickListener { previewVisibleElectiveCatalog() }'));
assert.ok(android.includes('window.__xiaoxuPreviewElectiveCatalog()'));
assert.ok(android.includes('if (!pageLoaded || pageFailed || !isOfficialPage(currentUrl))'));
assert.ok(android.includes('epoch != pageEpoch || browser.url != currentUrl || isFinishing'));
assert.ok(!/fetch\s*\(|XMLHttpRequest|document\.cookie|localStorage|innerHTML/i.test(source));
console.log('NCWU elective preview: manual local rows, strict header matching and sensitive-column exclusion passed');
