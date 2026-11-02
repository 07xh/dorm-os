import assert from 'node:assert/strict';
import {normalizeKingoCourseRows,SOURCE} from '../lib/academic-upstream-bridge.mjs';
const sample=[
 {name:'高等数学',teacher:'李老师',position:'A201',day:1,startSection:1,endSection:2,weeks:[1,3,5,7]},
 {name:'工程制图',teacher:'王老师',position:'B401',day:3,startSection:3,endSection:4,weeks:[1,2,3,4]},
];
const output=normalizeKingoCourseRows(sample,{semester:'2026-2027-1'});
assert.equal(output.length,2);
assert.equal(output[0].source,SOURCE);
assert.equal(output[0].title,'高等数学');
assert.equal(output[0].weekday,1);
assert.deepEqual(output[0].weeks,[1,3,5,7]);
assert.equal(output[0].startTime,null,'Cannot fabricate school bell times');
assert.equal(output[0].externalId,null,'Kingo HTML blocks do not prove an official course ID');
assert.equal(output[0].syncState,'unverified','Synthetic example must not be claimed as connected to NCWU');
assert.throws(()=>normalizeKingoCourseRows([],{semester:'2026-2027-1'}),/空响应/);
assert.throws(()=>normalizeKingoCourseRows(sample,{semester:''}),/学期/);
assert.throws(()=>normalizeKingoCourseRows([{...sample[0],day:0}],{semester:'2026-2027-1'}),/字段/);
assert.throws(()=>normalizeKingoCourseRows([{...sample[0],weeks:[0]}],{semester:'2026-2027-1'}),/周次/);
assert.throws(()=>normalizeKingoCourseRows([{...sample[0],startSection:8,endSection:6}],{semester:'2026-2027-1'}),/字段/);
assert.throws(()=>normalizeKingoCourseRows([sample[0],sample[0]],{semester:'2026-2027-1'}),/重复/);
console.log('Kingo/Shiguang bridge: week/section normalization and unverifiable source guards passed');
