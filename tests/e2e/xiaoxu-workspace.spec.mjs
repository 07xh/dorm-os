import {test,expect} from '@playwright/test';
test('overview and task list share counts, include date-specific courses and keep review separate',async({page})=>{
 await page.goto('/');
 await page.evaluate(()=>{
  const date=now().date;
  state.tasks=[{id:'ui-one',title:'保留的真实本地任务',category:'其他',done:false,due:date}];
  state.calendarEvents=[{id:'dated',date,start:'10:00',end:'11:40',subject:'按日期导入课程',location:'B301'}];
  render();
 });
 await expect(page.locator('[data-home-widget="timetable"]')).toContainText('按日期导入课程');
 await expect(page.locator('[data-home-widget="tasks"]')).toContainText('1 项');
 await expect(page.locator('.x-v8-stat')).toHaveCount(0);
 await page.locator('#sidebarNav [data-page="tasks"]').click();
 await expect(page.locator('[data-workspace-count]')).toHaveText('1 项任务');
 await page.locator('[data-filter="review"]').click();
 await expect(page.locator('#xuWechatTasksHost')).toBeVisible();
 await expect(page.locator('.v4-task-list')).toBeHidden();
 await page.locator('[data-filter="all"]').click();
 await page.locator('[data-task-toggle="ui-one"]').check();
 await expect(page.locator('[data-task-toggle="ui-one"]')).toBeChecked();
 await expect(page.locator('#xuWechatTasksHost')).toBeHidden();
});
test('current shell replaces obsolete UI layers and keeps touch targets at phone widths',async({page})=>{
 await page.goto('/');
 await expect(page.locator('link[href="/xiaoxu-workspace.css"]')).toHaveCount(1);
 await expect(page.locator('link[href*="xiaoxu-v8"],link[href*="xiaoxu-v9"],script[src*="xiaoxu-v9"]')).toHaveCount(0);
 for(const width of [320,393,768,1365]){
  await page.setViewportSize({width,height:900});
  expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(2);
 }
});
