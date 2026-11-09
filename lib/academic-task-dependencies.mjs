/* Owner-scoped cloud prerequisites, backed by the existing Academic OS D1.
 * Adapted from NextTodo's MIT DAG/ready model. No separate task store.
 * All writes use a database trigger-guarded graph revision inside one D1 batch.
 */
import {WorkspaceError} from './academic-workspace.mjs';

const fail=(code,status=422)=>{throw new WorkspaceError(code,status);};
const validId=x=>typeof x==='string'&&/^[A-Za-z0-9_-]{1,100}$/.test(x);

export async function readCloudTaskGraph(db,userId){
 if(!userId)fail('unauthorized',401);
 // One SQLite statement means revision and edges come from the SAME
 // consistent snapshot. Separate parallel reads could mix old edges with
 // a newer revision and let a concurrent cycle slip through the CAS guard.
 const snapshot=await db.prepare(`SELECT COALESCE(g.revision,0) AS graph_revision,
   d.task_id,d.prerequisite_id
   FROM (SELECT ? AS owner_id) owner
   LEFT JOIN academic_task_graph_versions g ON g.user_id=owner.owner_id
   LEFT JOIN academic_task_dependencies d ON d.user_id=owner.owner_id
   ORDER BY d.task_id,d.prerequisite_id`).bind(userId).all();
 const rows=snapshot?.results??[];
 if(rows.length>20000)fail('graph_too_large',413);
 return {revision:rows[0]?.graph_revision??0,edges:rows
  .filter(x=>x.task_id!==null&&x.prerequisite_id!==null)
  .map(x=>({taskId:x.task_id,prerequisiteId:x.prerequisite_id}))};
}

// Edges point from a task to its predecessors. Detect full-graph cycles
// (not merely self-references) independent of insertion / sorting order.
export function graphHasCycle(edges){
 // Kahn's algorithm avoids recursive call-stack exhaustion on long chains.
 // An edge is stored as task -> prerequisite, so indegree counts each
 // task's prerequisite links and adjacency points to its dependents.
 const pending=new Map(),dependents=new Map();
 for(const {taskId,prerequisiteId} of edges){
  if(!pending.has(taskId))pending.set(taskId,0);
  if(!pending.has(prerequisiteId))pending.set(prerequisiteId,0);
  pending.set(taskId,pending.get(taskId)+1);
  if(!dependents.has(prerequisiteId))dependents.set(prerequisiteId,[]);
  dependents.get(prerequisiteId).push(taskId);
 }
 const ready=[];
 for(const [id,count] of pending)if(count===0)ready.push(id);
 let visited=0;
 for(let index=0;index<ready.length;index++){
  const id=ready[index];visited++;
  for(const dependent of dependents.get(id)||[]){
   const remaining=pending.get(dependent)-1;
   pending.set(dependent,remaining);
   if(remaining===0)ready.push(dependent);
  }
 }
 return visited!==pending.size;
}

export async function setCloudTaskPrerequisites(db,userId,{
 taskId,taskRevision,graphRevision,prerequisiteIds
},{
 id=()=>crypto.randomUUID(),now=()=>new Date().toISOString()
}={}){
 if(!userId||!validId(taskId))fail('invalid_id',400);
 if(!Number.isSafeInteger(taskRevision)||taskRevision<0||
    !Number.isSafeInteger(graphRevision)||graphRevision<0)fail('revision_required');
 if(!Array.isArray(prerequisiteIds)||prerequisiteIds.length>30||
    prerequisiteIds.some(x=>!validId(x)||x===taskId)||
    new Set(prerequisiteIds).size!==prerequisiteIds.length)fail('invalid_prerequisites');
 const target=await db.prepare(`SELECT id,kind,revision FROM academic_assignments
  WHERE id=? AND user_id=?`).bind(taskId,userId).first();
 if(!target)fail('not_found',404);
 if(target.kind!=='manual')fail('platform_prerequisites_readonly',403);
 if(target.revision!==taskRevision)fail('task_revision_conflict',409);
 const graph=await readCloudTaskGraph(db,userId);
 if(graph.revision!==graphRevision)fail('graph_revision_conflict',409);
 if(prerequisiteIds.length){
  const found=await db.prepare(`SELECT id,kind FROM academic_assignments
   WHERE user_id=? AND id IN (${prerequisiteIds.map(()=>'?').join(',')})`)
   .bind(userId,...prerequisiteIds).all();
  if((found.results??[]).length!==prerequisiteIds.length||
     found.results.some(row=>row.kind!=='manual'))fail('unowned_or_platform_prerequisite',403);
 }
 const previous=graph.edges.filter(e=>e.taskId===taskId).map(e=>e.prerequisiteId).sort();
 const next=[...prerequisiteIds].sort();
 if(previous.length===next.length&&previous.every((x,i)=>x===next[i]))
  return {taskId,taskRevision,graphRevision,prerequisiteIds:next,unchanged:true};
 const candidate=[...graph.edges.filter(e=>e.taskId!==taskId),
  ...next.map(p=>({taskId,prerequisiteId:p}))];
 if(graphHasCycle(candidate))fail('dependency_cycle');
 const stamp=now();
 if(!Number.isFinite(Date.parse(stamp)))fail('invalid_timestamp',503);
 const guardId=id();
 const statements=[
  db.prepare(`INSERT OR IGNORE INTO academic_task_graph_versions
    (user_id,revision,updated_at) VALUES (?,0,?)`).bind(userId,stamp),
  db.prepare(`INSERT INTO academic_task_graph_guards
    (id,user_id,task_id,expected_graph_revision,expected_task_revision)
    VALUES (?,?,?,?,?)`).bind(guardId,userId,taskId,graphRevision,taskRevision),
  db.prepare('DELETE FROM academic_task_dependencies WHERE user_id=? AND task_id=?')
   .bind(userId,taskId),
  ...next.map(p=>db.prepare(`INSERT INTO academic_task_dependencies
    (user_id,task_id,prerequisite_id,created_at) VALUES (?,?,?,?)`)
    .bind(userId,taskId,p,stamp)),
  db.prepare(`UPDATE academic_task_graph_versions SET revision=revision+1,
    updated_at=? WHERE user_id=?`).bind(stamp,userId),
  db.prepare(`UPDATE academic_assignments SET revision=revision+1,updated_at=?
    WHERE id=? AND user_id=?`).bind(stamp,taskId,userId),
  db.prepare(`INSERT INTO academic_assignment_events
    (id,assignment_id,event_type,previous_value,current_value,observed_at)
    VALUES (?,?,'prerequisites_changed',?,?,?)`)
   .bind(id(),taskId,JSON.stringify(previous),JSON.stringify(next),stamp),
  db.prepare('DELETE FROM academic_task_graph_guards WHERE id=? AND user_id=?')
   .bind(guardId,userId)
 ];
 try{
  // D1 batch is a transaction. Guard trigger aborts the *entire* batch if
  // either revision changes after the graph was read.
  await db.batch(statements);
 }catch(e){
  const message=String(e?.message||e);
  if(/graph_revision_conflict/.test(message))fail('graph_revision_conflict',409);
  if(/task_revision_conflict/.test(message))fail('task_revision_conflict',409);
  if(/FOREIGN KEY constraint|foreign key constraint/i.test(message))
   fail('prerequisite_changed',409);
  throw e;
 }
 return {taskId,taskRevision:taskRevision+1,graphRevision:graphRevision+1,
  prerequisiteIds:next,unchanged:false};
}
