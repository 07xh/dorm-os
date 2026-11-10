-- NextTodo-style personal cloud task prerequisites.
-- Additive D1 migration: keeps the existing owner-scoped assignment ledger.
-- An edge means task_id waits until prerequisite_id is personally completed.
CREATE TABLE IF NOT EXISTS academic_task_dependencies (
 user_id TEXT NOT NULL,
 task_id TEXT NOT NULL,
 prerequisite_id TEXT NOT NULL,
 created_at TEXT NOT NULL,
 PRIMARY KEY(user_id,task_id,prerequisite_id),
 CHECK(task_id<>prerequisite_id),
 FOREIGN KEY(task_id,user_id) REFERENCES academic_assignments(id,user_id) ON DELETE CASCADE,
 FOREIGN KEY(prerequisite_id,user_id) REFERENCES academic_assignments(id,user_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_academic_task_deps_prerequisite
 ON academic_task_dependencies(user_id,prerequisite_id,task_id);

-- A single revision serializes graph edits across all tasks of one owner.
CREATE TABLE IF NOT EXISTS academic_task_graph_versions (
 user_id TEXT PRIMARY KEY REFERENCES academic_users(id) ON DELETE CASCADE,
 revision INTEGER NOT NULL DEFAULT 0 CHECK(revision>=0),
 updated_at TEXT NOT NULL
);

-- Temporary per-transaction compare-and-swap guard. Its trigger uses
-- RAISE(ABORT) on stale revisions, rolling back the D1 batch atomically.
CREATE TABLE IF NOT EXISTS academic_task_graph_guards (
 id TEXT PRIMARY KEY,
 user_id TEXT NOT NULL,
 task_id TEXT NOT NULL,
 expected_graph_revision INTEGER NOT NULL CHECK(expected_graph_revision>=0),
 expected_task_revision INTEGER NOT NULL CHECK(expected_task_revision>=0),
 FOREIGN KEY(task_id,user_id) REFERENCES academic_assignments(id,user_id) ON DELETE CASCADE
);
CREATE TRIGGER IF NOT EXISTS trg_academic_task_graph_guard
 BEFORE INSERT ON academic_task_graph_guards
 BEGIN
  SELECT CASE WHEN NOT EXISTS (
    SELECT 1 FROM academic_task_graph_versions g
    WHERE g.user_id=NEW.user_id AND g.revision=NEW.expected_graph_revision
  ) THEN RAISE(ABORT,'graph_revision_conflict') END;
  SELECT CASE WHEN NOT EXISTS (
    SELECT 1 FROM academic_assignments a
    WHERE a.id=NEW.task_id AND a.user_id=NEW.user_id
      AND a.revision=NEW.expected_task_revision AND a.kind='manual'
  ) THEN RAISE(ABORT,'task_revision_conflict') END;
 END;

-- Database-level completion gate. The application's pre-check is useful for
-- a friendly error but can race with an independent predecessor write.
-- This trigger observes prerequisite statuses at the actual UPDATE moment.
CREATE TRIGGER IF NOT EXISTS trg_academic_task_done_prerequisites
 BEFORE UPDATE OF local_status ON academic_assignments
 WHEN NEW.kind='manual' AND NEW.local_status='done' AND OLD.local_status<>'done'
 BEGIN
  SELECT CASE WHEN EXISTS (
   SELECT 1 FROM academic_task_dependencies d
   JOIN academic_assignments p
    ON p.id=d.prerequisite_id AND p.user_id=d.user_id
   WHERE d.task_id=NEW.id AND d.user_id=NEW.user_id
     AND p.local_status<>'done'
  ) THEN RAISE(ABORT,'prerequisites_incomplete') END;
 END;
