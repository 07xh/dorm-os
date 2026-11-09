import {test,expect} from '@playwright/test';

const baseTask=(id,title,prerequisiteIds=[],done=false)=>({
 id,title,category:'学习',source:'manual',sourceId:'',sourceStatus:'unknown',
 course:'',due:'',dueAt:'',done,prerequisiteIds,createdAt:'2026-11-09T00:00:00Z'
});

test('NextTodo graph reuses the existing local editor and preserves dependency backup across reload',async({page})=>{
 const issues=[];page.on('pageerror',e=>issues.push(e.message));
 const tasks=[
  baseTask('A','整理讲义'),baseTask('B','绘制主视图',['A']),
  baseTask('C','核对尺寸',['A','B']),baseTask('D','完成的旧任务',[],true)
 ];
 await page.addInitScript(items=>localStorage.setItem('dorm-os-state-v1',
  JSON.stringify({version:1,settings:{campus:'jianghuai',theme:'light'},
   tasks:items,classes:[]})),tasks);
 await page.goto('/#task-graph');
 await expect(page.locator('[data-filter="graph"]')).toHaveAttribute('aria-pressed','true');
 await expect(page.locator('[data-task-graph-view]')).toBeVisible();
 await expect(page.locator('.xu-graph-node')).toHaveCount(3);
 await expect(page.locator('.xu-graph-line')).toHaveCount(2);
 await expect(page.locator('.xu-graph-node')).toContainText(['整理讲义','绘制主视图','核对尺寸']);
 await page.locator('[data-xu-graph-node="local:B"]').click();
 await expect(page.locator('.xu-graph-chain')).toContainText('前置 1 项，后续 1 项');
 await expect(page.locator('[data-xu-graph-node="local:B"]')).toHaveAttribute('aria-pressed','true');
 await page.locator('[data-xu-graph-edit="local:C"]').click();
 await expect(page.locator('#modalForm #taskTitle')).toHaveValue('核对尺寸');
 await expect(page.locator('#modalForm input[value="A"][name="prerequisiteId"]')).toBeChecked();
 await expect(page.locator('#modalForm input[value="B"][name="prerequisiteId"]')).toBeChecked();
 await page.locator('#modalForm input[value="A"][name="prerequisiteId"]').uncheck();
 await page.locator('#modalForm button[type="submit"]').click();
 await expect(page.locator('#editor')).not.toBeVisible();
 expect(await page.evaluate(()=>JSON.parse(localStorage.getItem('dorm-os-state-v1'))
  .tasks.find(t=>t.id==='C').prerequisiteIds)).toEqual(['B']);
 await page.locator('[data-xu-graph-done]').click();
 await expect(page.locator('.xu-graph-node')).toHaveCount(4);
 await expect(page.locator('.xu-graph-node.is-done')).toContainText('完成的旧任务');
 await page.reload();
 await expect(page.locator('[data-task-graph-view]')).toBeVisible();
 await expect(page.locator('.xu-graph-line')).toHaveCount(2);
 await page.locator('[data-filter="all"]').click();
 await expect(page).toHaveURL(/#tasks$/);
 await expect(page.locator('[data-task-list-view]')).toBeVisible();
 expect(issues).toEqual([]);
});

test('cloud task dependency graph reads the same synced graph and opens the existing cloud editor',async({page})=>{
 const tasks=[
  {id:'cloud-a',title:'云端前置任务',kind:'manual',local_status:'todo',
   revision:0,due_precision:'unknown',due_local_date:null,source_provider:'manual'},
  {id:'cloud-b',title:'云端后续任务',kind:'manual',local_status:'todo',
   revision:1,due_precision:'unknown',due_local_date:null,source_provider:'manual'}
 ];
 await page.route('**/api/v1/assignments?*',route=>route.fulfill({
  status:200,contentType:'application/json',body:JSON.stringify({items:tasks})
 }));
 await page.route('**/api/v1/sources',route=>route.fulfill({
  status:200,contentType:'application/json',body:JSON.stringify({connections:[],userSnapshots:[]})
 }));
 await page.route('**/api/v1/assignments/dependencies',route=>route.fulfill({
  status:200,contentType:'application/json',
  body:JSON.stringify({revision:1,edges:[{taskId:'cloud-b',prerequisiteId:'cloud-a'}]})
 }));
 await page.goto('/#task-graph');
 await page.evaluate(()=>window.XiaoxuCloudTasks.connect());
 // The graph is entirely derived from task client state and never fetches a second store.
 await page.locator('[data-filter="all"]').click();
 await page.locator('[data-filter="graph"]').click();
 await expect(page.locator('.xu-graph-node')).toHaveCount(2);
 await expect(page.locator('.xu-graph-line')).toHaveCount(1);
 await expect(page.locator('.xu-graph-node.is-waiting')).toContainText('云端后续任务');
 await expect(page.locator('.xu-graph-node').first()).toContainText('私人云端');
 await page.locator('[data-xu-graph-edit="cloud:cloud-b"]').click();
 await expect(page.locator('#modalForm #taskTitle')).toHaveValue('云端后续任务');
 await expect(page.locator('#modalForm input[name="prerequisiteId"][value="cloud-a"]')).toBeChecked();
});

test('missing cloud dependency endpoint fails closed on graph but does not hide the normal task ledger',async({page})=>{
 const task={id:'cloud-z',title:'历史个人云端任务',kind:'manual',
  local_status:'todo',revision:0,due_precision:'unknown',source_provider:'manual'};
 await page.route('**/api/v1/assignments?*',route=>route.fulfill({
  status:200,contentType:'application/json',body:JSON.stringify({items:[task]})
 }));
 await page.route('**/api/v1/sources',route=>route.fulfill({
  status:200,contentType:'application/json',body:'{"connections":[],"userSnapshots":[]}'
 }));
 let graphUnavailable=true;
 await page.route('**/api/v1/assignments/dependencies',route=>route.fulfill(
  graphUnavailable?
   {status:404,contentType:'application/json',body:'{"error":"not_found"}'}:
   {status:200,contentType:'application/json',body:'{"revision":0,"edges":[]}'}
 ));
 await page.goto('/#tasks');
 await page.evaluate(()=>window.XiaoxuCloudTasks.connect());
 await page.locator('[data-filter="graph"]').click();
 await expect(page.locator('[data-task-graph-view]')).toContainText('暂不展示云端任务');
 await expect(page.locator('.xu-graph-node')).toHaveCount(0);
 await page.locator('[data-filter="all"]').click();
 await expect(page.locator('#app')).toContainText('历史个人云端任务');
 await expect(page.locator('.xu-cloud-task')).toContainText('云端依赖待同步');
 await expect(page.locator('.xu-cloud-task [data-action^="cloud-toggle:"]')).toHaveCount(0);
 await page.locator('[data-filter="ready"]').click();
 await expect(page.locator('.xu-cloud-task')).toHaveCount(0);
 await page.locator('[data-filter="open"]').click();
 await expect(page.locator('.xu-cloud-task')).toContainText('历史个人云端任务');
 // When the real dependency endpoint recovers, re-fetch before treating the
 // cloud task as actionable. The old outage status must not remain stuck.
 graphUnavailable=false;
 await page.evaluate(()=>window.XiaoxuCloudTasks.refresh());
 await page.locator('[data-filter="ready"]').click();
 await expect(page.locator('.xu-cloud-task')).toContainText('历史个人云端任务');
 await expect(page.locator('.xu-cloud-task [data-action^="cloud-toggle:"]')).toHaveCount(1);
 await page.locator('[data-filter="graph"]').click();
 await expect(page.locator('.xu-graph-node')).toHaveCount(1);
 await expect(page.locator('[data-task-graph-view]')).not.toContainText('暂不展示云端任务');
});

test('cloud sync receipt uses authenticated fetch only and does not expose private titles',async({page})=>{
 const personal=[{id:'owner-sample-01',title:'仅供当前测试的私密标题',
  kind:'manual',revision:2,local_status:'todo',source_provider:'manual',
  due_precision:'unknown',due_local_date:null}];
 let reads=0;
 await page.route('**/api/v1/assignments?*',route=>{
  reads++;return route.fulfill({status:200,contentType:'application/json',
   body:JSON.stringify({items:personal})});
 });
 await page.route('**/api/v1/sources',r=>r.fulfill({
  status:200,contentType:'application/json',body:'{"connections":[],"userSnapshots":[]}'
 }));
 await page.route('**/api/v1/assignments/dependencies',r=>r.fulfill({
  status:200,contentType:'application/json',body:'{"revision":7,"edges":[]}'
 }));
 await page.goto('/#tasks');
 await page.locator('.xu-task-tools summary').click();
 await page.locator('[data-action="cloud-sync-evidence"]').click();
 await expect(page.locator('#editor')).not.toBeVisible();
 await page.evaluate(()=>window.XiaoxuCloudTasks.connect());
 await page.locator('[data-action="cloud-sync-evidence"]').click();
 await expect(page.locator('#editor')).toBeVisible();
 await expect(page.locator('#editor')).toContainText('核对码');
 await expect(page.locator('#editor')).toContainText('个人任务 1 项');
 await expect(page.locator('#editor')).toContainText('图修订 7');
 await expect(page.locator('#editor')).not.toContainText('仅供当前测试的私密标题');
 expect(reads).toBeGreaterThanOrEqual(2,'receipt must re-fetch real API, never only local snapshot');
 await page.locator('#editor [data-action="close-modal"]').click();
 await expect(page.locator('#editor')).not.toBeVisible();
});
