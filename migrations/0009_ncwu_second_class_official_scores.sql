-- M9: official, independently read NCWU 素质分 and 志愿服务时长.
-- Stored separately from activity-level candidates and personal imports.
-- Only a trusted, whole-batch server-side read can update this table.
CREATE TABLE IF NOT EXISTS academic_second_class_official_scores (
 user_id TEXT PRIMARY KEY REFERENCES academic_users(id),
 batch_id TEXT NOT NULL,
 captured_at TEXT NOT NULL,
 payload TEXT NOT NULL CHECK(length(payload) <= 25000),
 updated_at TEXT NOT NULL
);
