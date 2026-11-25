// Validate the active local timetable route after replacing the old screen.
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
const root = new URL('../apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/', import.meta.url);
const read = name => readFileSync(new URL(name, root), 'utf8');
const feature = read('ScheduleFeature.kt');
const header = read('ScheduleHeader.kt');
const grid = read('UpstreamTimetable.kt');
const details = read('UpstreamCourseDetailSheet.kt');
const workspace = read('AppWorkspaceState.kt');
const routes = read('AppFeaturePages.kt');
const shell = read('RebuildShell.kt');
const settings = read('ScheduleSettingsFeature.kt');
const source = [feature, header, grid, details].join('\n');
for (const name of ['ScheduleScreen.kt', 'WeeklyTimetableGrid.kt', 'SemesterSettingsDialog.kt']) {
  assert.equal(existsSync(new URL(name, root)), false, 'Replaced UI must not return: ' + name);
}
for (const name of ['ScheduleBanner', 'ScheduleLoadingState', 'SkeletonBlock',
  'ScheduleNotConnectedState', 'ScheduleSourceMissingState', 'ScheduleUnverifiedState',
  'ScheduleFailedState', 'ScheduleEmptyState', 'StateScaffold']) {
  assert.doesNotMatch(source, new RegExp('\\b(?:private )?fun '+name+'\\s*\\('));
}
assert.doesNotMatch(source, /sealed interface ScheduleUiState|fun deriveScheduleUiState\(|CloudUiState|client:\s*CloudClient/);
assert.doesNotMatch(source, /华水青果 · 可信只读|localImport: Boolean|if \(localImport\)/);
assert.match(feature, /localImportedScheduleView\(lessons\)\.sourceLabel/);
assert.match(workspace, /lessons = pageSnapshot\(lessons\.takeIf/);
assert.match(shell, /rememberAppWorkspace\(dao, cloud, localLessons, localScheduleRevision/);
assert.match(feature, /ScheduleGrid\(courses =/);
assert.match(grid, /fun ScheduleGrid\(/);
assert.match(feature, /UpstreamCourseDetailSheet\(course, allCourses/);
assert.match(details, /fun UpstreamCourseDetailSheet\(/);
assert.match(routes, /actions\.openLocalCourse\(lesson\.id, lesson\.weekday\)/);
assert.match(shell, /inboxLessonId \?: reminderLessonId/);
assert.match(feature, /lessons\.isEmpty\(\)/);
assert.match(feature, /onSettingsClick = \{ actions\.navigate\(AppRoute\.SCHEDULE_SETTINGS\) \}/);
assert.match(header, /onSettingsClick\(\)/);
assert.match(settings, /title = "导入课表"[\s\S]*?onClick = actions\.importTimetable/);
assert.doesNotMatch(header, /onImportClick|onExportClick/);
assert.match(routes, /AppRoute\.SCHEDULE_SETTINGS -> ScheduleSettingsFeature/);
console.log('Local timetable uses the shared upstream layout; retired screen files remain absent');
