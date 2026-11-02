import { test, expect } from '@playwright/test';

test('V7 uses one top-only navigation on desktop and mobile', async ({page})=>{
 await page.goto('/');
 const nav=page.locator('#sidebarNav');
 await expect(nav).toBeVisible();
 await expect(nav.locator('button')).toHaveCount(5);
 await expect(page.locator('.sidebar,.mobile-tabs')).toHaveCount(0);
 for(const id of ['dashboard','timetable','academic','tasks','profile'])
  await expect(nav.locator('[data-page="'+id+'"]')).toHaveCount(1);
 await nav.locator('[data-page="timetable"]').click();
 await expect(page).toHaveURL(/#timetable$/);
 await expect(nav.locator('[data-page="timetable"]')).toHaveAttribute('aria-current','page');
 await nav.locator('[data-page="academic"]').click();
 await expect(page).toHaveURL(/#academic$/);
 await expect(page.locator('.x-v7-secondary [role="tab"]')).toHaveCount(5);
 await page.locator('[data-academic-tab="grades"]').click();
 await expect(page.locator('#app')).toContainText('尚未连接华水青果本人账号');
 await page.locator('[data-academic-tab="second"]').click();
 await expect(page.locator('#app')).toContainText('第二课堂');
 await page.locator('[data-academic-tab="notices"]').click();
 await expect(page.locator('#app a[href="https://www2.ncwu.edu.cn/jiaowu/jwdt/tzgg.htm"]')).toBeVisible();
 await page.goto('/#tasks');
 await expect(nav.locator('[data-page="tasks"]')).toHaveAttribute('aria-current','page');
 await page.goto('/#nfc');
 await expect(nav.locator('[data-page="profile"]')).toHaveAttribute('aria-current','page');
 const overflow=await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth);
 expect(overflow).toBeLessThanOrEqual(2);
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
 // An unrelated render (for example weather loading) must not collapse an active menu.
 await page.evaluate(()=>render());
 await expect(tag.locator('[data-tag-delete]')).toBeVisible();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(2);
});
