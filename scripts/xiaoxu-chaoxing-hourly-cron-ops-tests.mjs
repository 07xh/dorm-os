import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {classifySafeWranglerFailure} from './deploy-chaoxing-hourly-cron.mjs';
assert.equal(classifySafeWranglerFailure('', 'Error [code: 10000]: Authentication error'),
 'cloudflare_workers_permission_or_auth');
assert.equal(classifySafeWranglerFailure('', 'Rate limit exceeded HTTP 429'),
 'cloudflare_rate_limited');
assert.equal(classifySafeWranglerFailure('', 'Could not resolve src/index.mjs'),
 'worker_build_or_config');
assert.equal(classifySafeWranglerFailure('Account 12345678 deployed successfully', ''),
 'uncategorized');
const root=new URL('../',import.meta.url);
const file=path=>readFileSync(new URL(path,root),'utf8');
const config=JSON.parse(file('workers/academic-cron/wrangler.jsonc'));
assert.equal(config.name,'dorm-os-chaoxing-cron');
assert.equal(config.workers_dev,false);
assert.deepEqual(config.triggers.crons,['17 * * * *']);
const worker=file('workers/academic-cron/src/index.mjs');
assert.match(worker,/async scheduled\(/);
assert.match(worker,/x-xu-cron-key/);
assert.match(worker,/async fetch\(\)\{return new Response\('Not found'/);
assert.ok(!worker.includes('SCHOOL_SESSION_KEY'));
const workflow=file('.github/workflows/deploy-cloudflare.yml');
assert.ok(workflow.indexOf('Verify deployed HTML, PWA, and exact revision over HTTPS')<
 workflow.indexOf('Deploy authenticated hourly Chaoxing Cron Worker'),
 'The site must deploy before its hourly trigger');
assert.match(workflow,/node scripts\/deploy-chaoxing-hourly-cron\.mjs/);
assert.match(workflow,/id: chaoxing_cron/);
assert.match(workflow,/steps\.chaoxing_cron\.outcome/);
assert.match(workflow,/hourly school sync is NOT confirmed/);
const script=file('scripts/deploy-chaoxing-hourly-cron.mjs');
assert.match(script,/findPagesBoundD1/);
assert.match(script,/0016_academic_chaoxing_cron\.sql/);
assert.match(script,/0017_academic_chaoxing_cron_health\.sql/);
assert.match(script,/randomBytes\(32\)/);
assert.match(script,/secret','put','CRON_SYNC_TOKEN'/);
assert.ok(!script.includes("console.log(secret)"));
const heartbeatMigration=file('migrations/0017_academic_chaoxing_cron_health.sql');
assert.match(heartbeatMigration,/CREATE TABLE IF NOT EXISTS academic_cron_health/);
assert.match(heartbeatMigration,/configured_at/);
assert.doesNotMatch(heartbeatMigration.replace(/^--.*$/gm,''),/password|plaintext|session|cookie|user_id/i);
const heartbeatHelper=file('lib/academic-chaoxing-cron-health.mjs');
assert.match(heartbeatHelper,/readCronWorkerHealth/);
assert.match(heartbeatHelper,/recordCronWorkerHeartbeat/);
assert.match(heartbeatHelper,/observed_configured_at/);
const internal=file('functions/api/v1/internal/chaoxing-hourly.js');
assert.match(internal,/recordCronWorkerHeartbeat/);
assert.ok(internal.indexOf('verifyCronToken')<internal.indexOf('recordCronWorkerHeartbeat'));
const enabled=file('functions/api/v1/connections/chaoxing/auto-sync.js');
assert.match(enabled,/readCronWorkerHealth/);
assert.match(enabled,/if\(!worker\.ready\)return json\(\{error:'autosync_not_ready'\},503\)/);
const migration=file('migrations/0016_academic_chaoxing_cron.sql');
assert.match(migration,/token_sha256/);
assert.ok(!/token_plaintext|token_value/i.test(migration));
const route=file('functions/api/v1/internal/chaoxing-hourly.js');
assert.match(route,/verifyCronToken/);
assert.ok(!route.includes('authorize(context)'),'Cron is an internal signed job, not browser owner login');
const overview=file('functions/api/v1/connections.js');
assert.match(overview,/autoSyncAvailable/);
const ui=file('public/xiaoxu-academic-client.js');
assert.match(ui,/data-xu-chaoxing-auto-switch/);
assert.match(ui,/\/api\/v1\/connections\/chaoxing\/auto-sync/);
assert.match(ui,/autoSyncWorkerReady/);
assert.match(ui,/云端 Worker 尚无近期运行证明/);
console.log('Hourly Chaoxing Worker: scheduled-only private endpoint, account-level D1 binding, cryptographic token and opt-in UI contract passed');
