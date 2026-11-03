import {revokeOwnerSession,sessionClearCookie,validateLoginOrigin} from '../../../../lib/academic-owner-login.mjs';
const json=(v,s=200)=>new Response(JSON.stringify(v),{status:s,headers:{
 'content-type':'application/json; charset=utf-8','cache-control':'no-store, private',
 'set-cookie':sessionClearCookie(),'x-content-type-options':'nosniff'}});
export async function onRequestPost({request,env}){
 if(!validateLoginOrigin(request))return json({error:'forbidden_origin'},403);
 const result=await revokeOwnerSession(request,env);
 return result.ok?json({authenticated:false}):json({error:result.error},result.status);
}
