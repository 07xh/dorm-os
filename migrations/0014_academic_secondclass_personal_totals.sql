-- M21: user browser-reported totals are never mixed with trusted official totals.
-- No names, student identifiers, tokens or raw school response.
CREATE TABLE IF NOT EXISTS academic_secondclass_personal_totals (
 user_id TEXT PRIMARY KEY REFERENCES academic_users(id),
 captured_at TEXT NOT NULL,
 quality_score REAL CHECK(quality_score IS NULL OR (quality_score>=0 AND quality_score<=100000)),
 volunteer_hours REAL CHECK(volunteer_hours IS NULL OR (volunteer_hours>=0 AND volunteer_hours<=100000)),
 updated_at TEXT NOT NULL,
 source TEXT NOT NULL DEFAULT 'user_browser_export' CHECK(source='user_browser_export'),
 CHECK(quality_score IS NOT NULL OR volunteer_hours IS NOT NULL)
);
