import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {validTermShape} from '../lib/qingguo-term-contract.mjs';

const fixture = JSON.parse(readFileSync(new URL('../docs/protocol/term-shape-v1.json', import.meta.url), 'utf8'));
assert.equal(fixture.version, 1);
for (const item of fixture.cases) {
  assert.equal(validTermShape(item.payload), item.expected, item.name);
}
console.log('Qingguo term contract: ' + fixture.cases.length + ' synthetic cases passed');
