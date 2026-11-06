import {test,expect} from '@playwright/test';

// Uses existing route and data state. No synthetic school courses or login flow.
test('desktop gets workspace sidebar; mobile retains five-button navigation',async({page})=>{
 await page.goto('/#dashboard');
 const sidebar=page.locator('#sidebarNav');
 const review=sidebar.locator('a[href="#review"]');
 const connection=sidebar.locator('a[href="#settings"]');
 const desktop=page.viewportSize().width>=1180;
 if(desktop){
  await expect(page.locator('.xu-webnav-heading')).toBeVisible();
  await expect(sidebar.locator('button[data-page]')).toHaveCount(5);
  await expect(sidebar).toHaveCSS('position','fixed');
  await expect(review).toBeVisible();
  await expect(connection).toBeVisible();
  const navBox=await sidebar.boundingBox(),mainBox=await page.locator('main#app').boundingBox();
  expect(navBox).toBeTruthy();
  expect(mainBox).toBeTruthy();
  expect(mainBox.x).toBeGreaterThan(navBox.x+navBox.width-4);
  await review.click();
  await expect(page).toHaveURL(/#review$/);
  await expect(review).toHaveAttribute('data-active','true');
  await expect(page.locator('[data-task-review-view]')).toBeVisible();
  await connection.click();
  await expect(page).toHaveURL(/#settings$/);
  await expect(sidebar.locator('[data-page="profile"]')).toHaveAttribute('aria-current','page');
 }else{
  await expect(page.locator('.xu-webnav-heading')).toBeHidden();
  await expect(sidebar.locator('button[data-page]')).toHaveCount(5);
  await expect(review).toBeHidden();
  await expect(connection).toBeHidden();
  await expect(sidebar.locator('[data-page="timetable"]')).toBeVisible();
  await sidebar.locator('[data-page="tasks"]').click();
  await expect(page).toHaveURL(/#tasks$/);
 }
 // All legacy pages remain reachable independently of the visible navigation.
 await page.goto('/#nfc');
 await expect(page.locator('#sidebarNav [data-page="profile"]')).toHaveAttribute('aria-current','page');
});
