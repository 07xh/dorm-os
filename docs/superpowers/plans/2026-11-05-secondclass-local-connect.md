# Second-class local connection implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline; final independent code review.

**Goal:** Ship an installable Android build that reads personal second-class scores within the official on-device session.
**Architecture:** Reuse NativeSchoolConnectActivity, WebView's session and native_school_records Room transaction. A fixed asset projects validated JSON without exporting credentials.
**Tech Stack:** Existing Kotlin/Android WebView/Compose/Room, Node contract tests, Gradle 8.13.
**Spec:** ../specs/2026-11-05-secondclass-local-connect-design.md

## Global constraints
No new cloud connector, no credentials in JS result/Intent/Room/logs, no fake data, no native HTTP session handoff claim. Failed or cancelled capture preserves old data. Explicit user request authorizes Android build and release; real device checks require an attached owner device.

## Review focus
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
