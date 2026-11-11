import assert from 'node:assert/strict';
import {nextAttempt} from '../lib/academic-scheduler.mjs';
// Shared by the active runChaoxingAutoCycle. Its D1 leases, opt-in and
// expiry behavior are covered in xiaoxu-chaoxing-hourly-cron-tests.mjs.
const at='2026-11-03T10:00:00.000Z';
assert.equal(nextAttempt(at,120),'2026-11-03T12:00:00.000Z');
assert.equal(nextAttempt(at,120,1,'failed'),'2026-11-03T10:30:00.000Z');
assert.equal(nextAttempt(at,120,9,'failed'),'2026-11-04T10:00:00.000Z');
assert.equal(nextAttempt(at,30,0,'partial'),'2026-11-03T10:15:00.000Z');
assert.equal(nextAttempt(at,1440,20,'success'),'2026-11-04T10:00:00.000Z');
for(const args of [[at,1],[at,1441],[at,60,-1],[at,60,21],['bad',60],[at,60,0.5]])
 assert.throws(()=>nextAttempt(...args),/invalid_scheduler_values/);
console.log('Active scheduler shared backoff: interval, partial/failure retry, cap and invalid values passed');
