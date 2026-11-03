import {test,expect} from '@playwright/test';
const localTask={id:'local-1',title:'手绘工程制图',category:'工程制图',
 due:'2026-11-23',dueAt:'',done:false,source:'manual',sourceId:'',
 sourceStatus:'unknown',course:'',createdAt:''};
test('cloud tasks stay opt-in, migration previews before commit, and platform state remains separate',async({page})=>{
 const calls=[];
 let cloud=[{id:'cloud-1',title:'Python 平台练习',kind:'platform',
   due_precision:'date',due_local_date:'2026-11-20',due_at_utc:null,
   local_status:'todo',platform_status:'submitted',source_course:'Python',
   revision:0,legacy_id:null}];
 await page.addInitScript(task=>localStorage.setItem('dorm-os-state-v1',JSON.stringify({
   version:1,settings:{campus:'jianghuai',theme:'light'},tasks:[task],classes:[]
 })),localTask);
 await page.route('**/api/v1/connections',route=>route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({schema:'xiaoxu/connections-v1',platforms:[{id:'ncwu',status:'not_connected'},{id:'chaoxing',status:'not_connected'},{id:'second_class',status:'not_connected'}]})}));
 await page.route('**/api/v1/verified/**',route=>{
   const kind=route.request().url().split('/').pop();
   return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(
    kind==='connections'?{status:'ready',sources:[]}:
    {kind,status:'not_connected',records:[],lastSuccessAt:null}
   )});
 });
 await page.route('**/api/v1/sources',route=>route.fulfill({status:200,
   contentType:'application/json',body:JSON.stringify({connections:[],userSnapshots:[]})}));
 await page.route('**/api/v1/assignments?*',route=>{
   calls.push('list');
   return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({items:cloud})});
 });
 await page.route('**/api/v1/assignments/legacy-import',route=>{
   const body=route.request().postDataJSON();
   calls.push(body.mode);
   expect(body.tasks[0].id).toBe('local-1');
   expect(body.tasks[0].title).toBe('手绘工程制图');
   return route.fulfill({status:body.mode==='commit'?201:200,
    contentType:'application/json',body:JSON.stringify({
      mode:body.mode,total:1,toCreate:1,unchanged:0,conflicts:[],
      created:body.mode==='commit'?1:0
    })});
 });
 await page.route('**/api/v1/assignments/cloud-1',route=>{
   const body=route.request().postDataJSON();
   expect(route.request().method()).toBe('PATCH');
   expect(body).toEqual({revision:0,localStatus:'done'});
   calls.push('patch');
   cloud=[{...cloud[0],local_status:'done',revision:1}];
   return route.fulfill({status:200,contentType:'application/json',
     body:JSON.stringify({item:{id:'cloud-1',revision:1}})});
 });
 await page.goto('/#settings');
 await page.locator('.xu-advanced-tools').filter({hasText:'云端任务、提醒与旧数据迁移'}).locator('summary').first().click();
 await expect(page.locator('.xu-cloud-panel')).toContainText('未连接');
 expect(calls).toEqual([],'no private cloud requests before explicit authorization');
 await page.locator('[data-action="academic-connect"]').click();
 await expect(page.locator('.xu-cloud-panel')).toContainText('已载入 1 项');
 await expect(page.locator('[data-action="cloud-commit"]')).toHaveCount(0);
 await page.locator('[data-action="cloud-preview"]').click();
 await expect(page.locator('.xu-cloud-panel')).toContainText('新增 1 项');
 expect(calls).toContain('preview');
 page.once('dialog',d=>d.accept());
 await page.locator('[data-action="cloud-commit"]').click();
 await expect(page.locator('.xu-cloud-panel')).not.toContainText('确认上传到私人云端');
 expect(calls).toContain('commit');
 const preserved=await page.evaluate(()=>JSON.parse(localStorage.getItem('dorm-os-state-v1')).tasks);
 expect(preserved).toHaveLength(1);
 expect(preserved[0].done).toBe(false);
 await page.goto('/#tasks');
 await expect(page.locator('.xu-cloud-task')).toContainText('Python 平台练习');
 await expect(page.locator('.xu-cloud-task')).toContainText('平台已提交');
 await expect(page.locator('.xu-cloud-task')).toContainText('时间未知');
 await page.locator('.xu-cloud-task [data-action^="cloud-toggle:"]').click();
 await expect(page.locator('.xu-cloud-task')).toContainText('恢复待办');
 await expect(page.locator('.xu-cloud-task')).toContainText('平台已提交');
 expect(calls).toContain('patch');
 await page.reload();
 await expect(page.locator('.xu-cloud-task')).toHaveCount(0);
 await expect(page.locator('#app')).toContainText('手绘工程制图');
});


test('mobile resume refreshes a previously authorized cloud session without changing local tasks',async({page})=>{
 await page.setViewportSize({width:390,height:844});
 let remote=[{id:'cloud-shared',title:'跨设备同步任务',kind:'manual',
  due_precision:'date',due_local_date:'2026-11-25',due_at_utc:null,
  local_status:'todo',platform_status:null,source_course:null,
  revision:0,legacy_id:null}];
 let reads=0;
 await page.route('**/api/v1/connections',route=>route.fulfill({status:200,
  contentType:'application/json',body:JSON.stringify({schema:'xiaoxu/connections-v1',
   platforms:[{id:'ncwu',status:'not_connected'},{id:'chaoxing',status:'not_connected'},
    {id:'second_class',status:'not_connected'}]})}));
 await page.route('**/api/v1/verified/**',route=>route.fulfill({status:200,
  contentType:'application/json',body:JSON.stringify({status:'not_connected',
   records:[],lastSuccessAt:null})}));
 await page.route('**/api/v1/second-class',route=>route.fulfill({status:200,
  contentType:'application/json',body:JSON.stringify({status:'not_connected',
   records:[],lastSuccessAt:null})}));
 await page.route('**/api/v1/sources',route=>route.fulfill({status:200,
  contentType:'application/json',body:JSON.stringify({connections:[],userSnapshots:[]})}));
 await page.route('**/api/v1/assignments?*',route=>{
  reads++;
  return route.fulfill({status:200,contentType:'application/json',
   body:JSON.stringify({items:remote})});
 });
 await page.goto('/#settings');
 await expect(page.locator('.xu-cloud-panel')).toContainText('未连接');
 expect(reads).toBe(0,'no automatic private-data requests before owner opts in');
 await page.locator('[data-action="academic-connect"]').click();
 await expect(page.locator('.xu-cloud-panel')).toContainText('已载入 1 项');
 await page.locator('#sidebarNav [data-page="tasks"]').click();
 await expect(page.locator('.xu-cloud-task')).toContainText('标记完成');
 remote=[{...remote[0],local_status:'done',revision:1}]; // second device changed D1
 await page.evaluate(()=>window.dispatchEvent(new Event('online'))); // mobile network resume
 await expect(page.locator('.xu-cloud-task')).toContainText('恢复待办');
 expect(reads).toBeGreaterThanOrEqual(2);
 const overflow=await page.evaluate(()=>document.documentElement.scrollWidth-window.innerWidth);
 expect(overflow).toBeLessThanOrEqual(1);
});
