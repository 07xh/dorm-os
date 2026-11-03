/* NCWU second-class official browser SSO discovery (2026-11-03).
 * Observed without authentication:
 * dekt.ncwu.edu.cn/ -> /rump_frontend/login/?next=...
 * -> authserver.ncwu.edu.cn/authserver/login
 *    ?service=https://sec.ncwu.edu.cn/rump_frontend/loginFromCas/
 * This is a SCHOOL-owned CAS callback, not an Academic OS OAuth grant.
 */
export const NCWU_SECOND_CLASS_SSO=Object.freeze({
 provider:'second_class',
 method:'school_cas_external',
 entryUrl:'https://dekt.ncwu.edu.cn/',
 identityProvider:'authserver.ncwu.edu.cn',
 schoolOwnedService:'https://sec.ncwu.edu.cn/rump_frontend/loginFromCas/',
 thirdPartyCallbackVerified:false
});
export function describeSecondClassAuthorization({sessionState='not_connected',verifiedSession=false}={}){
 // A visit to the school's own SSO is not a delegation to Xiaoxu.
 const sessionVerified=verifiedSession===true&&sessionState==='active';
 return {
  method:NCWU_SECOND_CLASS_SSO.method,
  officialLoginUrl:NCWU_SECOND_CLASS_SSO.entryUrl,
  schoolCallbackOnly:true,
  xiaoxuAuthorization:sessionVerified?'verified_session':'not_verified',
  automaticSync:false,
  loginMode:sessionVerified?'connected':'official_sso'
 };
}
