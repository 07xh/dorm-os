-- Evidence that the CURRENT deployed Cron token has actually triggered Pages.
-- No user IDs, school sessions, access tokens or course contents are stored.
-- A new token configured_at invalidates all old heartbeat evidence.
CREATE TABLE IF NOT EXISTS academic_cron_health (
 name TEXT PRIMARY KEY CHECK(name='chaoxing-hourly'),
 configured_at TEXT NOT NULL,
 last_heartbeat_at TEXT NOT NULL
);
