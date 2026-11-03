-- M20: ONE owner-scoped browser bridge key for second-class personal snapshots.
-- Never contains school passwords, cookies, raw school response or CAS tickets.
CREATE TABLE IF NOT EXISTS academic_secondclass_browser_bridges (
 user_id TEXT PRIMARY KEY REFERENCES academic_users(id),
 token_hash TEXT NOT NULL UNIQUE CHECK(length(token_hash)=64),
 created_at TEXT NOT NULL,
 expires_at TEXT NOT NULL,
 last_sync_at TEXT,
 last_status TEXT NOT NULL DEFAULT 'pending'
   CHECK(last_status IN ('pending','synced','error')),
 last_error_code TEXT
);
CREATE INDEX IF NOT EXISTS idx_secondclass_browser_bridges_expiry
 ON academic_secondclass_browser_bridges(expires_at);
