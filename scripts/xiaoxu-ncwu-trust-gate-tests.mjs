import assert from 'node:assert/strict';
import {ncwuTrustedSyncReady,onRequest} from '../functions/api/v1/_middleware.js';

const configured={
 SCHOOL_SESSION_KEY:'A'.repeat(43),
 ACADEMIC_DB:{prepare:()=>({})}
};
const page='https://localhost/api/v1/connections/ncwu/preflight';
async function adaptersFor(env,seed){
 const context={env,request:new Request(page),
  data:{academicSchoolAdapters:{...seed}},
  next:async()=>new Response('ok')};
 await onRequest(context);
 return context.data.academicSchoolAdapters;
}
for(const flag of [undefined,false,'false','TRUE','1','']){
 const env={...configured};
 if(flag!==undefined)env.NCWU_QINGGUO_TRUSTED_SYNC_ENABLED=flag;
 assert.equal(ncwuTrustedSyncReady(env),false);
 const adapters=await adaptersFor(env,{
  ncwu:{provider:'ncwu',unsafeExisting:true},other:{provider:'other'}});
 assert.equal('ncwu' in adapters,false,'disabled adapter must be removed');
 assert.equal(adapters.other.provider,'other');
}
const enabled={...configured,NCWU_QINGGUO_TRUSTED_SYNC_ENABLED:'true'};
assert.equal(ncwuTrustedSyncReady(enabled),true);
const active=await adaptersFor(enabled);
assert.equal(active.ncwu.provider,'ncwu');
assert.equal(typeof active.ncwu.login,'function');
assert.equal(typeof active.ncwu.forSession,'function');
for(const env of [
 {...enabled,SCHOOL_SESSION_KEY:undefined},
 {...enabled,SCHOOL_SESSION_KEY:'short'},
 {...enabled,ACADEMIC_DB:{}},
 {NCWU_QINGGUO_TRUSTED_SYNC_ENABLED:'true'}
]){
 assert.equal(ncwuTrustedSyncReady(env),false);
 assert.equal('ncwu' in await adaptersFor(env),false);
}
// The middleware must never mutate unrelated endpoints.
const ctx={env:enabled,request:new Request('https://localhost/api/v1/health'),
 data:{academicSchoolAdapters:{}},next:async()=>null};
await onRequest(ctx);
assert.equal('ncwu' in ctx.data.academicSchoolAdapters,false);
console.log('NCWU explicit opt-in gate: absent/false/invalid blocked, true with vault registered');
