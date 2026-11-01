import { test, expect } from '@playwright/test';

test('desktop and mobile navigation use the same five line icons',async({page})=>{
 await page.goto('/');
 for(const [index,name] of ['home','study','nfc','life','profile'].entries()){
  await expect(page.locator('#sidebarNav button').nth(index).locator('svg use')).toHaveAttribute('href','#dicon-'+name);
  await expect(page.locator('.mobile-tabs button').nth(index).locator('svg use')).toHaveAttribute('href','#dicon-'+name);
 }
 await expect(page.locator('.dorm-icon-sprite symbol')).toHaveCount(7);
});

test('NFC on a 360px screen has scan status, clear controls and large tag actions',async({page})=>{
 await page.setViewportSize({width:360,height:780});
 await page.goto('/#nfc');
 await expect(page.locator('[data-action="nfc-start"]')).toBeVisible();
 await expect(page.locator('[data-action="nfc-stop"]')).toBeHidden();
 await expect(page.locator('[data-action="nfc-cancel-write"]')).toBeHidden();
 await expect(page.locator('.v44-nfc-live')).toBeVisible();
 await page.locator('[data-action="tag-add"]').click();
 await page.locator('#modalForm [name="name"]').fill('学习标签');
 await page.locator('#modalForm [type="submit"]').click();
 const tag=page.locator('.tag-entry');
 for(const selector of ['[data-tag-write]','[data-tag-edit]','.v41-tag-more summary']){
  const rect=await tag.locator(selector).boundingBox();
  expect(rect).not.toBeNull();
  expect(rect.height).toBeGreaterThanOrEqual(44);
 }
 await tag.locator('.v41-tag-more summary').click();
 await expect(tag.locator('[data-tag-delete]')).toBeVisible();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(2);
});

test('360px timetable day and week remain usable',async({page})=>{
 await page.setViewportSize({width:360,height:780});
 await page.goto('/#timetable');
 await page.locator('#quickAdd').click();
 await page.locator('#classSubject').fill('高等数学');
 await page.locator('#modalForm [name="day"]').selectOption('1');
 await page.locator('#modalForm [type="submit"]').click();
 await page.locator('[data-timetable-mode="day"]').click();
 await page.locator('[data-timetable-day="1"]').click();
 for(const btn of await page.locator('[data-timetable-day]').all()){
  const rect=await btn.boundingBox();
  expect(rect.height).toBeGreaterThanOrEqual(44);
 }
 const edit=await page.locator('.v42-day-content [data-class-edit]').boundingBox();
 expect(edit.height).toBeGreaterThanOrEqual(44);
 await page.locator('[data-timetable-mode="week"]').click();
 await expect(page.locator('.v42-week-grid .v42-day-card')).toHaveCount(7);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(2);
});
