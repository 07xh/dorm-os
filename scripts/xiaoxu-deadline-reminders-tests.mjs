import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
const store=new Map();
const ctx={Date,localStorage:{getItem:k=>store.get(k)||null,setItem:(k,v)=>store.set(k,v)}};
runInNewContext(readFileSync(new URL('../public/xiaoxu-deadline-reminders.js',import.meta.url),'utf8'),ctx);
const a=ctx.XiaoxuDeadlineReminders,now=Date.parse('2026-11-03T04:00:00Z');
const tasks=[
 {id:'t1',title:'工程制图',due:'2026-11-03',done:false},
 {id:'t2',title:'已交平台作业',dueAt:'2026-11-03T13:00:00+08:00',sourceStatus:'submitted'},
 {id:'t3',title:'<img src=x onerror=alert(1)>',due:'2026-11-04'},
 {id:'t4',title:'未知时间',due:''},
 {id:'t5',title:'已完成',due:'2026-11-03',done:true}
];
let plan=a.plan(tasks,now);
assert.equal(plan.length,2);
assert.match(plan[0].label,/时刻未知/);
const html=a.panel(tasks,now);
assert.match(html,/工程制图/);
assert.ok(!html.includes('<img'));
assert.ok(!html.includes('已交平台作业'));
assert.ok(html.includes('&lt;img'),'Malicious HTML must be displayed only as escaped text');
assert.ok(a.ack(plan[0].id));
assert.equal(Object.keys(JSON.parse(store.get('xiaoxu-deadline-ack-v1'))).length,1);
assert.ok(!store.get('xiaoxu-deadline-ack-v1').includes('工程制图'));
const future=a.plan([{id:'x',title:'最近',dueAt:'2026-11-03T14:00:00+08:00'}],now);
assert.equal(future[0].label,'24 小时内截止');
assert.equal(a.plan([{id:'x',title:'不猜时刻',due:'2026-02-30'}],now).length,0);
assert.equal(a.plan([{id:'x',title:'很久以后',due:'2026-12-30'}],now).length,0);
assert.equal(a.ack('not-safe<script>'),false);
console.log('M5 foreground-only task reminders, unknown-time handling, consent and HTML escaping passed');
