import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';

const path='apps/xiaoxu-android/app/src/main/assets/ncwu_elective_source_probe.js';
const source=readFileSync(path,'utf8');
const android=readFileSync(
 'apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/LocalQingguoImportActivity.kt',
 'utf8');
const row=values=>({querySelectorAll:selector=>selector==='th,td'?
 values.map(textContent=>({textContent})):[]});
const table=rows=>({querySelectorAll:selector=>selector==='tr'?rows.map(row):[]});
const doc=(tables=[],frames=[])=>({
 querySelectorAll:selector=>selector==='table'?tables:
  selector==='iframe,frame'?frames:[]
});
const secret='PRIVATE_STUDENT_IDENTIFIER_SHOULD_NOT_LEAVE_PAGE';
const personal='PRIVATE_ACCOUNT_SECRET_SHOULD_NOT_LEAVE_PAGE';
const candidate=table([
 ['课程名称','教学班代码','任课教师','剩余名额','学分','学号','手机号'],
 [secret,personal,'教师A','2','2','private-student','private-phone']
]);
const blockedFrame={get contentDocument(){throw new Error('cross origin');}};
const root=doc([], [{contentDocument:doc([candidate])},blockedFrame]);
let calls=0;
const context={document:root,window:{},
 fetch(){calls++;throw Error('unexpected network');},
 XMLHttpRequest:function(){calls++;throw Error('unexpected XHR');}};
runInNewContext(source,context,{timeout:2000});
const output=context.window.__xiaoxuInspectElectiveSource();
assert.equal(output.schema,'xiaoxu/ncwu-elective-source-fingerprint-v1');
assert.equal(output.scannedDocuments,2);
assert.equal(output.scannedTables,1);
assert.equal(output.blockedFrames,1);
assert.equal(output.candidateTables.length,1);
assert.equal(output.candidateTables[0].likelyCatalog,true);
assert.deepEqual([...output.candidateTables[0].columns],
 ['courseName','classCode','teacher','remaining','credit']);
assert.equal(output.candidateTables[0].rows,2);
assert.equal(output.officialAvailabilityVerified,false);
assert.equal(output.containsCourseRecords,false);
const serialized=JSON.stringify(output);
for(const sensitive of [secret,personal,'private-student','private-phone','学号','手机号']){
 assert.equal(serialized.includes(sensitive),false, 'inspector leaked: '+sensitive);
}
assert.equal(calls,0,'inspector must not call school or external services');
const empty={document:doc([table([['姓名','学号'],[secret,personal]])]),window:{}};
runInNewContext(source,empty,{timeout:2000});
assert.equal(empty.window.__xiaoxuInspectElectiveSource().candidateTables.length,0);
assert.ok(android.includes('assets.open("ncwu_elective_source_probe.js")'));
assert.ok(android.includes('inspectButton.setOnClickListener { inspectVisibleElectiveSource() }'));
assert.ok(android.includes('if (!pageLoaded || pageFailed || !isOfficialPage(currentUrl))'));
assert.ok(android.includes('epoch != pageEpoch || browser.url != currentUrl || isFinishing'));
assert.ok(android.includes('handler.cancel()'));
assert.ok(!/fetch\s*\(|XMLHttpRequest|document\.cookie|localStorage|innerHTML/i.test(source));
console.log('NCWU elective inspector: local headers only, safe frame handling, no secrets, no network passed');
