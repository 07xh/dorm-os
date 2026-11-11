import assert from 'node:assert/strict';
import {candidateSchedule} from '../lib/academic-provider-candidates.mjs';
const course=[{name:'工程制图',teacher:'教师甲',position:'A201',day:1,startSection:1,endSection:2,weeks:[1,3]}];
const s=candidateSchedule(course,'2026-2027-1');
assert.equal(s.verified,false);assert.equal(s.complete,false);assert.equal(s.records[0].externalId,null);
assert.throws(()=>candidateSchedule([{...course[0],weeks:[]}],'2026-1'),/课程/);
console.log('Active KINGO timetable candidate remains unverified, incomplete and strictly validated');
