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

test('mobile tasks filter includes school sources while review stays a separate workspace',async({page})=>{
 await page.setViewportSize({width:393,height:852});
 await page.goto('/');
 await page.evaluate(()=>{
  state.tasks=[
   {id:'ncwu-mobile',title:'青果课业检查',source:'ncwu',done:false},
   {id:'second-mobile',title:'第二课堂活动',source:'second_class',done:false}
  ];
  render();
 });
 await page.locator('#sidebarNav [data-page="tasks"]').click();
 const source=page.locator('[data-task-source]');
 await expect(source.locator('option[value="ncwu"]')).toHaveCount(1);
 await expect(source.locator('option[value="second_class"]')).toHaveCount(1);
 await source.selectOption('ncwu');
 await expect(page.locator('.v4-task-list')).toContainText('青果课业检查');
 await expect(page.locator('.v4-task-list')).not.toContainText('第二课堂活动');
 await source.selectOption('second_class');
 await expect(page.locator('.v4-task-list')).toContainText('第二课堂活动');
 await expect(page.locator('.v4-task-list')).not.toContainText('青果课业检查');
 await page.locator('[data-filter="review"]').click();
 await expect(page.locator('[data-task-source]')).toBeHidden();
 await expect(page.locator('.v4-export')).toBeHidden();
 await expect(page.locator('[data-task-list-view]')).toBeHidden();
 await expect(page.locator('[data-task-review-view]')).toBeVisible();
 await expect(page.locator('#xuWechatTasksHost')).toBeVisible();
 await expect(page.locator('#xuChaoxingInboxHost')).toBeVisible();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(2);
});
