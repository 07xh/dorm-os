import assert from 'node:assert/strict';
import {filterNcwuElectiveClasses,compareNcwuElectiveTimetables}
 from '../lib/academic-ncwu-elective-view.mjs';

const base=()=>({
 schema:'xiaoxu/ncwu-elective-candidate-v1',provider:'ncwu',
 term:'2026-2027-1',observedAt:'2026-11-10T09:00:00Z',
 classes:[
  {courseCode:'C1',classCode:'A',courseName:'电路基础',teacher:'王老师',
   campus:'江淮',capacity:40,remaining:2,
   meetings:[{weekday:2,startSection:3,endSection:4,weeks:[1,3,5]}]},
  {courseCode:'C1',classCode:'B',courseName:'电路基础',teacher:'李老师',
   campus:'江淮',capacity:40,remaining:0,
   meetings:[{weekday:2,startSection:3,endSection:4,weeks:[2,4,6]}]},
  {courseCode:'C2',classCode:'A',courseName:'大学物理',teacher:'陈老师',
   campus:'花园',
   meetings:[]}
 ]
});
const view=filterNcwuElectiveClasses(base());
assert.equal(view.classes.length,3);
assert.equal(view.verified,false);
assert.equal(view.readOnly,true);
assert.equal(view.submissionAllowed,false);
assert.equal(view.eligibilityVerified,false);
assert.equal(view.classes.every(cls=>cls.eligibility==='unknown'),true);
assert.equal(filterNcwuElectiveClasses(base(),{keyword:' 电路 '}).classes.length,2);
assert.equal(filterNcwuElectiveClasses(base(),{keyword:'C2'}).classes[0].courseName,'大学物理');
assert.equal(filterNcwuElectiveClasses(base(),{teacher:'王'}).classes.length,1);
assert.equal(filterNcwuElectiveClasses(base(),{campus:'江淮'}).classes.length,2);
assert.equal(filterNcwuElectiveClasses(base(),{seats:'known_available'}).classes[0].classCode,'A');
assert.equal(filterNcwuElectiveClasses(base(),{seats:'known_full'}).classes[0].classCode,'B');
assert.equal(filterNcwuElectiveClasses(base(),{seats:'unknown'}).classes[0].courseCode,'C2');
assert.equal(filterNcwuElectiveClasses(base(),{campus:'无此校区'}).classes.length,0);
for(const bad of [{seats:'eligible'},{cookie:'secret'},{keyword:'<img>'},{keyword:1},null]){
 assert.throws(()=>filterNcwuElectiveClasses(base(),bad),/ncwu_elective_view_/);
}
const own=[{weekday:2,startSection:4,endSection:5,weeks:[3,9]}];
const compared=compareNcwuElectiveTimetables(base(),own);
assert.deepEqual(compared.classes.map(x=>x.conflictStatus),
 ['conflict_detected','no_known_conflict','insufficient_data']);
assert.deepEqual(compared.classes[0].conflictingIndices,[0]);
assert.deepEqual(compared.classes[1].conflictingIndices,[]);
assert.equal(compared.classes.every(c=>c.eligibility==='unknown'),true);
assert.equal(compared.verified,false);
const noPersonal=compareNcwuElectiveTimetables(base());
assert.deepEqual(noPersonal.classes.map(c=>c.conflictStatus),
 ['insufficient_data','insufficient_data','insufficient_data']);
const emptyButComplete=compareNcwuElectiveTimetables(base(),[],
 {personalScheduleComplete:true});
assert.equal(emptyButComplete.classes[0].conflictStatus,'no_known_conflict');
assert.equal(emptyButComplete.classes[2].conflictStatus,'insufficient_data');
const adjacent=compareNcwuElectiveTimetables(base(),[
 {weekday:2,startSection:5,endSection:6,weeks:[3]}]);
assert.equal(adjacent.classes[0].conflictStatus,'no_known_conflict');
for(const bad of [
 [{weekday:8,startSection:3,endSection:4,weeks:[1]}],
 [{weekday:2,startSection:3,endSection:4,weeks:[1,1]}],
 [{weekday:2,startSection:3,endSection:4,weeks:[1],cookie:'bad'}],
 {},
]){
 assert.throws(()=>compareNcwuElectiveTimetables(base(),bad),
  /ncwu_elective_view_/);
}
console.log('NCWU elective read-only search: filters, unknown seats and week-aware conflict reporting passed');
