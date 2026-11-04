import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {classifyChaoxingInboxSchema} from './upgrade-academic-owner-auth-production.mjs';

const table=[{name:'academic_chaoxing_notices'}];
const expected=['user_id','notice_id','category','title','course_title',
 'source_course_id','task_external_id','sent_at_utc','due_at_utc',
 'platform_read','observed_at'];
const cols=expected.map(name=>({name}));
assert.equal(classifyChaoxingInboxSchema([],[]),'apply');
assert.equal(classifyChaoxingInboxSchema(table,cols),'verify');
assert.equal(classifyChaoxingInboxSchema(table,cols.slice(0,-1)),'partial_inbox_schema');
assert.throws(()=>classifyChaoxingInboxSchema('bad',cols),/invalid_chaoxing_inbox_schema/);
assert.throws(()=>classifyChaoxingInboxSchema(table,'bad'),/invalid_chaoxing_inbox_schema/);
const workflow=readFileSync(new URL('../.github/workflows/upgrade-owner-auth-d1.yml',import.meta.url),'utf8');
assert.ok(workflow.includes('workflow_dispatch:'),
 'Historical Chaoxing inbox migration is available for manual recovery');
assert.ok(!/^on:\s*\n\s*push:/m.test(workflow),
 'Previously applied migration must not auto-replay on source edits');
assert.ok(workflow.includes('scripts/xiaoxu-chaoxing-db-upgrade-tests.mjs'));
assert.ok(workflow.includes("github.ref == 'refs/heads/main'"));
assert.ok(workflow.includes('CLOUDFLARE_API_TOKEN: ${{ secrets.CLOUDFLARE_API_TOKEN }}'));
const sql=readFileSync(new URL('../migrations/0015_academic_chaoxing_notices.sql',import.meta.url),'utf8');
assert.ok(sql.includes('CREATE TABLE IF NOT EXISTS academic_chaoxing_notices'));
assert.ok(sql.includes('PRIMARY KEY (user_id,notice_id)'));
const schemaOnly=sql.split('\n').filter(line=>!line.trim().startsWith('--')).join('\n');
assert.ok(!/\b(cookie|password|token|session|secret)\b/i.test(schemaOnly),'inbox must never store credentials');
const upgrade=readFileSync(new URL('./upgrade-academic-owner-auth-production.mjs',import.meta.url),'utf8');
assert.ok(upgrade.includes('classifyChaoxingInboxSchema'));
assert.ok(upgrade.includes('--file=./migrations/0015_academic_chaoxing_notices.sql'));
assert.ok(upgrade.includes('existing_database_requires_manual_review'));
console.log('Chaoxing D1 migration 0015: schema idempotence, existing forward-only workflow, no plaintext credentials passed');
