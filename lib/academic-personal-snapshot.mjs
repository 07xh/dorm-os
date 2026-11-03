/* One personal snapshot writer, reused by existing manual import and
 * the paired second-class browser. Caller MUST authenticate owner/token.
 * A user/browser snapshot is never marked trusted_readonly.
 */
export class PersonalSnapshotError extends Error {
 constructor(code){super(code);this.name='PersonalSnapshotError';this.code=code;}
}
export async function savePersonalSnapshot(db,userId,batch,{
 now=()=>new Date().toISOString(),id=()=>crypto.randomUUID(),afterStatements=[]
}={}){
 if(!db?.prepare||!db?.batch||!userId||!batch||!Array.isArray(batch.records))
  throw new PersonalSnapshotError('database_unavailable');
 const latest=await db.prepare('SELECT last_success_at,provenance FROM academic_verified_batches WHERE user_id=? AND kind=?')
  .bind(userId,batch.kind).first();
 if(latest?.provenance==='trusted_readonly')
  throw new PersonalSnapshotError('trusted_source_readonly');
 if(latest&&Date.parse(latest.last_success_at)>Date.parse(batch.capturedAt))
  throw new PersonalSnapshotError('outdated_snapshot');
 const stamp=now(),batchId=id();
 const statements=[db.prepare('INSERT OR IGNORE INTO academic_users(id,created_at) VALUES (?,?)').bind(userId,stamp)];
 for(const record of batch.records){
  statements.push(db.prepare(
   'INSERT INTO academic_verified_records(user_id,kind,source,external_id,term,payload,batch_id,updated_at) VALUES(?,?,?,?,?,?,?,?) '+
   'ON CONFLICT(user_id,kind,source,external_id,term) DO UPDATE SET payload=excluded.payload,batch_id=excluded.batch_id,updated_at=excluded.updated_at'
  ).bind(userId,batch.kind,batch.source,record.externalId,record.term,JSON.stringify(record),batchId,batch.capturedAt));
 }
 statements.push(db.prepare(
  'INSERT INTO academic_verified_batches(user_id,kind,source,last_success_at,batch_id,updated_at,provenance) VALUES(?,?,?,?,?,?,?) '+
  'ON CONFLICT(user_id,kind) DO UPDATE SET source=excluded.source,last_success_at=excluded.last_success_at,batch_id=excluded.batch_id,updated_at=excluded.updated_at,provenance=excluded.provenance'
 ).bind(userId,batch.kind,batch.source,batch.capturedAt,batchId,stamp,'user_import'));
 statements.push(db.prepare('INSERT INTO academic_verified_ingests(id,user_id,kind,source,captured_at,accepted_at,count) VALUES(?,?,?,?,?,?,?)')
  .bind(id(),userId,batch.kind,batch.source,batch.capturedAt,stamp,batch.records.length));
 // For browser bridge, update pairing status in SAME D1 transaction.
 statements.push(...afterStatements);
 await db.batch(statements);
 return {status:'accepted',kind:batch.kind,count:batch.records.length,
  lastSuccessAt:batch.capturedAt,provenance:'user_import'};
}
