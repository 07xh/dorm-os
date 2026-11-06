import test from 'node:test';
import assert from 'node:assert/strict';
import {
  buildObservedSecondClassActivityRead,
  verifyObservedSecondClassActivityResponse
} from '../lib/academic-ncwu-second-class-activity-source.mjs';

const states=['报名中','待验证','即将开始报名'];
const observedEmpty={data:[],startIndex:0,totalPage:0,totalSize:0};

test('2026-11-06 actual official HAR: one POST path for three named filters',()=>{
  for(const title of states) {
    const req=buildObservedSecondClassActivityRead(title);
    assert.equal(req.path,'/sys/stu/activity');
    assert.equal(req.method,'POST');
    assert.equal(req.contentType,'application/x-www-form-urlencoded; charset=UTF-8');
    assert.equal(new URLSearchParams(req.body).get('title'),title);
    assert.deepEqual([...new URLSearchParams(req.body).keys()],['title']);
  }
  assert.notEqual(buildObservedSecondClassActivityRead('报名中').path,
    '/sys/stu/sys/stu/activity','do not reproduce the old observer path error');
});

test('observed optional form filters are encoded, never presumed to include pagination',()=>{
  const req=buildObservedSecondClassActivityRead('即将开始报名',{
    activity_name_like:'',hold_unit:'',activity_type:''
  });
  const keys=[...new URLSearchParams(req.body).keys()];
  assert.deepEqual(keys,['title','activity_name_like','hold_unit','activity_type']);
  assert.equal(keys.includes('page'),false);
  assert.equal(keys.includes('pagesize'),false);
});

test('only exact observed zero-row response is accepted as a single-filter empty result',()=>{
  for(const title of states) {
    const result=verifyObservedSecondClassActivityResponse(observedEmpty,title);
    assert.equal(result.status,'verified_empty_response');
    assert.equal(result.scope,'single_filter');
    assert.equal(result.filter,title);
    assert.deepEqual(result.records,[]);
    assert.equal(result.nonemptyContractVerified,false);
  }
});

test('nonempty data and ambiguous empty/count pagination never become verified activities',()=>{
  for(const payload of [
    {data:[{activity_id:'SYNTHETIC',activity_name:'合成活动'}],
      startIndex:0,totalPage:1,totalSize:1},
    {data:[],startIndex:0,totalPage:1,totalSize:0},
    {data:[],startIndex:0,totalPage:0,totalSize:3},
    {data:[],startIndex:1,totalPage:0,totalSize:0}
  ]) assert.throws(()=>verifyObservedSecondClassActivityResponse(payload,'报名中'));
});

test('unsupported fields, unsafe query values and unknown states fail closed',()=>{
  for(const [title,filters] of [
    ['未知状态',{}],
    ['报名中',{student_code:'SECRET'}],
    ['报名中',{activity_type:123}],
    ['报名中',{hold_unit:'unsafe\nheader'}]
  ]) assert.throws(()=>buildObservedSecondClassActivityRead(title,filters));
  assert.throws(()=>verifyObservedSecondClassActivityResponse(observedEmpty,'未知状态'));
});
