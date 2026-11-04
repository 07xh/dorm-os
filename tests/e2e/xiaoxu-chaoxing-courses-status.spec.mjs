import {test,expect} from '@playwright/test';

// Synthetic upstream data checks UI wiring only. No claim of a real owner login.
test('verified owner reads courses then official homework statuses in one click',async({page})=>{
 const requests=[];
 await page.route('**/api/v1/connections',route=>route.fulfill({
  status:200,contentType:'application/json',body:JSON.stringify({
   schema:'xiaoxu/connections-v1',
   platforms:['ncwu','chaoxing','second_class'].map(id=>({
    id,status:id==='chaoxing'?'connected':'not_connected',
    sessionState:id==='chaoxing'?'active':'not_connected',
    accountAuthorization:id==='chaoxing'?'verified_session':'not_verified',
    loginMode:id==='chaoxing'?'password':'unavailable',
    trustedKinds:[],importedKinds:[]
   }))
  })
 }));
 await page.route('**/api/v1/verified/**',route=>route.fulfill({
  status:200,contentType:'application/json',body:'{"status":"not_connected","records":[]}'
 }));
 await page.route('**/api/v1/second-class',route=>route.fulfill({
  status:200,contentType:'application/json',body:'{"status":"not_connected","records":[]}'
 }));
 await page.route('**/api/v1/chaoxing-homework?**',route=>{
  const q=new URL(route.request().url()).searchParams;
  requests.push(q.get('view')+':'+(q.get('courseId')||''));
  const data=q.get('view')==='courses'?{courses:[
   {courseId:'123',classId:'456',cpi:'789',title:'Python 基础'},
   {courseId:'321',classId:'654',cpi:'987',title:'工程制图'}
  ]}:q.get('courseId')==='123'?{
   verifiedEmpty:false,items:[
    {workId:'111',title:'循环练习',status:'已提交',statusCode:'submitted',platformVerified:true},
    {workId:'222',title:'列表作业',status:'未提交',statusCode:'todo',platformVerified:true}
   ]}:{verifiedEmpty:false,items:[
    {workId:'333',title:'三视图练习',status:'待公布',statusCode:'unknown',platformVerified:true}
   ]};
  return route.fulfill({status:200,contentType:'application/json',
   body:JSON.stringify({schema:'xiaoxu/chaoxing-work-browser-v1',view:q.get('view'),
    live:true,data})});
 });
 await page.goto('/#settings');
 await page.locator('[data-action="academic-connect"]').click();
 await page.locator('nav button[data-page="tasks"]').first().click();
 const host=page.locator('#xuChaoxingWorkHost');
 await host.locator('summary').first().click();
 await host.locator('[data-xu-work-courses]').click();
 await expect(host).toContainText('Python 基础');
 await expect(host).toContainText('循环练习');
 await expect(host).toContainText('官方显示：已提交 / 待批阅');
 await expect(host).toContainText('官方显示：未提交');
 expect(requests.slice(0,2)).toEqual(['courses:','list:123']);
 await host.locator('[data-xu-work-select]').selectOption('321:654:987');
 await expect(host).toContainText('三视图练习');
 await expect(host).toContainText('官方状态未知');
 await expect(host).not.toContainText('循环练习');
 expect(requests).toContain('list:321');
});
