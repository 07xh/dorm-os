import {test,expect} from '@playwright/test';

// Web-only review navigation must reuse the existing task and human-review flow.
// No school/WeChat account, private API mock or example data is required.
test('bookmarkable review route survives reload and returns to ordinary tasks',async({page})=>{
 const privateReads=[];
 page.on('request',req=>{
  const path=new URL(req.url()).pathname;
  if(path.startsWith('/api/v1/wechat/'))privateReads.push(path);
 });
 await page.goto('/#review');
 await expect(page.locator('[data-filter="review"]')).toHaveAttribute('aria-pressed','true');
 await expect(page.locator('[data-task-review-view]')).toBeVisible();
 await expect(page.locator('[data-task-list-view]')).toBeHidden();
 await expect(page.locator('#sidebarNav [data-page="tasks"]')).toHaveAttribute('aria-current','page');

 await page.reload();
 await expect(page.locator('[data-filter="review"]')).toHaveAttribute('aria-pressed','true');
 await expect(page.locator('[data-task-review-view]')).toBeVisible();
 expect(privateReads).toEqual([]); // no verified owner session, no private candidate fetch

 await page.locator('[data-filter="all"]').click();
 await expect(page).toHaveURL(/#tasks$/);
 await expect(page.locator('[data-task-list-view]')).toBeVisible();
 await expect(page.locator('[data-task-review-view]')).toBeHidden();
 await page.locator('#sidebarNav [data-page="dashboard"]').click();
 await expect(page.locator('[data-home-widget="tasks"] [data-page="review"]')).toBeVisible();
 await page.locator('[data-home-widget="tasks"] [data-page="review"]').click();
 await expect(page).toHaveURL(/#review$/);
 await expect(page.locator('[data-task-review-view]')).toBeVisible();
});
