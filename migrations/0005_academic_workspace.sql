-- M3: reliable optimistic concurrency for tasks edited from multiple devices.
ALTER TABLE academic_assignments ADD COLUMN revision INTEGER NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_academic_events_assignment_time
  ON academic_assignment_events(assignment_id, observed_at DESC);
