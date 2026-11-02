import {test,expect} from '@playwright/test';

test('360px V7 top-only header keeps content inside viewport',async({page})=>{
 await page.setViewportSize({width:360,height:780});
 await page.goto('/');
 await expect(page.locator('#sidebarNav')).toBeVisible();
 await expect(page.locator('.sidebar,.mobile-tabs')).toHaveCount(0);
 const m=await page.evaluate(()=>{
   const nav=document.querySelector('.x-v7-header'), main=document.querySelector('.workspace main');
   return {
     position:getComputedStyle(nav).position, top:nav.getBoundingClientRect().top,
     bottomPadding:parseFloat(getComputedStyle(main).paddingBottom),
     width:document.documentElement.scrollWidth-innerWidth
   };
 });
 expect(m.position).toBe('sticky');
 expect(m.top).toBeGreaterThanOrEqual(0);
 expect(m.bottomPadding).toBeGreaterThanOrEqual(40);
 expect(m.width).toBeLessThanOrEqual(2);
 await page.locator('#sidebarNav [data-page="academic"]').click();
 for(const id of ['notices','grades','exams','second','services'])
   await expect(page.locator('[data-academic-tab="'+id+'"]')).toBeVisible();
});

test('legacy NFC tag controls remain usable from direct links on a narrow phone',async({page})=>{
 await page.setViewportSize({width:360,height:780});
 await page.goto('/#nfc');
 await page.locator('[data-action="tag-add"]').click();
 await page.locator('#modalForm [name="name"]').fill('旧版 NFC 标签');
 await page.locator('#modalForm [type="submit"]').click();
 const entry=page.locator('.tag-entry').last();
 await entry.locator('.v41-tag-more summary').click();
 await expect(entry.locator('[data-tag-delete]')).toBeVisible();
 for(const button of await entry.locator('.v41-tag-menu button').all()){
  const rect=await button.boundingBox();
  expect(rect.height).toBeGreaterThanOrEqual(44);
 }
 expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(2);
});

test('course edit overflow remains tappable without a bottom nav',async({page})=>{
 await page.setViewportSize({width:360,height:780});
 await page.goto('/#timetable');
 await page.locator('#quickAdd').click();
 await page.locator('#classSubject').fill('手机端课程菜单验收');
 await page.locator('#modalForm [name="day"]').selectOption('1');
 await page.locator('#modalForm [type="submit"]').click();
 await page.locator('[data-timetable-mode="day"]').click();
 await page.locator('[data-timetable-day="1"]').click();
 const row=page.locator('.v42-day-content .v42-row-more').last();
 await row.locator('summary').click();
 await expect(row.locator('.v42-row-menu button')).toBeVisible();
 expect((await row.locator('.v42-row-menu button').boundingBox()).height).toBeGreaterThanOrEqual(44);
});

test('legacy life reminder toast and menu remain accessible',async({page})=>{
 await page.setViewportSize({width:360,height:780});
 await page.goto('/#life');
 await page.locator('[data-action="reminder-add"]').click();
 await page.locator('#modalForm [name="text"]').fill('提醒回归');
 await page.locator('#modalForm [type="submit"]').click();
 await expect(page.locator('#toast')).toContainText('已');
 const trigger=page.locator('[data-life-more]').last();
 await trigger.click();
 await expect(page.locator('.v42-life-more.is-open .v42-menu')).toBeVisible();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(2);
});
