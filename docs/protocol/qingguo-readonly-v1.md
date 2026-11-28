# NCWU Qingguo read-only contract v1

Shared between 07xh/kingo-assistant and 07xh/dorm-os. First increment: academic-term response shape only. Source: existing KingoAssistant ncwu_term_probe.py validation and the user-provided 2026-11-25 comparison; no raw HAR or third-party source imported.

- Required keys: xn, xqM, xqName, jwUserYeartermId, userCode.
- Each is a non-blank string of at most 100 Unicode code points.
- Extra properties are ignored. Output is a boolean, not the response or identity fields.
- This check is syntactic only. Identity, authenticated-session ownership, term correctness and official result verification are separate.
- No school network requests, seat inference, enrollment writer, authentication changes or UI changes in this increment.
- Unknown capacity remains unknown, and is not equivalent to eligibility.
- Maintain byte-identical term-shape-v1.json fixtures across the two repositories. Run the local tests before any change to this contract.
- The JS implementation is an offline compatibility helper; the existing school adapter remains unchanged until authorized response samples and integration tests establish compatibility.

Next increment: align existing personal timetable data contracts and compare parser behavior with synthetic fixtures. Do not copy GPL source without satisfying its license requirements.
