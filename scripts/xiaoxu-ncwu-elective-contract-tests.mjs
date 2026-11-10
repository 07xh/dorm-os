import assert from 'node:assert/strict';
import {normalizeNcwuElectiveCandidate} from '../lib/academic-ncwu-elective-contract.mjs';
const fixture=()=>({
 schema:'xiaoxu/ncwu-elective-candidate-v1',provider:'ncwu',
 term:'2026-2027-1',observedAt:'2026-11-10T09:00:00Z',
 classes:[{
  courseCode:'TEST_COURSE_1',classCode:'TEST_CLASS_A',
  courseName:'测试课程',teacher:'测试教师',campus:'测试校区',
  capacity:40,remaining:0,
  meetings:[{weekday:3,startSection:1,endSection:2,weeks:[3,1,2]}]
 }]
});
const result=normalizeNcwuElectiveCandidate(fixture());
assert.equal(result.readOnly,true);
assert.equal(result.verified,false);
assert.equal(result.submissionAllowed,false);
assert.equal(result.sourceState,'unverified');
assert.equal(result.classes[0].remaining,0);
assert.equal(result.classes[0].eligibility,'unknown');
assert.deepEqual(result.classes[0].meetings[0].weeks,[1,2,3]);
assert.equal('Cookie' in result.classes[0],false);
const clone=value=>JSON.parse(JSON.stringify(value));
function rejected(change){
 const obj=fixture();change(obj);
 assert.throws(()=>normalizeNcwuElectiveCandidate(obj),/ncwu_elective_candidate_/);
}
rejected(o=>o.token='secret');
rejected(o=>o.classes[0].cookie='secret');
rejected(o=>o.classes[0].submitUrl='https://example.org');
rejected(o=>o.classes[0].courseName='<script>bad</script>');
rejected(o=>o.provider='other');
rejected(o=>o.term='2026-2028-1');
rejected(o=>o.observedAt='not-a-timestamp');
rejected(o=>o.classes[0].remaining=41);
rejected(o=>o.classes[0].capacity=-1);
rejected(o=>o.classes[0].meetings[0].weeks=[1,1]);
rejected(o=>o.classes[0].meetings[0].endSection=0);
rejected(o=>o.classes.push(clone(o.classes[0])));
rejected(o=>o.classes=Array(301).fill(o.classes[0]));
const unknown=fixture();
delete unknown.classes[0].capacity;
delete unknown.classes[0].remaining;
delete unknown.classes[0].meetings;
const normalized=normalizeNcwuElectiveCandidate(unknown);
assert.equal(normalized.classes[0].capacity,null,'unknown is not zero');
assert.equal(normalized.classes[0].remaining,null,'unknown is not zero');
assert.deepEqual(normalized.classes[0].meetings,[]);
assert.deepEqual(normalizeNcwuElectiveCandidate({...fixture(),classes:[]}).classes,[],
 'an empty candidate is not proof of authoritative empty selection');
console.log('NCWU elective read-only candidate: strict schema, explicit unknowns and no submit path passed');
