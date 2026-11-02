import {test,expect} from '@playwright/test';

test('V8.1 academic tabs expose accessible panel and do not invent records',async({page})=>{
 await page.goto('/#academic');
 const tabs=page.locator('.x-v7-secondary [role="tab"]');
 await expect(tabs).toHaveCount(5);
 await expect(page.locator('.x-v8-academic-intro')).toContainText('校园官方通知');
 await expect(page.locator('[role="tabpanel"]')).toHaveAttribute('aria-labelledby','xu-tab-notices');
 await page.locator('[data-academic-tab="grades"]').click();
 await expect(page.locator('[role="tabpanel"]')).toHaveAttribute('aria-labelledby','xu-tab-grades');
 await expect(page.locator('[role="tabpanel"]')).toContainText('未连接青果成绩查询');
 await expect(page.locator('[role="tabpanel"]')).not.toContainText('4.0');
 await page.locator('[data-page="settings"]').first().click();
 await expect(page).toHaveURL(/#settings$/);
 await page.goto('/#academic');
 await page.locator('[data-academic-tab="grades"]').click();
 await page.locator('[data-academic-tab="grades"]').press('ArrowRight');
 await expect(page.locator('#xu-tab-exams')).toBeFocused();
 await expect(page.locator('[role="tabpanel"]')).toContainText('暂无可验证的个人考试安排');
 await page.locator('#xu-tab-exams').press('End');
 await expect(page.locator('#xu-tab-services')).toBeFocused();
 await page.locator('#xu-tab-services').press('Home');
 await expect(page.locator('#xu-tab-notices')).toBeFocused();
});

test('V8.1 academic, timetable and tasks maintain mobile no-overflow and active controls',async({page})=>{
 for(const width of [360,393,768,1280]){
  await page.setViewportSize({width,height:850});
  for(const route of ['academic','timetable','tasks']){
   await page.goto('/#'+route);
   const overflow=await page.evaluate(()=>document.documentElement.scrollWidth-window.innerWidth);
   expect(overflow,route+' width='+width).toBeLessThanOrEqual(2);
  }
 }
});

test('V8.1 preserves open menus when incoming live data re-renders the current view',async({page})=>{
 await page.goto('/#profile');
 await page.locator('.x-v7-legacy-links summary').click();
 await expect(page.locator('.x-v7-legacy-list [data-page="scenes"]')).toBeVisible();
 await page.evaluate(()=>render());
 await expect(page.locator('.x-v7-legacy-list [data-page="scenes"]')).toBeVisible();
 await page.goto('/#timetable');
 await page.evaluate(()=>{
  const day=now().day;
  state.classes=[{id:'ux-v8-class',subject:'线下绘图课',day,start:'08:00',end:'09:40',location:'B301'}];
  timetableMode='day';timetableDay=day;persist();render();
 });
 const row=page.locator('.v42-class-card').first();
 await row.locator('.v42-row-more summary').click();
 await expect(row.locator('[data-class-delete]')).toBeVisible();
 await page.evaluate(()=>render());
 await expect(row.locator('[data-class-delete]')).toBeVisible();
});
