/* NCWU second-class: a fail-closed activity DISCOVERY contract.
 *
 * The existing /sys/stu/center/signupactivity/list is a list of MY signups,
 * not proof of the official activity marketplace endpoint. Do not connect this
 * collector to any upstream path until the owner has verified that path and
 * its read-only pagination/status semantics in a normal school session.
 *
 * This module cannot log in, submit a signup, fetch arbitrary URLs, or store
 * school credentials. Only a trusted server-local adapter may supply pages.
 */
export class SecondClassCatalogError extends Error {
  constructor(code) { super(code); this.name='SecondClassCatalogError'; this.code=code; }
}
const fail=code=>{throw new SecondClassCatalogError(code);};
const ID=/^[A-Za-z0-9:_-]{1,120}$/;
const CURSOR=/^[A-Za-z0-9_.:=-]{1,160}$/;
const STAMP=/^20\d{2}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?(?:Z|[+-]\d{2}:\d{2})$/;
const STATES=new Set(['open','upcoming','closed','unknown']);
const isObject=x=>x!==null&&typeof x==='object'&&!Array.isArray(x);
function requiredText(value,max) {
  if(typeof value!=='string'||!value.trim()||value.length>max)fail('invalid_catalog_record');
  return value.trim();
}
function optionalText(value,max) {
  if(value===undefined||value===null||value==='')return undefined;
  return requiredText(value,max);
}
function optionalInstant(value) {
  if(value===undefined||value===null||value==='')return undefined;
  if(typeof value!=='string'||!STAMP.test(value)||!Number.isFinite(Date.parse(value)))
    fail('invalid_catalog_record');
  return value;
}
/* Project only witnessed, non-sensitive activity fields. This is not the
 * personal enrollment model: never infer my signup state from catalog state.
 */
export function normalizeSecondClassCatalogEntry(row) {
  if(!isObject(row))fail('invalid_catalog_record');
  const activityId=requiredText(row.activityId,120);
  if(!ID.test(activityId))fail('invalid_catalog_record');
  const title=requiredText(row.title,160);
  const signupState=row.signupState===undefined?'unknown':row.signupState;
  if(!STATES.has(signupState))fail('invalid_catalog_record');
  const record={activityId,title,signupState};
  for(const key of ['organizer','category','campus','location','audience']) {
    const value=optionalText(row[key],120);
    if(value!==undefined)record[key]=value;
  }
  for(const key of ['signupStartsAt','signupEndsAt','eventStartsAt','eventEndsAt']) {
    const value=optionalInstant(row[key]);
    if(value!==undefined)record[key]=value;
  }
  if(row.remainingPlaces!==undefined&&row.remainingPlaces!==null) {
    if(!Number.isSafeInteger(row.remainingPlaces)||
       row.remainingPlaces<0||row.remainingPlaces>100000)fail('invalid_catalog_record');
    record.remainingPlaces=row.remainingPlaces;
  }
  // Never derive "open" from a date, count or missing status field.
  // URLs, student identifiers, cookies and extra fields are not projected.
  return record;
}
export async function collectVerifiedSecondClassCatalog(adapter,{
  ownerId,maxPages=20,maxRecords=500
}={}) {
  if(!ID.test(ownerId||'')||!adapter||adapter.provider!=='second_class'||
     typeof adapter.verifyCatalogSource!=='function'||
     typeof adapter.readCatalogPage!=='function'||
     !Number.isInteger(maxPages)||maxPages<1||maxPages>100||
     !Number.isInteger(maxRecords)||maxRecords<1||maxRecords>1000)
    fail('catalog_not_configured');
  let proof;
  try{proof=await adapter.verifyCatalogSource();}
  catch{fail('authorization_failed');}
  if(!isObject(proof)||proof.authorized!==true||proof.scope!=='self'||
     proof.school!=='ncwu'||proof.provider!=='second_class'||
     proof.identityBound!==true||proof.principalBoundTo!==ownerId||
     proof.catalogContractVerified!==true||!isObject(proof.session))
    fail('authorization_failed');
  const records=[],seenIds=new Set(),seenCursors=new Set();
  let cursor=null;
  for(let page=1;page<=maxPages;page++) {
    let raw;
    try{raw=await adapter.readCatalogPage({cursor,session:proof.session});}
    catch{fail('upstream_unavailable');}
    if(!isObject(raw)||!Array.isArray(raw.activities)||
       raw.activities.length>50||typeof raw.complete!=='boolean')
      fail('invalid_catalog_page');
    const nextCursor=raw.nextCursor??null;
    if(raw.complete!==(nextCursor===null)||
       nextCursor!==null&&(typeof nextCursor!=='string'||!CURSOR.test(nextCursor)||seenCursors.has(nextCursor)))
      fail('invalid_catalog_page');
    if(raw.activities.length===0&&
       !(page===1&&raw.complete===true&&raw.verifiedEmpty===true))
      fail('unverified_catalog_empty');
    for(const item of raw.activities) {
      const record=normalizeSecondClassCatalogEntry(item);
      if(seenIds.has(record.activityId))fail('duplicate_catalog_activity');
      seenIds.add(record.activityId);
      records.push(record);
      if(records.length>maxRecords)fail('catalog_limit_exceeded');
    }
    if(raw.complete)return Object.freeze({
      source:'second_class',scope:'authorized_catalog',ownerId,
      complete:true,records,pages:page
    });
    seenCursors.add(nextCursor);
    cursor=nextCursor;
  }
  fail('incomplete_catalog');
}
/* Changes are emitted only between two complete, same-owner snapshots.
 * On first sync there is no trusted baseline. Disappearing activities are
 * NOT reported as cancelled: an upstream listing can filter or paginate.
 */
export function diffVerifiedSecondClassCatalog(previous,current) {
  function validate(snapshot) {
    if(!isObject(snapshot)||snapshot.source!=='second_class'||
       snapshot.scope!=='authorized_catalog'||snapshot.complete!==true||
       !ID.test(snapshot.ownerId||'')||!Array.isArray(snapshot.records)||
       snapshot.records.length>1000)fail('unverified_catalog_snapshot');
    const seen=new Set();
    for(const row of snapshot.records) {
      const normalized=normalizeSecondClassCatalogEntry(row);
      if(seen.has(normalized.activityId))fail('unverified_catalog_snapshot');
      seen.add(normalized.activityId);
    }
  }
  validate(current);
  if(previous===null)return [];
  validate(previous);
  if(previous.ownerId!==current.ownerId)fail('owner_mismatch');
  const old=new Map(previous.records.map(x=>[x.activityId,x]));
  const changes=[];
  for(const row of current.records) {
    const before=old.get(row.activityId);
    if(!before) {
      changes.push({type:'new_activity',activityId:row.activityId});
      continue;
    }
    if(before.signupState!==row.signupState&&
       before.signupState!=='unknown'&&row.signupState!=='unknown') {
      changes.push({type:'registration_changed',activityId:row.activityId,
        from:before.signupState,to:row.signupState});
    }
    if(before.signupEndsAt&&row.signupEndsAt&&
       before.signupEndsAt!==row.signupEndsAt)
      changes.push({type:'deadline_changed',activityId:row.activityId});
  }
  return changes;
}
