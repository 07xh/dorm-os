import {test,expect} from '@playwright/test';
const capturedAt=new Date().toISOString();
test('private academic UI is unconnected by default',async({page})=>{
 await page.goto('/#settings');
 await expect(page.locator('[data-action="academic-connect"]')).toBeVisible();
 await expect(page.locator('.xu-private-panel')).toContainText('尚未授权');
 await page.goto('/#academic');
 await page.locator('[data-academic-tab="grades"]').click();
 await expect(page.locator('[role="tabpanel"]')).toContainText('未连接青果成绩查询');
});
test('private academic client displays verified five-domain response without persisting token',async({page})=>{
 await page.route('**/api/v1/verified/**',async route=>{
  const kind=route.request().url().split('/').pop();
  const data={
   schedule:[{externalId:'course1',term:'2026-2027-1',title:'已核验测试课程',weekday:1,startSection:3,endSection:4,weeks:[2,4],location:'C202'}],
   assignments:[{externalId:'work:1:2',title:'已授权的测试作业',course:'高数',platformStatus:'todo',dueAt:'2026-11-20T23:59:00+08:00'}],
   grades:[{externalId:'score1',title:'课程成绩测试项',term:'2026-2027-1',score:'95.5',credit:2}],
   exams:[{externalId:'exam1',title:'期末考试测试项',term:'2026-2027-1',date:'2026-12-20',location:'A101'}],
   'second-class':[]
  };
  const body=kind==='connections'?{status:'ready',sources:[]}:
   {kind,status:'ready',records:data[kind]||[],lastSuccessAt:capturedAt};
  await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(body)});
 });
 await page.goto('/#settings');
 await page.locator('[data-action="academic-connect"]').click();
 await expect(page.locator('.xu-private-panel')).toContainText('本页已加载本人数据');
 await page.goto('/#timetable');
 await expect(page.locator('.xu-private-view')).toContainText('已核验测试课程');
 await expect(page.locator('.xu-private-view')).toContainText('第3–4节');
 await page.goto('/#tasks');
 await expect(page.locator('.xu-private-task')).toContainText('已授权的测试作业');
 await expect(page.locator('.xu-private-task input[type=checkbox]')).toHaveCount(0);
 await page.goto('/#academic');
 await page.locator('[data-academic-tab="grades"]').click();
 await expect(page.locator('[role="tabpanel"]')).toContainText('95.5');
 await page.locator('[data-academic-tab="exams"]').click();
 await expect(page.locator('[role="tabpanel"]')).toContainText('2026-12-20');
 await page.reload();
 await page.goto('/#settings');
 await expect(page.locator('.xu-private-panel')).toContainText('尚未授权');
});
test('unconfigured private service fails closed on mobile',async({page})=>{
 await page.route('**/api/v1/verified/**',route=>route.fulfill({
  status:503,contentType:'application/json',body:JSON.stringify({
    status:'auth_not_configured',message:'Cloudflare Access 尚未配置'
  })
 }));
 await page.goto('/#settings');
 await page.locator('[data-action="academic-connect"]').click();
 await expect(page.locator('.xu-private-panel')).toContainText('尚未授权');
 await expect(page.locator('.xu-private-panel')).toContainText('Cloudflare Access 尚未配置');
 for(const width of [360,393,768,1280]){
  await page.setViewportSize({width,height:850});
  const overflow=await page.evaluate(()=>document.documentElement.scrollWidth-window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(2);
 }
});
