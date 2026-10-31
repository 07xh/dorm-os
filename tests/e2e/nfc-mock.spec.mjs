import { test, expect } from '@playwright/test';

const STORAGE='dorm-os-state-v1';

async function setup(page){
 await page.addInitScript(()=>{
   const harness={instances:[],writes:[],mode:'success',scanError:null};
   class FakeReader extends EventTarget {
     constructor(){super();harness.instances.push(this);}
     async scan({signal}={}){
       if(harness.scanError)throw new DOMException('Scan denied',harness.scanError);
       if(signal?.aborted)throw new DOMException('Cancelled','AbortError');
     }
     async write(message,{signal}={}){
       harness.writes.push({message,canceled:false});
       const entry=harness.writes[harness.writes.length-1];
       if(harness.mode==='denied')throw new DOMException('Permission denied','NotAllowedError');
       if(harness.mode==='pending')return new Promise((resolve,reject)=>{
         signal.addEventListener('abort',()=>{
           entry.canceled=true;
           reject(new DOMException('Cancelled','AbortError'));
         },{once:true});
       });
       if(signal?.aborted)throw new DOMException('Cancelled','AbortError');
     }
   }
   harness.emit=(url)=>{
     const event=new Event('reading');
     event.message={records:[{recordType:'url',data:new TextEncoder().encode(url)}]};
     harness.instances.at(-1).dispatchEvent(event);
   };
   window.__nfcHarness=harness;
   window.NDEFReader=FakeReader;
 });
 await page.goto('/#nfc');
}
async function addTag(page,scene='study'){
 await page.locator('[data-action="tag-add"]').click();
 await page.locator('#modalForm [name="name"]').fill('NFC 模拟测试标签');
 await page.locator('#modalForm [name="scene"]').selectOption(scene);
 await page.locator('#modalForm [type="submit"]').click();
 return page.evaluate(k=>JSON.parse(localStorage.getItem(k)).nfcTags[0],STORAGE);
}
const tagLink=(id,scene)=>'https://dorm-os-dk9.pages.dev/?dormTag='+encodeURIComponent(id)+'&dormScene='+scene;

test('mock NDEF writer confirms only after successful write, reader event verifies it',async({page})=>{
 await setup(page);
 const tag=await addTag(page);
 page.once('dialog',d=>d.accept());
 await page.locator('[data-tag-write]').click();
 await expect(page.locator('#app')).toContainText('NDEF 写入已确认');
 const state=await page.evaluate(k=>JSON.parse(localStorage.getItem(k)),STORAGE);
 expect(state.nfcTags[0].writtenScene).toBe('study');
 expect(state.nfcTags[0].readAt).toBe('');
 expect(await page.evaluate(()=>__nfcHarness.writes[0].message.records[0].data)).toContain('dormScene=study');
 await page.locator('[data-action="nfc-start"]').click();
 await expect(page.locator('#app')).toContainText('正在扫描');
 await page.evaluate(url=>__nfcHarness.emit(url),tagLink(tag.id,'study'));
 await expect(page).toHaveURL(/#scenes$/);
 const after=await page.evaluate(k=>JSON.parse(localStorage.getItem(k)),STORAGE);
 expect(after.nfcTags[0].readAt).not.toBe('');
 expect(after.nfcTags[0].readScene).toBe('study');
});

test('mock NFC write cancellation prevents false success and allows retry',async({page})=>{
 await setup(page);
 await addTag(page);
 await page.evaluate(()=>{__nfcHarness.mode='pending';});
 page.once('dialog',d=>d.accept());
 await page.locator('[data-tag-write]').click();
 await expect(page.locator('[data-action="nfc-cancel-write"]')).toBeEnabled();
 await expect(page.locator('[data-action="nfc-start"]')).toBeDisabled();
 await page.locator('[data-action="nfc-cancel-write"]').click();
 await expect(page.locator('#app')).toContainText('尚未确认写入成功');
 const s=await page.evaluate(k=>JSON.parse(localStorage.getItem(k)),STORAGE);
 expect(s.nfcTags[0].writtenScene).toBeUndefined();
 expect(await page.evaluate(()=>__nfcHarness.writes[0].canceled)).toBe(true);
 await expect(page.locator('[data-tag-write]')).toBeEnabled();
});

test('navigating away aborts pending NFC write',async({page})=>{
 await setup(page);
 await addTag(page);
 await page.evaluate(()=>{__nfcHarness.mode='pending';});
 page.once('dialog',d=>d.accept());
 await page.locator('[data-tag-write]').click();
 await page.locator('[data-page="scenes"]:visible').first().click();
 await expect(page).toHaveURL(/#scenes$/);
 expect(await page.evaluate(()=>__nfcHarness.writes[0].canceled)).toBe(true);
 expect((await page.evaluate(k=>JSON.parse(localStorage.getItem(k)),STORAGE)).nfcTags[0].writtenAt).toBeUndefined();
});

test('mock scan rejects changed old scene without activating it',async({page})=>{
 await setup(page);
 const tag=await addTag(page,'study');
 await page.locator('[data-tag-edit]').click();
 await page.locator('#modalForm [name="scene"]').selectOption('dev');
 await page.locator('#modalForm [type="submit"]').click();
 await page.locator('[data-action="nfc-start"]').click();
 await page.evaluate(url=>__nfcHarness.emit(url),tagLink(tag.id,'study'));
 await expect(page.locator('#app')).toContainText('实体标签');
 await expect(page.locator('#app')).toContainText('旧绑定');
 const data=await page.evaluate(k=>JSON.parse(localStorage.getItem(k)),STORAGE);
 expect(data.scene).toBe('none');
 expect(data.nfcTags[0].readScene).toBe('study');
});

test('mock foreign NFC tag cannot activate local scene',async({page})=>{
 await setup(page);
 await page.locator('[data-action="nfc-start"]').click();
 await page.evaluate(()=>__nfcHarness.emit('https://untrusted.example/?dormScene=away'));
 await expect(page.locator('#app')).toContainText('不是本站场景链接');
 await expect(page).toHaveURL(/#nfc$/);
});
test('mock NFC permission denial reports failure without claiming success',async({page})=>{
 await setup(page);
 await page.evaluate(()=>{__nfcHarness.scanError='NotAllowedError';});
 await page.locator('[data-action="nfc-start"]').click();
 await expect(page.locator('#app')).toContainText('NFC 授权被拒绝');
 await expect(page.locator('[data-action="nfc-start"]')).toBeEnabled();
});
