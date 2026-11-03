import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {diagnoseChaoxingReadiness,CHAOXING_READINESS_CODES} from '../lib/academic-chaoxing-readiness.mjs';
import {onRequest} from '../functions/api/v1/_middleware.js';
const key='A'.repeat(43);
const tableDb=(mode='ready')=>({
 prepare(sql){
  return {async all(){
   if(mode==='session_missing'&&sql.includes('academic_provider_sessions'))throw Error('private schema error');
   if(mode==='inbox_missing'&&sql.includes('academic_chaoxing_notices'))throw Error('private schema error');
   return {results:[]};
  }};
 }
});
const ctx=(env,path='/api/v1/connections')=>{
 const context={request:new Request('https://site.example'+path),env,data:{},
  next:async()=>new Response('ok')};
 return context;
};
const cases=[
 [{},'session_key_missing'],
 [{SCHOOL_SESSION_KEY:'short',ACADEMIC_DB:tableDb()},'session_key_invalid'],
 [{SCHOOL_SESSION_KEY:key},'database_unbound'],
 [{SCHOOL_SESSION_KEY:key,ACADEMIC_DB:tableDb('session_missing')},'school_session_table_missing'],
 [{SCHOOL_SESSION_KEY:key,ACADEMIC_DB:tableDb('inbox_missing')},'chaoxing_inbox_table_missing'],
 [{SCHOOL_SESSION_KEY:key,ACADEMIC_DB:tableDb()},'ready']
];
for(const [env,expected] of cases){
 const context=ctx(env);
 assert.equal(await diagnoseChaoxingReadiness(context),expected);
 await onRequest(context);
 assert.equal(context.data.academicChaoxingReadiness,expected);
 assert.equal(context.data.academicSchoolAdapters?.chaoxing?.provider,
   expected==='ready'?'chaoxing':undefined);
 assert.ok(CHAOXING_READINESS_CODES.includes(expected));
 assert.ok(!JSON.stringify(context.data.academicChaoxingReadiness).includes(key));
}
const direct=ctx(cases.at(-1)[0],'/api/v1/chaoxing-homework');
await onRequest(direct);
assert.equal(direct.data.academicSchoolAdapters?.chaoxing?.provider,'chaoxing');
const unrelated=ctx(cases.at(-1)[0],'/api/v1/health');
await onRequest(unrelated);
assert.equal(unrelated.data.academicSchoolAdapters?.chaoxing,undefined);
const endpoint=readFileSync(new URL('../functions/api/v1/connections.js',import.meta.url),'utf8');
const client=readFileSync(new URL('../public/xiaoxu-academic-client.js',import.meta.url),'utf8');
assert.ok(endpoint.includes('loginReadiness')&&endpoint.includes('academicChaoxingReadiness'));
for(const code of CHAOXING_READINESS_CODES.filter(x=>x!=='ready')){
 assert.ok(client.includes(code),'Missing Chinese UI description for '+code);
}
assert.ok(client.includes('重新检测学习通连接'));
assert.ok(client.includes('data-action="academic-refresh"'));
assert.ok(client.includes('第三方 APK 已登录只代表该应用的本机会话有效'));
console.log('Chaoxing owner-only readiness: every failure reason, safe registration, UI retry and unchanged unrelated routes passed');
