/**
 * Xiaoxu local xuexitong-cli adapter, only executes read-only xxt commands.
 * No login, cookies, signed URLs or secret credentials are ever uploaded.
 */
import {readFileSync,statSync} from 'node:fs';
import {spawnSync} from 'node:child_process';
import {runInNewContext} from 'node:vm';

const MAX_BYTES=1024*1024;
const adapterCode=readFileSync(new URL('../public/xiaoxu-chaoxing-cli.js',import.meta.url),'utf8');
const sandbox={URL,Date};
runInNewContext(adapterCode,sandbox,{timeout:1500,filename:'xiaoxu-chaoxing-cli.js'});
const adapter=sandbox.XiaoxuChaoxingCli;
if(!adapter?.adaptCli)throw Error('校序学习通适配层未加载');

export function parseSource(raw){
 if(typeof raw!=='string'||Buffer.byteLength(raw,'utf8')>MAX_BYTES)throw Error('输入内容过大');
 let parsed;
 try{parsed=JSON.parse(raw)}catch{throw Error('输入不是有效 xxt JSON；请使用 --json 输出')}
 const clean=adapter.adaptCli(parsed);
 // Serialize into our own realm: never retain the original signed homework URL.
 return {batch:JSON.parse(JSON.stringify(clean.batch)),sourceFormat:clean.sourceFormat,
  count:clean.contentCount,unknownDeadlines:clean.unknownDeadlines,
  coverage:'partial',verifiedPersonalAccount:false};
}
export function readExport(path){
 if(typeof path!=='string'||!path)throw Error('缺少本地输入文件');
 const info=statSync(path);
 if(!info.isFile()||info.size>MAX_BYTES)throw Error('输入文件过大或不是普通文件');
 return parseSource(readFileSync(path,'utf8'));
}
export function runXxt({mode='hw',course='',clazzid='',runner=spawnSync}={}){
 if(!['hw','hw-ls'].includes(mode))throw Error('只允许本地只读 hw 或 hw-ls 命令');
 if(typeof course!=='string'||course.length>100||/[\r\n\0]/.test(course))throw Error('课程名无效');
 const name=course.trim();
 if(mode==='hw-ls'&&!name)throw Error('hw-ls 必须指定具体课程');
 if(mode==='hw'&&clazzid)throw Error('hw 不支持指定班级');
 if(typeof clazzid!=='string'||(clazzid&&!/^\d{1,24}$/.test(clazzid)))
  throw Error('班级标识无效');
 const args=mode==='hw'?['hw',...(name?['--course',name]:[]),'--json']:
  ['hw-ls',name,...(clazzid?['--clazzid',clazzid]:[]),'--json'];
 const result=runner('xxt',args,{
  encoding:'utf8',shell:false,timeout:180000,maxBuffer:2*1024*1024,
  env:{...process.env,NO_COLOR:'1'}
 });
 if(result.error||result.status!==0||result.signal)
  throw Error('xxt 查询失败、未登录或被限制；不能将失败当作无作业');
 // xxt may return code=0 when it skipped failed courses. Fail closed.
 if(String(result.stderr||'').trim())
  throw Error('xxt 报告警告或部分课程未取到，已拒绝不完整的导入');
 return parseSource(String(result.stdout||''));
}
