/* Register an actual server-owned NCWU second-class CAS adapter.
 * The browser cannot register or inject adapters; no extra login system.
 */
import {makeNcwuCasServerAdapter} from '../../../lib/academic-ncwu-cas-server.mjs';
import {makeNcwuQingguoServerAdapter} from '../../../lib/academic-ncwu-qingguo-server.mjs';
import {createChaoxingSchoolAdapter} from '../../../lib/academic-chaoxing-provider.mjs';
const CHA0XING_ROUTES=/^\/api\/v1\/connections(?:\/chaoxing\/(?:login|sync))?\/?$/;
export async function onRequest(context){
 const path=new URL(context.request.url).pathname;
 if(path==='/api/v1/connections'||path.startsWith('/api/v1/connections/')){
  context.data??={};
  context.data.academicSchoolAdapters={
   ...(context.data.academicSchoolAdapters||{}),
   second_class:makeNcwuCasServerAdapter(),
   // New NCWU sessions only become trusted AFTER an owner-reviewed preview.
   // Explicit false is a production rollback switch, not an onboarding step.
   ...(context.env?.NCWU_QINGGUO_TRUSTED_SYNC_ENABLED!=='false'&&
     typeof context.env?.SCHOOL_SESSION_KEY==='string'&&
     /^[A-Za-z0-9_-]{43}$/.test(context.env.SCHOOL_SESSION_KEY)&&
     typeof context.env?.ACADEMIC_DB?.prepare==='function'?{
    ncwu:makeNcwuQingguoServerAdapter({trustedSyncEnabled:true})
   }:{})
  };
 }
 if((CHA0XING_ROUTES.test(path)||path==='/api/v1/chaoxing-homework')&&
    typeof context.env?.SCHOOL_SESSION_KEY==='string'&&
    /^[A-Za-z0-9_-]{43}$/.test(context.env.SCHOOL_SESSION_KEY)&&
    typeof context.env?.ACADEMIC_DB?.prepare==='function'){
   try{
    await context.env.ACADEMIC_DB.prepare('SELECT provider FROM academic_provider_sessions LIMIT 0').all();
    await context.env.ACADEMIC_DB.prepare('SELECT notice_id FROM academic_chaoxing_notices LIMIT 0').all();
    context.data??={};
    context.data.academicSchoolAdapters??={};
    context.data.academicSchoolAdapters.chaoxing=createChaoxingSchoolAdapter();
   }catch{ /* Fail closed until D1 migration. */ }
  }
 return context.next();
}
