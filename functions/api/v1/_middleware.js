/* Existing Academic OS connection center: private server-side provider registration.
 * No additional login endpoint or new account database. The platform adapter is
 * available only to the owner-scoped connection routes after D1 and vault readiness.
 */
import {createChaoxingSchoolAdapter} from '../../../lib/academic-chaoxing-provider.mjs';

const CHA0XING_ROUTES=/^\/api\/v1\/connections(?:\/chaoxing\/(?:login|sync))?\/?$/;
export async function onRequest(context){
 const path=new URL(context.request.url).pathname;
 if(CHA0XING_ROUTES.test(path)&&
    typeof context.env?.SCHOOL_SESSION_KEY==='string'&&
    /^[A-Za-z0-9_-]{43}$/.test(context.env.SCHOOL_SESSION_KEY)&&
    typeof context.env?.ACADEMIC_DB?.prepare==='function'){
  try{
   // Never advertise a login control before both the encrypted session vault
   // and the inbox metadata schema are deployed to the current D1 database.
   await context.env.ACADEMIC_DB.prepare(
    'SELECT provider FROM academic_provider_sessions LIMIT 0').all();
   await context.env.ACADEMIC_DB.prepare(
    'SELECT notice_id FROM academic_chaoxing_notices LIMIT 0').all();
   context.data??={};
   context.data.academicSchoolAdapters??={};
   context.data.academicSchoolAdapters.chaoxing=createChaoxingSchoolAdapter();
  }catch{
   // Fail closed: existing /api/v1/connections continues to show unavailable.
  }
 }
 return context.next();
}
