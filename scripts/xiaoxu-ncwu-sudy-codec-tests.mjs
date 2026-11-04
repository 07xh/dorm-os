import assert from 'node:assert/strict';
import {encodeNcwuSudyPassword,verifyNcwuSudySource} from '../lib/academic-ncwu-sudy-codec.mjs';
// Synthetic vector independently generated using the school's public RSAUtils.
assert.equal(encodeNcwuSudyPassword('Synthetic_Pass123!'),'0924d69beb8f692a603c0b0c76095b7576da71e0dd8ede948189a8006c2bc4874231e0730ba57c2ea5bb31ef3807d62f23058291a000f34c85d960f939fbc46004aa6d905f747d4ae3042f5c651738537fbc2e50d348b068379b773a361ad98e881c20583d9952cc10ab1010d331d815e92b7f99dd2047e0670b3b393b15e0a4');
assert.equal(await verifyNcwuSudySource('function forged() {}'),false);
assert.throws(()=>encodeNcwuSudyPassword(''),/invalid_credentials/);
assert.throws(()=>encodeNcwuSudyPassword('a'.repeat(513)),/invalid_credentials/);
assert.throws(()=>encodeNcwuSudyPassword('\u{1f600}'),/invalid_credentials/);
console.log('NCWU Sudy RSA: independent synthetic vector and unsupported inputs passed');
