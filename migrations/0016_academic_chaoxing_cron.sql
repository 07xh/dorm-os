-- Hourly Chaoxing sync. Token is never stored in plaintext in D1.
-- User opt-in remains OFF until the owner explicitly enables it in Data Connections.
CREATE TABLE IF NOT EXISTS academic_cron_identity (
 name TEXT PRIMARY KEY CHECK(name='chaoxing-hourly'),
 token_sha256 TEXT NOT NULL CHECK(length(token_sha256)=64),
 configured_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS academic_chaoxing_sync_cursor (
 user_id TEXT PRIMARY KEY REFERENCES academic_users(id) ON DELETE CASCADE,
 next_course INTEGER NOT NULL DEFAULT 0 CHECK(next_course>=0 AND next_course<=120),
 updated_at TEXT NOT NULL,
 last_success_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_academic_chaoxing_sync_cursor_updated
 ON academic_chaoxing_sync_cursor(updated_at);
