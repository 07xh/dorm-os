#!/usr/bin/env node
/* Run locally on the owner's device. Never sends credentials to Xiaoxu. */
import {writeFileSync} from 'node:fs';
import {readExport,runXxt} from '../lib/xiaoxu-xxt-local-bridge.mjs';

function flagsOf(argv){
 const flags={};
 for(let i=0;i<argv.length;i++){
  const arg=argv[i];
  if(!['--file','--run','--course','--clazzid','--out'].includes(arg)||flags[arg]!==undefined)
   throw Error('未知/重复参数：仅支持 --file 或 --run，以及 --course、--clazzid、--out');
  const value=argv[++i];
  if(!value||value.startsWith('--'))throw Error('缺少参数 '+arg);
  flags[arg]=value;
 }
 if(Boolean(flags['--file'])===Boolean(flags['--run']))throw Error('必须且只能选择 --file 或 --run');
 if(!flags['--out'])throw Error('需要指定 --out 导出文件');
 if(flags['--file']&&(flags['--course']||flags['--clazzid']))throw Error('本地文件模式无需课程参数');
 return flags;
}
try{
 const args=flagsOf(process.argv.slice(2));
 const clean=args['--file']?readExport(args['--file']):runXxt({
  mode:args['--run'],course:args['--course']||'',clazzid:args['--clazzid']||''
 });
 // Exclusive creation; never overwrite an existing personal data export.
 writeFileSync(args['--out'],JSON.stringify(clean.batch,null,2)+'\n',{
  encoding:'utf8',flag:'wx',mode:0o600
 });
 console.log('已输出安全导入文件：作业 '+clean.count+' 条；截止日期未核实 '+clean.unknownDeadlines+' 条。');
 console.log('数据范围：部分（非云端同步）。进入校序「我的 → 数据连接与备份 → 数据迁移与手动导入」导入。');
}catch(e){
 // Do not log raw xxt responses or signed links.
 console.error('导出未完成：'+String(e.message||'错误').slice(0,160));
 process.exitCode=1;
}
