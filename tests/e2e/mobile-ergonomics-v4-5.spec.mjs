import {test,expect} from '@playwright/test';

async function addTag(page,name='床头 NFC'){
 await page.locator('[data-action="tag-add"]').click();
 await page.locator('#modalForm [name="name"]').fill(name);
 await page.locator('#modalForm [type="submit"]').click();
 await expect(page.locator('.tag-entry')).toContainText(name);
}
async function parkTriggerAboveBottomNav(page,trigger){
 await trigger.evaluate(el=>{
  const nav=document.querySelector('.mobile-tabs').getBoundingClientRect();
  const rect=el.getBoundingClientRect();
  window.scrollBy(0,rect.bottom-(nav.top-12));
 });
}
async function expectMenuAboveTriggerAndNav(page,menu,trigger){
 const nav=await page.locator('.mobile-tabs').boundingBox();
 const menuBox=await menu.boundingBox();
 const triggerBox=await trigger.boundingBox();
 expect(nav).not.toBeNull();
 expect(menuBox).not.toBeNull();
 expect(triggerBox).not.toBeNull();
 expect(menuBox.x).toBeGreaterThanOrEqual(0);
 expect(menuBox.x+menuBox.width).toBeLessThanOrEqual(361);
 expect(menuBox.y).toBeGreaterThanOrEqual(0);
 expect(menuBox.y+menuBox.height).toBeLessThanOrEqual(triggerBox.y-2);
 expect(menuBox.y+menuBox.height).toBeLessThan(nav.y-2);
}

test('mobile navigation is singular and scroll/viewport reserves the bottom safe area',async({page})=>{
 await page.setViewportSize({width:360,height:780});
 await page.goto('/');
 await expect(page.locator('#sidebarNav')).toBeHidden();
 await expect(page.locator('.mobile-tabs')).toBeVisible();
 const metrics=await page.evaluate(()=>({
  scrollBehavior:getComputedStyle(document.documentElement).scrollBehavior,
  bottomPadding:parseFloat(getComputedStyle(document.querySelector('.workspace main')).paddingBottom),
  width:document.documentElement.scrollWidth-innerWidth
 }));
 expect(metrics.scrollBehavior).toBe('auto');
 expect(metrics.bottomPadding).toBeGreaterThanOrEqual(110);
 expect(metrics.width).toBeLessThanOrEqual(2);
});

test('NFC overflow menu opens above the tag and bottom navigation',async({page})=>{
 await page.setViewportSize({width:360,height:780});
 await page.goto('/#nfc');
 await addTag(page);
 const entry=page.locator('.tag-entry').last();
 const trigger=entry.locator('.v41-tag-more summary');
 await page.evaluate(()=>window.scrollTo(0,document.documentElement.scrollHeight));
 await parkTriggerAboveBottomNav(page,trigger);
 await trigger.click();
 const menu=entry.locator('.v41-tag-menu');
 await expect(menu).toBeVisible();
 await expectMenuAboveTriggerAndNav(page,menu,trigger);
 for(const button of await menu.locator('button').all()){
  const box=await button.boundingBox();
  expect(box.height).toBeGreaterThanOrEqual(44);
 }
});

test('timetable row menu opens above its course and remains tappable',async({page})=>{
 await page.setViewportSize({width:360,height:780});
 await page.goto('/#timetable');
 const day=await page.evaluate(()=>{
  const name=new Intl.DateTimeFormat('en-US',{timeZone:'Asia/Shanghai',weekday:'short'}).format(new Date());
  return ({Sun:0,Mon:1,Tue:2,Wed:3,Thu:4,Fri:5,Sat:6})[name];
 });
 await page.locator('#quickAdd').click();
 await page.locator('#classSubject').fill('移动端菜单验收');
 await page.locator('#modalForm [name="day"]').selectOption(String(day));
 await page.locator('#modalForm [type="submit"]').click();
 await page.locator('[data-timetable-mode="day"]').click();
 await page.locator('[data-timetable-day="'+day+'"]').click();
 const trigger=page.locator('.v42-day-content .v42-row-more summary').last();
 await page.evaluate(()=>window.scrollTo(0,document.documentElement.scrollHeight));
 await parkTriggerAboveBottomNav(page,trigger);
 await trigger.click();
 const menu=page.locator('.v42-day-content .v42-row-menu').last();
 await expect(menu).toBeVisible();
 await expectMenuAboveTriggerAndNav(page,menu,trigger);
 const deleteButton=menu.locator('button');
 expect((await deleteButton.boundingBox()).height).toBeGreaterThanOrEqual(44);
});

test('life overflow menu opens upward and toast stays above the bottom tabs',async({page})=>{
 await page.setViewportSize({width:360,height:780});
 await page.goto('/#life');
 await page.locator('[data-action="reminder-add"]').click();
 await page.locator('#modalForm [name="text"]').fill('移动端操作提示');
 await page.locator('#modalForm [type="submit"]').click();
 const toast=page.locator('#toast');
 await expect(toast).toHaveClass(/show/);
 const toastBox=await toast.boundingBox();
 const navBox=await page.locator('.mobile-tabs').boundingBox();
 expect(toastBox.y).toBeGreaterThanOrEqual(0);
 expect(toastBox.y+toastBox.height).toBeLessThan(navBox.y-2);
 const trigger=page.locator('[data-life-more]').last();
 await page.evaluate(()=>window.scrollTo(0,document.documentElement.scrollHeight));
 await parkTriggerAboveBottomNav(page,trigger);
 await trigger.click();
 const menu=page.locator('.v42-life-more.is-open .v42-menu');
 await expect(menu).toBeVisible();
 await expectMenuAboveTriggerAndNav(page,menu,trigger);
 const deleteBox=await menu.locator('button').boundingBox();
 expect(deleteBox.height).toBeGreaterThanOrEqual(44);
});
