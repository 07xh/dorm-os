import assert from 'node:assert/strict';
import {collectAuthorizedSecondClass,SecondClassSyncError}
 from '../lib/academic-second-class-connector.mjs';

const proof={authorized:true,scope:'self',school:'ncwu',provider:'second_class',
 identityBound:true,principalBoundTo:'owner-a',session:{ephemeral:true}};
const page1={activities:[{activityId:'a1',title:'合成活动甲',date:'2026-11-01',hours:2,
 points:5,activityStatus:'completed',reviewStatus:'approved',unusedPII:'SHOULD_NOT_PERSIST'}],
 nextCursor:'page2',complete:false};
const page2={activities:[{activityId:'a2',title:'合成活动乙',date:'2026-11-02',
 activityStatus:'registered',reviewStatus:'pending'}],nextCursor:null,complete:true};
function adapter(pages=[page1,page2],identity=proof){
 const cursors=[];
 return {provider:'second_class',cursors,
  verifySession:async()=>identity,
  readPage:async ({cursor})=>{cursors.push(cursor);return pages[cursors.length-1];}
 };
}
const a=adapter();
const collected=await collectAuthorizedSecondClass(a);
assert.equal(collected.records.length,2);
assert.equal(collected.pages,2);
assert.equal(collected.records[0].hours,2);
assert.equal(collected.records[0].points,5);
assert.equal(collected.records[0].reviewStatus,'approved');
assert.equal(Object.hasOwn(collected.records[0],'unusedPII'),false);
assert.deepEqual(a.cursors,[null,'page2']);
async function mustFail(value,code){
 await assert.rejects(()=>value, e=>e instanceof SecondClassSyncError&&e.code===code);
}
await mustFail(collectAuthorizedSecondClass(adapter([page2],{...proof,identityBound:false})), 'authorization_failed');
await mustFail(collectAuthorizedSecondClass(adapter([page2],{...proof,school:'other'})), 'authorization_failed');
await mustFail(collectAuthorizedSecondClass(adapter([{activities:[{...page1.activities[0],points:-2}],complete:true}])), 'invalid_response');
await mustFail(collectAuthorizedSecondClass(adapter([{activities:[{...page1.activities[0],reviewStatus:'fake'}],complete:true}])), 'invalid_response');
await mustFail(collectAuthorizedSecondClass(adapter([page1,{...page2,activities:[{...page1.activities[0]}]}])), 'invalid_response');
await mustFail(collectAuthorizedSecondClass(adapter([{...page1,nextCursor:'page2'}, {...page1,nextCursor:'page2'}])), 'invalid_response');
await mustFail(collectAuthorizedSecondClass(adapter([{activities:[],complete:true}])), 'invalid_response');
await mustFail(collectAuthorizedSecondClass(adapter([{activities:[page1.activities[0]],complete:false,nextCursor:null}])), 'invalid_response');
await mustFail(collectAuthorizedSecondClass({provider:'second_class',verifySession:async()=>proof,
 readPage:async()=>{throw Error('cookie=redacted');}}),'upstream_unavailable');
await mustFail(collectAuthorizedSecondClass(adapter([page2],{...proof,authorized:false}),{userId:'owner-a'}),'authorization_failed');
await mustFail(collectAuthorizedSecondClass(adapter([page2],{...proof,principalBoundTo:'owner-b'}),{userId:'owner-a'}),'authorization_failed');
const empty=await collectAuthorizedSecondClass(adapter([
 {activities:[],complete:true,verifiedEmpty:true}
]),{userId:'owner-a'});
assert.deepEqual(empty.records,[]);
assert.equal(empty.complete,true);
// Persistence is tested through the live activities+scores writer in
// xiaoxu-ncwu-second-class-real-contract-tests.mjs, never a second writer.
console.log('Active second-class collector: owner authorization, pagination, strict status, verified empty and privacy passed');
