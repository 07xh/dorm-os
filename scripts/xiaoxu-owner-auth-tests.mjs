import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {onRequestPost as login} from '../functions/api/v1/auth/login.js';
import {onRequestPost as logout} from '../functions/api/v1/auth/logout.js';
import {onRequestGet as me} from '../functions/api/v1/auth/me.js';
import {verifyAcademicAccess} from '../lib/academic-access.mjs';
const sqlite=new DatabaseSync(':memory:');
sqlite.exec('PRAGMA foreign_keys=ON');
for(const name of [
 '0001_academic_core.sql','0002_academic_legacy_links.sql','0003_academic_verified_snapshots.sql',
 '0004_academic_source_course.sql','0005_academic_workspace.sql','0006_academic_scheduler.sql',
 '0007_academic_reminder_inbox.sql','0008_academic_second_class_source.sql','0009_ncwu_second_class_official_scores.sql',
 '0010_academic_owner_auth.sql'
])sqlite.exec(readFileSync(new URL('../migrations/'+name,import.meta.url),'utf8'));
const db={
 prepare(sql){let bound=[];return {bind(...v){bound=v;return this;},first:async()=>sqlite.prepare(sql).get(...bound)??null,
 all:async()=>({results:sqlite.prepare(sql).all(...bound)}),run:async()=>sqlite.prepare(sql).run(...bound)};},
 async batch(commands){sqlite.exec('BEGIN');try{for(const c of commands)await c.run();sqlite.exec('COMMIT');}catch(e){sqlite.exec('ROLLBACK');throw e;}}
};
const passphrase='local-owner-credential-long-unique-only-for-tests';
const env={ACADEMIC_DB:db,ACADEMIC_AUTH_MODE:'owner_password',OWNER_LOGIN_SECRET:passphrase};
const url='https://xiaoxu.test/api/v1/auth/login';
const request=(passphrase,other={})=>new Request(url,{method:'POST',headers:{origin:'https://xiaoxu.test','content-type':'application/json',...other},body:JSON.stringify({passphrase})});
try {
 let r=await login({request:request(passphrase),env:{}});
 assert.equal(r.status,503,'missing configuration is never public');
 r=await login({request:request(passphrase,{origin:'https://evil.test'}),env});
 assert.equal(r.status,403,'cross-site login must be denied');
 r=await login({request:request('wrong'),env});assert.equal(r.status,401,'invalid password');
 r=await login({request:request(passphrase),env});assert.equal(r.status,200);
 const setCookie=r.headers.get('set-cookie');
 assert.match(setCookie,/__Host-xiaoxu_session=/);
 assert.match(setCookie,/HttpOnly; Secure; SameSite=Strict/);
 assert.ok(!setCookie.includes(passphrase));
 const cookie=setCookie.split(';')[0];
 const getReq=()=>new Request('https://xiaoxu.test/api/v1/auth/me',{headers:{cookie}});
 assert.equal((await me({request:getReq(),env})).status,200,'logged in');
 assert.equal((await verifyAcademicAccess(getReq(),env)).userId,'local-owner-v1','all existing private APIs use same gate');
 assert.equal((await me({request:new Request('https://xiaoxu.test/api/v1/auth/me'),env})).status,401);
 assert.equal((await me({request:new Request('https://xiaoxu.test/api/v1/auth/me',{headers:{cookie:cookie+'; '+cookie}}),env})).status,401);
 const leave=new Request('https://xiaoxu.test/api/v1/auth/logout',{method:'POST',headers:{cookie,origin:'https://xiaoxu.test'}});
 assert.equal((await logout({request:leave,env})).status,200);
 assert.equal((await me({request:getReq(),env})).status,401,'logout revoked the server session');
 const evil=new Request('https://xiaoxu.test/api/v1/auth/logout',{method:'POST',headers:{cookie,origin:'https://evil.test'}});
 assert.equal((await logout({request:evil,env})).status,403);
 for(let i=0;i<7;i++)await login({request:request('wrong'),env});
 assert.equal((await login({request:request(passphrase),env})).status,429,'IP rate limited even if password correct');
 // Password policy: 8 characters are accepted, 7 are rejected by the server.
 // The checked-in test credential is synthetic; a real owner secret is configured in Cloudflare.
 const eight='A7!g9@p2';
 const shortEnv={...env,OWNER_LOGIN_SECRET:eight};
 const shortPassEnv={...env,OWNER_LOGIN_SECRET:'A7!g9@p'};
 assert.equal((await login({request:request(eight),env:shortPassEnv})).status,503,
  '7-character owner secret must never enable owner authentication');
 const eightLogin=await login({request:request(eight),env:shortEnv});
 assert.equal(eightLogin.status,200,'8-character owner secret must authenticate when correct');
 assert.match(eightLogin.headers.get('set-cookie')||'',/HttpOnly; Secure; SameSite=Strict/);
 assert.equal((await login({request:request('wrong000'),env:shortEnv})).status,401,
  'wrong 8-character credential must be rejected');
 assert.equal(sqlite.prepare('PRAGMA foreign_key_check').all().length,0);
 assert.equal(sqlite.prepare('PRAGMA integrity_check').get().integrity_check,'ok');
 console.log('M12 owner login: migration, strict origin, one-owner auth, same gate, cookie, revocation and rate limit passed');
}finally{sqlite.close();}
