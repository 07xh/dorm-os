import { test, expect } from '@playwright/test';

test('new visitor sees login in 我的 without private requests or duplicate header navigation', async ({ page }) => {
  const requests = [];
  await page.route('**/api/v1/**', async route => {
    requests.push(new URL(route.request().url()).pathname);
    await route.fulfill({status:401, contentType:'application/json', body:'{"authenticated":false}'});
  });
  await page.goto('/');
  expect(requests).toEqual([]);
  await expect(page.locator('[data-owner-session-link]')).toHaveCount(0);
  await page.evaluate(() => window.setPage('profile'));
  await expect(page.locator('[data-owner-auth-entry-title]')).toHaveText('登录校序');
  expect(requests).toEqual([]);
  const path = new URL(page.url()).pathname;
  await page.locator('[data-owner-auth-open]').click();
  const dialog=page.locator('#xuOwnerDialog');
  await expect(dialog).toBeVisible();
  await expect(dialog.locator('[data-owner-auth-form]')).toBeVisible();
  await expect(dialog.getByLabel('个人密码')).toHaveAttribute('minlength','8');
  expect(new URL(page.url()).pathname).toBe(path);
  await page.keyboard.press('Escape');
  await expect(dialog).toBeHidden();
});

test('login page remains a compact fallback with accessible password visibility', async ({ page }) => {
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

test('in-page login stays on 我的, server state wins and logout clears remembered marker', async ({ page }) => {
  let signedIn = false;
  const calls = [];
  await page.route('**/api/v1/auth/**', async route => {
    const pathname=new URL(route.request().url()).pathname;
    calls.push(pathname);
    if(pathname.endsWith('/login')){
      signedIn = true;
      return route.fulfill({status:200,contentType:'application/json',body:'{"authenticated":true}'});
    }
    if(pathname.endsWith('/logout')){
      signedIn = false;
      return route.fulfill({status:200,contentType:'application/json',body:'{"authenticated":false}'});
    }
    return route.fulfill({status:signedIn?200:401,contentType:'application/json',
      body:JSON.stringify({authenticated:signedIn})});
  });
  await page.goto('/');
  expect(calls).toEqual([]);
  await page.evaluate(()=>window.setPage('profile'));
  await page.locator('[data-owner-auth-open]').click();
  const dialog=page.locator('#xuOwnerDialog');
  await expect(dialog).toBeVisible();
  await dialog.locator('[data-owner-auth-password]').fill('q8r4t7y2');
  await dialog.getByRole('button',{name:'安全登录'}).click();
  await expect(dialog).toBeHidden();
  expect(new URL(page.url()).pathname).toBe('/');
  await expect(page.locator('[data-owner-auth-entry-title]')).toHaveText('云端已连接');
  expect(await page.evaluate(()=>localStorage.getItem('xiaoxu_owner_login_seen'))).toBe('1');
  await page.locator('[data-owner-auth-open]').click();
  await expect(dialog.locator('[data-owner-auth-form]')).toBeHidden();
  await dialog.getByRole('button',{name:'退出当前设备'}).click();
  await expect(dialog.locator('[data-owner-auth-form]')).toBeVisible();
  await expect(page.locator('[data-owner-auth-entry-title]')).toHaveText('登录校序');
  expect(await page.evaluate(()=>localStorage.getItem('xiaoxu_owner_login_seen'))).toBeNull();
});

test('standalone deep-link still uses the same login flow', async ({ page }) => {
  let signedIn=false;
  await page.route('**/api/v1/auth/**', async route => {
    const path=new URL(route.request().url()).pathname;
    if(path.endsWith('/login')) {
      signedIn=true;return route.fulfill({status:200,contentType:'application/json',body:'{"authenticated":true}'});
    }
    if(path.endsWith('/logout')) {
      signedIn=false;return route.fulfill({status:200,contentType:'application/json',body:'{"authenticated":false}'});
    }
    return route.fulfill({status:signedIn?200:401,contentType:'application/json',body:JSON.stringify({authenticated:signedIn})});
  });
  await page.goto('/login/');
  await page.locator('#passphrase').fill('unit-test-only-passphrase-strong');
  await page.getByRole('button',{name:'安全登录'}).click();
  await expect(page).toHaveURL(/127\.0\.0\.1:4173\/$/);
  expect(await page.evaluate(()=>localStorage.getItem('xiaoxu_owner_login_seen'))).toBe('1');
  await page.goto('/login/');
  await expect(page.locator('#form')).toBeHidden();
  await page.getByRole('button',{name:'退出当前设备'}).click();
  await expect(page.locator('#form')).toBeVisible();
  expect(await page.evaluate(()=>localStorage.getItem('xiaoxu_owner_login_seen'))).toBeNull();
});


test('custom login duration is chosen in 我的 and reaches the actual login request', async ({ page }) => {
  let submitted=null;
  await page.route('**/api/v1/auth/**', async route => {
    const path=new URL(route.request().url()).pathname;
    if(path.endsWith('/login')){
      submitted=route.request().postDataJSON();
      return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({
        authenticated:true,durationDays:submitted.durationDays,expiresAt:'2026-12-03T00:00:00.000Z'
      })});
    }
    return route.fulfill({status:401,contentType:'application/json',body:'{"authenticated":false}'});
  });
  await page.goto('/');
  await page.evaluate(()=>window.setPage('profile'));
  await expect(page.locator('[data-owner-duration-value]')).toHaveText('7 天');
  await expect(page.locator('.xu-owner-duration-pref select')).toHaveCount(0);
  await page.locator('[data-owner-duration-toggle]').click();
  await expect(page.locator('[data-owner-duration-panel]')).toBeVisible();
  await page.locator('[data-owner-duration-choice="custom"]').click();
  const custom=page.locator('[data-owner-duration-custom]');
  await expect(custom).toBeVisible();
  // A school/session refresh can redraw the current page between selecting
  // "custom" and typing. Keep the mode and expanded panel, even when the
  // previous selected value (7 days) was still one of the presets.
  await page.evaluate(()=>window.setPage('profile'));
  await expect(page.locator('[data-owner-duration-panel]')).toBeVisible();
  await expect(custom).toBeVisible();
  await expect(page.locator('[data-owner-duration-choice="custom"]'))
   .toHaveAttribute('aria-pressed','true');
  await custom.fill('12');
  await custom.dispatchEvent('change');
  expect(await page.evaluate(()=>localStorage.getItem('xiaoxu_owner_duration_days'))).toBe('12');
  await page.reload();
  await page.evaluate(()=>window.setPage('profile'));
  await expect(page.locator('[data-owner-duration-value]')).toHaveText('12 天');
  await page.locator('[data-owner-duration-toggle]').click();
  await expect(page.locator('[data-owner-duration-choice="custom"]')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('[data-owner-duration-custom]')).toHaveValue('12');
  await page.locator('[data-owner-auth-open]').click();
  const dialog=page.locator('#xuOwnerDialog');
  await expect(dialog).toBeVisible();
  await expect(dialog.locator('[data-owner-duration-summary]')).toContainText('12 天');
  await dialog.locator('[data-owner-auth-password]').fill('synthetic-test-password');
  await dialog.getByRole('button',{name:'安全登录'}).click();
  await expect(dialog).toBeHidden();
  expect(submitted.durationDays).toBe(12);
  expect(new URL(page.url()).pathname).toBe('/');
});

test('profile login link still navigates when the dialog script cannot load',async ({page})=>{
  await page.route('**/xiaoxu-owner-auth-ui.js',route=>route.abort());
  await page.goto('/');
  await page.evaluate(()=>window.setPage('profile'));
  await expect(page.locator('[data-owner-auth-open]')).toHaveAttribute('href','/login/');
  await page.locator('[data-owner-auth-open]').click();
  await expect(page).toHaveURL(/\/login\/$/);
  await expect(page.locator('#passphrase')).toBeVisible();
});

test('direct fallback can choose 30 days with the same server API',async ({page})=>{
  let days=null;
  await page.route('**/api/v1/auth/**',async route=>{
    const path=new URL(route.request().url()).pathname;
    if(path.endsWith('/login')){
      days=route.request().postDataJSON().durationDays;
      return route.fulfill({status:200,contentType:'application/json',body:'{"authenticated":true}'});
    }
    return route.fulfill({status:401,contentType:'application/json',body:'{"authenticated":false}'});
  });
  await page.goto('/login/');
  await page.locator('[data-owner-duration-select]').selectOption('30');
  await page.locator('#passphrase').fill('synthetic-test-password');
  await page.getByRole('button',{name:'安全登录'}).click();
  await expect(page).toHaveURL(/127\.0\.0\.1:4173\/$/);
  expect(days).toBe(30);
});

test('mobile duration preference is inline, keyboard dismissible and uses existing saved session value',async({page})=>{
  await page.setViewportSize({width:390,height:780});
  await page.goto('/');
  await page.evaluate(()=>window.setPage('profile'));
  const toggle=page.locator('[data-owner-duration-toggle]');
  await expect(toggle).toHaveAttribute('aria-expanded','false');
  await toggle.click();
  await expect(toggle).toHaveAttribute('aria-expanded','true');
  await expect(page.locator('[data-owner-duration-panel]')).toBeVisible();
  await page.locator('[data-owner-duration-choice="1"]').click();
  await expect(page.locator('[data-owner-duration-value]')).toHaveText('1 天');
  expect(await page.evaluate(()=>localStorage.getItem('xiaoxu_owner_duration_days'))).toBe('1');
  await expect(toggle).toHaveAttribute('aria-expanded','false');
  await toggle.click();
  await page.keyboard.press('Escape');
  await expect(toggle).toHaveAttribute('aria-expanded','false');
  const width=await page.evaluate(()=>[document.documentElement.scrollWidth,document.documentElement.clientWidth]);
  expect(width[0]).toBeLessThanOrEqual(width[1]+1);
});
