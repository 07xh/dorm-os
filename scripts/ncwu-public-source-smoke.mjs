// Read-only verification of fresh NCWU source extraction. No publication or persistence.
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {normalizeUpstreamSnapshot,mergeArchive,isVerifiedArchiveItem} from './ncwu-notices.mjs';

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
const previous=JSON.parse(readFileSync(new URL('../public/data/notices.json',import.meta.url),'utf8'));
const known=new Map(previous.items.filter(isVerifiedArchiveItem).map(item=>[item.url,item]));
assert.ok(known.size>=2,'Require real pre-existing verified articles for archive preservation test');
const proposed=mergeArchive(previous,batches,new Date().toISOString());
assert.ok(proposed.items.length>=known.size,'New sources must not shrink the existing verified archive');
for (const [url, old] of known) {
  const next=proposed.items.find(item=>item.url===url);
  assert.ok(next,'Previously verified article must survive the proposed merge: '+url);
  assert.equal(next.id,old.id,'Historical stable ID must not change');
}
for (const batch of success) {
  assert.equal(proposed.sources.find(item=>item.id===batch.source.id)?.status,'ok');
  for (const row of batch.items) {
    const actual=proposed.items.find(item=>item.url===row.url);
    assert.ok(actual,'Valid newly collected official article missing from candidate archive');
    assert.equal(actual.date,null,
      'No upstream inferred or guessed publication year may be certified');
  }
}
console.log('Read-only source smoke verified '+success.length+'/'+batches.length+
  ' official sources; candidate merged archive '+proposed.items.length+
  ' records and '+known.size+' previous articles preserved; nothing published');
