# Second-class local connection implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline; final independent code review.

**Goal:** Ship an installable Android build that reads personal second-class scores within the official on-device session.
**Architecture:** Reuse NativeSchoolConnectActivity, WebView's session and native_school_records Room transaction. A fixed asset projects validated JSON without exporting credentials.
**Tech Stack:** Existing Kotlin/Android WebView/Compose/Room, Node contract tests, Gradle 8.13.
**Spec:** ../specs/2026-11-05-secondclass-local-connect-design.md

## Global constraints
No new cloud connector, no credentials in JS result/Intent/Room/logs, no fake data, no native HTTP session handoff claim. Failed or cancelled capture preserves old data. Explicit user request authorizes Android build and release; real device checks require an attached owner device.

## 2026-11-06 correction: enforce the approved official-device entry

- [ ] Disable second-class cloud password POST before body parsing or upstream access; the old preflight must never authorize credential entry. Preserve encrypted existing sessions and read-only refresh.
- [ ] Remove the misleading scan-detection/password UI. Browser entry directs to the existing App official reader without asserting a cloud QR grant.
- [ ] Make the existing standalone App connection open its official reader directly, then forward only validated successful local-save receipt fields. Keep regular Academic tab browsing unchanged.
- [ ] Replace tests for the retired password route with denial/no-upstream tests; verify real browser UI, Android compile/JVM checks and independent review. Do not mark cloud QR association or real-device acceptance complete.

## Review focus

## 2026-11-06 owner read confirmation and snapshot integrity follow-up

The owner reports the shipped native QA can read. This confirms that read path only, not all official values, restart/expiry behavior or cloud QR handoff. The nonempty catalog still needs a real response; the official HTML field names alone do not lift that gate.

- [x] Execute the production DAO default method in JVM tests with a recording SQL boundary. New JVM run 42239614150 compiled and ran 65 tests; the three invalid-input tests failed before the fix, while the coherent-read case passed.
- [x] Observe the new JVM failure before adding a DAO precondition for one positive capture time and the exact official business host across scores and activities. Existing catalog validation and Room transaction are unchanged; green JVM verification is pending.
- [ ] Compile and run JVM/full CI, review, merge and verify main. Retain the readable QA installation; no UI, connector or schema migration is part of this patch.

## 2026-11-06 follow-up: return to the initiating App page

- [ ] Extend the existing Android bootstrap contract: a validated onSavedReceipt must set the result before finishing the intermediate Activity; a failed read must not finish it as a success. Observe the missing finish failure, then add the single finish after setSavedReceipt.
- [ ] Run bootstrap and existing reader checks, request Android compile/JVM tests, review and merge only the focused main patch. Create a separate QA integration branch from #338's exact native-UI snapshot plus verified main and this fix; never overwrite or merge #338. Validate and deliver that APK as QA, with real school authentication and cloud QR linkage still unverified.
Anonymous aggregate with no identified semester must fail. Mixed accounts fail. Text/plain JSON accepted. Navigation during async read must never save. Confirmed local snapshot must not imply live authorization.

### Task 1: Fixed same-origin score reader
- [ ] Write scripts/xiaoxu-secondclass-local-reader-tests.mjs, observe missing asset failure.
- [ ] Implement assets/ncwu_secondclass_local.js with bounded HTTP/JSON/identity/score validation and sanitized failure diagnostics.
- [ ] Run Node reader tests; add them to existing check workflow.

### Task 2: Android capture and local display
- [ ] Add strict native score projection using data/LocalSecondClassScores.kt.
- [ ] Integrate fixed async reader, epoch/poll timeout, confirmation, Room save and return into NativeSchoolConnectActivity. Preserve Chaoxing behavior and CookieManager.flush.
- [ ] Make second-class connection and snapshot copy describe score refresh and session expiry accurately.
- [ ] Run existing native reader/bootstrap checks and local Gradle build.

### Task 3: Reviewed build and release
- [ ] Increment Android version, add explicit build request marker and acceptance document.
- [ ] Run npm check and Android build. Conduct independent final review and fix important findings.
- [ ] Commit, push PR, inspect exact-head CI, merge when checks permit, verify main build and expose installable APK. Report device verification separately.
