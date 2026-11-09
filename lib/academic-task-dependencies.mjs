/* Owner-scoped cloud prerequisites, backed by the existing Academic OS D1.
 * Adapted from NextTodo's MIT DAG/ready model. No separate task store.
 * All writes use a database trigger-guarded graph revision inside one D1 batch.
 */
import {WorkspaceError} from './academic-workspace.mjs';

const fail=(code,status=422)=>{throw new WorkspaceError(code,status);};
const validId=x=>typeof x==='string'&&/^[A-Za-z0-9_-]{1,100}$/.test(x);

export async function readCloudTaskGraph(db,userId){
 if(!userId)fail('unauthorized',401);
 const [meta,edges]=await Promise.all([
  db.prepare('SELECT revision FROM academic_task_graph_versions WHERE user_id=?')
   .bind(userId).first(),
  db.prepare(`SELECT task_id,prerequisite_id FROM academic_task_dependencies
    WHERE user_id=? ORDER BY task_id,prerequisite_id`).bind(userId).all()
 ]);
 const rows=edges?.results??[];
 if(rows.length>20000)fail('graph_too_large',413);
 return {revision:meta?.revision??0,edges:rows.map(x=>({
  taskId:x.task_id,prerequisiteId:x.prerequisite_id
 }))};
}

// Edges point from a task to its predecessors. Detect full-graph cycles
// (not merely self-references) independent of insertion / sorting order.
export function graphHasCycle(edges){
 const links=new Map();
 for(const e of edges){
  if(!links.has(e.taskId))links.set(e.taskId,[]);
  links.get(e.taskId).push(e.prerequisiteId);
 }
 const finished=new Set(),visiting=new Set();
 function visit(id){
  if(visiting.has(id))return true;
  if(finished.has(id))return false;
  visiting.add(id);
  for(const p of links.get(id)||[])if(visit(p))return true;
  visiting.delete(id);finished.add(id);return false;
 }
 for(const id of links.keys())if(visit(id))return true;
 return false;
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
