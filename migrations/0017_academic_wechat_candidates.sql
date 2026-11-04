-- M27: private owner-bound Windows collector. This is NOT an academic provider session.
-- Messages remain candidates until explicit review; no raw chat archive is stored.
CREATE TABLE IF NOT EXISTS academic_wechat_devices (
 id TEXT PRIMARY KEY,
 user_id TEXT NOT NULL REFERENCES academic_users(id) ON DELETE CASCADE,
 display_name TEXT NOT NULL,
 token_sha256 TEXT NOT NULL UNIQUE CHECK(length(token_sha256)=64),
 created_at TEXT NOT NULL,
 last_seen_at TEXT,
 revoked_at TEXT,
 UNIQUE(id,user_id)
);
CREATE INDEX IF NOT EXISTS idx_academic_wechat_devices_user
 ON academic_wechat_devices(user_id,revoked_at);

CREATE TABLE IF NOT EXISTS academic_wechat_batches (
 device_id TEXT NOT NULL REFERENCES academic_wechat_devices(id) ON DELETE CASCADE,
 batch_id TEXT NOT NULL,
 content_sha256 TEXT NOT NULL CHECK(length(content_sha256)=64),
 captured_at TEXT NOT NULL,
 received_at TEXT NOT NULL,
 PRIMARY KEY(device_id,batch_id)
);

CREATE TABLE IF NOT EXISTS academic_wechat_candidates (
 id TEXT PRIMARY KEY,
 user_id TEXT NOT NULL REFERENCES academic_users(id) ON DELETE CASCADE,
 device_id TEXT NOT NULL REFERENCES academic_wechat_devices(id) ON DELETE CASCADE,
 message_key TEXT NOT NULL,
 candidate_key TEXT NOT NULL,
 chat_key TEXT NOT NULL,
 chat_label TEXT,
 kind TEXT NOT NULL CHECK(kind IN ('homework','exam','activity','signup','other')),
 title TEXT NOT NULL,
 excerpt TEXT,
 sent_at TEXT NOT NULL,
 due_precision TEXT NOT NULL CHECK(due_precision IN ('unknown','date','exact')),
 due_local_date TEXT,
 due_at_utc TEXT,
 review_status TEXT NOT NULL DEFAULT 'needs_review'
   CHECK(review_status IN ('needs_review','accepted','dismissed')),
 reviewed_at TEXT,
 assignment_id TEXT,
 created_at TEXT NOT NULL,
 UNIQUE(device_id,candidate_key),
 FOREIGN KEY(assignment_id,user_id) REFERENCES academic_assignments(id,user_id),
 CHECK (
  (due_precision='unknown' AND due_local_date IS NULL AND due_at_utc IS NULL) OR
  (due_precision='date' AND due_local_date IS NOT NULL AND due_at_utc IS NULL) OR
  (due_precision='exact' AND due_local_date IS NOT NULL AND due_at_utc IS NOT NULL)
 )
);
CREATE INDEX IF NOT EXISTS idx_academic_wechat_candidates_queue
 ON academic_wechat_candidates(user_id,review_status,sent_at DESC);
