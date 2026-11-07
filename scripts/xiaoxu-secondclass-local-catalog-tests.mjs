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
async function read({nonemptyAt=null,failedAt=null,changingAccountAt=null,invalidAt=null,partialAt=null}={}){
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
        envelope([{activity_id:'SYNTHETIC_ACTIVITY',activity_name:'最强演说家决赛',
          student_code:'SYNTHETIC_PRIVATE'}]):
        envelope([]);
      if(title===invalidAt)
        result=envelope([{activity_id:'SYNTHETIC_ACTIVITY',person_name:'SYNTHETIC_PRIVATE'}]);
      if(title===partialAt)
        result={data:[{activity_name:'最强演说家决赛'}],totalSize:2,totalPage:2,startIndex:0};
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

test('verified nonempty activity titles are saved without owner or raw response',async()=>{
  const {result,requests}=await read({nonemptyAt:'报名中'});
  assert.equal(result.ok,true);
  assert.equal(result.catalogStatus,'verified_nonempty');
  assert.equal(result.catalogActivities.length,1);
  assert.equal(result.catalogActivities[0].title,'最强演说家决赛');
  assert.equal(result.catalogActivities[0].filter,'报名中');
  assert.deepEqual(Array.from(result.catalogEmptyFilters),filters.slice(1));
  assert.equal(result.catalogActivities[0].student_code,undefined);
  assert.equal(result.catalogActivities[0].activity_id,undefined);
  assert.equal(requests.length,5);
});

test('unknown nonempty fields are never treated as verified',async()=>{
  const {result}=await read({invalidAt:'待验证'});
  assert.equal(result.ok,true,'verified scores must remain independently readable');
  assert.equal(result.catalogStatus,'nonempty_unverified');
  assert.equal(result.catalogActivities.length,0);
  assert.equal(result.catalogEmptyFilters.length,0);
});
test('incomplete catalog pages are rejected instead of silently truncating',async()=>{
  const {result}=await read({partialAt:'报名中'});
  assert.equal(result.ok,true);
  assert.equal(result.catalogStatus,'nonempty_unverified');
  assert.equal(result.catalogActivities.length,0);
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


test('single-page second-class keeps verified data and no duplicate refresh',()=>{
  const page=readFileSync(
    'apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/SecondClassPage.kt','utf8');
  assert.match(page,/LazyColumn\(/, 'one lazy scroll list instead of three stacked scroll views');
  assert.doesNotMatch(page,/catalogScroll|activitiesScroll|scoresScroll|sectionOptions/);
  for(const heading of ['积分概览','我的活动','活动广场','积分明细'])
    assert.ok(page.includes('"'+heading+'"'),'missing section '+heading);
  assert.match(page,/secondClassPoints\(schoolTotal\) \?: "—"/,
    'missing official total is unknown, not fabricated zero');
  assert.match(page,/if \(countConfirmed\) myActivities\.size\.toString\(\) else "—"/,
    'do not show personal activity count without a complete batch');
  assert.match(page,/val catalogActivities = all\.filter/,
    'catalog list reads only persisted validated local records');
  assert.match(page,/visibleCatalogActivities\.isNotEmpty\(\)/,
    'nonempty verified official catalog renders native cards');
  assert.match(page,/if \(catalogRecord != null\) "上次读取：暂无活动" else "活动暂未获取"/);
  assert.doesNotMatch(page,/row\.reviewStatus == "approved"/);
  assert.match(page,/XiaoxuGlassDialog\(/, 'details use existing glass dialog, not inline expansion');
  const header = page.slice(page.indexOf('item(key = "points_header")'),
    page.indexOf('item(key = "metrics")'));
  assert.match(header,/Text\("积分概览"/);
  assert.match(header,/DropdownMenu\(/,'term selector stays in the overview title row');
  assert.doesNotMatch(page,/item\(key = "term_selector"\)/,
    'no separate term card beneath the summary');
  const breakdown = page.slice(page.indexOf('item(key = "term_breakdown")'),
    page.indexOf('item(key = "activities_header")'));
  assert.match(breakdown,/Text\("积分明细"/);
  assert.doesNotMatch(breakdown,/secondClassCategories\(/,
    'six categories must not be expanded in the default scroll list');
  assert.match(page,/if \(detailRecord\.kind == "credits"\)/,
    'points are only expanded inside the glass dialog');
  assert.match(page,/if \(refreshing\) return/);
  assert.match(page,/refreshing = true[\s\S]*reader\.launch/);
  assert.match(page,/val receipt = if \(result\.resultCode == Activity\.RESULT_OK\) SecondClassSaveReceipt\.validate/);
  assert.match(page,/val succeeded = receipt != null/);
  assert.match(page,/refreshing = false/);
  assert.match(page,/enabled = !refreshing/);
  assert.match(page,/onRefreshResult\?\.invoke\(false\)/);
});
