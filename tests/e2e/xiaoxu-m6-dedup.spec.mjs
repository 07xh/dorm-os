import {test,expect} from '@playwright/test';

test('same-source task displays once across personal import, cloud ledger and snapshot',async({page})=>{
 const local={
   id:'local-work-2',title:'本地导入的学习通任务',category:'高等数学',
   due:'2026-11-20',dueAt:'',done:false,
   source:'chaoxing',sourceId:'work:2',sourceStatus:'todo',course:'高等数学'
 };
 await page.addInitScript(task=>{
  localStorage.setItem('dorm-os-state-v1',JSON.stringify({
   version:1,settings:{campus:'jianghuai',theme:'light'},tasks:[task],classes:[]
  }));
 },local);
 const cloud=[
  {id:'cloud-1',title:'同名作业',kind:'platform',revision:0,
   local_status:'todo',due_precision:'date',due_local_date:'2026-11-20',
   source_provider:'chaoxing',source_external_id:'work:1',platform_status:'todo'},
  {id:'cloud-2',title:'本地导入的学习通任务',kind:'platform',revision:0,
   local_status:'todo',due_precision:'date',due_local_date:'2026-11-20',
   source_provider:'chaoxing',source_external_id:'work:2',platform_status:'todo'}
 ];
 const snapshot=[
  {externalId:'work:1',title:'同名作业',course:'高等数学',platformStatus:'todo'},
  {externalId:'work:2',title:'本地导入的学习通任务',course:'高等数学',platformStatus:'todo'},
  {externalId:'work:other',title:'同名作业',course:'高等数学',platformStatus:'todo'}
 ];
 await page.route('**/api/v1/verified/**',route=>{
  const kind=route.request().url().split('/').pop();
  return route.fulfill({status:200,contentType:'application/json',
   body:JSON.stringify(kind==='connections'?{status:'ready',sources:[]}:
    {kind,status:kind==='assignments'?'ready':'not_connected',
      records:kind==='assignments'?snapshot:[],lastSuccessAt:'2026-11-03T00:00:00Z'})});
 });
 await page.route('**/api/v1/assignments?*',route=>route.fulfill({
  status:200,contentType:'application/json',body:JSON.stringify({items:cloud})
 }));
 await page.route('**/api/v1/sources',route=>route.fulfill({
  status:200,contentType:'application/json',body:JSON.stringify({connections:[],userSnapshots:[]})
 }));
 await page.route('**/api/v1/reminders',route=>route.fulfill({
  status:200,contentType:'application/json',body:JSON.stringify({
   delivery:'in_app_only',items:[],unread:0,preferences:{
    enabled:false,examLeadDays:7,taskLeadDays:3
   },source:{exams:'not_connected'}
  })
 }));
 await page.goto('/#settings');
 await page.locator('[data-action="academic-connect"]').click();
 await expect(page.locator('.xu-cloud-panel')).toContainText('已载入 2 项');
 await page.goto('/#tasks');
 await expect(page.locator('.xu-cloud-task')).toHaveCount(1);
 await expect(page.locator('.xu-private-task')).toHaveCount(1);
 await expect(page.locator('.xu-private-task')).toContainText('同名作业');
 await expect(page.locator('.xu-cloud-task')).toContainText('同名作业');
 await expect(page.locator('#app')).toContainText('本地导入的学习通任务');
 expect(await page.locator('.xu-cloud-task .entry-title,.xu-private-task .entry-title').allTextContents())
  .toEqual(['同名作业','同名作业'],'same title but different stable IDs remain separate');
 const existing=await page.evaluate(()=>JSON.parse(localStorage.getItem('dorm-os-state-v1')).tasks);
 expect(existing).toHaveLength(1);
 expect(existing[0].sourceId).toBe('work:2');
});
