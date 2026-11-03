-- M6: independently attested source provenance. Existing user-imported
-- snapshots remain user_import after migration. Never trust client flags.
ALTER TABLE academic_verified_batches
 ADD COLUMN provenance TEXT NOT NULL DEFAULT 'user_import'
 CHECK(provenance IN ('user_import','trusted_readonly'));
CREATE TABLE IF NOT EXISTS academic_second_class_sync_audit (
 id TEXT PRIMARY KEY,
 user_id TEXT NOT NULL REFERENCES academic_users(id),
 source TEXT NOT NULL CHECK(source='second_class'),
 status TEXT NOT NULL CHECK(status IN ('success','failed','expired')),
 observed_at TEXT NOT NULL,
 item_count INTEGER NOT NULL DEFAULT 0 CHECK(item_count BETWEEN 0 AND 250),
 error_code TEXT CHECK(error_code IN ('authorization_failed','invalid_response','upstream_unavailable') OR error_code IS NULL)
);
CREATE INDEX IF NOT EXISTS idx_second_class_sync_audit_owner
 ON academic_second_class_sync_audit(user_id,observed_at DESC);
