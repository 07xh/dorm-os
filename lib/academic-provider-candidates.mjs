/* Timetable parser candidates remain unverified until owner/source review.
 * Grades/exams use academic-contract; second-class uses its verified reader.
 */
import {normalizeKingoCourseRows} from './academic-upstream-bridge.mjs';
export function candidateSchedule(rows,semester){
 const candidates=normalizeKingoCourseRows(rows,{semester});
 return {kind:'schedule',source:'ncwu',verified:false,complete:false,records:candidates};
}
