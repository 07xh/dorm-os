-- M5 no-account scheduler metadata; default disabled, no Cron is enabled.
CREATE TABLE IF NOT EXISTS academic_sync_jobs (
 user_id TEXT NOT NULL REFERENCES academic_users(id),
 provider TEXT NOT NULL CHECK(provider IN ('chaoxing','ncwu')),
 enabled INTEGER NOT NULL DEFAULT 0 CHECK(enabled IN (0,1)),
 interval_minutes INTEGER NOT NULL DEFAULT 120 CHECK(interval_minutes BETWEEN 30 AND 1440),
 next_at TEXT, lease_until TEXT, last_run_at TEXT,
 last_result TEXT CHECK(last_result IN ('success','partial','failed','expired') OR last_result IS NULL),
 failure_count INTEGER NOT NULL DEFAULT 0 CHECK(failure_count BETWEEN 0 AND 20),
 PRIMARY KEY(user_id,provider)
);
CREATE INDEX IF NOT EXISTS idx_academic_sync_jobs_due ON academic_sync_jobs(enabled,next_at);
