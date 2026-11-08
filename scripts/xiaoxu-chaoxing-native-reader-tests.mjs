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

assert.match(native,/CHAOXING_STUDY_TASKS/);
assert.match(native,/browser\.loadUrl\(CHAOXING_STUDY_TASKS\)/);
assert.match(native,/path == "\/userStudyPlan\/studyPlanSubPage"/);
assert.match(native,/if \(kind !in kinds\)/);
assert.match(records,/nativeChaoxingVisibleStatus/);
assert.match(records,/kind in setOf\("course", "homework", "study_task"\)/);
assert.match(records,/sourceHost !in setOf/);

console.log('xiaoxu chaoxing native visible-reader tests passed');
