import assert from 'node:assert/strict';
import {onRequestGet} from '../functions/api/v1/schedule.js';

const token = 'A'.repeat(43); // Synthetic session only, never a real account.
const url = 'https://dorm-os-dk9.pages.dev/api/v1/schedule';
const session = new Request(url, {headers:{cookie:'__Host-xiaoxu_session='+token}});
const anonymous = new Request(url);
const record = {
  externalId:'term1-c1',term:'2026-2027-1',title:'电路基础',
  teacher:'课程教师',location:'教学楼C101',
  weekday:2,startSection:3,endSection:4,weeks:[1,3,5]
};
const batch = {
  batch_id:'b1',source:'ncwu',provenance:'trusted_readonly',
  last_success_at:new Date().toISOString()
};
function envFor(recordBatch=batch,records=[{payload:JSON.stringify(record)}]) {
  const calls=[];
  const db={
    prepare(sql) {
      let args=[];
      return {
        bind(...xs){args=xs;return this;},
        async first(){
          calls.push({sql,args});
          if(sql.includes('academic_auth_sessions'))return {
            user_id:'owner-a',expires_at:'2099-01-01T00:00:00Z'
          };
          if(sql.includes('academic_verified_batches'))return recordBatch;
          throw Error('unexpected_first_query');
        },
        async all(){
          calls.push({sql,args});
          if(sql.includes('academic_verified_records'))return {results:records};
          throw Error('unexpected_all_query');
        }
      };
    }
  };
  return {env:{ACADEMIC_AUTH_MODE:'owner_password',ACADEMIC_DB:db},calls};
}
let source=envFor();
const forbidden=await onRequestGet({request:anonymous,env:source.env});
assert.equal(forbidden.status,401,'Anonymous users cannot read private timetable');

const ok=await onRequestGet({request:session,env:source.env});
assert.equal(ok.status,200);
const data=await ok.json();
assert.equal(data.schema,'xiaoxu/verified-schedule-v1');
assert.equal(data.provenance,'trusted_readonly');
assert.equal(data.records.length,1);
assert.equal(data.records[0].title,'电路基础');
assert.ok(source.calls.filter(x=>x.sql.includes('academic_verified_')).every(x=>x.args[0]==='owner-a'),
  'Every school data query is scoped to the authorized owner');

source=envFor(null);
const absent=await onRequestGet({request:session,env:source.env});
assert.equal(absent.status,200);
assert.deepEqual((await absent.json()).records,[]);

source=envFor({...batch,provenance:'user_import'});
const imported=await onRequestGet({request:session,env:source.env});
assert.equal(imported.status,200);
assert.equal((await imported.json()).status,'unverified_snapshot');
assert.equal(source.calls.filter(x=>x.sql.includes('academic_verified_records')).length,0,
  'User-imported or untrusted data cannot be read as verified');
source=envFor(batch,[{payload:JSON.stringify({...record,weekday:10})}]);
const corrupt=await onRequestGet({request:session,env:source.env});
assert.equal(corrupt.status,503);
assert.deepEqual((await corrupt.json()).records,[],'Corrupt batch must fail closed');

console.log('校序原生可信课表只读API：鉴权隔离、可信来源、完整性和拒绝伪造数据测试通过');
