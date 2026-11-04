import { test, expect } from '@playwright/test';

const overview = {
 schema: 'xiaoxu/connections-v1',
 platforms: ['ncwu', 'chaoxing', 'second_class'].map(id => ({
  id, status: 'not_connected', trustedKinds: [], importedKinds: [],
  sessionState: 'not_connected', accountAuthorization: 'not_verified',
  loginMode: id === 'ncwu' ? 'password' : 'unavailable'
 }))
};
const json = (route, body) => route.fulfill({
 status: 200, contentType: 'application/json', body: JSON.stringify(body)
});

test('我的页面保留原入口，设置页不重复说明', async ({ page }) => {
 await page.goto('/#profile');
 const profile = page.locator('#app');
 await expect(profile.locator('.v4-profile-avatar')).toHaveCount(0);
 await expect(profile).toContainText('账户与设备');
 await expect(profile).not.toContainText('原有个人功能（教务整合后再联动）');
 await expect(profile.locator('[data-owner-duration-toggle]')).toHaveCount(1);
 await profile.locator('button[data-page="settings"]').click();
 const center = page.getByRole('region', { name: '统一数据连接中心' });
 await expect(center).toBeVisible();
 await expect(center.locator('.xu-connection-source')).toHaveCount(3);
 await expect(page.locator('#app')).not.toContainText('PREFERENCES & DATA');
 await expect(center.locator('.xu-connection-intro')).toHaveCount(0);
 await expect(page.locator('#app .xu-settings-more')).toHaveCount(1);
});

test('连接表单仅在已验证的适配器下按需展开，保持手机端操作尺寸', async ({ page }) => {
 await page.route('**/api/v1/connections', route => json(route, overview));
 await page.route('**/api/v1/verified/**', route => json(route, { status:'not_connected', records: [] }));
 await page.route('**/api/v1/second-class', route => json(route, { status:'not_connected', records: [] }));
 await page.goto('/#settings');
 const center = page.getByRole('region', { name: '统一数据连接中心' });
 await center.getByRole('button', { name: '读取我的连接状态' }).click();
 const school = center.locator('.xu-connection-source').first();
 await expect(school.locator('form[data-xu-school-login="ncwu"]')).toBeHidden();
 await school.locator('.xu-school-manage > summary').click();
 await expect(school.locator('form[data-xu-school-login="ncwu"]')).toBeVisible();
 await expect(center.locator('form[data-xu-school-login="second_class"]')).toHaveCount(0);
 await expect(center.locator('form[data-xu-school-login="chaoxing"]')).toHaveCount(0);
 for (const width of [320,393,768]) {
  await page.setViewportSize({ width, height: 850 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(2);
  const summary = await school.locator('.xu-school-manage > summary').boundingBox();
  expect(summary?.height).toBeGreaterThanOrEqual(40);
 }
});
