import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const file = name => readFileSync(name, 'utf8');
const main = file('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/MainActivity.kt');
const screen = file('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/ScheduleFeature.kt');
const header = file('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/ScheduleHeader.kt');
const workspace = file('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/AppWorkspaceState.kt');
const shell = file('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/RebuildShell.kt');
const pages = file('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/AppFeaturePages.kt');
const preview = file('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/TimetableImportPreviewSheet.kt');
const settings = file('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/ScheduleSettingsFeature.kt');
const store = file('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/LocalScheduleStore.kt');
const localReminders = file('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/LocalCourseReminders.kt');
const ics = file('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/IcsTimetableParser.kt');
const test = file('apps/xiaoxu-android/app/src/test/java/io/github/x07xh/xiaoxu/data/IcsTimetableParserTest.kt');

assert.match(main, /ActivityResultContracts\.OpenDocument\(\)/,
  'Android app must have its own file picker');
assert.match(main, /timetableFilePicker\.launch\(arrayOf\("\*\/\*"\)\)/);
assert.match(main, /contentResolver\.openInputStream\(uri\)/,
  'Read only the selected file URI');
assert.match(main, /LocalScheduleStore\.parseIcs\(text\)/);
assert.match(main, /LocalScheduleStore\.parseCourses\(text\)/);
assert.match(main, /LocalTimetableBackup\.parse\(text\)/);
assert.match(main, /LocalTimetableBackup\.export\(/,
  '可从原生页面导出完整课程与学期设置');
assert.match(main, /localScheduleRevision\+\+/,
  '导入相同课程但不同学期设置时也必须触发界面刷新');
assert.match(main, /localScheduleRevision = localScheduleRevision/,
  '课表版本变动必须送达原生课表');
assert.match(workspace, /key2 = scheduleRevision, key3 = settingsRevision/);
assert.match(shell, /rememberAppWorkspace\(dao, cloud, localLessons, localScheduleRevision, settingsRevision/);
assert.match(settings, /title = "导出课表"[\s\S]*?onClick = actions\.exportTimetable/);
assert.match(screen, /selectableTeachingWeeks\(availableLessons, calendar\)/);
assert.match(screen, /todayWeek = calendar\?\.currentWeek\(today\)/);
assert.match(screen, /todayWeek\?\.takeIf \{ it in weeks \}/);
// File writes and post-commit reload must run on IO; old asynchronous reads
// must not restore an earlier timetable after a successful import.
assert.match(main, /withContext\(Dispatchers\.IO\)\s*\{\s*val count = LocalScheduleStore\.saveParsed\(/);
assert.match(main, /applicationContext, pending\.imported\.lessons/);
assert.match(main, /Triple\(count, updated, reminderOk\)/);
assert.match(main, /LocalCourseReminders\.reconcileAfterSave\(/,
  '提醒维护失败不可将已经保存的课表伪装成导入失败');
assert.match(main, /已保存 " \+ count \+ " 个课块，但提醒维护失败/,
  '若课表保存成功但通知维护失败，必须提供准确反馈');
assert.match(shell, /LocalCourseReminders\.reconcileAfterSave\(/,
  '手动添加或单周编辑成功也不能被提醒调度错误掩盖');
assert.match(localReminders, /fun reconcileAfterSave\(/);
assert.match(localReminders, /getNotificationChannel\(CHANNEL\)/,
  '已禁用的上课提醒通知渠道不能被误判为可通知');
assert.match(localReminders, /channel\.importance != NotificationManager\.IMPORTANCE_NONE/,
  '原生提醒必须尊重系统通知渠道开关');
assert.match(localReminders, /runCatching\s*\{\s*reconcile\(context, lessons, calendar\)\s*\}/,
  '提醒错误必须被独立捕获，不得回滚已经持久化的课程');
assert.match(main, /localScheduleReadSerial\+\+/);
assert.match(main, /localLessons = savedLessons/);
assert.match(main, /PendingTimetableImport/);
assert.match(main, /Uri\.decode\(name\)/,
  'Percent-encoded ICS filenames must be displayed as readable Chinese');
assert.match(main, /TimetableImportPreviewSheet\(/,
  'The file confirmation must be rendered inside the single Compose design system');
assert.doesNotMatch(main, /AlertDialog\.Builder\(/,
  'Do not show a raw Android text-dialog for parsed lessons');
assert.match(preview, /AppDialog\(/);
assert.match(preview, /LazyColumn\(/,
  '25+ lessons must be reviewed in a bounded, scrollable list');
assert.match(preview, /previewWeeks\(lesson\.weeks\)/);
assert.match(preview, /lesson\.location\.ifBlank/);
assert.match(preview, /lesson\.teacher\.ifBlank/);
assert.match(preview, /confirmedReduced/,
  'Replacing fewer meetings requires an additional explicit confirmation');
// The new route is a thin adapter to the existing local workspace and original import callbacks.
assert.match(screen, /localImportedScheduleView\(lessons\)\.sourceLabel/);
assert.match(workspace, /val latestLessons = rememberUpdatedState\(lessons\)/);
assert.match(workspace, /val latestRevision = rememberUpdatedState\(scheduleRevision\)/);
assert.match(workspace, /lessonsSource = \{ pageSnapshot\(latestLessons\.value\.takeIf \{ latestRevision\.value > 0 \|\| it\.isNotEmpty\(\) \}\) \}/,
  '读取最新课表及版本；没有真实导入时不得伪造已读取的空课表');
assert.match(screen, /UpstreamCourseDetailSheet\(course, allCourses/);
assert.match(screen, /onClose = \{ selectedId = null \}/);
assert.match(screen, /onEdit = \{/);
assert.match(pages, /actions\.openLocalCourse\(lesson\.id, lesson\.weekday\)/);
assert.match(shell, /openLocalCourse = \{ id, _ -> inboxLessonId = id; goTo\(AppRoute\.SCHEDULE\)/);
assert.match(shell, /inboxLessonId \?: reminderLessonId/);
assert.doesNotMatch(screen, /华水青果 · 可信只读|deriveScheduleUiState\(state\)|client\.refresh\(\)|state\.lessons/);
assert.match(screen, /requestedWeek\.takeIf \{ it in weeks \}/);
assert.match(screen, /lessons\.isEmpty\(\)/);
assert.match(settings, /title = "导入课表"[\s\S]*?onClick = actions\.importTimetable/);
assert.doesNotMatch(header, /onImportClick|onExportClick/);
assert.match(header, /onSettingsClick\(\)/);
assert.match(shell, /importTimetable = openFileImport, exportTimetable = openFileExport/);
assert.match(store, /fun parseIcs\(input: String\): List<VerifiedLesson>/);
assert.match(ics, /anchors\.size == 1/);
assert.match(ics, /MAX_EVENTS = 1200/);
assert.match(ics, /缺少完整课程名称、连续节次或原始周次/);
assert.match(test, /@Test fun converterEventsBecomeOneWeeklyCourse/);
assert.match(test, /@Test fun rejectsWeekInconsistencyInsteadOfGuessing/);
assert.doesNotMatch(main + store, /getAllCookies|CookieManager\.getCookie|https?:\/\/dorm-os.*importTimetableFile/,
  'file upload never needs school credentials or external services');
console.log('native timetable file import guards passed');
