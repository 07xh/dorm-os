import {createOwnerSession,sessionSetCookie,validateLoginOrigin} from '../../../../lib/academic-owner-login.mjs';
const headers={'content-type':'application/json; charset=utf-8','cache-control':'no-store, private',
 'x-content-type-options':'nosniff','referrer-policy':'no-referrer'};
const json=(value,status=200,extra={})=>new Response(JSON.stringify(value),{status,headers:{...headers,...extra}});
export async function onRequestPost({request,env}){
 if(!validateLoginOrigin(request))return json({error:'forbidden_origin'},403);
 if(!(request.headers.get('content-type')||'').toLowerCase().startsWith('application/json'))
  return json({error:'json_required'},415);
 const result=await createOwnerSession(request,env);
 if(!result.ok)return json({error:result.error},result.status,
  result.status===429?{'retry-after':'900'}:{});
 return json({authenticated:true,expiresAt:result.expiresAt,durationDays:result.durationDays},200,
  {'set-cookie':sessionSetCookie(result.token,result.durationDays)});
}
