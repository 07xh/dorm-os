-- Private upstream session vault within the existing one-owner Academic OS data model.
-- Migration must be applied before enabling any real school login adapter.
-- Never store platform passwords; only a server-encrypted, time-bounded session.
CREATE TABLE IF NOT EXISTS academic_provider_sessions (
  user_id TEXT NOT NULL REFERENCES academic_users(id),
  provider TEXT NOT NULL CHECK(provider IN ('ncwu','chaoxing')),
  ciphertext TEXT NOT NULL,
  nonce TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  PRIMARY KEY(user_id,provider)
);
CREATE INDEX IF NOT EXISTS idx_academic_provider_sessions_expires
  ON academic_provider_sessions(expires_at);
