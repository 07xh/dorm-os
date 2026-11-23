import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';

const html = readFileSync(new URL('../public/index.html',import.meta.url),'utf8');
const start = html.indexOf('function selectNoticeFilter(next){');
const end = html.indexOf('\n}\n// An explicit user gesture',start);
assert(start > 0 && end > start, 'Notice filter update function missing');
assert(html.includes('target.dataset.noticeFilter){selectNoticeFilter(target.dataset.noticeFilter);}'),
  'Notice filter click must use scoped update');
const source = html.slice(start,end + 2);
const noticeHost = {innerHTML:'before'};
const buttons = ['all','unread','notice','exam'].map(type => ({
  dataset:{noticeFilter:type}, attrs:{}, selected:false,
  classList:{toggle(_,on){this.selected=on;}},
  setAttribute(name,value){this.attrs[name]=value;}
}));
const app = {querySelectorAll(selector){
  assert.equal(selector,'[data-notice-filter]');
  return buttons;
}};
const ctx = {
  noticeFilter:'all',page:'academic',academicTab:'notices',redraws:0,
  $(id){return id==='publicNoticeList'?noticeHost:id==='app'?app:null;},
  publicNoticeList(){return 'filtered:'+ctx.noticeFilter;},
  render(){ctx.redraws++;}
};
runInNewContext(source,ctx);
runInNewContext("selectNoticeFilter('unread')",ctx);
assert.equal(ctx.noticeFilter,'unread');
assert.equal(noticeHost.innerHTML,'filtered:unread');
assert.equal(ctx.redraws,0,'Filter click should not redraw the academic page');
assert.equal(buttons[1].attrs['aria-pressed'],'true');
assert.equal(buttons[0].attrs['aria-pressed'],'false');
runInNewContext("selectNoticeFilter('unread');selectNoticeFilter('invalid')",ctx);
assert.equal(ctx.redraws,0,'Repeat/invalid filters should be no-ops');
ctx.$=id=>id==='publicNoticeList'?null:id==='app'?app:null;
runInNewContext("selectNoticeFilter('exam')",ctx);
assert.equal(ctx.noticeFilter,'exam');
assert.equal(ctx.redraws,1,'Incomplete/retired markup should safely use normal render');
console.log('Notice filters update only results/buttons and retain legacy redraw fallback');
