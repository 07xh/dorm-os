-- M1-B: reversible trace of every legacy browser task imported into D1.
-- User-scoped task identity is (user_id, legacy_id). No imported record claims
-- to be live or platform-verified. This table never mutates localStorage.
CREATE TABLE IF NOT EXISTS academic_legacy_links (
  user_id TEXT NOT NULL REFERENCES academic_users(id),
  legacy_id TEXT NOT NULL,
  assignment_id TEXT NOT NULL,
  legacy_source TEXT NOT NULL
    CHECK(legacy_source IN ('manual','chaoxing','ncwu','second_class')),
  legacy_external_id TEXT,
  legacy_platform_status TEXT NOT NULL DEFAULT 'unknown'
    CHECK(legacy_platform_status IN ('todo','submitted','graded','returned','unknown')),
  legacy_course_text TEXT,
  legacy_category TEXT,
  legacy_created_at TEXT,
  original_fingerprint TEXT NOT NULL,
  migrated_at TEXT NOT NULL,
  PRIMARY KEY(user_id,legacy_id),
  UNIQUE(assignment_id),
  FOREIGN KEY(assignment_id,user_id)
    REFERENCES academic_assignments(id,user_id)
);
CREATE INDEX IF NOT EXISTS idx_academic_legacy_sources
  ON academic_legacy_links(user_id,legacy_source,legacy_external_id);
