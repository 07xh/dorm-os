// Read-only verification of fresh NCWU source extraction. No publication or persistence.
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {normalizeUpstreamSnapshot} from './ncwu-notices.mjs';

const input=process.argv[2];
if (!input) throw new Error('Usage: node scripts/ncwu-public-source-smoke.mjs snapshot.json');
const json=JSON.parse(readFileSync(input,'utf8'));
const batches=normalizeUpstreamSnapshot(json);
const success=batches.filter(x=>!x.error&&x.items?.length>=2);
for (const batch of batches) {
  const label=batch.source.id;
  if (batch.error) console.warn(label+': unavailable ('+batch.error+')');
  else console.log(label+': '+batch.items.length+' official article URLs validated');
}
assert.ok(success.length>0,
  'None of three real official sources returned two verified article URLs; do not claim live acceptance');
for (const batch of success) {
  for (const item of batch.items) {
    assert.ok(item.url.startsWith('https://www2.ncwu.edu.cn/'));
    assert.equal(item.id.length,'ncwu-'.length+16);
    assert.notEqual(item.date?.length,5,'Month/day-only dates must remain unknown');
  }
}
console.log('Read-only source smoke verified '+success.length+'/'+batches.length+' official sources; nothing published');
