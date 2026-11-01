import {test,expect} from '@playwright/test';
const STORAGE='dorm-os-state-v1';
test('NFC status filtering uses real registry records and does not alter data',async({page})=>{
 await page.goto('/#nfc');
 await page.evaluate(key=>{
  const s=JSON.parse(localStorage.getItem(key));
  s.nfcTags=[
   {id:'new1',name:'空白新标签',scene:'study'},
   {id:'waiting',name:'写入待验证',scene:'rest',writtenScene:'rest'},
   {id:'done',name:'读卡已验证',scene:'away',writtenScene:'away',readScene:'away',readAt:'2026-11-01'},
   {id:'stale',name:'改绑需重写',scene:'dev',writtenScene:'study',readScene:'study',readAt:'2026-11-01'}
  ];
  s.events.push({at:'2026-11-01T08:00:00.000Z',text:'保存 NFC 标签：空白新标签'});
  localStorage.setItem(key,JSON.stringify(s));
 },STORAGE);
 await page.reload();
 await expect(page.locator('.nfc-stat b').nth(0)).toHaveText('4');
 await expect(page.locator('.nfc-stat b').nth(1)).toHaveText('1');
 await expect(page.locator('.nfc-stat b').nth(2)).toHaveText('1');
 await expect(page.locator('.tag-entry')).toHaveCount(4);
 for(const [filter,expected] of [['rewrite','改绑需重写'],['verified','读卡已验证'],['pending','写入待验证'],['new','空白新标签']]){
  await page.locator('[data-nfc-filter="'+filter+'"]').click();
  await expect(page.locator('.tag-entry')).toHaveCount(1);
  await expect(page.locator('.tag-entry')).toContainText(expected);
 }
 await page.locator('[data-nfc-filter="rewrite"]').click();
 await expect(page.locator('.tag-entry [data-tag-write]')).toHaveText('重新写入');
 await expect(page.locator('.nfc-activity')).toContainText('保存 NFC 标签');
 expect((await page.evaluate(key=>JSON.parse(localStorage.getItem(key)).nfcTags,STORAGE))).toHaveLength(4);
});
test('mobile NFC empty-state filtering never causes horizontal overflow',async({page})=>{
 await page.goto('/#nfc');
 await page.locator('[data-nfc-filter="verified"]').click();
 await expect(page.locator('#app')).toContainText('这个状态下暂无标签');
 expect(await page.evaluate(()=>document.documentElement.scrollWidth-window.innerWidth)).toBeLessThanOrEqual(2);
});
