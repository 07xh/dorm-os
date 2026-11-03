/* Register an actual server-owned NCWU second-class CAS adapter.
 * The browser cannot register or inject adapters; no extra login system.
 */
import {makeNcwuCasServerAdapter} from '../../../lib/academic-ncwu-cas-server.mjs';
export async function onRequest(context){
 const path=new URL(context.request.url).pathname;
 if(path==='/api/v1/connections'||path.startsWith('/api/v1/connections/')){
  context.data??={};
  context.data.academicSchoolAdapters={
   ...(context.data.academicSchoolAdapters||{}),
   second_class:makeNcwuCasServerAdapter()
  };
 }
 return context.next();
}
