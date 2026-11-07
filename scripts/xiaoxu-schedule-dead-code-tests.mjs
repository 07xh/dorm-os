// Prevent retired cloud-only Compose views from being reintroduced into the
// local-import timetable. Keep the local-import source only; cloud school sessions live in CloudClient.
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const source=readFileSync(new URL('../apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/ScheduleScreen.kt',import.meta.url),'utf8');
const retired=[
  "ScheduleHeader",
  "ScheduleBanner",
  "DetailRow",
  "ScheduleLoadingState",
  "SkeletonBlock",
  "ScheduleNotConnectedState",
  "ScheduleSourceMissingState",
  "ScheduleUnverifiedState",
  "ScheduleFailedState",
  "ScheduleEmptyState",
  "StateScaffold"
];
for(const name of retired){
  assert.doesNotMatch(source,new RegExp('\\bprivate fun '+name+'\\s*\\('),
    'Retired cloud-only timetable UI must not return: '+name);
}
for(const name of ['CloudTimetablePanel','LocalTimetableEmptyState',
  'ScheduleVerifiedContent','HomeCourseDetailSheet','WeeklyTimetableGrid',
  'LocalImportedScheduleView','localImportedScheduleView']){
  assert.match(source,new RegExp('\\b'+name+'\\b'),
    'Active local schedule or compatibility model must remain: '+name);
}
assert.doesNotMatch(source,/sealed interface ScheduleUiState|fun deriveScheduleUiState\(|CloudUiState/,
  'Retired cloud timetable UI state must not reappear in the active screen');
assert.doesNotMatch(source,/华水青果 · 可信只读|localImport: Boolean|if \(localImport\)/,
  'Local-only course detail cannot offer an unreachable cloud-verified branch');
assert.match(source,/sourceLabel = "本机导入 · 待本人核对"/,
  'Import provenance and explicit user-verification status must remain visible without redundant course count');
assert.doesNotMatch(source,/text = "本机导入 · 待核对"/,
  'Course detail should retain exact reference screenshot copy without a duplicated provenance badge');
assert.match(source,/CourseDetailBottomSheet\(lesson\.toUi\(\), onDismiss = onDismiss\)/,
  'Home course detail must reuse the same drawer without cloud-source flags');
assert.doesNotMatch(source, /client:\s*CloudClient|openWebsite:\s*\(\)\s*->\s*Unit|openProfile:\s*\(\(\)\s*->\s*Unit\)\?|openLocalImport:/,
  'Local timetable must not accept unused cloud, website, profile, or legacy import callbacks');
assert.match(source,/data\.lessons\.isEmpty\(\)/,
  'Empty timetable must be based on the actual local import');
assert.match(source,/LocalTimetableEmptyState\(/,
  'Import action remains reachable');
assert.match(source,/onSettings = \{ showSemesterSettings = true \}/,
  'Empty timetable reaches the same settings host and inbox as imported timetable');
assert.doesNotMatch(source,/label = "重新导入课表"/,
  'File import must not remain in the timetable heading');
console.log('Local timetable uses only live UI; retired cloud-state views removed');
