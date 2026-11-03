-- M12: deliberately single-owner local authentication; forward-only migration.
-- No unverified email identity, public signup or externally supplied user ID.
CREATE TABLE IF NOT EXISTS academic_auth_identities (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES academic_users(id),
  login_identifier TEXT NOT NULL UNIQUE,
  enrolled_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS academic_auth_sessions (
  id TEXT PRIMARY KEY,
  identity_id TEXT NOT NULL REFERENCES academic_auth_identities(id),
  token_hash TEXT NOT NULL UNIQUE,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  revoked_at TEXT,
  last_seen_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_academic_auth_sessions_identity
  ON academic_auth_sessions(identity_id,expires_at);
CREATE TABLE IF NOT EXISTS academic_auth_login_attempts (
  bucket TEXT PRIMARY KEY,
  hits INTEGER NOT NULL CHECK(hits >= 0),
  window_end INTEGER NOT NULL
);
