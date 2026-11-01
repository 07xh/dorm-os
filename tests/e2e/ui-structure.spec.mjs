import { test, expect } from '@playwright/test';

test('main navigation exposes five distinct personal areas on mobile and desktop', async ({page}) => {
 await page.goto('/');
 for(const selector of ['.main-nav','.mobile-tabs']){
  const nav=page.locator(selector);
  await expect(nav.locator('button')).toHaveCount(5);
  await expect(nav.locator('[data-page="study"]')).toHaveCount(1);
  await expect(nav.locator('[data-page="nfc"]')).toHaveCount(1);
  await expect(nav.locator('[data-page="life"]')).toHaveCount(1);
 }
 const primary=page.locator('.mobile-tabs:visible button, .main-nav:visible button');
 await primary.filter({hasText:'学习'}).click();
 await expect(page).toHaveURL(/#study$/);
 await expect(page.locator('#app')).toContainText('学习中心');
 await expect(primary.filter({hasText:'学习'})).toHaveAttribute('aria-current','page');
 await page.goto('/#timetable');
 await expect(primary.filter({hasText:'学习'})).toHaveAttribute('aria-current','page');
 await page.goto('/#tasks');
 await expect(primary.filter({hasText:'学习'})).toHaveAttribute('aria-current','page');
 await page.goto('/#nfc');
 await expect(primary.filter({hasText:'NFC'})).toHaveAttribute('aria-current','page');
 await page.goto('/#life');
 await expect(primary.filter({hasText:'生活'})).toHaveAttribute('aria-current','page');
 expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(2);
});

test('study hub retains direct links to editable timetable, tasks and campus service', async ({page})=>{
 await page.goto('/#study');
 await expect(page.locator('.v41-study-grid .v41-study-panel')).toHaveCount(2);
 await page.locator('.v41-more-link[data-page="timetable"]').click();
 await expect(page).toHaveURL(/#timetable$/);
 await page.goto('/#study');
 await page.locator('.v41-more-link[data-page="tasks"]').click();
 await expect(page).toHaveURL(/#tasks$/);
 await page.goto('/#study');
 await page.locator('.v41-more-link[data-page="campus"]').click();
 await expect(page).toHaveURL(/#campus$/);
});

test('NFC first screen prioritizes scan and puts destructive actions behind More', async ({page})=>{
 await page.goto('/#nfc');
 await expect(page.locator('.v41-nfc-scan')).toBeVisible();
 await expect(page.locator('.v41-nfc-list [data-action="tag-add"]')).toBeVisible();
 await expect(page.locator('#quickAdd')).toBeHidden();
 await page.locator('[data-action="tag-add"]').click();
 await page.locator('#modalForm [name="name"]').fill('书桌标签');
 await page.locator('#modalForm [type="submit"]').click();
 const tag=page.locator('.tag-entry');
 await expect(tag.locator('[data-tag-write]')).toBeVisible();
 await expect(tag.locator('[data-tag-edit]')).toBeVisible();
 await expect(tag.locator('[data-tag-delete]')).toBeHidden();
 await tag.locator('.v41-tag-more summary').click();
 await expect(tag.locator('[data-tag-delete]')).toBeVisible();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(2);
});
