import {test,expect} from '@playwright/test';
async function enablePrivateData(page){
 await page.route('**/api/v1/connections',route=>route.fulfill({
  status:200,contentType:'application/json',body:JSON.stringify({
   schema:'xiaoxu/connections-v1',platforms:['ncwu','chaoxing','second_class']
    .map(id=>({id,status:'not_connected',trustedKinds:[],importedKinds:[]}))
  })
 }));
 await page.route('**/api/v1/verified/**',route=>route.fulfill({
  status:200,contentType:'application/json',
  body:JSON.stringify({status:'not_connected',records:[],lastSuccessAt:null})
 }));
 await page.route('**/api/v1/second-class',route=>route.fulfill({
  status:200,contentType:'application/json',
  body:JSON.stringify({status:'not_connected',records:[],lastSuccessAt:null})
 }));
}
test('schema check is private, on-demand and within the same source center',async({page})=>{
 const healthCalls=[];
 await enablePrivateData(page);
 await page.route('**/api/v1/health',route=>{
  healthCalls.push(route.request().url());
  return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({
    status:'database_ready',databaseReady:true,loginAuthenticated:true,
    missingTables:[],missingColumns:[],schoolConnection:'not_verified',automaticSync:false
  })});
 });
 await page.goto('/#settings');
 const center=page.getByRole('region',{name:'统一数据连接中心'});
 await expect(center.locator('.xu-connection-source')).toHaveCount(3);
 await expect(center).toContainText('校序云端未读取');
 expect(healthCalls).toHaveLength(0);
 await center.locator('.xu-connection-diagnostics summary').click();
 await expect(center).toContainText('请先使用上方按钮读取本人连接状态');
 await page.locator('[data-action="academic-connect"]').click();
 await expect(center).toContainText('校序云端已读取');
 expect(healthCalls).toHaveLength(0);
 await expect(center.locator('.xu-connection-diagnostics')).toHaveAttribute('open','');
 await center.getByRole('button',{name:'只读检查数据库'}).click();
 await expect(center).toContainText('校序数据库结构完整');
 await expect(center).toContainText('当前校序登录已通过');
 await expect(center.locator('.xu-connection-diagnostics')).toHaveAttribute('open','');
 expect(healthCalls).toHaveLength(1,'readiness is not polled or background fetched');
 await expect(center).toContainText('学校账号授权是两回事');
 for(const width of [360,393,768,1280]){
  await page.setViewportSize({width,height:850});
  const excess=await page.evaluate(()=>document.documentElement.scrollWidth-window.innerWidth);
  expect(excess,'viewport '+width).toBeLessThanOrEqual(2);
 }
});
test('missing migration is visible without treating it as school connection',async({page})=>{
 await enablePrivateData(page);
 await page.route('**/api/v1/health',route=>route.fulfill({
  status:503,contentType:'application/json',body:JSON.stringify({
   status:'database_schema_incomplete',databaseReady:false,
   missingTables:['academic_auth_sessions'],missingColumns:['academic_verified_batches.provenance'],
   schoolConnection:'not_verified'
  })
 }));
 await page.goto('/#settings');
 const center=page.getByRole('region',{name:'统一数据连接中心'});
 await center.getByRole('button',{name:'读取我的连接状态'}).click();
 await center.locator('.xu-connection-diagnostics summary').click();
 await center.getByRole('button',{name:'只读检查数据库'}).click();
 await expect(center).toContainText('数据库结构不完整');
 await expect(center).toContainText('academic_auth_sessions');
 await expect(center).toContainText('academic_verified_batches.provenance');
 await expect(center).toContainText('未连接');
});

test('unavailable D1 is not misreported as a missing migration',async({page})=>{
 await enablePrivateData(page);
 await page.route('**/api/v1/health',route=>route.fulfill({
  status:503,contentType:'application/json',body:JSON.stringify({
   status:'database_unavailable',databaseReady:false
  })
 }));
 await page.goto('/#settings');
 const center=page.getByRole('region',{name:'统一数据连接中心'});
 await center.getByRole('button',{name:'读取我的连接状态'}).click();
 await center.locator('.xu-connection-diagnostics summary').click();
 await center.getByRole('button',{name:'只读检查数据库'}).click();
 await expect(center).toContainText('校序数据服务暂不可用，暂时无法确认数据库结构');
 await expect(center).not.toContainText('数据库结构不完整');
 await expect(center.locator('.xu-connection-source')).toHaveCount(3);
});
