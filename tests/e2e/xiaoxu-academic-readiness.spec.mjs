import {test,expect} from '@playwright/test';

// Public source reachability stays in the production release monitor,
// not in a competing school-login UI.
test('settings has one real provider connection center, without the redundant probe',async({page})=>{
 let calls=0;
 await page.route('**/api/academic/status',route=>{
  calls++;return route.fulfill({status:200,contentType:'application/json',body:'{}'});
 });
 await page.goto('/#settings');
 await expect(page.getByRole('region',{name:'统一数据连接中心'})).toBeVisible();
 await expect(page.locator('.xu-connection-source')).toHaveCount(3);
 await expect(page.locator('.x-v84-source-health')).toHaveCount(0);
 await expect(page.locator('[data-action="check-academic-sources"]')).toHaveCount(0);
 await expect(page.locator('text=安全与功能边界')).toHaveCount(0);
 expect(calls).toBe(0);
});

test('legacy task import is still usable without a duplicate source-status section',async({page})=>{
 await page.goto('/#settings');
 await page.locator('.xu-settings-more > summary').click();
 const legacy=page.locator('.xu-advanced-tools:not(.xu-settings-more)').filter({hasText:'导入旧任务'});
 await legacy.locator('summary').click();
 await expect(legacy.locator('[data-action="import-task-batch"]')).toBeVisible();
 await expect(page.locator('.task-connection-dot')).toHaveCount(0);
 await expect(page.locator('#app')).not.toContainText('尚未实现');
});
