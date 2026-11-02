/* 校序：将 xuexitong-cli 的只读 hw / hw-ls --json 输出转换为本站快照。
 * 拒绝不含可信 workId 的记录。绝不保存详情链接、签名或账号 Cookie。
 * 独立适配层；参考 DanMo661/xuexitong-cli (MIT) 的公开 CLI 输出契约。
 */
(function(root){
 'use strict';
 const FORMAT='xiaoxu-task-batch-v1';
 function hasOwn(o,k){return Object.prototype.hasOwnProperty.call(o,k);}
 function stableWorkId(raw){
   if(typeof raw!=='string'||raw.length>3000)throw Error('学习通作业链接无效');
   let url;
   try{url=new URL(raw);}catch{throw Error('学习通作业缺少有效详情链接，无法安全去重');}
   const hostname=url.hostname.toLowerCase();
   if(url.protocol!=='https:'||url.username||url.password||url.port||
      !(hostname==='chaoxing.com'||hostname.endsWith('.chaoxing.com')))
     throw Error('学习通作业链接不是可信的 HTTPS 超星域名');
   const pairs=[...url.searchParams.entries()];
   const byNames=(names)=>{
     const vals=pairs.filter(([k])=>names.includes(k.toLowerCase())).map(([,v])=>v);
     if(new Set(vals).size>1)throw Error('作业链接存在互相冲突的任务标识');
     return vals[0]||'';
   };
   const work=byNames(['workid','work_id']);
   const clazz=byNames(['classid','clazzid']);
   const course=byNames(['courseid','course_id']);
   if(!/^[A-Za-z0-9_-]{1,48}$/.test(work))throw Error('作业链接缺少稳定 workId，不使用标题猜测作业身份');
   const scope=clazz||course;
   if(scope&&!/^[A-Za-z0-9_-]{1,28}$/.test(scope))throw Error('课程/班级标识无效');
   return 'work:'+work+(scope?':'+scope:'');
 }
 function deadlineParts(raw){
   if(raw===null||raw===undefined||raw==='')return {due:'',dueAt:'',unverified:false};
   if(typeof raw!=='string'||raw.length>80)throw Error('学习通截止时间格式不支持');
   const m=/^(\d{4}-\d\d-\d\d)[ T](\d\d):(\d\d)$/.exec(raw.trim());
   // Upstream fallback sometimes contains only MM-DD HH:mm; never guess the year.
   if(!m)return {due:'',dueAt:'',unverified:true};
   const [_,date,hour,minute]=m;
   const d=new Date(date+'T00:00:00Z');
   if(!Number.isFinite(d.getTime())||d.toISOString().slice(0,10)!==date||+hour>23||+minute>59)
     throw Error('学习通截止时间不是有效日期');
   return {due:date,dueAt:date+'T'+hour+':'+minute+':00+08:00',unverified:false};
 }
 function statusOf(raw){
   const value=typeof raw==='string'?raw.trim():'';
   if(!value)return 'unknown';
   if(/退回|重做|重新提交/.test(value))return 'returned';
   if(/已批阅|已评分|已评阅|已打分/.test(value))return 'graded';
   if(/未交|未提交|待提交|未完成|已截止|待完成/.test(value))return 'todo';
   if(/已交|已提交|待批阅|待评阅|已完成/.test(value))return 'submitted';
   return 'unknown';
 }
 function adaptCli(raw){
   let entries,from;
   if(Array.isArray(raw)){
     if(raw.length===0)throw Error('空列表无法证明课程读取完整，请使用校序标准快照或重新查询');
     if(!raw.every(x=>x&&typeof x==='object'&&!Array.isArray(x)&&
         hasOwn(x,'course')&&hasOwn(x,'title')&&hasOwn(x,'url')&&hasOwn(x,'status')&&hasOwn(x,'deadline')))
       throw Error('不是 xuexitong-cli hw --json 作业截止看板');
     entries=raw;from='hw';
   }else if(raw&&typeof raw==='object'&&!Array.isArray(raw)&&
         typeof raw.course==='string'&&Array.isArray(raw.items)){
     if(!raw.items.length)throw Error('空作业列表不能证明抓取成功，不进行导入');
     if(!raw.items.every(x=>x&&typeof x==='object'&&!Array.isArray(x)&&
       hasOwn(x,'index')&&hasOwn(x,'title')&&hasOwn(x,'status')&&hasOwn(x,'url')))
       throw Error('不是 xuexitong-cli hw-ls --json 输出');
     entries=raw.items.map(x=>({...x,course:raw.course,deadline:''}));from='hw-ls';
   }else throw Error('不是受支持的 xuexitong-cli 作业 JSON');
   if(entries.length>300)throw Error('作业数量超过单次 300 条上限');
   const seen=new Set();let unknownDeadlines=0;
   const tasks=entries.map((row,i)=>{
     if(typeof row.course!=='string'||!row.course.trim()||row.course.trim().length>80||
       typeof row.title!=='string'||!row.title.trim()||row.title.trim().length>100)
       throw Error('第 '+(i+1)+' 条课程或作业标题无效');
     const externalId=stableWorkId(row.url);
     if(seen.has(externalId))throw Error('同一文件中存在重复作业 ID，不能安全合并');
     seen.add(externalId);
     const d=deadlineParts(row.deadline);
     if(d.unverified)unknownDeadlines++;
     return {externalId,title:row.title.trim(),course:row.course.trim(),
       due:d.due,dueAt:d.dueAt,status:statusOf(row.status)};
   });
   return {batch:{format:FORMAT,source:'chaoxing',tasks},sourceFormat:from,
      unknownDeadlines,contentCount:entries.length};
 }
 root.XiaoxuChaoxingCli=Object.freeze({adaptCli,stableWorkId,deadlineParts,statusOf});
})(globalThis);
