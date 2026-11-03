/**
 * Server-only, privacy-preserving NCWU schedule evidence comparison.
 *
 * An existing owner-bound upstream session must be obtained separately.
 * The independent reference must come from a separately checked official
 * timetable display; do not construct it from the parsed candidate itself.
 * This function does not log, store, authorize, or promote HTML to verified
 * academic records, even when the two schedule lists match exactly.
 */
import {readNcwuTimetableCandidate} from './academic-ncwu-page-reader.mjs';
import {compareNcwuTimetableCandidate} from './academic-ncwu-timetable-compare.mjs';

export async function auditNcwuTimetableAgainstReference({
 cookie,semester,referenceRecords,fetchImpl
}={}){
 const candidate=await readNcwuTimetableCandidate({
  cookie,semester,...(fetchImpl?{fetchImpl}:{})
 });
 // Compare independently transcribed official timetable blocks to the
 // syntactic candidate. The report contains counts/codes, not classes,
 // instructors, locations, source URLs, or session values.
 const comparison=compareNcwuTimetableCandidate(candidate,referenceRecords);
 if(comparison.verified!==false||comparison.trustedSyncAllowed!==false)
  throw new Error('unexpected_audit_trust_state');
 return comparison;
}
