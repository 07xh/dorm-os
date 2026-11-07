/* Synthetic second-class read-only session transcript; no school network or real identity. */
import assert from 'node:assert/strict';
import {makeNcwuSecondClassServerAdapter,makeSecondClassSessionJar}
 from '../lib/academic-ncwu-secondclass-server.mjs';
import {NCWU_SECOND_CLASS_READS} from '../lib/academic-ncwu-second-class.mjs';
const school='https://dekt.ncwu.edu.cn';
const service='https://sec.ncwu.edu.cn/rump_frontend/loginFromCas/';
const casUrl='https://authserver.ncwu.edu.cn/authserver/login?service='+encodeURIComponent(service);
const response=(status,body='',headers={})=>new Response(body,{status,headers});
// The retired adapter must reject all credential inputs before any request.
let networkReads=0;
const retired=makeNcwuSecondClassServerAdapter({fetcher:async()=>{
 networkReads++;throw Error('must not contact school');
}});
for(const input of [{username:'SYNTHETIC_ACCOUNT',password:'DO_NOT_SEND'},{}])
 await assert.rejects(()=>retired.login(input),
  e=>e.code==='interactive_login_required'&&e.reason==='official_app_authorization_required');
assert.equal(networkReads,0);

// Existing encrypted sessions remain read-only and independently identity-bound.
const student='SYNTHETIC_STUDENT';
const original={studentCode:student,cookies:[{
 name:'school_ss',value:'synthetic-old',domain:'ncwu.edu.cn',path:'/',hostOnly:false
}]};
const calls=[];
async function readonlySchool(url,options){
 const u=new URL(url);calls.push({host:u.hostname,path:u.pathname,method:options.method});
 assert.equal(u.hostname,'dekt.ncwu.edu.cn');
 assert.equal(options.method,'POST');
 assert.ok(Object.values(NCWU_SECOND_CLASS_READS).includes(u.pathname));
 assert.equal(new URLSearchParams(options.body).has('password'),false);
 assert.ok(options.headers.cookie?.includes('school_ss='));
 if(u.pathname===NCWU_SECOND_CLASS_READS.scores)
  return response(200,JSON.stringify({data:[{student_code:student,
   school_term_index:100,score_value:4.5,score_plus:2,activity_type1_score:1.5}],
   startIndex:0,totalPage:0,totalSize:1}));
 if(u.pathname===NCWU_SECOND_CLASS_READS.scoreTypes)
  return response(200,JSON.stringify([{code:'1',name:'志愿服务'}]));
 if(u.pathname===NCWU_SECOND_CLASS_READS.activities)
  return response(200,JSON.stringify({data:[{id:'synthetic-1',activity_name:'合成活动',
   student_code:student,school_year:'2026-2027',school_term:'1',signup_state:'1',
   verify_state:'1',activity_state:'5',score_value:1.5}],totalPage:1,totalSize:1,startIndex:1}),
   {'set-cookie':'school_ss=synthetic-rotated; Domain=ncwu.edu.cn; Path=/; Secure'});
 throw Error('unexpected readonly endpoint');
}
const adapter=makeNcwuSecondClassServerAdapter({fetcher:readonlySchool});
const reader=await adapter.forSession({session:original,userId:'synthetic-owner'});
const verified=await reader.verifySession();
assert.equal(verified.authorized,true);
assert.equal(verified.principalBoundTo,'synthetic-owner');
const page=await reader.readPage({cursor:null,session:verified.session});
assert.equal(page.activities[0].points,1.5);
const scores=await reader.readOfficialScores();
assert.equal(scores.aggregate.qualityScore,4.5);
assert.equal(scores.aggregate.volunteerHours,2);
assert.equal(reader.getRefreshedSession().cookies.find(c=>c.name==='school_ss').value,'synthetic-rotated');
assert.equal(original.cookies[0].value,'synthetic-old','a read cannot mutate the stored input snapshot');
assert.ok(calls.every(c=>c.host==='dekt.ncwu.edu.cn'),'personal reads never POST credentials to CAS');
assert.equal(typeof adapter.preflight,'undefined','legacy form probe is no longer part of the adapter');
await assert.rejects(()=>adapter.login({username:'SYNTHETIC_ACCOUNT',password:'DO_NOT_SEND'}),
 e=>e.code==='interactive_login_required');

const mismatched=await adapter.forSession({session:{...original,studentCode:'OTHER_SYNTHETIC'},userId:'synthetic-owner'});
await assert.rejects(()=>mismatched.verifySession(),e=>e.code==='authorization_failed');
const expired=makeNcwuSecondClassServerAdapter({fetcher:async()=>response(200,'<html>Login required</html>')});
const expiredReader=await expired.forSession({session:original,userId:'synthetic-owner'});
await assert.rejects(()=>expiredReader.verifySession(),e=>e.code==='authorization_failed');

// Header compatibility and host-only scope still protect saved read sessions.
const jar=makeSecondClassSessionJar();
const cookieLines=['SESSION=synthetic-cas; Path=/authserver/; HttpOnly',
 'school_locale=synthetic; Domain=ncwu.edu.cn; Path=/; Expires=Wed, 21 Oct 2037 07:28:00 GMT'];
jar.merge({headers:{getAll:()=>cookieLines,get:()=>cookieLines.join(', ')}},new URL(casUrl));
assert.ok(jar.header(new URL(casUrl)).includes('SESSION=synthetic-cas'));
assert.ok(jar.header(new URL(casUrl)).includes('school_locale=synthetic'));
assert.ok(!jar.header(new URL(service)).includes('SESSION='));
jar.merge(response(200,'',{'set-cookie':'spoof=x; Domain=authserver.ncwu.edu.cn; Path=/'}),new URL(school));
assert.ok(!jar.header(new URL(casUrl)).includes('spoof='));

// Existing-session read rejects cross-origin redirects before contacting them.
let outbound=0;
const invalidRedirect=makeNcwuSecondClassServerAdapter({fetcher:async()=>{
 outbound++;return response(302,'',{location:'https://outsider.example/'});
}});
const invalidReader=await invalidRedirect.forSession({session:original,userId:'synthetic-owner'});
await assert.rejects(()=>invalidReader.verifySession(),e=>e.code==='authorization_failed');
assert.equal(outbound,1,'no request may leave the fixed school allowlist');
console.log('Second-class read-only transport: password transport blocked, existing-session identity, reads, rotation, redirect and cookie scope passed');
