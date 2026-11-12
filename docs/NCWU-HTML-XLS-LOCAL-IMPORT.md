# NCWU local HTML-as-XLS timetable import

## Scope

The Android file picker in MainActivity supports user-selected, local personal
NCWU timetables exported as real OOXML .xlsx, HTML disguised as .xls (also
.html / .htm), and the existing ICS / JSON formats.

Some NCWU Qingguo exports contain HTML tables even when the extension is .xls.
The new isolated parser reads only a uniquely identifiable detailed student table
with the exact columns: 课程 / 任课教师 / 上课时间地点 (or equivalent labels).
It parses multiple meeting segments, weekday, explicit teaching weeks (including
odd/even weeks), period range and actual room string.

Unknown classroom text is retained as an empty location (never fabricated); an
explicitly scheduled class must not be dropped merely because its room has not
been assigned. Weekdays, periods and explicit teaching weeks remain mandatory.

It converts *only* observed values into the existing LocalScheduleStore/VerifiedLesson
schema. The existing import preview, lower-count confirmation, backup and local
persistence paths remain unchanged. It makes no HTTP requests, never obtains
credentials, and does not turn local files into school-verified records.

## Explicit non-goals

- The old OLE2 binary .xls format is intentionally rejected. Real OOXML .xlsx
  personal tables are accepted using a bounded ZIP/XML adapter.
- A university-wide classroom timetable (Course Auditor's 按教室 export) is
  explicitly rejected as a personal import; it is not proof of enrollment.
- Advanced/merged-grid Excel sheets and formulas without text results are not
  accepted as verified personal lesson data.
- No new Room database, timetable Repository, Compose screen, school login or
  cloud auto-sync is introduced.
- The parser does not invent unknown weeks, days, teacher, location or academic
  term; any unrecognized meeting segment rejects the import.
- It does not import the separate school-wide "按教室" Excel template from
  NCWU Course Auditor. That template is for course discovery, not proof of a
  student's personal enrollment.

## Qingguo list-export interoperability (MIT upstream review)

Reviewed [LingNc/stnet_syllabus](https://github.com/LingNc/stnet_syllabus)
(MIT, copyright 2026 LingNc), particularly \`internal/parser/list_parser.go\` and
\`internal/simplify/simplify.go\`. It confirms the real Qingguo HTML-as-XLS
pattern \`1-11周(单) 五[3-4] 三教楼106(172)\`, as well as parenthesized
\`（双）\` markers. This upstream targets *another* school's Qingguo instance:
it is a format reference, **not** proof of NCWU export acceptance.

Our independent Kotlin adapter now handles parenthesized odd/even markers
after \`周\` alongside its existing \`1-11单周五[3-4]\` notation and
explicit numeral weekdays 1–7. It rejects any row containing two parity
markers (even if they agree); this avoids silently assigning an ambiguous
schedule. Class location text (including trailing occupancy notation) is
preserved verbatim. Both HTML-as-XLS and OOXML XLSX use the same normalization,
same preview and same local persistence. No third-party Go code, AI API,
student ID, campus login or calendar date is copied or invoked.

Upstream MIT license:
https://github.com/LingNc/stnet_syllabus/blob/main/LICENSE
The new tests use a short public **format example**, not a user's timetable.

ACRA (Apache-2.0, https://github.com/ACRA/acra) was evaluated for Android
crash reports. Because default crash collectors can collect device details
and other potentially identifying context, no ACRA sender or silent network
reporting is introduced. The existing offline QA-only adb diagnostics
remain the method for user-controlled crash evidence collection.

## Validation

Unit tests: NcwuHtmlXlsTimetableParserTest and NcwuXlsxTimetableParserTest;
cover HTML exports, OOXML XLSX, shared/inline strings, odd/even weeks,
duplicates, unknown rooms, school-wide spreadsheet refusal, malformed rows,
XML entity rejection, ZIP expansion bounds, legacy OLE Excel refusal, GB18030.

Before release: run :app:testDebugUnitTest and :app:assembleDebug on the same
commit; then import an actual *personal* HTML-as-XLS export on the user's Android
device and compare course/time/room/week with the source file.

## Open-source provenance

Independent Kotlin implementation using the already-installed Jsoup library.
The parsing scenario was identified by examining NCWU Space's publicly
viewable source. That repository lacks an identified root LICENSE in the
review; its source code is **not** copied. No new third-party licenses are
required by this change.
