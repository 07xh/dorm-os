import assert from 'node:assert/strict';
import {
  SecondClassCatalogError,normalizeSecondClassCatalogEntry,
  collectVerifiedSecondClassCatalog,diffVerifiedSecondClassCatalog
} from '../lib/academic-ncwu-second-class-catalog.mjs';

const ownerId='test-owner';
const proof={authorized:true,school:'ncwu',scope:'self',
  provider:'second_class',identityBound:true,principalBoundTo:ownerId,
  catalogContractVerified:true,session:{ephemeral:true}};
const entry=(activityId,signupState='open')=>({
  activityId,title:'合成活动'+activityId,signupState,organizer:'合成主办单位',
  signupEndsAt:'2026-11-25T18:00:00+08:00',
  unusedStudentCode:'SECRET_STUDENT_CODE',ticket:'SENSITIVE_TICKET',
  officialPageUrl:'https://example.invalid/?token=secret'
});
function adapter(pages=[{activities:[entry('a1')],complete:true,nextCursor:null}],
  identity=proof) {
  let calls=0;
  return {provider:'second_class',
    verifyCatalogSource:async()=>identity,
    readCatalogPage:async({cursor,session})=>{
      assert.equal(session,identity.session);
      assert.equal(cursor,calls===0?null:'page2');
      return pages[calls++];
    }};
}
const project=normalizeSecondClassCatalogEntry(entry('a1'));
assert.deepEqual(project,{
 activityId:'a1',title:'合成活动a1',signupState:'open',
 organizer:'合成主办单位',signupEndsAt:'2026-11-25T18:00:00+08:00'
});
assert.equal(JSON.stringify(project).includes('SECRET'),false);
assert.equal(JSON.stringify(project).includes('officialPageUrl'),false);
assert.equal(normalizeSecondClassCatalogEntry({activityId:'x',title:'X'}).signupState,
 'unknown','do not infer signup status from time or capacity');
assert.equal(normalizeSecondClassCatalogEntry({
  activityId:'x',title:'X',signupState:'closed',remainingPlaces:0
}).signupState,'closed');
const bad=fn=>assert.throws(fn,e=>e instanceof SecondClassCatalogError);
for(const value of [
 {...entry('x'),signupState:'registered'},
 {...entry('x'),activityId:'bad/unsafe'},
 {...entry('x'),remainingPlaces:-1},
 {...entry('x'),signupEndsAt:'tomorrow'},
 {...entry('x'),signupEndsAt:'2026-11-25 18:00:00'},
 {...entry('x'),title:''}
])bad(()=>normalizeSecondClassCatalogEntry(value));
const first={activities:[entry('a1','upcoming')],complete:false,nextCursor:'page2'};
const last={activities:[entry('a2','open')],complete:true,nextCursor:null};
const collected=await collectVerifiedSecondClassCatalog(adapter([first,last]),{ownerId});
assert.equal(collected.records.length,2);
assert.equal(collected.pages,2);
assert.equal(collected.scope,'authorized_catalog');
assert.equal(JSON.stringify(collected).includes('SECRET'),false);
const empty=await collectVerifiedSecondClassCatalog(adapter([
 {activities:[],complete:true,verifiedEmpty:true}
]),{ownerId});
assert.deepEqual(empty.records,[]);
const rejects=async(fn,code)=>assert.rejects(fn,e=>
 e instanceof SecondClassCatalogError&&e.code===code);
await rejects(()=>collectVerifiedSecondClassCatalog(adapter(),{}),'catalog_not_configured');
await rejects(()=>collectVerifiedSecondClassCatalog(
 adapter(undefined,{...proof,principalBoundTo:'another-owner'}),{ownerId}),
 'authorization_failed');
await rejects(()=>collectVerifiedSecondClassCatalog(
 adapter(undefined,{...proof,catalogContractVerified:false}),{ownerId}),
 'authorization_failed');
await rejects(()=>collectVerifiedSecondClassCatalog(
 adapter(undefined,{...proof,identityBound:false}),{ownerId}),
 'authorization_failed');
await rejects(()=>collectVerifiedSecondClassCatalog(adapter([
 {activities:[],complete:true,nextCursor:null}
]),{ownerId}),'unverified_catalog_empty');
await rejects(()=>collectVerifiedSecondClassCatalog(adapter([
 {...first,nextCursor:null}
]),{ownerId}),'invalid_catalog_page');
await rejects(()=>collectVerifiedSecondClassCatalog(adapter([
 first,{activities:[entry('a1')],complete:true,nextCursor:null}
]),{ownerId}),'duplicate_catalog_activity');
await rejects(()=>collectVerifiedSecondClassCatalog(adapter([
 first,{...first}
]),{ownerId}),'invalid_catalog_page');
await rejects(()=>collectVerifiedSecondClassCatalog(adapter([
 {activities:[entry('cursor-type')],complete:false,nextCursor:42}
]),{ownerId}),'invalid_catalog_page');
await rejects(()=>collectVerifiedSecondClassCatalog(adapter([
 first,last
]),{ownerId,maxPages:1}),'incomplete_catalog');
await rejects(()=>collectVerifiedSecondClassCatalog(adapter([
 first,last
]),{ownerId,maxRecords:1}),'catalog_limit_exceeded');
await rejects(()=>collectVerifiedSecondClassCatalog({
 provider:'second_class',verifyCatalogSource:async()=>proof,
 readCatalogPage:async()=>{throw Error('cookie=private');}
},{ownerId}),'upstream_unavailable');
// No notification storm on first sync. Neither listing absence nor status
// unknown is proof that an activity was cancelled or was just opened.
assert.deepEqual(diffVerifiedSecondClassCatalog(null,collected),[]);
const newer={...collected,records:[
 {...project,signupState:'closed',signupEndsAt:'2026-11-26T18:00:00+08:00'},
 {activityId:'a3',title:'合成新增活动',signupState:'upcoming'}
]};
const changes=diffVerifiedSecondClassCatalog(
 {...collected,records:[project]},newer);
assert.deepEqual(changes,[
 {type:'registration_changed',activityId:'a1',from:'open',to:'closed'},
 {type:'deadline_changed',activityId:'a1'},
 {type:'new_activity',activityId:'a3'}
]);
assert.deepEqual(diffVerifiedSecondClassCatalog(
 {...collected,records:[project]}, {...collected,records:[]}),[],
 'disappearance must not imply cancellation');
assert.deepEqual(diffVerifiedSecondClassCatalog(
 {...collected,records:[{...project,signupState:'unknown'}]},
 {...collected,records:[project]}),[],
 'previous unknown is not authoritative open transition');
bad(()=>diffVerifiedSecondClassCatalog({...collected,ownerId:'other-owner'},collected));
bad(()=>diffVerifiedSecondClassCatalog({...collected,complete:false},collected));
console.log('Second-class activity catalog: explicit source verification, privacy, strict paging, ownership, change detection passed');
