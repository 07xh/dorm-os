import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const file = name => readFileSync(name, 'utf8');
const main = file('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/MainActivity.kt');
const screen = file('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/ScheduleScreen.kt');
const store = file('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/LocalScheduleStore.kt');
const ics = file('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/IcsTimetableParser.kt');
const test = file('apps/xiaoxu-android/app/src/test/java/io/github/x07xh/xiaoxu/data/IcsTimetableParserTest.kt');

assert.match(main, /ActivityResultContracts\.OpenDocument\(\)/,
  'Android app must have its own file picker');
assert.match(main, /timetableFilePicker\.launch\(arrayOf\("\*\/\*"\)\)/);
assert.match(main, /contentResolver\.openInputStream\(uri\)/,
  'Read only the selected file URI');
assert.match(main, /LocalScheduleStore\.parseIcs\(text\)/);
assert.match(main, /LocalScheduleStore\.parseCourses\(text\)/);
// File writes and post-commit reload must run on IO; old asynchronous reads
// must not restore an earlier timetable after a successful import.
assert.match(main, /withContext\(Dispatchers\.IO\)\s*\{\s*val count = LocalScheduleStore\.saveParsed\(/);
assert.match(main, /applicationContext, parsed\.second/);
assert.match(main, /count to LocalScheduleStore\.load\(applicationContext\)/);
assert.match(main, /localScheduleReadSerial\+\+/);
assert.match(main, /localLessons = savedLessons/);
assert.match(main, /课表数量减少，请核对/);
assert.match(main, /本机解析详情（前 30 条）/);
assert.match(main, /lesson\.weeks\.joinToString\(","\)/);
assert.match(main, /lesson\.location\.ifBlank/);
assert.match(main, /lesson\.teacher\.ifBlank/);
assert.match(main, /val preview = parsed\.second/);
// 本机导入是课表唯一数据源；只检查当前真实渲染的组件，
// 不再要求已废弃的云端 Header / stale badge 继续存在。
assert.match(screen, /sourceLabel = "华水青果 · 本机导入（待本人核对）"/,
  '本机导入源标签须明确待本人核对');
assert.match(screen, /Text\("本机导入 · " \+ data.lessons.size \+ " 个课块 · 请核对课程信息",/,
  '当前课表必须直接提示本人核对导入课程');
assert.match(screen, /text = "本机导入 · 待核对"/,
  '课程详情必须明确标记本机导入，不能暗示学校官方核验');
assert.match(screen, /CourseDetailBottomSheet\(\s*lesson = opened,\s*onDismiss =/);
assert.match(screen, /CourseDetailBottomSheet\(lesson\.toUi\(\), onDismiss = onDismiss\)/,
  '首页与课表复用本机来源的同一课程详情');
assert.doesNotMatch(screen, /华水青果 · 可信只读|localImport: Boolean|if \(localImport\)/,
  '禁止已退役的云端可信详情分支回流');
assert.match(screen, /当前混合展示整学期课块；选择具体周次才能判断那周是否有课/);
assert.match(screen, /个课块/);

assert.match(main, /再次确认替换/);
assert.match(screen, /Text\("从文件导入课表"\)/);
const currentTimetableEntry = screen.slice(
  screen.indexOf('fun CloudTimetablePanel('),
  screen.indexOf('private fun ScheduleVerifiedContent(')
);
assert.match(currentTimetableEntry, /localLessons\.isEmpty\(\)/);
assert.match(currentTimetableEntry, /localImportedScheduleView\(localLessons\)/);
assert.match(currentTimetableEntry, /LocalTimetableEmptyState\(openFileImport\)/);
assert.doesNotMatch(currentTimetableEntry, /deriveScheduleUiState\(state\)|client\.refresh\(\)|state\.lessons/,
  '本机课表不依赖云端课程或云端新鲜度');
assert.match(screen, /onClick = openFileImport/);
assert.match(store, /fun parseIcs\(input: String\): List<VerifiedLesson>/);
assert.match(ics, /anchors\.size == 1/);
assert.match(ics, /MAX_EVENTS = 1200/);
assert.match(ics, /缺少完整课程名称、连续节次或原始周次/);
assert.match(test, /@Test fun converterEventsBecomeOneWeeklyCourse/);
assert.match(test, /@Test fun rejectsWeekInconsistencyInsteadOfGuessing/);
assert.doesNotMatch(main + store, /getAllCookies|CookieManager\.getCookie|https?:\/\/dorm-os.*importTimetableFile/,
  'file upload never needs school credentials or external services');
console.log('native timetable file import guards passed');
