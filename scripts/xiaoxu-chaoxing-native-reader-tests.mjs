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
assert.match(records, /require\(provider == "chaoxing" && kind in setOf\("course", "homework"\)\)/);
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
function runReader({ host, path, tables = [], tasks = [], courses = [] }) {
  const doc = {
    querySelectorAll(selector) {
      if (selector === 'iframe') return [];
      if (selector === 'table') return tables;
      if (selector === 'li[onclick*="goTask"]') return tasks;
      if (selector.startsWith('.course-name[title]')) return courses;
      return [];
    }
  };
  const location = { hostname: host, pathname: path, origin: 'https://' + host };
  return JSON.parse(runInNewContext(matched[1], { document: doc, location }));
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

console.log('xiaoxu chaoxing native visible-reader tests passed');
