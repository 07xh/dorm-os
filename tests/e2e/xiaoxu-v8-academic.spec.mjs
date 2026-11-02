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
