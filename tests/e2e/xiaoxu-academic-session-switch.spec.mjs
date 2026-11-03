import {test,expect} from '@playwright/test';

// Simulate the old owner’s pending connection request resolving only after a
// newer verified owner session has already connected in the same browser tab.
test('an old failed connection cannot disconnect the newly connected owner',async({page})=>{
 let releaseOld;
 const gate=new Promise(resolve=>{releaseOld=resolve;});
 let connectionCalls=0;
 await page.route('**/api/v1/connections',async route=>{
  connectionCalls++;
  if(connectionCalls===1){
   await gate;
   return route.fulfill({status:503,contentType:'application/json',
    body:JSON.stringify({error:'old_session_expired'})});
  }
  return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({
   schema:'xiaoxu/connections-v1',
   platforms:['ncwu','chaoxing','second_class'].map(id=>({
    id,status:'not_connected',sessionState:'not_connected',
    loginMode:'unavailable',trustedKinds:[],importedKinds:[],
    accountAuthorization:'not_verified'
   }))
  })});
 });
 await page.route('**/api/v1/verified/**',route=>route.fulfill({
  status:200,contentType:'application/json',
  body:JSON.stringify({status:'not_connected',records:[]})
 }));
 await page.route('**/api/v1/second-class',route=>route.fulfill({
  status:200,contentType:'application/json',
  body:JSON.stringify({status:'not_connected',records:[]})
 }));
 await page.goto('/#settings');
 await page.evaluate(()=>{
  window.__previousOwnerConnect=window.XiaoxuAcademic.connect()
   .then(()=> 'old-resolved',()=> 'old-rejected');
 });
 await expect.poll(()=>connectionCalls).toBe(1);
 await page.evaluate(()=>window.XiaoxuAcademic.disconnect());
 expect(await page.evaluate(()=>window.XiaoxuAcademic.connect())).toBe(true);
 releaseOld();
 await expect.poll(()=>page.evaluate(()=>window.__previousOwnerConnect)).toBe('old-rejected');
 expect(await page.evaluate(()=>window.XiaoxuAcademic.isReady())).toBe(true);
 expect(connectionCalls).toBe(2);
});
