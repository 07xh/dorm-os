/* Register active school adapters. The old second-class password CAS
 * proxy was removed; school-owned SSO is not a third-party grant. */
import {makeNcwuQingguoServerAdapter} from '../../../lib/academic-ncwu-qingguo-server.mjs';
import {createChaoxingSchoolAdapter} from '../../../lib/academic-chaoxing-provider.mjs';
import {diagnoseChaoxingReadiness} from '../../../lib/academic-chaoxing-readiness.mjs';
const CHAOXING_ROUTES=/^\/api\/v1\/connections(?:\/chaoxing\/(?:login|sync))?\/?$/;
export async function onRequest(context){
 const path=new URL(context.request.url).pathname;
 if(path==='/api/v1/connections'||path.startsWith('/api/v1/connections/')){
  context.data??={};
  context.data.academicSchoolAdapters={
   ...(context.data.academicSchoolAdapters||{}),
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
 if(CHAOXING_ROUTES.test(path)||path==='/api/v1/chaoxing-homework'){
  context.data??={};
  const state=await diagnoseChaoxingReadiness(context);
  context.data.academicChaoxingReadiness=state;
  if(state==='ready'){
   context.data.academicSchoolAdapters??={};
   context.data.academicSchoolAdapters.chaoxing=createChaoxingSchoolAdapter();
  }
 }
 return context.next();
}
