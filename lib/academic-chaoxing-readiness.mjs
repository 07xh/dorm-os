/* Safe runtime diagnosis of the EXISTING Chaoxing connection.
 * The diagnostics never include secrets, cookies or SQL errors. It does not
 * attempt school login or mutate session state.
 */
export const CHAOXING_READINESS_CODES=Object.freeze([
 'ready','session_key_missing','session_key_invalid','database_unbound',
 'school_session_table_missing','chaoxing_inbox_table_missing','adapter_unavailable'
]);
const KEY=/^[A-Za-z0-9_-]{43}$/;
export async function diagnoseChaoxingReadiness(context){
 const secret=context?.env?.SCHOOL_SESSION_KEY;
 if(typeof secret!=='string'||secret.length===0)return 'session_key_missing';
 if(!KEY.test(secret))return 'session_key_invalid';
 const db=context?.env?.ACADEMIC_DB;
 if(typeof db?.prepare!=='function')return 'database_unbound';
 try{
  await db.prepare('SELECT provider FROM academic_provider_sessions LIMIT 0').all();
 }catch{return 'school_session_table_missing';}
 try{
  await db.prepare('SELECT notice_id FROM academic_chaoxing_notices LIMIT 0').all();
 }catch{return 'chaoxing_inbox_table_missing';}
 return 'ready';
}
