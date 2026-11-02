import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {webcrypto} from 'node:crypto';
import {
  LegacyMigrationError,normalizeLegacyBatch,migrateLegacyBatch
} from '../lib/academic-legacy-migration.mjs';
import {onRequestPost} from '../functions/api/v1/assignments/legacy-import.js';

const sqlite=new DatabaseSync(':memory:');
sqlite.exec('PRAGMA foreign_keys=ON');
for(const file of ['0001_academic_core.sql','0002_academic_legacy_links.sql']) {
  sqlite.exec(readFileSync(new URL('../migrations/'+file,import.meta.url),'utf8'));
}
const db={
  prepare(sql){
    let bound=[];
    return {
      bind(...values){bound=values;return this;},
      all(){return {results:sqlite.prepare(sql).all(...bound)};},
      first(){return sqlite.prepare(sql).get(...bound)??null;},
      run(){return sqlite.prepare(sql).run(...bound);}
    };
  },
  async batch(statements){
    sqlite.exec('BEGIN');
    try {
      const result=statements.map(s=>s.run());
      sqlite.exec('COMMIT');
      return result;
    } catch(e) {
      sqlite.exec('ROLLBACK');
      throw e;
    }
  }
};
const task=(id,overrides={})=>({
  id,title:'工程制图习题',category:'工程制图',due:'2026-11-20',
  dueAt:'2026-11-20T23:59:00+08:00',done:false,
  createdAt:'2026-11-02T12:00:00Z',source:'manual',
  sourceId:'',sourceStatus:'unknown',course:'',
  ...overrides
});
const batch=(tasks,mode='preview')=>({format:'xiaoxu-legacy-tasks-v1',mode,tasks});
assert.throws(()=>normalizeLegacyBatch(batch([task('dup'),task('dup')])),LegacyMigrationError);
assert.throws(()=>normalizeLegacyBatch(batch([task('bad',{due:'2026-02-30',dueAt:''})])),LegacyMigrationError);
assert.throws(()=>normalizeLegacyBatch(batch([task('bad',{dueAt:'2026-11-20T23:59:00'})])),LegacyMigrationError);
assert.throws(()=>normalizeLegacyBatch(batch([task('bad',{done:'true'})])),LegacyMigrationError);
assert.throws(()=>normalizeLegacyBatch(batch([task('bad',{source:'chaoxing',sourceId:''})])),LegacyMigrationError);
assert.throws(()=>normalizeLegacyBatch(batch(Array.from({length:21},(_,i)=>task(String(i))))),LegacyMigrationError);
const first=batch([task('local-1'),task('local-2',{
  title:'学习通作业',source:'chaoxing',sourceId:'work:456:class789',
  sourceStatus:'submitted',done:true,course:'高等数学',
  due:'2026-11-21',dueAt:''
})]);
let counter=0;
const options={id:()=> 'migration-'+(++counter),
  now:()=> '2026-11-03T10:00:00.000Z',subtle:webcrypto.subtle};
const preview=await migrateLegacyBatch(db,'user-A',first,options);
assert.equal(preview.toCreate,2);
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n,0,
  'preview must not write');
const committed=await migrateLegacyBatch(db,'user-A',{...first,mode:'commit'},options);
assert.equal(committed.created,2);
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n,2);
assert.equal(sqlite.prepare(
  "SELECT local_status FROM academic_assignments WHERE id='migration-2'"
).get().local_status,'done');
const source=sqlite.prepare("SELECT * FROM academic_legacy_links WHERE legacy_id='local-2'").get();
assert.equal(source.legacy_source,'chaoxing');
assert.equal(source.legacy_platform_status,'submitted');
assert.equal(source.legacy_course_text,'高等数学');
assert.equal(sqlite.prepare(
  "SELECT due_precision FROM academic_assignments WHERE id='migration-2'"
).get().due_precision,'date');
const repeated=await migrateLegacyBatch(db,'user-A',{...first,mode:'commit'},options);
assert.equal(repeated.toCreate,0,'replay must be idempotent');
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n,2);
const conflict=await migrateLegacyBatch(db,'user-A',batch([
  task('local-1',{title:'edited after import'}),task('new-one')
],'commit'),options);
assert.deepEqual(conflict.conflicts,['local-1']);
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n,2,
  'conflict must block entire chunk, not partially commit');
const userB=await migrateLegacyBatch(db,'user-B',batch([task('local-1')],'commit'),options);
assert.equal(userB.created,1,'same legacy key belongs to different user');
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n,3);
const before=sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n;
await assert.rejects(()=>migrateLegacyBatch(db,'user-A',batch([
  task('rollback-1'),task('rollback-2')
],'commit'),{
  id:(()=>{let n=0;return ()=>n++===0?'migration-1':'rollback-new-id';})(),
  now:options.now,subtle:webcrypto.subtle
}), /UNIQUE constraint/);
assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM academic_assignments').get().n,before,
  'failed transaction must roll back all newly added records');

const domain='academic-test.cloudflareaccess.com',aud='academic-test-aud';
const pair=await webcrypto.subtle.generateKey({name:'RSASSA-PKCS1-v1_5',
  modulusLength:2048,publicExponent:new Uint8Array([1,0,1]),hash:'SHA-256'},true,['sign','verify']);
const key=await webcrypto.subtle.exportKey('jwk',pair.publicKey);
Object.assign(key,{kid:'migration-key',alg:'RS256',use:'sig'});
const base64=x=>Buffer.from(JSON.stringify(x)).toString('base64url');
const timestamp=Math.floor(Date.now()/1000);
const payload={iss:'https://'+domain,aud:[aud],sub:'test-user',iat:timestamp-5,exp:timestamp+3600};
const message=base64({alg:'RS256',kid:'migration-key'})+'.'+base64(payload);
const sig=await webcrypto.subtle.sign('RSASSA-PKCS1-v1_5',pair.privateKey,new TextEncoder().encode(message));
const jwt=message+'.'+Buffer.from(sig).toString('base64url');
const env={ACCESS_TEAM_DOMAIN:domain,ACCESS_AUD:aud,ACADEMIC_DB:db};
const createRequest=(input,origin='https://academic.example')=>
  new Request('https://academic.example/api/v1/assignments/legacy-import',{
    method:'POST',headers:{
      'Content-Type':'application/json','Origin':origin,'Cf-Access-Jwt-Assertion':jwt
    },body:JSON.stringify(input)
  });
const originalFetch=globalThis.fetch;
globalThis.fetch=async()=>new Response(JSON.stringify({keys:[key]}));
try{
  const response=await onRequestPost({request:createRequest(batch([task('route-a')])),env});
  assert.equal(response.status,200);
  assert.equal((await response.json()).toCreate,1);
  const committedResponse=await onRequestPost({
    request:createRequest(batch([task('route-a')],'commit')),env
  });
  assert.equal(committedResponse.status,201);
  const denied=await onRequestPost({
    request:createRequest(batch([task('route-b')],'commit'),'https://other.example'),env
  });
  assert.equal(denied.status,403);
  const noDb=await onRequestPost({request:createRequest(batch([task('route-b')])) ,env:{
    ACCESS_TEAM_DOMAIN:domain,ACCESS_AUD:aud
  }});
  assert.equal(noDb.status,503);
} finally {
  globalThis.fetch=originalFetch;
}
console.log('校序 M1-B: 历史任务预览、幂等提交、身份隔离、冲突/事务回滚与跨站拒绝均通过');
