import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';

const html = readFileSync(new URL('../public/index.html', import.meta.url), 'utf8');
function sourceBetween(start, end) {
  const a = html.indexOf(start);
  const b = html.indexOf(end, a + start.length);
  assert.ok(a !== -1 && b > a, 'Required source fragment missing: ' + start);
  return html.slice(a, b);
}
const ctx = {
  URL, TextDecoder, console,
  CAMPUSES: { jianghuai: {}, huayuan: {} },
  CATEGORIES: ['课程','高数','工程制图','Python','英语','生活','其他'],
  location: { origin:'https://dorm-os-dk9.pages.dev',pathname:'/' },
  state: { nfcTags:[] },
  sceneDefs: ['study','rest','dev','away'].map(id=>({id})),
};
runInNewContext(
  sourceBetween('function safeState(value){','function classForDay(') +
  sourceBetween('function tagLink(tag){','async function copyTagLink(') +
  sourceBetween('function getSceneFromNdef(message){','async function startNfcScan('),
  ctx,
);
const base = {
  version:1, settings:{campus:'jianghuai',theme:'light'},
  tasks:[{id:'t1',title:'高数作业',due:'2026-11-01',done:false}],
  classes:[],nfcTags:[{id:'tag-a',name:'书桌',scene:'study',writtenScene:'study',writtenAt:'2026-10-31T12:00:00Z'}],
  reminders:[{id:'r1',text:'洗衣',due:'2026-11-01',done:true}],
  items:[{id:'i1',name:'充电器',place:'抽屉'}],
};
const sanitized = ctx.safeState(base);
assert.equal(sanitized.tasks[0].title,'高数作业');
assert.equal(sanitized.nfcTags[0].writtenScene,'study');
assert.equal(sanitized.nfcTags[0].writtenAt,'2026-10-31T12:00:00Z');
assert.equal(sanitized.reminders[0].done,true);
assert.equal(sanitized.items[0].place,'抽屉');

const legacy = ctx.safeState({version:1,settings:{theme:'dark'},nfcTags:[{id:'old',name:'旧标签',scene:'rest'}]});
assert.equal(legacy.settings.theme,'dark');
assert.equal(legacy.nfcTags[0].writtenScene,'');
assert.equal(legacy.nfcTags[0].writtenAt,'');
assert.throws(()=>ctx.safeState({version:999}),/不支持|支持/);

ctx.state.nfcTags = sanitized.nfcTags;
const url = ctx.tagLink(sanitized.nfcTags[0]);
assert.equal(new URL(url).searchParams.get('dormTag'),'tag-a');
assert.equal(new URL(url).searchParams.get('dormScene'),'study');
const tagMessage = link => ({records:[{recordType:'url',data:new TextEncoder().encode(link)}]});
assert.equal(ctx.getSceneFromNdef(tagMessage(url)).scene,'study');
// An edited local binding overrides the scene embedded in an older written URL.
ctx.state.nfcTags[0].scene='dev';
assert.equal(ctx.getSceneFromNdef(tagMessage(url)).scene,'dev');
// Other browsers can use the signed-off scene fallback, but cannot inherit local rebinding.
ctx.state.nfcTags = [];
assert.equal(ctx.getSceneFromNdef(tagMessage(url)).scene,'study');
assert.equal(ctx.getSceneFromNdef(tagMessage('https://example.com/?dormScene=study')),null);
assert.equal(ctx.getSceneFromNdef(tagMessage('https://dorm-os-dk9.pages.dev/other?dormScene=study')),null);
assert.equal(ctx.getSceneFromNdef(tagMessage('invalid url')),null);
assert.equal(ctx.getSceneFromNdef({records:[{recordType:'mime',data:new Uint8Array()}]}),null);
console.log('DORM OS NFC link, local rebinding, backup compatibility and URL origin tests passed');


// Background tabs may throttle callbacks; focused time is computed using elapsed clock time.
let fakeClock = 1_000_000;
const focusCtx = {
  Date:{now:()=>fakeClock},
  timer:{remaining:25*60,running:false,interval:null,deadline:0},
  setInterval:()=>1,clearInterval:()=>{},
  render:()=>{},tick:()=>{},toast:()=>{},
};
runInNewContext(sourceBetween('function timerText(){','function openDialog('),focusCtx);
focusCtx.timerStart();
assert.equal(focusCtx.timer.running,true);
fakeClock+=61_000;
focusCtx.timerSync();
assert.equal(focusCtx.timer.remaining,1439);
focusCtx.timerStop();
assert.equal(focusCtx.timer.remaining,1439);
fakeClock+=120_000; // Pause should not consume remaining focus time.
focusCtx.timerStart();
fakeClock+=1439_000;
focusCtx.timerSync();
assert.equal(focusCtx.timer.running,false);
assert.equal(focusCtx.timer.remaining,0);
console.log('Focus timer wall-clock, pause and resume tests passed');

import { webcrypto } from 'node:crypto';
const cryptoCtx={crypto:webcrypto,TextEncoder,TextDecoder,Uint8Array,atob,btoa,safeState:ctx.safeState};
runInNewContext(sourceBetween('const ENCRYPTED_BACKUP_MARKER','function clickAction('),cryptoCtx);
const transfer = await cryptoCtx.encryptBackup(base,'A-strong-passphrase-2048');
assert.equal(transfer.format,'dorm-os-encrypted-v1');
assert.equal(transfer.cipher,'AES-256-GCM');
assert.equal(transfer.iterations,250000);
assert.equal(transfer.data.includes('高数作业'),false);
const recovered = await cryptoCtx.decryptBackup(transfer,'A-strong-passphrase-2048');
assert.equal(recovered.tasks[0].title,'高数作业');
assert.equal(recovered.nfcTags[0].id,'tag-a');
await assert.rejects(cryptoCtx.decryptBackup(transfer,'wrong-passphrase'));
const ix=Math.floor(transfer.data.length/2);
const tampered={...transfer,data:transfer.data.slice(0,ix)+(transfer.data[ix]==='A'?'B':'A')+transfer.data.slice(ix+1)};
await assert.rejects(cryptoCtx.decryptBackup(tampered,'A-strong-passphrase-2048'));
await assert.rejects(cryptoCtx.decryptBackup({...transfer,iterations:999999999},'A-strong-passphrase-2048'));
console.log('Encrypted backup round trip, wrong-password and tamper rejection passed');

assert.equal(ctx.getSceneFromNdef(tagMessage('https://dorm.0701000.xyz/?dormScene=study')).scene,'study');
assert.equal(ctx.getSceneFromNdef(tagMessage('https://dorm-os-dk9.pages.dev/index.html?dormScene=dev')).scene,'dev');
assert.equal(ctx.getSceneFromNdef(tagMessage('https://evil.dorm-os-dk9.pages.dev/?dormScene=dev')),null);
assert.equal(ctx.getSceneFromNdef(tagMessage('http://dorm.0701000.xyz/?dormScene=study')),null);
console.log('NFC canonical and custom domain alias allowlist tests passed');


ctx.state.nfcTags=[{id:'tag-a',name:'书桌',scene:'dev'}];
const stale=ctx.getSceneFromNdef(tagMessage('https://dorm-os-dk9.pages.dev/?dormTag=tag-a&dormScene=study'));
assert.equal(stale.scene,'dev');
assert.equal(stale.stale,true);
assert.equal(stale.tagId,'tag-a');
assert.equal(stale.encodedScene,'study');
const current=ctx.getSceneFromNdef(tagMessage('https://dorm-os-dk9.pages.dev/?dormTag=tag-a&dormScene=dev'));
assert.equal(current.stale,false);
assert.equal(current.tagId,'tag-a');
ctx.state.nfcTags=[];
const fallback=ctx.getSceneFromNdef(tagMessage('https://dorm-os-dk9.pages.dev/?dormTag=tag-a&dormScene=study'));
assert.equal(fallback.scene,'study');
assert.equal(fallback.tagId,null);
console.log('NFC stale binding and actual-read evidence boundaries validated');


const mergeCtx={safeState:ctx.safeState,structuredClone};
runInNewContext(sourceBetween('function mergePersonalBackup(', 'const ENCRYPTED_BACKUP_MARKER'),mergeCtx);
const currentData={...base,tasks:[{id:'shared',title:'本机高数作业',done:true,category:'高数',due:''}],nfcTags:[{id:'nfc-shared',name:'本机书桌',scene:'dev'}],reminders:[{id:'r1',text:'洗衣',done:true}]};
const remoteData={...base,tasks:[{id:'shared',title:'云外版本',done:false,category:'高数'},{id:'new-task',title:'Python 作业',done:false}],nfcTags:[{id:'nfc-shared',name:'异地书桌',scene:'rest'},{id:'new-tag',name:'新标签',scene:'study'}],reminders:[{id:'r1',text:'洗衣',done:false},{id:'new-reminder',text:'取快递',done:false}]};
const merged=mergeCtx.mergePersonalBackup(currentData,remoteData);
assert.equal(merged.data.tasks.find(x=>x.id==='shared').title,'本机高数作业');
assert.equal(merged.data.tasks.find(x=>x.id==='shared').done,true);
assert.equal(merged.data.tasks.find(x=>x.id==='new-task').title,'Python 作业');
assert.equal(merged.data.nfcTags.find(x=>x.id==='nfc-shared').scene,'dev');
assert.equal(merged.data.nfcTags.find(x=>x.id==='new-tag').scene,'study');
assert.equal(merged.data.reminders.find(x=>x.id==='r1').done,true);
assert.equal(merged.data.reminders.find(x=>x.id==='new-reminder').text,'取快递');
assert.equal(merged.added,3);
assert.ok(merged.conflicts>=2);
assert.equal(mergeCtx.mergePersonalBackup(merged.data,remoteData).added,0);
const maxed={...base,tasks:Array.from({length:300},(_,i)=>({id:'t-'+i,title:'Task '+i}))};
const atCapacity=mergeCtx.mergePersonalBackup(maxed,{...base,tasks:[{id:'new',title:'Task new'}]});
assert.equal(atCapacity.data.tasks.length,300);
assert.equal(atCapacity.capacitySkipped,1);
console.log('Import merge preserves local records and completion, handles conflicts, idempotence and caps');
