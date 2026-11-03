-- 校序个人提醒收件箱。仅在已验证 Cloudflare Access 身份下读写。
-- 这是站内提醒与跨设备已读，不是 Web Push；不存储平台凭证。
CREATE TABLE IF NOT EXISTS academic_reminder_preferences (
  user_id TEXT PRIMARY KEY REFERENCES academic_users(id) ON DELETE CASCADE,
  enabled INTEGER NOT NULL DEFAULT 0 CHECK(enabled IN (0,1)),
  exam_lead_days INTEGER NOT NULL DEFAULT 7 CHECK(exam_lead_days BETWEEN 0 AND 30),
  task_lead_days INTEGER NOT NULL DEFAULT 3 CHECK(task_lead_days BETWEEN 0 AND 30),
  updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS academic_reminder_receipts (
  user_id TEXT NOT NULL REFERENCES academic_users(id) ON DELETE CASCADE,
  event_key TEXT NOT NULL,
  read_at TEXT NOT NULL,
  PRIMARY KEY(user_id,event_key),
  CHECK(length(event_key) BETWEEN 1 AND 220)
);
CREATE INDEX IF NOT EXISTS idx_academic_reminder_receipts_user
  ON academic_reminder_receipts(user_id,read_at DESC);
