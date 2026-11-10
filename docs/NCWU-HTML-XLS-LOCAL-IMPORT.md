# NCWU local HTML-as-XLS timetable import

## Scope

The Android file picker in MainActivity now supports user-selected, local HTML
timetable exports named .xls / .html / .htm, in addition to existing ICS and JSON.

Some NCWU Qingguo exports contain HTML tables even when the extension is .xls.
The new isolated parser reads only a uniquely identifiable detailed student table
with the exact columns: 课程 / 任课教师 / 上课时间地点 (or equivalent labels).
It parses multiple meeting segments, weekday, explicit teaching weeks (including
odd/even weeks), period range and actual room string.

It converts *only* observed values into the existing LocalScheduleStore/VerifiedLesson
schema. The existing import preview, lower-count confirmation, backup and local
persistence paths remain unchanged. It makes no HTTP requests, never obtains
credentials, and does not turn local files into school-verified records.

## Explicit non-goals

- Binary OLE .xls and ZIP-based .xlsx are intentionally rejected with actionable
  errors. They are not HTML-as-XLS; do not claim binary Excel support.
- No new Room database, timetable Repository, Compose screen, school login or
  cloud auto-sync is introduced.
- The parser does not invent unknown weeks, days, teacher, location or academic
  term; any unrecognized meeting segment rejects the import.
- It does not import the separate school-wide "按教室" Excel template from
  NCWU Course Auditor. That template is for course discovery, not proof of a
  student's personal enrollment.

## Validation

Unit tests: NcwuHtmlXlsTimetableParserTest, including HTML exports, odd/even
weeks, duplicates, malformed rows, binary Excel rejection, GB18030 encoding.

Before release: run :app:testDebugUnitTest and :app:assembleDebug on the same
commit; then import an actual *personal* HTML-as-XLS export on the user's Android
device and compare course/time/room/week with the source file.

## Open-source provenance

Independent Kotlin implementation using the already-installed Jsoup library.
The parsing scenario was identified by examining NCWU Space's publicly
viewable source. That repository lacks an identified root LICENSE in the
review; its source code is **not** copied. No new third-party licenses are
required by this change.
