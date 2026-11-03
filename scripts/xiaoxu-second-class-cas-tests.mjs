import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {NCWU_SECOND_CLASS_SSO,describeSecondClassAuthorization}
 from '../lib/academic-ncwu-secondclass-sso.mjs';
import {onRequestPost as loginPost}
 from '../functions/api/v1/connections/[provider]/login.js';

assert.equal(NCWU_SECOND_CLASS_SSO.entryUrl,'https://dekt.ncwu.edu.cn/');
assert.equal(NCWU_SECOND_CLASS_SSO.schoolOwnedService,
 'https://sec.ncwu.edu.cn/rump_frontend/loginFromCas/');
assert.equal(NCWU_SECOND_CLASS_SSO.thirdPartyCallbackVerified,false);
assert.equal(describeSecondClassAuthorization({sessionState:'active',verifiedSession:false})
 .xiaoxuAuthorization,'not_verified');
assert.equal(describeSecondClassAuthorization({sessionState:'active',verifiedSession:true})
 .xiaoxuAuthorization,'verified_session');
assert.equal(describeSecondClassAuthorization({sessionState:'expired',verifiedSession:true})
 .xiaoxuAuthorization,'not_verified');
const denied=await loginPost({
 request:new Request('https://xiaoxu.test/api/v1/connections/second_class/login',{
 method:'POST',headers:{origin:'https://xiaoxu.test','content-type':'application/json'},
 body:JSON.stringify({username:'SYNTHETIC_ACCOUNT',password:'DO_NOT_STORE'})
 }),
 env:{},params:{provider:'second_class'}
});
assert.equal(denied.status,503,'school login still requires owner authentication');
assert.ok(!JSON.stringify(await denied.json()).includes('DO_NOT_STORE'));
const schoolLogin=readFileSync(new URL('../functions/api/v1/connections/[provider]/login.js',import.meta.url),'utf8');
assert.ok(schoolLogin.includes("provider==='second_class')return json({error:'official_sso_only'},410)"));
console.log('Second-class official SSO only: no third-party callback or password proxy passed');
