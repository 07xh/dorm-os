-- Learning platform notification inbox is distinct from the existing internal reminders.
-- Only minimal verified metadata; never persist raw captured HAR, HTML, cookies, links or credentials.
CREATE TABLE IF NOT EXISTS academic_chaoxing_notices (
  user_id TEXT NOT NULL REFERENCES academic_users(id) ON DELETE CASCADE,
  notice_id TEXT NOT NULL,
  category TEXT NOT NULL CHECK(category IN
    ('assignment','assignment_reminder','peer_review','exam','checkin','quiz','activity','notice')),
  title TEXT NOT NULL,
  course_title TEXT NOT NULL DEFAULT '',
  source_course_id TEXT NOT NULL DEFAULT '',
  task_external_id TEXT,
  sent_at_utc TEXT NOT NULL,
  due_at_utc TEXT,
  platform_read INTEGER NOT NULL DEFAULT 0 CHECK(platform_read IN (0,1)),
  observed_at TEXT NOT NULL,
  PRIMARY KEY (user_id,notice_id),
  CHECK(length(notice_id) BETWEEN 1 AND 100),
  CHECK(length(title) BETWEEN 1 AND 140)
);
CREATE INDEX IF NOT EXISTS idx_academic_chaoxing_notices_user_sent
 ON academic_chaoxing_notices(user_id,sent_at_utc DESC,notice_id);
CREATE INDEX IF NOT EXISTS idx_academic_chaoxing_notices_user_task
 ON academic_chaoxing_notices(user_id,task_external_id);
