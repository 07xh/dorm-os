/* Synthetic NCWU Qingguo native CAS transcript, never a real credential or HAR. */
import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
import {makeNcwuQingguoServerAdapter,NcwuQingguoError} from '../lib/academic-ncwu-qingguo-server.mjs';
globalThis.crypto??=webcrypto;
const CAMPUS='https://authserver.ncwu.edu.cn';
const SCHOOL='https://jwmis.ncwu.edu.cn';
const SERVICE=SCHOOL+'/hsjw/caslogin';
const CAS=CAMPUS+'/authserver/login?service='+encodeURIComponent(SERVICE);
const student='TEST_STUDENT_01',password='NEVER_REAL_SCHOOL_PASSWORD';
const form='<html><form method="post" action="'+CAS+'">'+
 '<input name="username"><input type="password" name="password">'+
 '<input type="hidden" name="execution" value="e1s1">'+
 '<input type="hidden" name="_eventId" value="submit"></form></html>';
const headers={'content-type':'text/html;charset=UTF-8'};
const reply=(status,content='',extra={})=>new Response(content,{status,headers:{...headers,...extra}});
const meta='<input type="hidden" id="xh" value="'+student+'">'+
 '<input type="hidden" id="xn" value="2026">'+
 '<input type="hidden" id="xq_m" value="1">';
const entry='<html>'+meta+'<iframe src="/hsjw/student/wsxk.xskcb10319.jsp?params=FAKE_TERM_ONLY"></iframe></html>';
const title='合成电路',teacher='虚构老师',room='虚构教室A101';
const block='<div style="padding-bottom:5px">'+title+'<br>'+teacher+'<br>1-8周[1-2]<br>'+room+'</div>';
const grid='<table id="mytable"><tr>'+Array.from({length:7},(_,i)=>
 '<td class="td">'+(i===0?block:'')+'</td>').join('')+'</tr></table>';
const columns=['上课班级代码','课程','总学时','学分','修读性质','任课教师','选课状态','上课时间地点'];
function detail(value='1-8周 一[1-2] '+room){
 return '<table id="mytable"><tr>'+columns.map(x=>'<td>'+x+'</td>').join('')+'</tr>'+
  '<tr><td>CLASS-CODE-FAKE</td><td>'+title+'</td><td>32</td><td>2</td>'+
  '<td>必修</td><td>'+teacher+'</td><td>已选</td><td>'+value+'</td></tr></table>';
}
const page=value=>'<html><input id="xn" value="2026"><input id="xq_m" value="1">'+grid+detail(value)+'</html>';
function school({second=false,identity=student,detailTime,challenge=false,cryptoForm=false,redirectAway=false}={}){
 const calls=[];
 async function fetcher(url,options={}){
  const u=new URL(url),method=options.method;
  calls.push({host:u.hostname,path:u.pathname,method,hasCookie:!!(options.headers?.cookie||options.headers?.Cookie)});
  if(u.hostname==='authserver.ncwu.edu.cn'&&u.pathname==='/authserver/login'&&method==='GET')
   return reply(200,challenge?form.replace('</form>','<input name="captcha"></form>'):
    cryptoForm?form+'<script>encryptPassword();</script>':form,
    {'set-cookie':'CAS_FAKE=authorization; Path=/authserver/; Secure'});
  if(u.hostname==='authserver.ncwu.edu.cn'&&method==='POST'){
   const p=new URLSearchParams(options.body);
   assert.equal(p.get('username'),student);
   assert.equal(p.get('password'),password);
   return reply(302,'',{location:redirectAway?'https://foreign.invalid/capture':
    SERVICE+'?ticket=FAKE_TICKET_DO_NOT_REPLAY'});
  }
  if(u.hostname==='jwmis.ncwu.edu.cn'&&u.pathname==='/hsjw/caslogin')
   return reply(302,'',{location:SCHOOL+'/hsjw/frame/home.jsp',
    'set-cookie':'QG_SESS=school-authorized; Path=/hsjw/; Secure'});
  if(u.hostname==='jwmis.ncwu.edu.cn'&&u.pathname==='/hsjw/frame/home.jsp'){
   if(second)return reply(200,'<html><form method="post" action="/hsjw/cas/logon.action">'+
    '<input name="username"><input type="password" name="password">'+
    '<input type="hidden" name="loginmethod" value="plain"></form></html>');
   return reply(200,'<html>Authorized synthetic Qingguo portal</html>');
  }
  if(u.hostname==='jwmis.ncwu.edu.cn'&&u.pathname==='/hsjw/cas/logon.action'){
   const p=new URLSearchParams(options.body);
   assert.equal(p.get('username'),student);
   assert.equal(p.get('password'),'SECOND_PASSWORD_FAKE');
   assert.equal(p.get('loginmethod'),'plain');
   return reply(302,'',{location:'/hsjw/frame/authorized.jsp'});
  }
  if(u.hostname==='jwmis.ncwu.edu.cn'&&u.pathname==='/hsjw/frame/authorized.jsp')
   return reply(200,'<html>Authorized second-step portal</html>');
  if(u.hostname==='jwmis.ncwu.edu.cn'&&u.pathname==='/hsjw/student/xkjg.wdkb.jsp'){
   assert.ok((options.headers?.cookie||options.headers?.Cookie||'').includes('QG_SESS=school-authorized'),
    'the official school cookie must be scoped to the school origin');
   return reply(200,entry.replace('value="'+student+'"','value="'+identity+'"'));
  }
  if(u.hostname==='jwmis.ncwu.edu.cn'&&u.pathname==='/hsjw/student/wsxk.xskcb10319.jsp')
   return reply(200,page(detailTime));
  throw Error('Unexpected synthetic school request');
 }
 return {fetcher,calls};
}
const deniedSchool=school();
const gate=makeNcwuQingguoServerAdapter({fetcher:deniedSchool.fetcher});
assert.deepEqual(await gate.preflight(),{
 loginSupported:false,mode:'schedule_review_pending'
});
await assert.rejects(()=>gate.login({username:student,password}),
 e=>e instanceof NcwuQingguoError&&e.code==='interactive_login_required');
assert.ok(deniedSchool.calls.every(x=>x.method==='GET'),
 'disabled trust gate must not send credentials');

const fake=school();
const adapter=makeNcwuQingguoServerAdapter({fetcher:fake.fetcher,trustedSyncEnabled:true});
assert.deepEqual(await adapter.preflight(),{loginSupported:true,mode:'password_form_verified'});
const result=await adapter.login({username:student,password});
assert.equal(result.session.studentId,student);
assert.equal(result.session.semester,'2026-2027-1');
assert.ok(!JSON.stringify(result.session).includes(password));
assert.ok(!JSON.stringify(result.session).includes('FAKE_TICKET_DO_NOT_REPLAY'));
assert.ok(fake.calls.every(x=>['jwmis.ncwu.edu.cn','authserver.ncwu.edu.cn'].includes(x.host)));
const read=await adapter.forSession({session:result.session,userId:'owner-synthetic'});
await assert.rejects(()=>read.readSection({kind:'schedule',session:{studentId:student,semester:'2026-2027-1'}}),
 e=>e.code==='authorization_failed','no data write without fresh identity proof');
const proof=await read.verifySession();
assert.equal(proof.principalBoundTo,'owner-synthetic');
assert.equal(proof.officialOrigin,SCHOOL);
assert.equal(proof.identityBound,true);
const schedule=await read.readSection({kind:'schedule',session:proof.session});
assert.equal(schedule.complete,true);
assert.equal(schedule.records.length,1);
assert.match(schedule.records[0].externalId,/^qg-[a-f0-9]{32}$/);
assert.equal(schedule.records[0].title,title);
assert.equal(schedule.records[0].weekday,1);
assert.deepEqual(schedule.records[0].weeks,[1,2,3,4,5,6,7,8]);
assert.equal(schedule.records[0].term,'2026-2027-1');
const again=await read.readSection({kind:'schedule',session:proof.session});
assert.equal(again.records[0].externalId,schedule.records[0].externalId);
assert.ok(!JSON.stringify(schedule).includes(password));

const second=school({second:true});
const twoStep=makeNcwuQingguoServerAdapter({fetcher:second.fetcher,trustedSyncEnabled:true});
await assert.rejects(()=>twoStep.login({username:student,password}),
 e=>e.code==='interactive_login_required');
assert.equal(second.calls.filter(c=>c.path==='/hsjw/cas/logon.action').length,0);
const twoLogin=await twoStep.login({username:student,password,
 academicUsername:student,academicPassword:'SECOND_PASSWORD_FAKE'});
assert.equal(twoLogin.session.studentId,student);
assert.equal(second.calls.filter(c=>c.path==='/hsjw/cas/logon.action').length,1);
assert.ok(!JSON.stringify(twoLogin.session).includes('SECOND_PASSWORD_FAKE'));

const other=school({identity:'ANOTHER_STUDENT'});
await assert.rejects(()=>makeNcwuQingguoServerAdapter({
 fetcher:other.fetcher,trustedSyncEnabled:true
}).login({username:student,password}),e=>e.code==='authorization_failed');
const invalidRedirect=school({redirectAway:true});
await assert.rejects(()=>makeNcwuQingguoServerAdapter({
 fetcher:invalidRedirect.fetcher,trustedSyncEnabled:true
}).login({username:student,password}),e=>e.code==='interactive_login_required');
const interactive=school({challenge:true});
const challenged=makeNcwuQingguoServerAdapter({
 fetcher:interactive.fetcher,trustedSyncEnabled:true
});
assert.deepEqual(await challenged.preflight(),{loginSupported:false,mode:'interactive_login_required',
 reason:'interactive_challenge_required'});
await assert.rejects(()=>challenged.login({username:student,password}),
 e=>e.code==='interactive_login_required');
assert.ok(interactive.calls.every(c=>c.method==='GET'));

// Crypto-enabled official CAS pages must not receive a plain password POST.
const encryptedSchool=school({cryptoForm:true});
const encryptionBlocked=makeNcwuQingguoServerAdapter({
 fetcher:encryptedSchool.fetcher,trustedSyncEnabled:true
});
assert.deepEqual(await encryptionBlocked.preflight(),{
 loginSupported:false,mode:'interactive_login_required',reason:'password_transform_required'
});
await assert.rejects(()=>encryptionBlocked.login({username:student,password}),
 e=>e instanceof NcwuQingguoError&&e.code==='interactive_login_required'&&
 e.reason==='password_transform_required');
assert.ok(encryptedSchool.calls.every(c=>c.method==='GET'));

const incomplete=school({detailTime:'1-8周'});
const incompleteAdapter=makeNcwuQingguoServerAdapter({
 fetcher:incomplete.fetcher,trustedSyncEnabled:true
});
const incompleteLogin=await incompleteAdapter.login({username:student,password});
const candidate=await incompleteAdapter.forSession({session:incompleteLogin.session,userId:'owner'});
const candidateProof=await candidate.verifySession();
await assert.rejects(()=>candidate.readSection({kind:'schedule',session:candidateProof.session}),
 e=>e.code==='schedule_review_pending');
console.log('NCWU Qingguo native CAS: origin-bound SSO, optional secondary login, student identity, derived keys, full HTML gate, safe re-read and failure checks passed');
