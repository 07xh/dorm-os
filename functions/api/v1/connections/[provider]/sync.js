import {authorize,json} from '../../assignments.js';
import {availableSchoolAdapter,refreshSchoolAccount,checkSchoolMutation,SchoolLoginError} from '../../../../../lib/academic-school-login.mjs';
export async function onRequestPost(context){
 const auth=await authorize(context);if(auth.response)return auth.response;
 if(!checkSchoolMutation(context.request))return json({error:'forbidden_origin_or_format'},403);
 const provider=context.params?.provider;
 if(provider==='second_class')return json({error:'official_sso_only'},410);
 const adapter=availableSchoolAdapter(context,provider);
 if(!adapter)return json({error:'school_login_unavailable'},503);
 try{
  return json(await refreshSchoolAccount({db:auth.db,userId:auth.userId,provider,adapter,
   secret:context.env?.SCHOOL_SESSION_KEY}));
 }catch(e){
  const code=e instanceof SchoolLoginError?e.code:'sync_failed';
  return json({error:code},code==='session_expired'?409:code==='sync_failed'?502:503);
 }
}
