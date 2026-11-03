import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {classifyAuthUpgrade} from './upgrade-academic-owner-auth-production.mjs';
const core=['academic_users','academic_verified_batches','academic_verified_records',
 'academic_second_class_sync_audit','academic_second_class_official_scores'].map(name=>({name}));
const cols=[{name:'provenance'}];
assert.equal(classifyAuthUpgrade(core,cols),'apply');
assert.equal(classifyAuthUpgrade(core.concat([{name:'academic_auth_sessions'}]),cols),'partial_auth_schema');
assert.equal(classifyAuthUpgrade([{name:'academic_users'}],cols),'core_not_ready');
assert.equal(classifyAuthUpgrade(core.concat(['academic_auth_sessions','academic_auth_identities','academic_auth_login_attempts'].map(name=>({name}))),cols),'verify');
const workflow=readFileSync(new URL('../.github/workflows/upgrade-owner-auth-d1.yml',import.meta.url),'utf8');
assert.match(workflow,/workflow_dispatch:/);
assert.match(workflow,/\$\{\{ secrets.CLOUDFLARE_API_TOKEN \}\}/);
assert.ok(!workflow.includes('\\$'+'{{'),'Secrets interpolation must not be escaped');
assert.match(workflow,/github.repository == '07xh\/dorm-os'/);
console.log('M12 production upgrade decisions and GitHub workflow checked');
