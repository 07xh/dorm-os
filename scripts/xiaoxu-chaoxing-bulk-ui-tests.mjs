/* A no-browser regression for the actual homework UI's explicit course load.
 * The first course may be empty while later courses have pending/returned work.
 */
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';

const courseIds=['111','222','333'];
const courses=courseIds.map(courseId=>({
 courseId,classId:'456',cpi:'789',title:'课程 '+courseId
}));
const synced=[],listeners=new Map();
let cloudRefreshes=0;
const host={innerHTML:'',querySelector:()=>null};
const document={
 getElementById:id=>id==='xuChaoxingWorkHost'?host:null,
 addEventListener:(type,callback)=>listeners.set(type,callback)
};
const window={
 XiaoxuAcademic:{isReady:()=>true},
 XiaoxuCloudTasks:{isReady:()=>true,refresh:async()=>{cloudRefreshes++;}},
 dispatchEvent:()=>{}
};
const reply=(view,data)=>({ok:true,json:async()=>({
 schema:'xiaoxu/chaoxing-work-browser-v1',view,live:true,data
})});
const fakeFetch=async(url,options)=>{
 const u=new URL(url,'https://school.example');
 if(u.pathname==='/api/v1/chaoxing-homework'&&u.searchParams.get('view')==='courses')
  return reply('courses',{courses});
 if(u.pathname==='/api/v1/chaoxing-homework/sync'){
  assert.equal(options.method,'POST');
  const keys=JSON.parse(options.body);
  assert.equal(keys.classId,'456');
  assert.equal(keys.cpi,'789');
  synced.push(keys.courseId);
  const item=(workId,statusCode)=>({workId,title:'作业 '+workId,
   status:statusCode==='returned'?'已退回':'未提交',
   statusCode,platformVerified:true});
  const items=keys.courseId==='111'?[]:
   keys.courseId==='222'?[item('901','todo')]:[item('902','returned')];
  return reply('list',{items,verifiedEmpty:items.length===0,sync:{received:items.length}});
 }
 throw new Error('Unexpected request: '+u.pathname);
};
const source=readFileSync(new URL('../public/xiaoxu-chaoxing-homework.js',import.meta.url),'utf8');
runInNewContext(source,{window,document,URL,fetch:fakeFetch,
 location:{origin:'https://school.example'},Event:class {}});
const click=listeners.get('click');
assert.equal(typeof click,'function');
click({target:{closest:selector=>selector==='[data-xu-work-courses]'?{}:null}});
for(let i=0;i<80&&!host.innerHTML.includes('全部 3 门课程已核实');i++)
 await new Promise(resolve=>setImmediate(resolve));
assert.deepEqual(synced,courseIds,
 'the explicit course-load action must sync every course, including those after an empty first course');
assert.match(host.innerHTML,/全部 3 门课程已核实/);
assert.match(host.innerHTML,/待处理 2 份/,
 'both unsubmitted and returned official work must be visible as actionable tasks');
assert.ok(cloudRefreshes>=1,'the updated D1 tasks must refresh in the cloud task view');
console.log('Chaoxing UI: explicit load synchronizes every course and preserves actionable status totals');
