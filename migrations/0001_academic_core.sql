-- 校序 Academic OS M1: durable, per-user academic data.
-- This migration intentionally does not alter legacy localStorage records.
CREATE TABLE IF NOT EXISTS academic_users (
  id TEXT PRIMARY KEY,
  created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS academic_connections (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES academic_users(id),
  provider TEXT NOT NULL CHECK(provider IN ('chaoxing','rainclassroom','ncwu','manual')),
  profile_key TEXT NOT NULL DEFAULT 'default',
  mode TEXT NOT NULL CHECK(mode IN ('manual','import','readonly')),
  status TEXT NOT NULL DEFAULT 'disconnected'
    CHECK(status IN ('disconnected','connected','partial','expired','error')),
  last_attempt_at TEXT,
  last_success_at TEXT,
  last_error_code TEXT,
  UNIQUE(user_id,provider,profile_key),
  UNIQUE(id,user_id)
);
CREATE TABLE IF NOT EXISTS academic_courses (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES academic_users(id),
  semester TEXT,
  title TEXT NOT NULL,
  created_at TEXT NOT NULL,
  UNIQUE(id,user_id)
);
CREATE TABLE IF NOT EXISTS academic_assignments (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES academic_users(id),
  course_id TEXT,
  title TEXT NOT NULL,
  kind TEXT NOT NULL DEFAULT 'manual' CHECK(kind IN ('manual','platform')),
  due_at_utc TEXT,
  due_local_date TEXT,
  due_timezone TEXT NOT NULL DEFAULT 'Asia/Shanghai',
  due_precision TEXT NOT NULL DEFAULT 'unknown'
    CHECK(due_precision IN ('unknown','date','exact')),
  personal_target_at TEXT,
  local_status TEXT NOT NULL DEFAULT 'todo'
    CHECK(local_status IN ('todo','in_progress','done')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  FOREIGN KEY(course_id,user_id) REFERENCES academic_courses(id,user_id),
  UNIQUE(id,user_id),
  CHECK (
    (due_precision='unknown' AND due_at_utc IS NULL AND due_local_date IS NULL)
    OR (due_precision='date' AND due_at_utc IS NULL AND due_local_date IS NOT NULL)
    OR (due_precision='exact' AND due_at_utc IS NOT NULL AND due_local_date IS NOT NULL)
  )
);
CREATE INDEX IF NOT EXISTS idx_academic_assignments_owner_due
  ON academic_assignments(user_id,due_at_utc,due_local_date,id);
CREATE INDEX IF NOT EXISTS idx_academic_assignments_owner_updated
  ON academic_assignments(user_id,updated_at,id);
CREATE TABLE IF NOT EXISTS academic_assignment_sources (
  assignment_id TEXT NOT NULL,
  connection_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  external_id TEXT NOT NULL,
  platform_status TEXT NOT NULL DEFAULT 'unknown'
    CHECK(platform_status IN ('todo','submitted','graded','returned','unknown')),
  last_seen_at TEXT,
  PRIMARY KEY(connection_id,external_id),
  UNIQUE(assignment_id,connection_id),
  FOREIGN KEY(assignment_id,user_id) REFERENCES academic_assignments(id,user_id),
  FOREIGN KEY(connection_id,user_id) REFERENCES academic_connections(id,user_id)
);
CREATE TABLE IF NOT EXISTS academic_sync_runs (
  id TEXT PRIMARY KEY,
  connection_id TEXT NOT NULL REFERENCES academic_connections(id),
  started_at TEXT NOT NULL,
  completed_at TEXT,
  status TEXT NOT NULL CHECK(status IN ('running','success','partial','failed')),
  coverage TEXT NOT NULL DEFAULT 'unknown'
    CHECK(coverage IN ('unknown','partial','complete')),
  received_count INTEGER NOT NULL DEFAULT 0,
  inserted_count INTEGER NOT NULL DEFAULT 0,
  updated_count INTEGER NOT NULL DEFAULT 0,
  error_code TEXT
);
CREATE INDEX IF NOT EXISTS idx_academic_sync_runs_source_time
  ON academic_sync_runs(connection_id,started_at DESC);
CREATE TABLE IF NOT EXISTS academic_assignment_events (
  id TEXT PRIMARY KEY,
  assignment_id TEXT NOT NULL REFERENCES academic_assignments(id),
  event_type TEXT NOT NULL,
  previous_value TEXT,
  current_value TEXT,
  observed_at TEXT NOT NULL,
  UNIQUE(assignment_id,event_type,observed_at)
);
