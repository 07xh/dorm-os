import {verifyOwnerSession} from '../../../../lib/academic-owner-session.mjs';
const json=(v,s=200)=>new Response(JSON.stringify(v),{status:s,headers:{
 'content-type':'application/json; charset=utf-8','cache-control':'no-store, private',
 'vary':'Cookie','x-content-type-options':'nosniff'}});
export async function onRequestGet({request,env}){
 if(env?.ACADEMIC_AUTH_MODE!=='owner_password')return json({authenticated:false,error:'auth_not_configured'},503);
 const result=await verifyOwnerSession(request,env);
 return result.ok?json({authenticated:true,provider:'local_owner'}):
  json({authenticated:false,error:result.error},result.status);
}
