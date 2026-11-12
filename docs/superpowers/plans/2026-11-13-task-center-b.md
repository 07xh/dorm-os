# Task Center B Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement task-by-task.

**Goal:** Ship the approved execution-first Android task center with scoped child steps, explicit parent completion and removal of superseded UI.
**Architecture:** Extend the existing Room database, source-aware task hub and shared task detail. Keep native/cloud official states independent. Reuse #429 in its single existing branch.
**Tech Stack:** Kotlin, Compose, Room, JUnit, Android instrumentation, existing GitHub Actions signing pipeline.
**Spec:** docs/superpowers/specs/2026-11-13-task-center-design.md

## Global Constraints
- One level of child steps; no second database, shell, editor system, school connector or hidden sync queue.
- Roots counted once; official completion read only; unknown official identity never inferred from titles.
- Preserve existing Room v1-v4 migrations, task/dependency records and backup v1-v3 compatibility.
- Explicit online reopen precedes editing a completed remote parent; local steps otherwise offline.
- User authorized implementation, publication and evidence-based duplicate removal on 2026-11-13; continue without redundant approval.

## Review Focus
- Account switch must never attach another account's steps to a reused root ID.
- Concurrent parent completion and child reopen must recheck committed state.
- Missing root records after refresh must preserve personal history.
- Restoring the same backup must not silently remap steps to unrelated roots.
- An unknown/removed official work must not become actionable through personal steps.

### Task 1: Scoped persistent execution and dependency transactions
Files: data/TaskExecution.kt (new), data/LocalTaskDatabase.kt, data/CloudClient.kt, functions/api/v1/auth/me.js; tests TaskExecutionTest.kt and TaskExecutionDatabaseTest.kt.
Interfaces: ExecutionRoot(scope: String,key: String); TaskStep; TaskStepEdge; TaskExecutionDao.observeSteps(scope), addSteps, toggleStep, editStep, removeStep, setPrerequisites, finish; pure readyTaskSteps.
- [ ] Add JVM tests for multi-prerequisite readiness, invalid/cross-root edges, completed history and one-root progress; add actual Room tests for guarded completion/reopen.
- [ ] Run tests RED, implement same-database v4-v5 additive migration and root/step transactions, run GREEN.
- [ ] Expose verified opaque cloud owner identity via existing auth/me; native scopes use existing account_binding hash; absent identity disables attaching steps.
- [ ] Commit.

### Task 2: Execution-first list and unified detail
Files: ui/TaskExecutionPanel.kt, ui/TaskExecutionRows.kt (new), ui/RealDataWorkspace.kt, ui/TaskBoard.kt.
Interfaces: TaskExecutionPanel(root,rootDone,dao,onFinish,onReopen); execution list derives root identity from WorkspaceTask and current verified owner binding.
- [ ] Add readiness/count/search/date tests; run RED.
- [ ] Integrate child rows into now list, preserving official readiness and inherited root dependency gate; root context and progress, explicit finish, grouped source views and filtered empty recovery.
- [ ] Integrate same panel in existing details and local edit; add/edit/reorder/delete steps and select multiple prerequisites; ordinary task taps open details.
- [ ] Verify Kotlin/JVM and guarded remote reopen behavior; commit.

### Task 3: Backup and safe duplicate retirement
Files: data/LocalTaskBackup.kt, ui/LocalTaskBreakdownSheet.kt, data/LocalTaskBreakdown.kt, data/LocalTaskDatabase.kt, affected tests.
- [ ] Add actual Room export/import tests: step/edge/ended roundtrip, identical names, rollback, old formats, source scope retention.
- [ ] Extend backup v4 in one transaction, remap local root IDs and step IDs without guessing external owner; reject incompatible merges atomically.
- [ ] Replace old DAG-as-decomposition entry with the new child-step panel; delete the superseded sheet/edge builder only after checking all call sites. Keep existing persisted DAG and migrations.
- [ ] Run tests GREEN and document deletion evidence; commit.

### Task 4: Review, exact-SHA validation, build and publication
Files: docs/superpowers, existing pending release request, app/build.gradle.
- [ ] Run npm check, Android compile/JVM, actual Room migration/execution instrumentation and regression UI tests.
- [ ] Fresh whole-branch review; fix important findings with failing regression tests.
- [ ] Reconcile latest main without overwriting parallel work; push to #429 with lease against observed head.
- [ ] Use existing formal APK request and signing pipeline; verify exact source SHA, successful main checks and published APK signature/hash. Report any real device gap without claiming it passed.
- [ ] Delete redundant implementation only with replacement/reference proof; never erase user tasks or applied migrations.
