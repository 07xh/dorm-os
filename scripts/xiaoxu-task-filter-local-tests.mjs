import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';

const html=readFileSync(new URL('../public/index.html',import.meta.url),'utf8');
const start=html.indexOf('function patchTaskList(){');
const end=html.indexOf('function tasksPage(){',start);
assert(start>0&&end>start,'Task-local renderer must be defined before tasksPage');
assert(html.includes('else if(target.dataset.filter){selectTaskFilter(target.dataset.filter);}'),
 'Filter click handler must use local update');
assert(html.includes("if(el.matches('[data-task-source]')){selectTaskSource(el.value);}"),
 'Source change handler must use local update');

const buttons=['all','open','done','review'].map(filter=>{
 const obj={dataset:{filter},attrs:{},active:false,
  setAttribute(name,value){this.attrs[name]=value;}};
 obj.classList={toggle(name,on){assert.equal(name,'active');obj.active=on;}};
 return obj;
});
const list={innerHTML:'initial'},count={textContent:'initial'};
const select={focused:0,focus(){this.focused++;}};
const view={hidden:false,querySelector(selector){
 if(selector==='.v4-task-list')return list;
 if(selector==='[data-workspace-count]')return count;
 throw Error('Unexpected view selector: '+selector);
}};
const app={querySelector(selector){
 if(selector==='[data-task-list-view]')return view;
 if(selector==='[data-task-source]')return select;
 throw Error('Unexpected app selector: '+selector);
},querySelectorAll(selector){
 assert.equal(selector,'.v4-task-toolbar [data-filter]');
 return buttons;
}};
const ctx={
 page:'tasks',taskFilter:'all',taskSource:'all',
 renders:0,hash:'',taskRows:['personal','platform'],
 $(name){assert.equal(name,'app');return app;},
 workspaceTasks(filter){
  if(filter==='done')return [];
  return ctx.taskRows.map(title=>({title}));
 },
 workspaceTaskRow(row){return '<article>'+row.title+'</article>';},
 history:{replaceState(_state,_title,hash){ctx.hash=hash;}},
 XiaoxuWebRouter:{taskFilterHash(filter){return filter==='review'?'#review':'#tasks';}},
 render(){ctx.renders++;}
};
runInNewContext(html.slice(start,end),ctx);
runInNewContext("selectTaskFilter('open')",ctx);
assert.equal(ctx.taskFilter,'open');
assert.equal(list.innerHTML,'<article>personal</article><article>platform</article>');
assert.equal(count.textContent,'2 项任务');
assert.equal(ctx.hash,'#tasks');
assert.equal(ctx.renders,0,'Ordinary filters must not call render');
assert.equal(buttons[1].attrs['aria-pressed'],'true');
assert.equal(buttons[0].attrs['aria-pressed'],'false');
runInNewContext("selectTaskFilter('done')",ctx);
assert.match(list.innerHTML,/当前筛选下暂无任务/);
assert.equal(count.textContent,'0 项任务');
assert.equal(ctx.renders,0);
runInNewContext("selectTaskSource('manual')",ctx);
assert.equal(ctx.taskSource,'manual');
assert.equal(ctx.renders,0);
assert.equal(select.focused,0,'Local filter must preserve source select without refocusing');
runInNewContext("selectTaskFilter('invalid')",ctx);
assert.equal(ctx.taskFilter,'done','Invalid filter must not update state');
runInNewContext("selectTaskFilter('review')",ctx);
assert.equal(ctx.renders,1,'Review panel transition requires full render');
assert.equal(ctx.hash,'#review');
runInNewContext("selectTaskFilter('all')",ctx);
assert.equal(ctx.renders,2,'Leaving review requires full render');
view.hidden=true;
runInNewContext("selectTaskFilter('open')",ctx);
assert.equal(ctx.renders,3,'Unavailable task view must fall back to render');
console.log('Task/source scoped updates, accessibility, history and review fallback verified');
