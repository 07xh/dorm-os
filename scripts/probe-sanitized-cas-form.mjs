import {inspectCasForm} from '../lib/academic-ncwu-secondclass-server.mjs';
import * as cheerio from 'cheerio';
const url='https://authserver.ncwu.edu.cn/authserver/login?service='+encodeURIComponent('https://sec.ncwu.edu.cn/rump_frontend/loginFromCas/');
const r=await fetch(url,{redirect:'error',signal:AbortSignal.timeout(25000)});
const html=await r.text();
const $=cheerio.load(html);
const safe=s=>/^[A-Za-z_$][\w$.-]{0,70}$/.test(s||'')?s:null;
const summary=[];
let formInspection;
try{inspectCasForm(html,url);formInspection={supported:true};}
catch(e){formInspection={code:e.code||'other',reason:e.reason||'none'};}
for(const m of html.matchAll(/<form\b([^>]*)>([\s\S]*?)<\/form\s*>/gi)){
 const rawAttrs=m[1],body=m[2];const seen=new Set(),duplicates=[];
 for(const a of rawAttrs.matchAll(/([A-Za-z_][A-Za-z_0-9:-]*)\s*(?:=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?/g)){
  const key=a[1].toLowerCase();if(seen.has(key))duplicates.push(key);seen.add(key);
 }
 const f=$('<form '+rawAttrs+'>'+body+'</form>').find('form').first();
 const parsed=cheerio.load(m[0])('form').first();
 summary.push({id:safe(parsed.attr('id')),method:parsed.attr('method'),
  formDuplicates:duplicates,
  inputDuplicates:Array.from(body.matchAll(/<input\b([^>]*)>/gi),t=>{
   const seen=new Set(),duplicates=[];
   for(const a of t[1].matchAll(/([A-Za-z_][A-Za-z_0-9:-]*)\s*(?:=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?/g)){
    const key=a[1].toLowerCase();if(seen.has(key))duplicates.push(key);seen.add(key);
   }
   return duplicates;
  }).filter(a=>a.length),
  inputs:parsed.find('input').map((_,e)=>({id:safe($(e).attr('id')),name:safe($(e).attr('name')),
   type:$(e).attr('type')||'text',valueOver1000:($(e).attr('value')||'').length>1000})).get(),
  submitHandler:parsed.attr('onsubmit')?.match(/doPcMobilesubmit/)?'doPcMobilesubmit':'other',
  hiddenOversize:parsed.find('input[type=hidden]').toArray().some(e=>($(e).attr('value')||'').length>1000)});
}
console.log('Sanitized CAS form structure: '+JSON.stringify({httpStatus:r.status,formInspection,forms:summary}));