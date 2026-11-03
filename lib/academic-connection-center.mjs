/* One read-only connection overview. Existing D1 tables are the source of truth.
 * Imported snapshots NEVER imply that a school account is authenticated.
 * No credentials, cookies, or school session tokens are stored here.
 */
const PROVIDERS=Object.freeze([
  {id:'ncwu',name:'华水青果教务',url:'https://jwmis.ncwu.edu.cn/',kinds:['schedule','grades','exams']},
  {id:'chaoxing',name:'学习通',url:'https://ncwu.fanya.chaoxing.com/portal',kinds:['assignments']},
  {id:'second_class',name:'第二课堂',url:'https://dekt.ncwu.edu.cn/',kinds:['second-class']}
]);
const STATES=new Set(['connected','partial','expired','error']);
function safeDate(value){
  return typeof value==='string'&&Number.isFinite(Date.parse(value))?value:null;
}
export function projectConnectionCenter(connections=[],batches=[]){
  if(!Array.isArray(connections)||!Array.isArray(batches))throw Error('invalid_source_rows');
  return PROVIDERS.map(provider=>{
    const connection=connections.find(row=>row?.provider===provider.id&&
      row.mode==='readonly'&&STATES.has(row.status));
    const scope=batches.filter(row=>row?.source===provider.id&&provider.kinds.includes(row.kind));
    const trusted=scope.filter(row=>row.provenance==='trusted_readonly');
    const imported=scope.filter(row=>row.provenance!=='trusted_readonly');
    const latest=trusted.map(row=>safeDate(row.last_success_at)).filter(Boolean).sort().at(-1)||null;
    const validReadOnly=connection&&safeDate(connection.last_success_at);
    // A verified historical snapshot is not proof of an active upstream login.
    return {
      id:provider.id,name:provider.name,officialUrl:provider.url,
      status:validReadOnly?connection.status:'not_connected',
      lastSuccessAt:validReadOnly?connection.last_success_at:latest,
      trustedKinds:trusted.map(row=>row.kind),
      importedKinds:imported.map(row=>row.kind),
      accountAuthorization:'not_verified',
      automaticSync:false,
      availableAction:'open_official_site'
    };
  });
}
export async function readConnectionCenter(db,userId){
  if(!db?.prepare||typeof userId!=='string'||!userId)throw Error('database_unavailable');
  const [connections,batches]=await Promise.all([
    db.prepare("SELECT provider,mode,status,last_success_at FROM academic_connections WHERE user_id=? AND provider IN ('ncwu','chaoxing')").bind(userId).all(),
    db.prepare('SELECT kind,source,provenance,last_success_at FROM academic_verified_batches WHERE user_id=?').bind(userId).all()
  ]);
  return {schema:'xiaoxu/connections-v1',platforms:projectConnectionCenter(
    connections.results??[],batches.results??[])};
}
