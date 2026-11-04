# Xiaoxu UI Workspace Implementation Plan
> **For agentic workers:** Use superpowers:executing-plans to implement task-by-task.
**Goal:** Apply the approved interactive preview to the real app and remove replaced UI code.
**Architecture:** Keep vanilla JS, APIs, storage and event contracts. Add a pure read-only presentation model shared by overview and tasks. Replace versioned shell/style overrides with one workspace stylesheet.
**Tech Stack:** HTML, CSS, browser JS, Node tests, Playwright.
**Spec:** docs/superpowers/specs/2026-11-04-ui-workspace-design.md
## Global Constraints
No demo data, credentials in browser storage, new authentication path, destructive migration or framework change. Retain NFC, backups, QR resume, cloud revision protection and existing URLs.
## Review Focus
- Deduplicate local/cloud/snapshot records without hiding unrelated sources.
- Date-only deadlines retain unknown time; no invented school timetable times.
- Logout removes private feeds; loading/errors do not claim connected.
- Mobile menus/dialogs avoid bottom navigation; keyboard focus survives updates.
- Task review never changes platform submission state.
### Task 1: Shared display model
Files: public/xiaoxu-workspace.js, scripts/xiaoxu-workspace-tests.mjs, cloud/academic export accessors.
- [ ] Write failing behavior tests for dedup, sort, filters, missing deadlines, escaping and immutable inputs.
- [ ] Implement tasks({local,cloud,remote}, {filter,source}) and course list projection; export read-only record copies from clients.
- [ ] Run new tests and existing task/import/dedup tests.
### Task 2: Real pages
Files: public/index.html, public/xiaoxu-academic-client.js.
- [ ] Add browser regression for shared home/task values, review tab and preserved real action selectors.
- [ ] Replace overview/task layout and compact connection summaries. Keep module hosts and real forms/actions.
- [ ] Remove duplicate statistics, unused compact task renderer, redundant academic explanatory headers.
- [ ] Verify existing local CRUD, QR/CAS, source logout and connection tests.
### Task 3: Styles and verification
Files: public/xiaoxu-workspace.css, public/sw.js, obsolete CSS/JS, affected UI tests.
- [ ] Replace V8/V8.3/V9 styling and JS nav decorator with unified stylesheet/static nav icons; remove superseded base home styles.
- [ ] Update intentionally obsolete visual assertions; retain all functional tests.
- [ ] Run npm run check, full browser regressions where supported, and desktop/mobile visual inspection.
- [ ] Commit and push feature branch; open reviewable PR with validation and deletion evidence. Do not claim production deployment.
