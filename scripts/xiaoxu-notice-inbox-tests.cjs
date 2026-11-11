const assert=require('node:assert/strict');
const lib=require('../public/xiaoxu-notices.js');
const article=(id,cat='notice')=>({
 id:'notices:'+id,category:cat,
 title:'关于2026学年课程事务的通知 '+id,
 url:'https://www2.ncwu.edu.cn/jiaowu/info/'+(cat==='exam'?'1034':'1008')+'/'+id+'.htm',
 dateLabel:'11-02',publishedAt:null
});
const archiveItems=[
 {id:'ncwu-1',title:'关于2026年开展线上课程安排的通知',date:'2026-11-02',
  url:'https://www.ncwu.edu.cn/info/1063/23331.htm',sourceId:'jwc',sourceName:'教务处',category:'教学'},
 {id:'ncwu-2',title:'关于2026学年学生事务校级工作通知',date:null,
  url:'https://www2.ncwu.edu.cn/xsc/info/1013/4139.htm',sourceId:'student',sourceName:'学工处',category:'学生事务'}
];
const archive={schema:'xiaoxu/notices-v1',mode:'auto',lastSuccessAt:'2026-11-03T02:00:00Z',items:archiveItems};
const api={success:true,partial:false,fetchedAt:'2026-11-03T02:05:00Z',
 items:[article(2995),article(3003,'exam')]};
assert.equal(lib.fromArchive(archive).items.length,2);
const tainted={...archive,items:[...archiveItems,{
 id:'seed-jwc-9888',title:'不是确认真实发布的学校样本',sourceName:'教务处',
 sourceId:'jwc',category:'教学',date:'2026-11-02',
 url:'https://www.ncwu.edu.cn/info/1063/9888.htm'
}]};
const filtered=lib.fromArchive(tainted);
assert.equal(filtered.items.length,2,'Historic seed item must not be promoted by mode=auto');
assert.equal(filtered.items.some(x=>x.title.includes('样本')),false,'Unverified record must never reach the inbox');
assert.throws(()=>lib.fromArchive({...archive,items:[tainted.items[2],tainted.items[2]]}),/有效条目不足/);
assert.equal(lib.fromArchive(archive).items[1].dateLabel,'','Unknown date must remain unknown');
assert.equal(lib.fromApi(api).items.length,2);
assert.equal(lib.merge(lib.fromApi(api),lib.fromArchive(archive)).length,4);
assert.throws(()=>lib.fromArchive({...archive,mode:'seed'}),/验收/);
assert.throws(()=>lib.fromArchive({...archive,lastSuccessAt:null}),/验收/);
assert.throws(()=>lib.fromArchive({...archive,items:[archiveItems[0],{...archiveItems[1],url:'https://not-ncwu.example/info/1000/1.htm'}]}),/异常/);
assert.throws(()=>lib.fromApi({...api,items:[article(2995),{...article(3003),url:'https://evil.example/jiaowu/info/1008/3003.htm'}]}),/非法/);
assert.equal(lib.officialUrl('https://www2.ncwu.edu.cn/dianqi/info/1001/5001.htm'),
 'https://www2.ncwu.edu.cn/dianqi/info/1001/5001.htm','Approved department notices appear in existing inbox');
assert.equal(lib.officialUrl('https://www2.ncwu.edu.cn/gcxlzx/info/1001/5002.htm'),
 'https://www2.ncwu.edu.cn/gcxlzx/info/1001/5002.htm');
assert.equal(lib.officialUrl('https://www2.ncwu.edu.cn/qysy/info/1001/5003.htm'),
 'https://www2.ncwu.edu.cn/qysy/info/1001/5003.htm');
assert.equal(lib.officialUrl('https://www2.ncwu.edu.cn/random/info/1001/5003.htm'),'',
 'Unapproved departmental article paths are rejected');
assert.equal(lib.officialUrl('javascript:alert(1)'),'');
assert.equal(lib.officialUrl('https://www.ncwu.edu.cn.evil.example/info/1063/23331.htm'),'');
const items=lib.merge(lib.fromApi(api),lib.fromArchive(archive));
let current=lib.normalizeReading(null);
let step=lib.reconcileReading(current,items);
assert.equal(step.added,0,'First valid sync establishes baseline, does not mark historical notices as new');
assert.equal(step.next.unread.length,0);
current=step.next;
step=lib.reconcileReading(current,[...items,{...article(3004),id:'notices:3004'}]);
assert.equal(step.added,1);
assert.equal(step.next.unread.length,1);
assert.equal(step.next.unread[0],article(3004).url);
current=lib.markRead(step.next,article(3004).url);
assert.equal(current.unread.length,0);
step=lib.reconcileReading(current,[...items,article(3004)]);
assert.equal(step.added,0,'Read item must not return as new');
assert.deepEqual(lib.markAllRead(step.next).unread,[]);

const fixedNow=Date.parse('2026-11-03T17:00:00Z');
assert.equal(lib.isStaleFeed('2026-11-03T08:00:00Z',fixedNow),false,
 'recent verified API data must not be called stale');
assert.equal(lib.isStaleFeed('2026-11-03T04:59:59Z',fixedNow),true,
 'an old but valid archive must not appear recently updated');
assert.equal(lib.isStaleFeed(null,fixedNow),true);
assert.equal(lib.isStaleFeed('2030-01-01T00:00:00Z',fixedNow),true,
 'unreasonably future-dated cache must not count as fresh');
console.log('校序通知整合：真实档案映射、恶意源拒绝、初始快照、新通知/已读状态通过');
