import { test, expect } from '@playwright/test';

test('V8 approved visual shell preserves existing navigation and real-data dashboard', async ({ page }) => {
  await page.goto('/#dashboard');
  await expect(page.locator('link[href="/xiaoxu-workspace.css"]')).toHaveCount(1);
  await expect(page.locator('#sidebarNav [data-page]')).toHaveCount(5);
  await expect(page.locator('.x-v8-stat')).toHaveCount(0);
  await expect(page.locator('.x-v7-home-grid')).toBeVisible();
  await expect(page.locator('.x-v8-connection')).toHaveCount(0);
  await expect(page.locator('.x-v7-home-notices')).toBeVisible();

  await page.evaluate(() => {
    const day = now().day;
    state.classes = [{ id: 'v8-course', subject: 'V8真实本地课程', day, start: '08:00', end: '11:00', location: 'B301' }];
    state.tasks = [{ id: 'v8-task', title: 'V8真实本地任务', due: '', category: '其他', done: false }];
    persist();
    render();
  });
  await expect(page.locator('[data-home-widget="timetable"]')).toContainText('1 节');
  await expect(page.locator('[data-home-widget="tasks"]')).toContainText('1 项');
  await expect(page.locator('[data-home-widget="timetable"]')).toContainText('V8真实本地课程');
  await page.locator('[data-home-widget="timetable"] [data-page="timetable"]').first().click();
  await expect(page).toHaveURL(/#timetable$/);
  await page.locator('#sidebarNav [data-page="academic"]').click();
  await page.locator('[data-academic-tab="grades"]').click();
  await expect(page.locator('#app')).toContainText('尚未连接本人账号');
});

test('V8 theme toggle works, remains stored and mobile layout does not overflow', async ({ page }) => {
  await page.goto('/');
  const before = await page.locator('html').evaluate(el => el.classList.contains('theme-light'));
  await page.locator('[data-action="theme-v8"]').click();
  await expect.poll(() => page.locator('html').evaluate(el => el.classList.contains('theme-light'))).toBe(!before);
  await page.reload();
  await expect.poll(() => page.locator('html').evaluate(el => el.classList.contains('theme-light'))).toBe(!before);
  for(const width of [360,393,768,1280]) {
    await page.setViewportSize({width,height:850});
    await expect(page.locator('.x-v8-stat')).toHaveCount(0);
    const overflow=await page.evaluate(()=>document.documentElement.scrollWidth-window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(2);
  }
  await page.setViewportSize({width:393,height:850});
  await page.locator('#sidebarNav [data-page="tasks"]').click();
  await expect(page).toHaveURL(/#tasks$/);
});
