-- 扩展已有 0001_academic_core.sql；按已验证身份隔离全部个人数据。
-- 与 M1-A 的 academic_connections / academic_assignments 复用同一个 D1 和 Access。
CREATE TABLE IF NOT EXISTS academic_verified_batches (
 user_id TEXT NOT NULL REFERENCES academic_users(id),
 kind TEXT NOT NULL CHECK(kind IN ('schedule','assignments','grades','exams','second-class')),
 source TEXT NOT NULL CHECK(source IN ('ncwu','chaoxing','second_class')),
 last_success_at TEXT NOT NULL,
 batch_id TEXT NOT NULL,
 updated_at TEXT NOT NULL,
 PRIMARY KEY(user_id,kind)
);
CREATE TABLE IF NOT EXISTS academic_verified_records (
 user_id TEXT NOT NULL REFERENCES academic_users(id),
 kind TEXT NOT NULL CHECK(kind IN ('schedule','assignments','grades','exams','second-class')),
 source TEXT NOT NULL,
 external_id TEXT NOT NULL,
 term TEXT NOT NULL DEFAULT '',
 payload TEXT NOT NULL,
 batch_id TEXT NOT NULL,
 updated_at TEXT NOT NULL,
 PRIMARY KEY(user_id,kind,source,external_id,term)
);
CREATE INDEX IF NOT EXISTS idx_academic_verified_records ON academic_verified_records(user_id,kind,batch_id);
CREATE TABLE IF NOT EXISTS academic_verified_ingests (
 id TEXT PRIMARY KEY,
 user_id TEXT NOT NULL REFERENCES academic_users(id),
 kind TEXT NOT NULL,
 source TEXT NOT NULL,
 captured_at TEXT NOT NULL,
 accepted_at TEXT NOT NULL,
 count INTEGER NOT NULL
);
