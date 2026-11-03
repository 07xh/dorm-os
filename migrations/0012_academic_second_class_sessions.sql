-- M18: extend the SAME encrypted school-session vault to the second-class
-- provider. Preserve existing NCWU/Chaoxing ciphertext byte-for-byte.
-- Run exactly once after 0011; production upgrader checks the CHECK constraint.
CREATE TABLE academic_provider_sessions_next (
  user_id TEXT NOT NULL REFERENCES academic_users(id),
  provider TEXT NOT NULL CHECK(provider IN ('ncwu','chaoxing','second_class')),
  ciphertext TEXT NOT NULL,
  nonce TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  PRIMARY KEY(user_id,provider)
);
INSERT INTO academic_provider_sessions_next
 (user_id,provider,ciphertext,nonce,expires_at,updated_at)
 SELECT user_id,provider,ciphertext,nonce,expires_at,updated_at
 FROM academic_provider_sessions;
DROP TABLE academic_provider_sessions;
ALTER TABLE academic_provider_sessions_next RENAME TO academic_provider_sessions;
CREATE INDEX idx_academic_provider_sessions_expires
  ON academic_provider_sessions(expires_at);
