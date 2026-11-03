/**
 * Server-only transport for the NCWU timetable HTML observed in the owner's
 * de-identified HAR. No login, credential collection or trusted adapter here:
 * an independently authenticated, owner-bound session is a prerequisite.
 * Never expose the cookie, source URL (it may include a student identifier),
 * or raw page to the browser or logs.
 */
import {load} from 'cheerio';
import {parseNcwuStudentTimetableHtml} from './academic-ncwu-html-parser.mjs';

const ORIGIN='https://jwmis.ncwu.edu.cn';
const ENTRY=ORIGIN+'/hsjw/student/xkjg.wdkb.jsp';
const DETAIL='/hsjw/student/wsxk.xskcb10319.jsp';
const SEMESTER=/^\d{4}-\d{4}-[1-3]$/;
export class NcwuPageReadError extends Error {
 constructor(code){super(code);this.code=code;this.name='NcwuPageReadError';}
}
const fail=code=>{throw new NcwuPageReadError(code);};

export function findNcwuTimetableUrl(entryHtml) {
 if(typeof entryHtml!=='string'||entryHtml.length>750_000)fail('invalid_entry');
 const $=load(entryHtml);
 if($('input[type=password], form[action*="/authserver/login"]').length)
  fail('session_expired');
 const raw=$('iframe[src],frame[src]').toArray().map(el=>$(el).attr('src')||'');
 const urls=[];
 for(const value of raw){
  if(!value.includes('wsxk.xskcb10319.jsp'))continue;
  let url;
  try{url=new URL(value,ENTRY);}catch{fail('invalid_detail_url');}
  if(url.origin!==ORIGIN||url.pathname!==DETAIL||url.username||url.password||
     url.hash||url.href.length>3500)fail('invalid_detail_url');
  if(!url.searchParams.has('params')||!url.searchParams.get('params'))
   fail('timetable_parameter_missing');
  urls.push(url.href);
 }
 const unique=[...new Set(urls)];
 if(unique.length!==1)fail(unique.length?'ambiguous_detail_url':'detail_url_missing');
 return unique[0];
}

async function readOfficialHtml(fetchImpl,url,cookie) {
 let response;
 try{
  response=await fetchImpl(url,{
   method:'GET',redirect:'manual',
   headers:{Cookie:cookie,Accept:'text/html,application/xhtml+xml',
    Referer:ENTRY,'Cache-Control':'no-cache'}
  });
 }catch{fail('upstream_unavailable');}
 if(!response||!Number.isInteger(response.status))fail('upstream_unavailable');
 if([301,302,303,307,308,401,403].includes(response.status))
  fail('session_expired');
 if(response.status!==200)fail('upstream_unavailable');
 const contentType=response.headers?.get?.('content-type')||'';
 if(contentType&&!/text\/html|application\/xhtml\+xml/i.test(contentType))
  fail('unexpected_content_type');
 let bytes;
 try{bytes=await response.arrayBuffer();}catch{fail('upstream_unavailable');}
 if(!bytes||bytes.byteLength<30||bytes.byteLength>750_000)
  fail('invalid_html_size');
 const declaration=new TextDecoder('latin1').decode(bytes.slice(0,1500));
 const charset=/charset\s*=\s*["']?([a-z0-9_-]+)/i.exec(contentType)?.[1]||
  /charset\s*=\s*["']?([a-z0-9_-]+)/i.exec(declaration)?.[1]||'utf-8';
 const encoding=/^(?:gbk|gb2312|gb18030)$/i.test(charset)?'gb18030':charset;
 let html;
 try{html=new TextDecoder(encoding,{fatal:true}).decode(bytes);}
 catch{fail('decode_unavailable');}
 if(/<input\b[^>]*type\s*=\s*["']?password/i.test(html))
  fail('session_expired');
 return html;
}

/** Returns an UNVERIFIED candidate and counts, not a D1 trusted snapshot. */
export async function readNcwuTimetableCandidate({
 cookie,semester,fetchImpl=fetch
}={}) {
 if(typeof cookie!=='string'||cookie.length<3||cookie.length>8000||
    /[\r\n\u0000]/.test(cookie)||!SEMESTER.test(semester||'')||
    typeof fetchImpl!=='function')fail('invalid_session_input');
 const entry=await readOfficialHtml(fetchImpl,ENTRY,cookie);
 const detailUrl=findNcwuTimetableUrl(entry);
 const detail=await readOfficialHtml(fetchImpl,detailUrl,cookie);
 const parsed=parseNcwuStudentTimetableHtml(detail,{semester});
 if(parsed.verified!==false||parsed.trustedSyncAllowed!==false)
  fail('unexpected_trust_state');
 return parsed;
}
