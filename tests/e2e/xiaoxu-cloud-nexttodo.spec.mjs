import {test,expect} from '@playwright/test';

test('NextTodo cloud prerequisites reuse the existing task editor and unlock after completion',async({page})=>{
 let graphRevision=0,edges=[],writes=0;
 const tasks=[
  {id:'cloud-a',title:'先整理讲义',kind:'manual',local_status:'todo',
   revision:0,due_precision:'unknown',due_local_date:null,source_provider:'manual'},
  {id:'cloud-b',title:'再绘制三视图',kind:'manual',local_status:'todo',
   revision:0,due_precision:'unknown',due_local_date:null,source_provider:'manual'}
 ];
 const respond=(route,value,status=200)=>route.fulfill({
  status,contentType:'application/json',body:JSON.stringify(value)
 });
 await page.route('**/api/v1/assignments?*',r=>respond(r,{items:tasks.map(x=>({...x}))}));
 await page.route('**/api/v1/sources',r=>respond(r,{connections:[],userSnapshots:[]}));
 await page.route('**/api/v1/assignments/dependencies',r=>{
  const request=r.request(),method=request.method();
  if(method==='GET')return respond(r,{revision:graphRevision,edges:[...edges]});
  if(method==='PUT'){
   const data=request.postDataJSON();
   const target=tasks.find(x=>x.id===data.taskId);
   if(data.graphRevision!==graphRevision||data.taskRevision!==target?.revision)
    return respond(r,{error:'graph_revision_conflict'},409);
   edges=edges.filter(e=>e.taskId!==data.taskId)
    .concat(data.prerequisiteIds.map(prerequisiteId=>({
      taskId:data.taskId,prerequisiteId
    })));
   graphRevision++;target.revision++;writes++;
   return respond(r,{item:{taskId:data.taskId,graphRevision}});
  }
  return respond(r,{error:'unexpected_request'},405);
 });
 await page.route('**/api/v1/assignments/cloud-*',r=>{
  const request=r.request(),id=new URL(request.url()).pathname.split('/').pop();
  const target=tasks.find(x=>x.id===id);
  if(!target)return respond(r,{error:'not_found'},404);
  const data=request.postDataJSON();
  if(data.revision!==target.revision)return respond(r,{error:'revision_conflict'},409);
  if(data.localStatus==='done'&&edges.some(e=>
    e.taskId===id&&tasks.find(x=>x.id===e.prerequisiteId).local_status!=='done'))
   return respond(r,{error:'prerequisites_incomplete'},409);
  const changed=(data.title!==undefined&&target.title!==data.title)||
   (data.localStatus!==undefined&&target.local_status!==data.localStatus);
  if(data.title!==undefined)target.title=data.title;
  if(data.localStatus!==undefined)target.local_status=data.localStatus;
  if(changed)target.revision++;
  return respond(r,{item:{id,revision:target.revision}});
 });
 await page.goto('/#tasks');
 await page.evaluate(()=>window.XiaoxuCloudTasks.connect());
 await page.locator('[data-filter="ready"]').click();
 await expect(page.locator('.xu-cloud-task')).toHaveCount(2);
 await page.locator('[data-action="cloud-edit:cloud-b"]').click();
 await expect(page.locator('fieldset.xu-task-prerequisites')).toBeVisible();
 await page.locator('input[name="prerequisiteId"][value="cloud-a"]').check();
 await page.locator('#modalForm button[type="submit"]').click();
 await expect(page.locator('#editor')).not.toBeVisible();
 expect(writes).toBe(1);
 await expect(page.locator('.xu-cloud-task')).toHaveCount(1);
 await expect(page.locator('.xu-cloud-task')).toContainText('先整理讲义');
 await page.locator('[data-filter="waiting"]').click();
 await expect(page.locator('.xu-cloud-task')).toContainText('再绘制三视图');
 await expect(page.locator('.xu-cloud-task')).toContainText('等待：先整理讲义');
 await expect(page.locator('[data-action="cloud-toggle:cloud-b:done"]')).toHaveCount(0);
 await page.locator('[data-filter="ready"]').click();
 await page.locator('[data-action="cloud-toggle:cloud-a:done"]').click();
 await expect(page.locator('.xu-cloud-task')).toContainText('再绘制三视图');
 expect(graphRevision).toBe(1);
});
