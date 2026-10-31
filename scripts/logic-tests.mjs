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
