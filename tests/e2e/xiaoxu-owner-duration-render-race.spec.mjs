import {test,expect} from '@playwright/test';

test('duration preference stays open across a profile rerender from auth state updates',async({page})=>{
 await page.goto('/');
 await page.evaluate(()=>window.setPage('profile'));
 const toggle=page.locator('#app [data-owner-duration-toggle]');
 const panel=page.locator('#app [data-owner-duration-panel]');
 await toggle.click();
 await expect(panel).toBeVisible();
 await expect(toggle).toHaveAttribute('aria-expanded','true');
 // An async session verification can emit an auth-state event while choosing
 // a login duration. The profile is rendered again, but the open panel and
 // keyboard target must survive that unrelated refresh.
 await page.evaluate(()=>window.dispatchEvent(
  new CustomEvent('xiaoxu-owner-auth-changed',{detail:{authenticated:false}})
 ));
 await expect(panel).toBeVisible();
 await expect(toggle).toHaveAttribute('aria-expanded','true');
 await expect(toggle).toBeFocused();
 await page.locator('#app [data-owner-duration-choice="1"]').click();
 await expect(page.locator('#app [data-owner-duration-value]')).toHaveText('1 天');
 expect(await page.evaluate(()=>localStorage.getItem('xiaoxu_owner_duration_days'))).toBe('1');
 await toggle.click();
 await page.keyboard.press('Escape');
 await expect(panel).toBeHidden();
 await expect(toggle).toBeFocused();
});

test('async profile rerender does not spuriously open closed duration picker',async({page})=>{
 await page.goto('/');
 await page.evaluate(()=>window.setPage('profile'));
 await expect(page.locator('#app [data-owner-duration-panel]')).toBeHidden();
 await page.evaluate(()=>window.dispatchEvent(
  new CustomEvent('xiaoxu-owner-auth-changed',{detail:{authenticated:false}})
 ));
 await expect(page.locator('#app [data-owner-duration-panel]')).toBeHidden();
 await expect(page.locator('#app [data-owner-duration-toggle]')).toHaveAttribute('aria-expanded','false');
});
