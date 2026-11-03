/* Safe production-secret setup tests: never invoke Wrangler or inspect actual values. */
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {needsSchoolSessionKey,generateSchoolSessionKey}
 from './ensure-school-session-key.mjs';

const project=(production,preview={})=>({
 name:'dorm-os',production_branch:'main',
 deployment_configs:{production:{env_vars:production},preview:{env_vars:preview}}
});
assert.equal(needsSchoolSessionKey(project({})),true);
assert.equal(needsSchoolSessionKey(project({ACADEMIC_AUTH_MODE:{value:'owner_password'}},
 {SCHOOL_SESSION_KEY:{type:'secret_text'}})),true,
 'preview keys must not count as a production secret');
assert.equal(needsSchoolSessionKey(project({
 SCHOOL_SESSION_KEY:{type:'secret_text'},
 OWNER_LOGIN_SECRET:{type:'secret_text'}
})),false,'existing secret must NEVER be rotated');
assert.equal(needsSchoolSessionKey(project({
 SCHOOL_SESSION_KEY:{type:'secret_text',value:'DO_NOT_LOG_VALUE'}
})),false,'hidden and populated secrets have identical idempotence behavior');
assert.throws(()=>needsSchoolSessionKey(project(null)),/production_environment_unavailable/);
assert.throws(()=>needsSchoolSessionKey({...project({}),name:'other-project'}),/unexpected_pages_project/);
for(let i=0;i<4;i++){
 const key=generateSchoolSessionKey();
 assert.match(key,/^[A-Za-z0-9_-]{43}$/);
 assert.equal(Buffer.from(key,'base64url').length,32);
}
const workflow=readFileSync(new URL('../.github/workflows/deploy-cloudflare.yml',import.meta.url),'utf8');
const setup=workflow.indexOf('node scripts/ensure-school-session-key.mjs');
const deploy=workflow.indexOf('npx --yes wrangler@4 pages deploy public');
assert.ok(setup>0&&deploy>setup,'provision before publishing the Pages Function');
assert.ok(workflow.includes('CLOUDFLARE_API_TOKEN: ${{ secrets.CLOUDFLARE_API_TOKEN }}'));
const implementation=readFileSync(new URL('./ensure-school-session-key.mjs',import.meta.url),'utf8');
assert.ok(implementation.includes("'--env=production'"));
assert.ok(implementation.includes('input:value+'), 'secret must be passed only through stdin');
assert.ok(!implementation.includes('console.log(value)'));
console.log('Second-class production vault setup: idempotence, no-rotation, cryptographic key format and deploy order passed');
