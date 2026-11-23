import {test, expect} from '@playwright/test';

test('校序导航顺序与桌面/移动布局一致', async ({page}, testInfo) => {
  await page.goto('/');
  const nav = page.locator('#sidebarNav');
  await expect(nav).toHaveCount(1);
  const routes = await nav.locator('[data-page]').evaluateAll(nodes => nodes.map(el => el.dataset.page));
  expect(routes).toEqual(['dashboard','timetable','academic','tasks','profile']);
  const first = nav.locator('[data-page="dashboard"]');
  await first.focus();
  await page.keyboard.press('Tab');
  await expect(nav.locator('[data-page="timetable"]')).toBeFocused();

  if (testInfo.project.name === 'desktop-chromium') {
    const boxes = await page.evaluate(() => {
      const nav = document.querySelector('#sidebarNav').getBoundingClientRect();
      const main = document.querySelector('#app').getBoundingClientRect();
      return {navRight:nav.right, mainLeft:main.left,
        position:getComputedStyle(document.querySelector('#sidebarNav')).position};
    });
    expect(boxes.position).toBe('sticky');
    expect(boxes.navRight).toBeLessThan(boxes.mainLeft);
    await expect(nav.locator('[data-console-route="review"]')).toBeVisible();
    await expect(nav.locator('[data-console-route="settings"]')).toBeVisible();
  } else {
    await expect(nav.locator('[data-console-route="review"]')).toBeHidden();
    await expect(nav.locator('[data-console-route="settings"]')).toBeHidden();
    const bounds = await nav.evaluate(el => {
      const r = el.getBoundingClientRect();
      return {bottom:r.bottom, left:r.left, right:r.right, width:window.innerWidth,
        height:window.innerHeight, position:getComputedStyle(el).position};
    });
    expect(bounds.position).toBe('fixed');
    expect(bounds.left).toBeGreaterThanOrEqual(0);
    expect(bounds.right).toBeLessThanOrEqual(bounds.width);
    expect(bounds.bottom).toBeLessThanOrEqual(bounds.height);
    await expect(nav.locator('[data-page="profile"]')).toBeVisible();
  }
});
