import { test, expect } from '@playwright/test';

test('a new visitor does not fetch private session endpoints on page load', async ({ page }) => {
  const paths = [];
  await page.route('**/api/v1/**', async route => {
    paths.push(new URL(route.request().url()).pathname);
    await route.fulfill({status:401, contentType:'application/json', body:'{"authenticated":false}'});
  });
  await page.goto('/');
  await expect(page.locator('[data-owner-session-link]')).toHaveText('云端登录');
  expect(paths).toEqual([]);
});

test('login page is compact on phones and password visibility remains accessible', async ({ page }) => {
  await page.goto('/login/');
  await expect(page.getByRole('heading', {name:'登录'})).toBeVisible();
  await expect(page.locator('main')).not.toContainText('Cloudflare Pages Secrets');
  const size = await page.locator('main').boundingBox();
  expect(size?.height).toBeLessThan(440);
  const dims=await page.evaluate(()=>({
    documentWidth:document.documentElement.scrollWidth,
    viewportWidth:document.documentElement.clientWidth,
    minLength:document.getElementById('passphrase').minLength
  }));
  expect(dims.documentWidth).toBeLessThanOrEqual(dims.viewportWidth+1);
  expect(dims.minLength).toBe(8);
  await page.getByRole('button',{name:'显示密码'}).click();
  await expect(page.locator('#passphrase')).toHaveAttribute('type','text');
  await page.getByRole('button',{name:'隐藏密码'}).click();
  await expect(page.locator('#passphrase')).toHaveAttribute('type','password');
});

test('owner login is remembered for display and logout clears the opt-in marker', async ({ page }) => {
  let signedIn = false;
  await page.route('**/api/v1/auth/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/login')) {
      signedIn = true;
      return route.fulfill({status:200, contentType:'application/json', body:'{"authenticated":true}'});
    }
    if (path.endsWith('/logout')) {
      signedIn = false;
      return route.fulfill({status:200, contentType:'application/json', body:'{"authenticated":false}'});
    }
    return route.fulfill({
      status:signedIn ? 200 : 401, contentType:'application/json',
      body:JSON.stringify({authenticated:signedIn}),
    });
  });
  await page.goto('/login/');
  await page.locator('#passphrase').fill('unit-test-only-passphrase-strong');
  await page.getByRole('button', {name:'安全登录'}).click();
  await expect(page).toHaveURL(/127\.0\.0\.1:4173\/$/);
  await expect(page.locator('[data-owner-session-link]')).toHaveText('云端已连接');
  expect(await page.evaluate(() => localStorage.getItem('xiaoxu_owner_login_seen'))).toBe('1');

  await page.locator('[data-owner-session-link]').click();
  await expect(page.locator('#form')).toBeHidden();
  await page.getByRole('button',{name:'退出当前设备'}).click();
  await expect(page.locator('#form')).toBeVisible();
  expect(await page.evaluate(() => localStorage.getItem('xiaoxu_owner_login_seen'))).toBeNull();
});
