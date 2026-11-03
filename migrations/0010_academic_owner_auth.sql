-- M12: Self-hosted owner authentication foundation. No automatic account provisioning.
-- Session tokens are never stored in plaintext. Existing Access routes remain unchanged.
CREATE TABLE IF NOT EXISTS academic_auth_identities (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES academic_users(id),
  email_normalized TEXT NOT NULL UNIQUE,
  email_verified_at TEXT NOT NULL,
  created_at TEXT NOT NULL
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
CREATE TABLE IF NOT EXISTS academic_auth_challenges (
  id TEXT PRIMARY KEY,
  identity_id TEXT NOT NULL REFERENCES academic_auth_identities(id),
  challenge_hash TEXT NOT NULL UNIQUE,
  purpose TEXT NOT NULL CHECK(purpose IN ('login','passkey_registration')),
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  consumed_at TEXT,
  attempt_count INTEGER NOT NULL DEFAULT 0 CHECK(attempt_count >= 0)
);
CREATE INDEX IF NOT EXISTS idx_academic_auth_challenges_expiry
  ON academic_auth_challenges(expires_at);
