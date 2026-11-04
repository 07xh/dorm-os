import {test,expect} from '@playwright/test';

const state='A'.repeat(43);

test('Android connection link opens one source, scrubs nonce and survives SPA reload',async({page})=>{
 await page.goto('/#settings?xu_app_source=chaoxing&xu_app_state='+state);
 await expect(page).toHaveURL(/#settings$/);
 const banner=page.locator('#xuAndroidSchoolReturn');
 await expect(banner).toBeVisible();
 await expect(banner).toContainText('学习通');
 await expect(banner).toContainText('返回后 App 将核验实际数据');
 await page.reload();
 await expect(page).toHaveURL(/#settings$/);
 await expect(page.locator('#xuAndroidSchoolReturn')).toBeVisible();
 await page.getByRole('button',{name:'关闭引导'}).click();
 await expect(page.locator('#xuAndroidSchoolReturn')).toHaveCount(0);
 await page.reload();
 await expect(page.locator('#xuAndroidSchoolReturn')).toHaveCount(0);
});

test('unknown provider and malformed return state do not create a return control',async({page})=>{
 await page.goto('/#settings?xu_app_source=evil&xu_app_state='+state);
 await expect(page).toHaveURL(/#settings$/);
 await expect(page.locator('#xuAndroidSchoolReturn')).toHaveCount(0);
 await page.goto('/#settings?xu_app_source=ncwu&xu_app_state=short');
 await expect(page).toHaveURL(/#settings$/);
 await expect(page.locator('#xuAndroidSchoolReturn')).toHaveCount(0);
});
