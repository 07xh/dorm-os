import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';

const html=readFileSync(new URL('../public/index.html',import.meta.url),'utf8');
const start=html.indexOf('function timetableViewMarkup(');
const end=html.indexOf('function timetable(){',start);
assert(start>0&&end>start,'Timetable view updater should precede timetable page renderer');
assert(html.includes('data-timetable-view')&&html.includes('data-timetable-range'),
 'Schedule markup needs independently patchable hooks');
for(const snippet of [
 'target.dataset.timetableMode){setTimetableMode(target.dataset.timetableMode);}',
 'target.dataset.timetableWeek!==undefined){shiftTimetableWeek(Number(target.dataset.timetableWeek));}',
 'target.dataset.timetableDay!==undefined){selectTimetableDay(Number(target.dataset.timetableDay));}'
])assert(html.includes(snippet),'Timetable event handler still uses full render: '+snippet);
const modes=['day','week'].map(mode=>{
 const button={dataset:{timetableMode:mode},attrs:{},active:false,
 setAttribute(k,v){this.attrs[k]=v;}};
 button.classList={toggle(_cls,yes){button.active=yes;}};
 return button;
});
const picker=[1,2,3,4,5,6,0].map(day=>{
 const button={dataset:{timetableDay:String(day)},attrs:{},selected:false,
 setAttribute(k,v){this.attrs[k]=v;}};
 button.classList={toggle(_cls,yes){button.selected=yes;}};
 return button;
});
const dayContent={innerHTML:'initial day'};
const active={innerHTML:'initial view',querySelector(s){
 if(s==='.v42-day-content')return dayContent;
 return null;
},querySelectorAll(s){
 assert.equal(s,'[data-timetable-day]');
 return picker;
}};
const range={textContent:'initial range'},reset={hidden:true},next={focusCount:0,
 focus(){this.focusCount++;}};
const week={querySelector(s){
 if(s==='[data-timetable-range]')return range;
 if(s==='[data-timetable-week="0"]')return reset;
 return null;
}};
let missing=false;
const app={
 querySelector(s){
 if(s==='[data-timetable-view]')return missing?null:active;
 if(s==='[aria-label="课表周次"]')return week;
 if(s==='[data-timetable-week="1"]')return next;
 return null;
 },
 querySelectorAll(s){
 assert.equal(s,'.v42-segments [data-timetable-mode]');
 return modes;
 }
};
const dates=(date,n)=>{
 const value=new Date(date+'T00:00:00Z');
 value.setUTCDate(value.getUTCDate()+n);
 return value.toISOString().slice(0,10);
};
const ctx={
 page:'timetable',timetableMode:'day',timetableDay:null,timetableWeekOffset:0,
 renders:0,WEEKDAYS:['周日','周一','周二','周三','周四','周五','周六'],
 $:id=>{assert.equal(id,'app');return app;},
 now:()=>({date:'2026-11-24',day:2}),
 addDays:dates,fmtDate:date=>date,esc:value=>value,
 timetableDayPanel:(day,weekStart)=>'panel:'+day+':'+weekStart,
 document:{activeElement:{matches:()=>false}},
 render(){ctx.renders++;}
};
runInNewContext(html.slice(start,end),ctx);
runInNewContext('selectTimetableDay(4)',ctx);
assert.equal(ctx.timetableDay,4);
assert.equal(dayContent.innerHTML,'panel:4:2026-11-23');
assert.equal(active.innerHTML,'initial view','Day changes should not replace the picker');
assert.equal(picker[3].attrs['aria-pressed'],'true');
assert.equal(ctx.renders,0);
runInNewContext("setTimetableMode('week')",ctx);
assert.equal(ctx.timetableMode,'week');
assert.match(active.innerHTML,/v42-week-grid/);
assert.equal(modes[1].attrs['aria-pressed'],'true');
assert.equal(ctx.renders,0);
runInNewContext('shiftTimetableWeek(1)',ctx);
assert.equal(ctx.timetableWeekOffset,1);
assert.match(range.textContent,/2026-11-30/);
assert.equal(reset.hidden,false);
assert.equal(ctx.renders,0);
ctx.document.activeElement.matches=()=>true;
runInNewContext('shiftTimetableWeek(0)',ctx);
assert.equal(ctx.timetableWeekOffset,0);
assert.equal(reset.hidden,true);
assert.equal(next.focusCount,1,'Resetting a week should move focus off the hidden reset button');
runInNewContext("setTimetableMode('day')",ctx);
assert.match(active.innerHTML,/v42-day-picker/);
assert.equal(ctx.renders,0);
runInNewContext("selectTimetableDay(-1);setTimetableMode('unknown');shiftTimetableWeek(3)",ctx);
assert.equal(ctx.timetableDay,4);
assert.equal(ctx.timetableMode,'day');
assert.equal(ctx.timetableWeekOffset,0);
missing=true;
runInNewContext("setTimetableMode('week')",ctx);
assert.equal(ctx.renders,1,'Missing viewport must fall back to full render');
console.log('Timetable day, week, mode, accessibility, focus, fallback and DOM retention verified');
