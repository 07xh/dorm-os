import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import {webcrypto} from 'node:crypto';

const source=readFileSync(
  'apps/xiaoxu-android/app/src/main/assets/ncwu_secondclass_local.js','utf8');
const host='https://dekt.ncwu.edu.cn';
const account='SYNTHETIC_SELF';
const filters=['报名中','待验证','即将开始报名'];
const semester={student_code:account,school_term_index:1,score_value:0,score_plus:0,
  ...Object.fromEntries(Array.from({length:6},(_,i)=>['activity_type'+(i+1)+'_score',0]))};
const envelope=(data=[])=>({data,totalSize:data.length,
  totalPage:data.length?1:0,startIndex:0});
async function read({nonemptyAt=null,failedAt=null,changingAccountAt=null}={}){
  const requests=[];
  const location={origin:host,pathname:'/sys/stu/score'};
  let current=account;
  const document={querySelectorAll:()=>[{
    getClientRects:()=>[{}],get innerText(){return '本人['+current+']';}
  }]};
  const fetch=async(url,opts)=>{
    requests.push({url,body:opts.body,method:opts.method});
    assert.equal(opts.credentials,'same-origin');
    assert.equal(opts.redirect,'error');
    assert.equal(opts.method,'POST');
    assert.equal(opts.headers.cookie,undefined);
    assert.equal(opts.headers.authorization,undefined);
    let result;
    if(url===host+'/sys/stu/score/list')
      result=envelope([semester]);
    else if(url===host+'/sys/stu/center/signupactivity/list') {
      assert.equal(opts.body,'page=1&pagesize=50');
      result=envelope([]);
    }else {
      assert.equal(url,host+'/sys/stu/activity');
      const title=new URLSearchParams(opts.body).get('title');
      assert.ok(filters.includes(title));
      assert.deepEqual([...new URLSearchParams(opts.body).keys()],['title']);
      if(changingAccountAt===title)current='ANOTHER_ACCOUNT';
      result=title===nonemptyAt?
        envelope([{activity_id:'SYNTHETIC_ACTIVITY',activity_name:'SYNTHETIC_PRIVATE'}]):
        envelope([]);
      if(title===failedAt)
        return {status:503,url,redirected:false,headers:{get:()=> 'text/html'},
          text:async()=>'<html>SYNTHETIC_SECRET</html>'};
    }
    return {status:200,url,redirected:false,headers:{get:()=> 'application/json'},
      text:async()=>JSON.stringify(result)};
  };
  const fn=runInNewContext(source,{fetch,location,document,
    AbortController,setTimeout,clearTimeout,crypto:webcrypto,TextEncoder});
  const result=await fn('a'.repeat(32),true,true);
  assert.equal(JSON.stringify(result).includes('SYNTHETIC_PRIVATE'),false);
  assert.equal(JSON.stringify(result).includes('ANOTHER_ACCOUNT'),false);
  return {result,requests};
}

test('one score identity + personal page + three real empty catalog filters',async()=>{
  const {result,requests}=await read();
  assert.equal(result.ok,true);
  assert.equal(result.identityMatched,true);
  assert.equal(result.httpStatus,200,'catalog requests must preserve score reader status');
  assert.equal(result.activitiesComplete,true);
  assert.deepEqual(Array.from(result.activities),[]);
  assert.equal(result.catalogStatus,'verified_empty');
  assert.deepEqual(Array.from(result.catalogEmptyFilters),filters);
  assert.equal(result.fullSyncVerified,false);
  assert.equal(requests.length,5);
  assert.deepEqual(requests.slice(2).map(x=>new URLSearchParams(x.body).get('title')),filters);
  assert.ok(requests.every(x=>!x.url.includes('/sys/stu/sys/stu/activity')));
});

test('unknown nonempty activities NEVER become invented verified directory',async()=>{
  const {result,requests}=await read({nonemptyAt:'待验证'});
  assert.equal(result.ok,true,'personal score can still be saved');
  assert.equal(result.catalogStatus,'nonempty_unverified');
  assert.deepEqual(Array.from(result.catalogEmptyFilters),[]);
  assert.equal(result.catalogActivities,undefined);
  assert.equal(requests.length,4,'stop on first nonempty filter');
});

test('server failures do not lie that all three filters are empty',async()=>{
  const {result,requests}=await read({failedAt:'即将开始报名'});
  assert.equal(result.ok,true);
  assert.equal(result.catalogStatus,'unavailable');
  assert.deepEqual(Array.from(result.catalogEmptyFilters),[]);
  assert.equal(requests.length,5);
});

test('switching the visible owner while catalog is read rejects entire snapshot',async()=>{
  const {result}=await read({changingAccountAt:'报名中'});
  assert.equal(result.ok,false);
  assert.equal(result.stage,'identity');
  assert.equal(result.rows,undefined);
  assert.equal(result.accountHash,undefined);
});


test('native second-class tabs retain independent scroll and prevent duplicate refresh',()=>{
  const page=readFileSync(
    'apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/SecondClassPage.kt','utf8');
  for(const name of ['catalogScroll','activitiesScroll','scoresScroll'])
    assert.match(page,new RegExp('val '+name+' = rememberScrollState\\(\\)'));
  assert.match(page,/0 -> catalogScroll[\s\S]*1 -> activitiesScroll[\s\S]*else -> scoresScroll/);
  assert.match(page,/verticalScroll\(currentScroll\)/);
  assert.doesNotMatch(page,/verticalScroll\(rememberScrollState\(\)\)/);
  assert.match(page,/if \(refreshing\) return/);
  assert.match(page,/refreshing = true[\s\S]*reader\.launch/);
  assert.match(page,/val succeeded = result\.resultCode == Activity\.RESULT_OK/);
  assert.match(page,/refreshing = false/);
  assert.match(page,/enabled = !refreshing/);
  assert.match(page,/onRefreshResult\?\.invoke\(false\)/);
});
