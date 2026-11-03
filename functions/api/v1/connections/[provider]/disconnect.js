import {authorize,json} from '../../assignments.js';
import {checkSchoolMutation,revokeSchoolAccount,SchoolLoginError} from '../../../../../lib/academic-school-login.mjs';
export async function onRequestPost(context){
 const auth=await authorize(context);if(auth.response)return auth.response;
 if(!checkSchoolMutation(context.request))return json({error:'forbidden_origin_or_format'},403);
 try{return json(await revokeSchoolAccount(auth.db,auth.userId,context.params?.provider));}
 catch(e){return json({error:e instanceof SchoolLoginError?e.code:'database_unavailable'},503);}
}
