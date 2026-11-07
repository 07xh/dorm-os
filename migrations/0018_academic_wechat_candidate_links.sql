-- M28: verified HTTPS links captured alongside reviewed WeChat candidates.
-- Links remain bound to their owning candidate and owner. Acceptance links
-- them to the official task through academic_wechat_candidates.assignment_id,
-- so no separate task-copy table and no review-status duplication is needed.
CREATE UNIQUE INDEX IF NOT EXISTS idx_academic_wechat_candidates_id_user
 ON academic_wechat_candidates(id,user_id);

CREATE TABLE IF NOT EXISTS academic_wechat_candidate_links (
 candidate_id TEXT NOT NULL,
 user_id TEXT NOT NULL,
 position INTEGER NOT NULL CHECK(position BETWEEN 0 AND 2),
 url TEXT NOT NULL CHECK(length(url) BETWEEN 12 AND 512 AND url LIKE 'https://%'),
 created_at TEXT NOT NULL,
 PRIMARY KEY(candidate_id,position),
 FOREIGN KEY(candidate_id,user_id)
   REFERENCES academic_wechat_candidates(id,user_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_academic_wechat_candidate_links_user
 ON academic_wechat_candidate_links(user_id);
