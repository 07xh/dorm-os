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
assert.ok(client.includes('我另有超星独立账号密码（非校园密码）'),
 'The independent Chaoxing password form must remain distinguished from campus CAS');
assert.ok(client.includes('authserver.ncwu.edu.cn/authserver/login'),
 'The normal campus CAS link must remain available');
assert.ok(!client.includes("(!authorized&&provider.id==='chaoxing'?'open':'')"),
 'A campus password must not be solicited through an automatically opened Chaoxing form');
assert.ok(client.includes('重新检测学习通连接'));
assert.ok(client.includes('data-action="academic-refresh"'));
assert.ok(client.includes('第三方 APK 的本机会话不自动共享给校序'));
console.log('Chaoxing owner-only readiness: every failure reason, safe registration, UI retry and unchanged unrelated routes passed');
