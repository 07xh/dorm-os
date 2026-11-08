import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';

const native = readFileSync(
  'apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/NativeSchoolConnectActivity.kt',
  'utf8'
);
const matched = native.match(/private const val READ_VISIBLE_PAGE = """([\s\S]*?)"""/);
assert.ok(matched, 'must retain a fixed, inspectable, read-only page script');
assert.match(native, /browser\.loadUrl\(CHAOXING_COURSES\)/);
assert.match(native, /if \(payload\.optBoolean\("truncated"\)\)/);
assert.match(native, /captureVisibleRecords\(append = true\)/);
assert.match(native, /if \(append\) dao\.appendKind\(provider, kind, accepted\)/);
assert.match(native, /dao\.replaceKind\(provider, kind, accepted\)/);
assert.match(native, /savedCount = total/);
// Regression: an asynchronous official course scanner must persist its own
// complete sanitized response, not scrape the parent page a second time.
const scannerBody = native.slice(native.indexOf('private fun scanOwnedCourseHomework()'),
  native.indexOf('private fun openNextOfficialPlan()'));
assert.match(scannerBody, /preparedPayload=result/);
assert.match(scannerBody, /val expected = "work:" \+ course\.courseId/);
assert.match(scannerBody, /key\.removePrefix\(expected\)\.matches/);
assert.doesNotMatch(scannerBody,
  /captureVisibleRecords\(append=true,readTaskGroups=false\)\s*\{/);
assert.match(native,
  /if \(preparedPayload != null\) \{\s*persistVisiblePayload\(preparedPayload, currentUrl, epoch, append, onSaved\)/);
assert.match(native, /private fun persistVisiblePayload\(/);
assert.equal((native.match(/dao\.appendKind\(provider, kind, accepted\)/g)||[]).length,1,
  'DOM reader and fetched official task scan must share a single persistence path');
assert.match(native, /extract = control\("读取并替换同类记录"\)/);
assert.match(native, /appendVisible\?\.isEnabled = false/);
assert.ok(native.indexOf('if (payload.optBoolean("truncated"))') <
  native.indexOf('if (append) dao.appendKind(provider, kind, accepted)'),
  'must reject truncated imports before any Room write');

const records = readFileSync(
  'apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/NativeSchoolRecords.kt',
  'utf8'
);
const main = readFileSync(
  'apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/MainActivity.kt',
  'utf8'
);
assert.match(records, /@Transaction\s+suspend fun appendKind\(/);
assert.match(records, /require\(provider == "chaoxing" && kind in setOf\("course", "homework", "study_task"\)\)/);
assert.match(records, /require\(rows\.isNotEmpty\(\) && rows\.size <= 120\)/);
assert.match(records, /require\(keys\.size <= 500\)/);
assert.match(records, /insertAll\(rows\)\s+return keys\.size/);
assert.match(main, /count in 1\.\.500/);

function element(innerText) {
  return { innerText, getClientRects: () => [{}] };
}
function tableOf(headers, records) {
  const trs = [
    { ...element('header'), querySelectorAll: () => headers.map(element) },
    ...records.map(cells => ({
      ...element('row'),
      querySelectorAll: () => cells.map(element)
    }))
  ];
  return { ...element('table'), querySelectorAll: () => trs };
}
function runReader({ host, path, tables = [], tasks = [], courses = [], plans = [] }) {
  const doc = {
    querySelectorAll(selector) {
      if (selector === 'iframe') return [];
      if (selector === 'table') return tables;
      if (selector === '.bottomList li[data]') return tasks;
      if (selector === '.xtask_item') return plans;
      if (selector.startsWith('.course-name[title]')) return courses;
      return [];
    }
  };
  const location = { hostname: host, pathname: path,
    origin: 'https://' + host, href: 'https://' + host + path };
  return JSON.parse(runInNewContext(matched[1], { document: doc, location, URL }));
}

const homeworkHost = 'mooc1-api.chaoxing.com';
const homeworkPath = '/mooc-ans/mooc2/work/all-task';
const workRow = i => ['作业 ' + i, i % 2 ? '已提交' : '未提交', '高等数学'];
const makeWorkTable = count => tableOf(
  ['作业名称', '状态', '课程名称'], Array.from({ length: count }, (_,i) => workRow(i+1))
);
const normal = runReader({ host: homeworkHost, path: homeworkPath, tables: [makeWorkTable(120)] });
assert.equal(normal.kind, 'homework');
assert.equal(normal.records.length, 120);
assert.equal(normal.records[119].title, '作业 120');
assert.equal(normal.truncated, undefined);

const tooMany = runReader({ host: homeworkHost, path: homeworkPath, tables: [makeWorkTable(121)] });
assert.equal(tooMany.truncated, true);
assert.equal(tooMany.records.length, 0, 'partial results must never replace saved records');

// Multiple valid tables must be counted together, not silently stop at the first 120.
const split = runReader({ host: homeworkHost, path: homeworkPath,
  tables: [makeWorkTable(70), makeWorkTable(51)] });
assert.equal(split.truncated, true);
assert.equal(split.records.length, 0);

// A valid official homework table may appear after more than 35 visible layout tables.
// Never silently discard it and claim the imported snapshot is complete.
const unrelatedTable = () => tableOf(['普通说明', '日期'], [['非作业内容', '本周']]);
const afterThirtyFive = runReader({ host: homeworkHost, path: homeworkPath,
  tables: [...Array.from({ length: 36 }, unrelatedTable), makeWorkTable(1)] });
assert.equal(afterThirtyFive.kind, 'homework');
assert.equal(afterThirtyFive.records.length, 1);
assert.equal(afterThirtyFive.records[0].title, '作业 1');
assert.equal(afterThirtyFive.truncated, undefined);

// Exactly 200 visible tables remain readable; the 201st causes an explicit,
// all-or-nothing truncation rather than silently omitting official records.
const atTableLimit = runReader({ host: homeworkHost, path: homeworkPath,
  tables: [...Array.from({ length: 199 }, unrelatedTable), makeWorkTable(1)] });
assert.equal(atTableLimit.records.length, 1);
assert.equal(atTableLimit.truncated, undefined);
const overTableLimit = runReader({ host: homeworkHost, path: homeworkPath,
  tables: [...Array.from({ length: 200 }, unrelatedTable), makeWorkTable(1)] });
assert.equal(overTableLimit.truncated, true);
assert.equal(overTableLimit.records.length, 0, 'table-limit overflow must not replace saved data');

// Hidden framework tables do not count toward the visible table budget,
// so an otherwise valid owner-visible work list must not be falsely rejected.
const hiddenTable = () => ({ ...unrelatedTable(), getClientRects: () => [] });
const withHiddenLayoutTables = runReader({ host: homeworkHost, path: homeworkPath,
  tables: [...Array.from({ length: 220 }, hiddenTable), makeWorkTable(1)] });
assert.equal(withHiddenLayoutTables.records.length, 1);
assert.equal(withHiddenLayoutTables.records[0].title, '作业 1');
assert.equal(withHiddenLayoutTables.truncated, undefined);


const onlyCourseNames = runReader({ host: homeworkHost, path: homeworkPath,
  tables: [tableOf(['课程名称', '提交状态'], [['高等数学', '已完成']])] });
assert.equal(onlyCourseNames.records.length, 0, 'cannot invent a work title from a course name');

const courseHost = 'mooc2-ans.chaoxing.com';
const coursePath = '/mooc2-ans/visit/courselistdata';
const courseNode = title => ({
  ...element(title),
  getAttribute: name => name === 'title' ? title : null
});
const courseList = runReader({ host: courseHost, path: coursePath,
  courses: [courseNode('高等数学'), courseNode('大学英语')] });
assert.equal(courseList.kind, 'course');
assert.deepEqual(Array.from(courseList.records, r=>r.title), ['高等数学', '大学英语']);
const largeCourseList = runReader({ host: courseHost, path: coursePath,
  courses: Array.from({length:121}, (_,i)=>courseNode('课程 '+i)) });
assert.equal(largeCourseList.truncated, true);
assert.equal(largeCourseList.records.length, 0);

const taskNode = title => ({
  ...element(title),
  querySelector: selector => {
    if (selector.startsWith('p.overHidden2')) return element(title);
    if (selector.startsWith('p.status')) return element('未提交');
    return null;
  }
});
const workList = runReader({ host: 'mooc1.chaoxing.com', path: '/mooc2/work/list',
  tasks: Array.from({length:121},(_,i)=>taskNode('实验报告 '+i)) });
assert.equal(workList.truncated, true);
assert.equal(workList.records.length, 0);

// A completed official work row need not contain onclick/goTask.
const finishedNode = {
  ...element('作业已完成'),
  getAttribute: name => name === 'data'
    ? 'https://mooc1.chaoxing.com/mooc-ans/mooc2/work/task?courseId=123&classId=456&workId=987&enc=secret-token'
    : null,
  querySelector: selector => selector.startsWith('p.overHidden2')
    ? element('Python 实验报告一')
    : selector.startsWith('p.status') ? element('待批阅') : null
};
const finished = runReader({ host: 'mooc1.chaoxing.com', path: '/mooc2/work/list',
  tasks: [finishedNode] });
assert.equal(finished.kind, 'homework');
assert.deepEqual(Array.from(finished.records, x => [x.title, x.details]),
  [['Python 实验报告一',
    '当前网页显示的状态：待批阅 · 官方作业身份：work:123:456:987']]);
assert.equal(finished.records[0].sourceKey,'work:123:456:987');
// Jsoup-based reference reader sees completed tabs whose DOM rows are hidden.
// A validated official work tuple and explicit status are required for this.
const hiddenCompletedNode = {
  ...finishedNode,
  getClientRects: () => []
};
const hiddenCompleted = runReader({ host:'mooc1.chaoxing.com',
  path:'/mooc2/work/list', tasks:[hiddenCompletedNode] });
assert.equal(hiddenCompleted.records.length,1);
assert.equal(hiddenCompleted.records[0].sourceKey,'work:123:456:987');
const hiddenUntrustedNode={...hiddenCompletedNode,
  getAttribute: ()=>'https://attacker.invalid/work/task?courseId=123&classId=456&workId=987'};
const hiddenUntrusted=runReader({host:'mooc1.chaoxing.com',
  path:'/mooc2/work/list',tasks:[hiddenUntrustedNode]});
assert.equal(hiddenUntrusted.records.length,0);

assert.ok(!JSON.stringify(finished).includes('secret-token'));
const unrelatedSignedNode = {...finishedNode,
  getAttribute: () => 'https://attacker.example/work/task?courseId=123&classId=456&workId=987'};
const untrusted = runReader({host:'mooc1.chaoxing.com',
  path:'/mooc2/work/list',tasks:[unrelatedSignedNode]});
assert.equal(untrusted.records.length,1);
assert.equal(untrusted.records[0].sourceKey,'',
  'untrusted URL cannot identify a saved cloud task by title');

// Only the owner-visible plan page yields local learning-plan records.
// These are display evidence, never a request to click or finish anything.
const planCard = (title, state, id) => ({
  ...element(title),
  getAttribute: name => name === 'data-plan-id' ? id : null,
  querySelector: selector => selector === '.xname' ? element(title) : null,
  querySelectorAll: selector => selector === '.xtip_info_card' ? [element(state)] : []
});
const plans = runReader({host:'task.chaoxing.com',
  path:'/userStudyPlan/studyPlanSubPage',
  plans: [planCard('工程制图 第四次作业','待批阅','987654'),
    planCard('课程阅读','未完成')]});
assert.equal(plans.kind, 'study_task');
assert.deepEqual(Array.from(plans.records, x=>x.details),
 ['学习计划状态：待批阅 · 学习任务身份：plan:987654','学习计划状态：未完成']);
assert.equal(plans.records[0].sourceKey,'plan:987654');
assert.equal(plans.records[1].sourceKey,'');
const wrongPage = runReader({host:'task.chaoxing.com',
 path:'/untrusted',plans:[planCard('假状态','已完成')]});
assert.equal(wrongPage.records.length,0);

assert.match(native,/CHAOXING_TASK_DISCOVERY_ORIGIN/);
assert.match(native,/browser\.loadUrl\(CHAOXING_COURSES\)/);
assert.match(native,/startOwnerTaskDiscovery\(\)/);
assert.match(native,/path == "\/userStudyPlan\/studyPlanSubPage"/);
assert.match(native,/if \(kind !in kinds\)/);
assert.match(records,/nativeChaoxingVisibleStatus/);
assert.match(records,/kind in setOf\("course", "homework", "study_task"\)/);
assert.match(records,/sourceHost !in setOf/);


// One tap, same-origin official task-plan group APIs in the on-device WebView.
// The reference app also uses these endpoints, but not its auto-reading code.
const studyScriptMatch = native.match(/private const val READ_TASK_PLAN_GROUPS = """([\s\S]*?)"""/);
assert.ok(studyScriptMatch, 'the task-group scan must be a reviewable fixed script');
const taskScript = studyScriptMatch[1];
assert.doesNotMatch(taskScript,/document\.cookie|\.click\(|\.submit\(|getToStudyUrl|readProgress/);
assert.match(native, /readTaskGroups = false, onSaved = onSaved/);
assert.match(native, /readTaskGroups && provider == "chaoxing"/);
assert.match(native, /scoreHandler\.postDelayed\(\{ poll\(attempt \+ 1\) \}, 250\)/);
assert.match(native, /if \(attempt >= 72\)/);
assert.match(native, /__xiaoxuPlanReadEpoch/);
async function taskGroupsFixture({
  page='/userStudyPlan/studyPlanSubPage',
  userId='opaqueUser7',
  groups=[{encryptGroupId:'encryptedGroup7'}],
  plans=[{planId:567,name:'Python 实验报告一',statusText:'待批阅'},
    {planId:568,name:'工程制图 第五次作业',isFinish:1},
    {planId:569,name:'课程阅读',isFinish:'0'},
    {name:'无来源 ID 的学习任务',statusText:'状态未知'}],
  failPlan=false
}={}) {
  const window={};
  const requested=[];
  const location={hostname:'task.chaoxing.com',pathname:page,
    origin:'https://task.chaoxing.com',protocol:'https:',
    href:'https://task.chaoxing.com'+page};
  const document={scripts:[{textContent:'const eTaskUserId = "'+userId+'";'}]};
  const fetch=async (url,opts)=>{
    const u=new URL(url,location.origin);
    requested.push(u.pathname);
    assert.equal(u.origin,location.origin,'requests must stay same-origin');
    assert.equal(opts.method,'POST');
    assert.equal(opts.credentials,'same-origin');
    assert.equal(opts.redirect,'error');
    assert.equal(opts.headers['X-Requested-With'],'XMLHttpRequest');
    assert.equal(u.searchParams.get('encryTaskUserId'),userId);
    if(u.pathname==='/userStudyPlan/getGroupData') {
      return {ok:true,text:async()=>JSON.stringify({data:groups})};
    }
    if(u.pathname==='/userStudyPlan/getPlanDataByGroupId') {
      if(failPlan) return {ok:false,text:async()=>'<secret html>'};
      assert.equal(u.searchParams.get('encryGroupId'),groups[0].encryptGroupId);
      return {ok:true,text:async()=>JSON.stringify({data:plans})};
    }
    throw Error('unapproved path');
  };
  runInNewContext(taskScript,{window,location,document,URL,fetch});
  for(let i=0;i<30&&!window.__xiaoxuPlanResult;i++)
    await new Promise(resolve=>setTimeout(resolve,5));
  assert.ok(window.__xiaoxuPlanResult,'async read must finish with bounded result');
  return {data:JSON.parse(window.__xiaoxuPlanResult),requested};
}
const fetched=await taskGroupsFixture();
assert.equal(fetched.data.error,'');
assert.equal(fetched.data.kind,'study_task');
assert.equal(fetched.data.records.length,4);
assert.deepEqual(Array.from(fetched.data.records,x=>[x.sourceKey,x.details]),[
 ['plan:567','学习计划状态：待批阅 · 学习任务身份：plan:567'],
 ['plan:568','学习计划状态：已完成 · 学习任务身份：plan:568'],
 ['plan:569','学习计划状态：未完成 · 学习任务身份：plan:569'],
 ['','学习计划状态：状态未知']
]);
assert.deepEqual(fetched.requested,[
 '/userStudyPlan/getGroupData','/userStudyPlan/getPlanDataByGroupId']);
assert.ok(!JSON.stringify(fetched.data).includes('opaqueUser7'));
assert.ok(!JSON.stringify(fetched.data).includes('encryptedGroup7'));
const partial=await taskGroupsFixture({failPlan:true});
assert.equal(partial.data.error,'upstream_unavailable');
assert.equal(partial.data.records.length,0,'no partially trusted group read');
const invalidId=await taskGroupsFixture({userId:'invalid\\nCookie=secret'});
assert.equal(invalidId.data.error,'identity_unavailable');
assert.equal(invalidId.requested.length,0);
const oversizedGroupRead=await taskGroupsFixture({groups:Array.from({length:25},
  (_,i)=>({encryptGroupId:'g'+i}))});
assert.equal(oversizedGroupRead.data.error,'too_many_groups');
assert.deepEqual(oversizedGroupRead.requested,['/userStudyPlan/getGroupData']);
const wrongHost=await taskGroupsFixture({page:'/untrusted'});
assert.equal(wrongHost.data.error,'wrong_page');
assert.equal(wrongHost.requested.length,0);
const planPayload=JSON.stringify(fetched.data);
const displayed=JSON.parse(runInNewContext(matched[1],{
  document:{querySelectorAll:()=>[]},
  location:{hostname:'task.chaoxing.com',pathname:'/userStudyPlan/studyPlanSubPage'},
  window:{__xiaoxuPlanResult:planPayload}
}));
assert.deepEqual(Array.from(displayed.records,x=>x.title),
 fetched.data.records.map(x=>x.title));
console.log('xiaoxu official on-device plan-group read and status mapping passed');


// The previous shortcut was a bare studyPlanSubPage with no taskId.
// Discovery must first obtain numeric course IDs, read task-engine summaries
// from mobilelearn's own origin, then construct the official jumpStudyPlanList.
assert.doesNotMatch(native,/CHAOXING_STUDY_TASKS\s*=/);
assert.match(native,/startOwnerTaskDiscovery\(\)/);
assert.match(native,/\/api\/v1\/middlePageApi\/jumpStudyPlanList/);
assert.match(native,/captureStudyPlanGroups \{ success ->/);
assert.match(native,/onSaved\?\.invoke\(true\)/);
assert.match(native,/planFallbackTried/);
const courseTaskScript = readFileSync(
 'apps/xiaoxu-android/app/src/main/assets/xiaoxu_chaoxing_course_task_discovery.js','utf8');
assert.doesNotMatch(courseTaskScript,/document\.cookie|\.click\(|\.submit\(|getToStudyUrl/);
const profileData=JSON.parse(runInNewContext(courseTaskScript,{
 URL,location:{hostname:'i.chaoxing.com',pathname:'/base',
  href:'https://i.chaoxing.com/base'},
 document:{scripts:[{textContent:'var uid="secret"; var fid="427";'}]}
}));
assert.deepEqual(Object.keys(profileData).sort(),['fid','kind']);
assert.equal(profileData.kind,'profile');
assert.equal(profileData.fid,'427');
assert.ok(!JSON.stringify(profileData).includes('secret'));
assert.match(native,/courseType=1&courseFid=/);
const badProfile=JSON.parse(runInNewContext(courseTaskScript,{
 URL,location:{hostname:'i.chaoxing.com',pathname:'/base',
  href:'https://i.chaoxing.com/base'},
 document:{scripts:[{textContent:'var fid="not-a-number";'}]}
}));
assert.equal(badProfile.error,'school_identity');
const baseCourse=[
 {href:'/visit/stucoursemiddle?courseid=123&clazzid=456&cpi=789',title:'Python 程序设计'},
 {href:'/visit/stucoursemiddle?cpi=777&courseid=124&clazzid=457',title:'工程制图'}
];
const courseAnchors=baseCourse.map(({href,title})=>({
 getAttribute:key=>key==='href'?href:null,
 querySelector:key=>key==='.course-name'?{getAttribute:k=>k==='title'?title:null}:null,
 parentElement:null
}));
const courseResult=JSON.parse(runInNewContext(courseTaskScript,{
 URL,location:{hostname:'mooc2-ans.chaoxing.com',
  pathname:'/mooc2-ans/visit/courselistdata',
  href:'https://mooc2-ans.chaoxing.com/mooc2-ans/visit/courselistdata'},
 document:{querySelectorAll:selector=>
   selector==='a[href*="stucoursemiddle"]'?courseAnchors:[]}
}));
assert.equal(courseResult.kind,'courses');
assert.deepEqual(courseResult.courses.map(c=>[c.courseId,c.classId,c.title]),[
 ['123','456','Python 程序设计'],['124','457','工程制图']]);
const fakeOutsideCourse={...courseAnchors[0],
 getAttribute:()=> 'https://other.example/visit/stucoursemiddle?courseid=123&clazzid=456&cpi=789'};
const spoofed=JSON.parse(runInNewContext(courseTaskScript,{
 URL,location:{hostname:'mooc2-ans.chaoxing.com',
  pathname:'/mooc2-ans/visit/courselistdata',
  href:'https://mooc2-ans.chaoxing.com/mooc2-ans/visit/courselistdata'},
 document:{querySelectorAll:()=>[fakeOutsideCourse]}
}));
assert.equal(spoofed.error,'courses_unverified');
const ownerSession={__xiaoxuOwnedCourse:{courseId:'123',classId:'456'}};
const seenCalls=[];
const discovered=JSON.parse(runInNewContext(courseTaskScript,{
 URL,window:ownerSession,
 location:{hostname:'mobilelearn.chaoxing.com',pathname:'/',
  href:'https://mobilelearn.chaoxing.com/'},
 fetch:async (raw,opts)=>{
   const url=new URL(raw,'https://mobilelearn.chaoxing.com');
   seenCalls.push(url);
   assert.equal(url.hostname,'mobilelearn.chaoxing.com');
   assert.equal(url.pathname,'/v2/apis/active/getData');
   assert.equal(url.searchParams.get('courseId'),'123');
   assert.equal(url.searchParams.get('classId'),'456');
   assert.equal(opts.credentials,'same-origin');
   assert.equal(opts.method,'GET');
   return {ok:true,text:async()=>JSON.stringify({
     data:[{id:4321,name:'Python 课程任务',planCount:3},
           {id:4322,name:'实验报告',planCount:4}]
   })};
 }
}));
assert.equal(discovered.kind,'started');
for(let i=0;i<40&&!ownerSession.__xiaoxuTaskDiscoveryResult;i++)
 await new Promise(resolve=>setTimeout(resolve,5));
const taskResult=JSON.parse(ownerSession.__xiaoxuTaskDiscoveryResult);
assert.equal(taskResult.kind,'tasks');
assert.deepEqual(taskResult.tasks.map(t=>t.taskId),['4321','4322']);
assert.equal(seenCalls.length,1);
assert.ok(!JSON.stringify(taskResult).includes('encryTaskUserId'));
const invalidSource=JSON.parse(runInNewContext(courseTaskScript,{
 URL,window:{__xiaoxuOwnedCourse:{courseId:'x',classId:'456'}},
 location:{hostname:'mobilelearn.chaoxing.com',pathname:'/',
  href:'https://mobilelearn.chaoxing.com/'}
}));
assert.equal(invalidSource.error,'course_identity');
console.log('xiaoxu course-to-mobilelearn task discovery tests passed');


// Ordinary course homework is distinct from task-engine study plans. Fetch
// only official pages in the signed-in mooc1 WebView and save numeric IDs.
// The batch audit must expose bounded source-owned failure causes, otherwise
// a repeat failure screenshot cannot distinguish course-entry from list parse.
const taskHub=readFileSync(
 'apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/RealDataWorkspace.kt','utf8');
assert.match(taskHub,/course\.failure\?\.label/);
assert.match(taskHub,/course\.failureStage\?\.let/);
assert.match(native,/学习计划入口需要单独登录；继续读取本人普通课程作业/);
const homeworkScript=readFileSync(
  'apps/xiaoxu-android/app/src/main/assets/xiaoxu_chaoxing_course_homework_scan.js','utf8');
assert.doesNotMatch(homeworkScript,/document\.cookie|\.click\(|\.submit\(|localStorage|sessionStorage/);
assert.match(native,/openNextOwnedCourseHomework\(\)/);
assert.match(native,/window\.__xiaoxuOwnedWorkCourse/);
assert.match(native,/__xiaoxuCourseHomeworkResult/);
assert.match(native,/course\.cpi \+ "&ismooc2=1&v=2"/);
const homeworkLink=id=>'https://mooc1.chaoxing.com/mooc-ans/mooc2/work/task'+
  '?courseId=123&classId=456&workId='+id+'&enc=private-signed-token';
const workNode=(id,status)=>({
 getAttribute:key=>key==='data'?homeworkLink(id):null,
 querySelector:selector=> {
   if(selector==='p.overHidden2.fl')return {textContent:'Python 第'+id+'次作业'};
   if(selector==='p.status.fl')return {textContent:status};
   return null;
 }
});
const signedDoc=(rows=[],pages=1,inputs={},bare=false)=>({
 title:'作业列表',
 querySelectorAll:selector=>
   selector==='.bottomList li[data]'?(bare?[]:rows):
   selector==='li[data]'?rows:
   selector==='#page li'?
     Array.from({length:pages},(_,i)=>({textContent:String(i+1)})):[],
 querySelector:selector=>{
   const match=/^input#(.+)$/.exec(selector);
   return match&&inputs[match[1]]!==undefined?
     {value:inputs[match[1]]}:null;
 }
});
async function runCourseHomeworkScan({
 host='mooc1.chaoxing.com',path='/visit/stucoursemiddle',
 document=signedDoc([workNode('901','待批阅')]),
 course={courseId:'123',classId:'456',cpi:'789'},responses={},
 failFetch=false
}={}){
 const window={__xiaoxuOwnedWorkCourse:course};
 const calls=[];
 const fetch=async(raw,options)=>{
   const url=new URL(raw,'https://mooc1.chaoxing.com');
   calls.push(url);
   assert.equal(url.hostname,'mooc1.chaoxing.com');
   assert.equal(options.credentials,'same-origin');
   assert.equal(options.method,'GET');
   assert.equal(url.searchParams.get('courseId'),'123');
   assert.equal(url.searchParams.get('classId'),'456');
   assert.equal(url.searchParams.get('cpi'),'789');
   assert.equal(url.searchParams.get('ut'),'s');
   assert.equal(url.searchParams.get('isdisplaytable'),'2');
   assert.match(url.searchParams.get('t')||'',/^\d{10,}$/);
   if(failFetch)return {ok:false,text:async()=>''};
   return {ok:true,text:async()=>responses[url.searchParams.get('pageNum')]||'page1'};
 };
 const DOMParser=class {
   parseFromString(content){
     if(content==='page1')return signedDoc([workNode('902','已批阅')],2,{},true);
     if(content==='page2')return signedDoc([workNode('903','已完成')],2,{},true);
     return signedDoc();
   }
 };
 const location={hostname:host,pathname:path,
   href:'https://'+host+path,origin:'https://'+host,protocol:'https:'};
 const result=runInNewContext(homeworkScript,{window,location,URL,fetch,DOMParser,document,URLSearchParams});
 assert.equal(result,host==='mooc1.chaoxing.com'?'started':'blocked');
 for(let i=0;i<35&&!window.__xiaoxuCourseHomeworkResult;i++)
   await new Promise(resolve=>setTimeout(resolve,5));
 assert.ok(window.__xiaoxuCourseHomeworkResult);
 return {data:JSON.parse(window.__xiaoxuCourseHomeworkResult),calls};
}
const embeddedCourse=await runCourseHomeworkScan();
assert.equal(embeddedCourse.data.error,'');
assert.equal(embeddedCourse.data.records.length,1);
assert.equal(embeddedCourse.data.records[0].sourceKey,'work:123:456:901');
assert.match(embeddedCourse.data.records[0].details,/待批阅/);
assert.equal(embeddedCourse.calls.length,0);
// A real official work-list template need not wrap li[data] in .bottomList.
const bareList=await runCourseHomeworkScan({
 path:'/mooc2/work/list',
 document:signedDoc([workNode('905','已完成')],1,{},true)
});
assert.equal(bareList.data.error,'');
assert.equal(bareList.data.records.length,1);
assert.match(bareList.data.records[0].details,/已完成/);
assert.equal(bareList.calls.length,0);
assert.ok(!JSON.stringify(embeddedCourse.data).includes('private-signed-token'));
const inputCreds={workEnc:'private-work-enc',enc:'private-stu-enc',openc:'private-openc'};
const pagedCourse=await runCourseHomeworkScan({
 document:signedDoc([],1,inputCreds),responses:{'1':'page1','2':'page2'}
});
assert.equal(pagedCourse.data.error,'');
assert.deepEqual(pagedCourse.data.records.map(x=>x.sourceKey),
 ['work:123:456:902','work:123:456:903']);
assert.equal(pagedCourse.calls.length,2);
// A course entry that displays only page one must continue to the official
// paginated list when its own signed work token is available.
const partialEntry=await runCourseHomeworkScan({
 document:signedDoc([workNode('901','待批阅')],2,inputCreds),
 responses:{'1':'page1','2':'page2'}
});
assert.equal(partialEntry.data.error,'');
assert.deepEqual(partialEntry.data.records.map(x=>x.sourceKey),
 ['work:123:456:902','work:123:456:903']);
assert.equal(partialEntry.calls.length,2);
const incompleteEntry=await runCourseHomeworkScan({
 document:signedDoc([workNode('901','待批阅')],2)
});
assert.equal(incompleteEntry.data.error,'partial_page');
assert.equal(incompleteEntry.data.records.length,0);
assert.ok(pagedCourse.calls.every(url=>
 url.searchParams.get('enc')==='private-work-enc'));
assert.ok(!JSON.stringify(pagedCourse.data).includes('private-work-enc'));
const optionalTokens=await runCourseHomeworkScan({
 document:signedDoc([],1,{workEnc:'private-work-enc'}),
 responses:{'1':'page1','2':'page2'}
});
assert.equal(optionalTokens.data.error,'');
assert.equal(optionalTokens.calls.length,2);
assert.equal(optionalTokens.calls[0].searchParams.has('openc'),false);
assert.equal(optionalTokens.calls[0].searchParams.has('stuenc'),false);
const absentWork=await runCourseHomeworkScan({document:signedDoc()});
assert.equal(absentWork.data.error,'work_entry_unavailable');
assert.equal(absentWork.data.records.length,0);
const unownedWork=await runCourseHomeworkScan({
 document:signedDoc([workNode('901','待批阅')]),
 course:{courseId:'124',classId:'456',cpi:'789'}
});
assert.equal(unownedWork.data.error,'invalid_response');
assert.equal(unownedWork.data.records.length,0);
const blockedWork=await runCourseHomeworkScan({host:'evil.example'});
assert.equal(blockedWork.data.error,'wrong_origin');
console.log('xiaoxu same-origin ordinary homework discovery passed');

console.log('xiaoxu chaoxing native visible-reader tests passed');
