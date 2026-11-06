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
assert.match(source,/localLessons\.isEmpty\(\)/,
  'Empty timetable must be based on the actual local import');
assert.match(source,/LocalTimetableEmptyState\(openFileImport\)/,
  'Import action remains reachable');
console.log('Local timetable uses only live UI; retired cloud-state views removed');
