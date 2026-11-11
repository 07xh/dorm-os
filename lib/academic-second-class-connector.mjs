/* NCWU second-class read-only integration boundary.
 * No guessed upstream URLs, login automation, browser-supplied "verified"
 * flags, hidden attendance actions, or raw credentials in D1.
 * An authorized adapter must implement the two methods against the actual
 * signed-in school site after its endpoints have been verified by the owner.
 */
import {normalizeRecord} from './academic-contract.mjs';

export class SecondClassSyncError extends Error {
 constructor(code){super(code);this.name='SecondClassSyncError';this.code=code;}
}
const fail=code=>{throw new SecondClassSyncError(code);};
const safeId=x=>typeof x==='string'&&/^[A-Za-z0-9:_-]{1,120}$/.test(x);
const safeCursor=x=>typeof x==='string'&&x.length>0&&x.length<=160&&
 /^[A-Za-z0-9_:.=-]+$/.test(x);
function normalizeActivity(value){
 if(!value||typeof value!=='object'||Array.isArray(value))fail('invalid_response');
 // Only explicitly witnessed fields are projected; never persist student
 // numbers, names, avatars, signed links, session or raw upstream payloads.
 const data={
  externalId:value.activityId,title:value.title,
  ...(value.term!=null?{term:value.term}:{}),
  ...(value.date!=null?{date:value.date}:{}),
  ...(value.credit!=null?{credit:value.credit}:{}),
  ...(value.hours!=null?{hours:value.hours}:{}),
  ...(value.points!=null?{points:value.points}:{}),
  ...(value.activityStatus!=null?{activityStatus:value.activityStatus}:{}),
  ...(value.reviewStatus!=null?{reviewStatus:value.reviewStatus}:{}),
  ...(value.settlementStatus!=null?{settlementStatus:value.settlementStatus}:{})
 };
 try{return normalizeRecord('second-class',data);}
 catch{fail('invalid_response');}
}
export async function collectAuthorizedSecondClass(adapter,{maxPages=25,maxRecords=250,userId=null}={}){
 if(!adapter||adapter.provider!=='second_class'||
  typeof adapter.verifySession!=='function'||typeof adapter.readPage!=='function'||
  !Number.isInteger(maxPages)||maxPages<1||maxPages>100||
  !Number.isInteger(maxRecords)||maxRecords<1||maxRecords>250||
  (userId!==null&&!safeId(userId)))fail('connector_not_configured');
 let proof;
 try{proof=await adapter.verifySession();}
 catch{fail('authorization_failed');}
 if(!proof||proof.authorized!==true||proof.scope!=='self'||
   proof.school!=='ncwu'||proof.provider!=='second_class'||
   proof.identityBound!==true||
   (userId!==null&&proof.principalBoundTo!==userId))fail('authorization_failed');
 const seenIds=new Set(),seenCursors=new Set(),records=[];
 let cursor=null,pages=0;
 for(let i=0;i<maxPages;i++){
  let result;
  try{result=await adapter.readPage({cursor,session:proof.session});}
  catch{fail('upstream_unavailable');}
  if(!result||typeof result!=='object'||Array.isArray(result)||
    !Array.isArray(result.activities)||result.activities.length>50||
    typeof result.complete!=='boolean')fail('invalid_response');
  const next=result.nextCursor??null;
  if(next!==null&&!safeCursor(next))fail('invalid_response');
  if(result.complete!==(next===null))fail('invalid_response');
  if(!result.activities.length&&
    !(pages===0&&result.complete===true&&result.verifiedEmpty===true))
    fail('invalid_response');
  for(const raw of result.activities){
   const row=normalizeActivity(raw),key=row.externalId+'|'+row.term;
   if(seenIds.has(key))fail('invalid_response');
   seenIds.add(key);records.push(row);
   if(records.length>maxRecords)fail('invalid_response');
  }
  pages++;
  if(result.complete)return {source:'second_class',kind:'second-class',
    records,complete:true,pages,provenance:'trusted_readonly'};
  if(seenCursors.has(next))fail('invalid_response');
  seenCursors.add(next);cursor=next;
 }
 fail('invalid_response');
}
