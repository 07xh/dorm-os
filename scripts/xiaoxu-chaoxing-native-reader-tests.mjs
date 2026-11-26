import assert from 'node:assert/strict';
import {readFileSync,existsSync} from 'node:fs';
const base='apps/xiaoxu-android/app/src/main/';
const native=readFileSync(base+'java/io/github/x07xh/xiaoxu/NativeSchoolConnectActivity.kt','utf8');
const reader=readFileSync(base+'java/io/github/x07xh/xiaoxu/data/ChaoxingLocalReader.kt','utf8');
const session=readFileSync(base+'java/io/github/x07xh/xiaoxu/data/ChaoxingWebSession.kt','utf8');
const cloud=readFileSync(base+'java/io/github/x07xh/xiaoxu/data/CloudClient.kt','utf8');
const storage=readFileSync(base+'java/io/github/x07xh/xiaoxu/data/NativeSchoolRecords.kt','utf8');
assert.match(native,/ChaoxingLocalReader\(session/);
assert.match(native,/mergeChaoxingRead\(rows, result.accountHash, result.complete, result.supersededKeys\)/);
assert.match(native,/withContext\(Dispatchers.IO\)/);
assert.match(native,/context.ensureActive\(\)/);
assert.doesNotMatch(native,/READ_VISIBLE_PAGE|READ_TASK_PLAN_GROUPS|scanStage|__xiaoxuOwnedWorkCourse/);
assert.doesNotMatch(cloud,/suspend fun (?:loadChaoxingCourses|syncChaoxingAllCourses|startChaoxingQr)/);
for(const file of ['xiaoxu_chaoxing_course_homework_scan.js','xiaoxu_chaoxing_course_task_discovery.js'])
 assert.equal(existsSync(base+'assets/'+file),false,'retired scanner must be deleted');
assert.match(reader,/noticeLinks/);
assert.match(reader,/readStudyPlan/);
assert.match(reader,/readCourse/);
const courseFetchOffset=reader.search(/\b(?:val|var) page=fetch\(COURSES/);
assert.ok(courseFetchOffset>=0 && reader.indexOf('for(link in links.values.filter')<courseFetchOffset,
 'notice links must be scanned before course catalog fetch');
assert.match(reader,/account\(fetch\(HOME\)\)!=owner/);
assert.match(session,/instanceFollowRedirects=false/);
assert.match(session,/getCookie\(url\)/);
assert.match(session,/setCookie\(url,cookie\)/);
assert.match(session,/readOnlyPosts/);
const workspace=readFileSync(base+'java/io/github/x07xh/xiaoxu/ui/RealDataWorkspace.kt','utf8');
assert.doesNotMatch(workspace,/state\.tasks\.filterNot \{ it\.provider == "chaoxing" \}/,
 'retiring the reader must not hide existing cloud Chaoxing tasks');
assert.doesNotMatch(workspace,/if \(\(capturedHomework\.isNotEmpty\(\) \|\| capturedStudyTasks\.isNotEmpty\(\)\) &&/,
 'the Chaoxing group remains available on a fresh install with cloud-only or no records');
const panels=readFileSync(base+'java/io/github/x07xh/xiaoxu/ui/CloudPanels.kt','utf8');
assert.match(panels,/"chaoxing" to "学习通"/,'cloud connections must retain the Chaoxing card');
assert.match(panels,/id == "chaoxing"\) client\.refreshPrivate\(\)/,
 'cloud record refresh uses the existing assignment API, not the retired reader');
assert.doesNotMatch(reader+session,/api\/v1|CloudClient|addJavascriptInterface|readProgress|saveStatus|submitForm.*POST/);
assert.match(storage,/if \(old != accountHash \|\| complete\) \{/);
assert.match(storage,/@Transaction\s+suspend fun mergeChaoxingRead/);
console.log('Chaoxing single local reader, cookie scope, account boundary and legacy retirement passed; runtime protocol is covered by ChaoxingLocalReaderTest');
// Native Chaoxing tasks must keep their own UI source after retiring cloud reads.
assert.match(workspace,/"native", "chaoxing", provider = "chaoxing"/);
assert.match(workspace,/title = if \(filter == "verify"\) "学习通待核对" else "学习通任务"/);
assert.match(workspace,/shown\.filter \{ it\.category == "chaoxing" \}/);
assert.match(workspace,/"chaoxing" to "学习通任务"/);
