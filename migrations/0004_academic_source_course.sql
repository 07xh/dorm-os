-- M2: preserve the original course label supplied by verified read-only connectors.
-- It is not a verified official course ID and must not auto-link homonymous courses.
ALTER TABLE academic_assignment_sources
  ADD COLUMN course_title TEXT;
