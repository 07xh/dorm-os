import {test,expect} from '@playwright/test';

// The desktop overview projects pre-existing state; it does not authenticate,
// fetch personal/school data on mount or invent any school records.
test('desktop overview distinguishes local records from unread private sources',async({page})=>{
 const apiCalls=[];
 page.on('request',req=>{
  if(new URL(req.url()).pathname.startsWith('/api/v1/'))apiCalls.push(new URL(req.url()).pathname);
 });
 await page.goto('/#dashboard');
 const panel=page.getByRole('region',{name:'桌面工作台数据概览'});
 if(page.viewportSize().width<1180){
  await expect(panel).toBeHidden();
  return;
 }
 await expect(panel).toBeVisible();
 await expect(panel.locator('.xu-console-stat')).toHaveCount(4);
 await expect(panel.locator('[data-xu-console-source="cloud"] .xu-console-value')).toHaveText('未读取');
 await expect(panel.locator('[data-xu-console-source="school"] .xu-console-value')).toHaveText('尚未核验');
 await expect(panel.locator('[data-xu-console-source="review"] .xu-console-value')).toHaveText('未登录');
 await expect(panel).toContainText('不会读取或上传 Android 本机课表');
 await page.evaluate(()=>{
  state.tasks.push({id:'local-console-test',title:'仅本浏览器记录',due:'',category:'其他',done:false});
  persist();
  render();
 });
 await expect(panel.locator('[data-xu-console-source="browser"] .xu-console-value')).toContainText('1');
 await expect(panel.locator('[data-xu-console-source="cloud"] .xu-console-value')).toHaveText('未读取');
 expect(apiCalls).toEqual([]);
 await panel.getByRole('link',{name:'前往消息审核'}).click();
 await expect(page).toHaveURL(/#review$/);
 await expect(page.locator('[data-task-review-view]')).toBeVisible();
 await page.goto('/#dashboard');
 await page.getByRole('region',{name:'桌面工作台数据概览'})
  .getByRole('link',{name:'检查数据连接'}).click();
 await expect(page).toHaveURL(/#settings$/);
 await expect(page.getByRole('region',{name:'统一数据连接中心'})).toBeVisible();
});

test('console overview does not appear on a phone or overwrite existing tasks',async({page})=>{
 await page.setViewportSize({width:393,height:852});
 await page.goto('/#dashboard');
 await expect(page.getByRole('region',{name:'桌面工作台数据概览'})).toBeHidden();
 await expect(page.locator('#sidebarNav button[data-page]')).toHaveCount(5);
 await page.locator('#sidebarNav [data-page="tasks"]').click();
 await expect(page.locator('[data-task-list-view]')).toBeVisible();
});
