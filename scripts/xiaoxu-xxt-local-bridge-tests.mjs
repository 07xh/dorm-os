import assert from 'node:assert/strict';
import {mkdtempSync,readFileSync,rmSync,writeFileSync,statSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
import {parseSource,readExport,runXxt} from '../lib/xiaoxu-xxt-local-bridge.mjs';

const url=enc=>'https://mooc1.chaoxing.com/mooc2/work/task?workId=123&courseId=456&enc='+enc;
const source=[{course:'高等数学',title:'练习作业',status:'未提交',deadline:'2026-11-20 23:59',url:url('private-token')}];
const safe=parseSource(JSON.stringify(source));
assert.equal(safe.batch.format,'xiaoxu-task-batch-v1');
assert.equal(safe.batch.tasks[0].externalId,'work:123:456');
assert.equal(safe.batch.tasks[0].dueAt,'2026-11-20T23:59:00+08:00');
assert.equal(safe.batch.tasks[0].status,'todo');
assert.equal(safe.coverage,'partial');
assert.equal(safe.verifiedPersonalAccount,false);
assert.ok(!JSON.stringify(safe).includes('private-token'));
assert.ok(!JSON.stringify(safe).includes('https://'));
const single={course:'高等数学',items:[{index:1,title:'练习作业',status:'待批阅',url:url('rotated-token')}]};
assert.equal(parseSource(JSON.stringify(single)).batch.tasks[0].status,'submitted');
assert.equal(parseSource(JSON.stringify(single)).batch.tasks[0].due,'');
assert.equal(parseSource(JSON.stringify([{...source[0],deadline:'11-20 23:59'}])).unknownDeadlines,1);
assert.throws(()=>parseSource('not JSON'),/不是有效/);
assert.throws(()=>parseSource('[]'),/空列表/);
assert.throws(()=>parseSource(JSON.stringify([{...source[0],url:'https://evil.example/?workId=123'}])),/可信/);
assert.throws(()=>parseSource(JSON.stringify([source[0],source[0]])),/重复/);
let invoked;
const runner=(bin,args,opts)=>{invoked={bin,args,opts};return {status:0,stdout:JSON.stringify(source),stderr:''};};
assert.equal(runXxt({runner}).count,1);
assert.equal(invoked.bin,'xxt');
assert.deepEqual(invoked.args,['hw','--json']);
assert.equal(invoked.opts.shell,false);
assert.equal(runXxt({mode:'hw-ls',course:'高等数学',clazzid:'42',runner:(bin,args,opts)=>{
 invoked={bin,args,opts};return {status:0,stdout:JSON.stringify(single),stderr:''};
}}).count,1);
assert.deepEqual(invoked.args,['hw-ls','高等数学','--clazzid','42','--json']);
assert.throws(()=>runXxt({mode:'hw-ls',runner}),/必须指定/);
assert.throws(()=>runXxt({mode:'hw',clazzid:'12',runner}),/不支持/);
assert.throws(()=>runXxt({mode:'hw-ls',course:'课',clazzid:'invalid',runner}),/无效/);
assert.throws(()=>runXxt({runner:()=>({status:0,stdout:JSON.stringify(source),stderr:'⚠ 部分课程失败'})}),/警告/);
assert.throws(()=>runXxt({runner:()=>({status:2,stdout:'[]',stderr:'private password'})}),/查询失败/);
const folder=mkdtempSync(join(tmpdir(),'xiaoxu-xxt-bridge-'));
try{
 const input=join(folder,'input.json'),output=join(folder,'clean.json');
 writeFileSync(input,JSON.stringify(source));
 assert.equal(readExport(input).count,1);
 const cwd=fileURLToPath(new URL('../',import.meta.url));
 const run=()=>spawnSync(process.execPath,['scripts/xiaoxu-xxt-local-bridge.mjs','--file',input,'--out',output],{
   cwd,encoding:'utf8'
 });
 let first=run();assert.equal(first.status,0,first.stderr);
 const exported=JSON.parse(readFileSync(output,'utf8'));
 assert.equal(exported.tasks[0].externalId,'work:123:456');
 assert.ok(!JSON.stringify(exported).includes('private-token'));
 if(process.platform!=='win32')assert.equal(statSync(output).mode&0o777,0o600);
 const second=run();
 assert.notEqual(second.status,0,'must not silently overwrite an old export');
 assert.ok(!second.stderr.includes('private-token'));
}finally{rmSync(folder,{recursive:true,force:true});}
console.log('校序 xxt 本机桥接：课程作业 ID、签名清理、仅部分覆盖、错误拒绝、独占安全导出通过');
