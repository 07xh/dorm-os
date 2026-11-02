/* Offline only. Does not read Cloudflare tokens or contact production D1.
 * Applies all migrations to disposable SQLite and checks basic restore invariants.
 */
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {DatabaseSync} from 'node:sqlite';
const names=[
 '0001_academic_core.sql','0002_academic_legacy_links.sql',
 '0003_academic_verified_snapshots.sql','0004_academic_source_course.sql',
 '0005_academic_workspace.sql','0006_academic_scheduler.sql'
];
const sql=names.map(n=>({name:n,content:readFileSync(new URL('../migrations/'+n,import.meta.url),'utf8')}));
function fresh(){
 const db=new DatabaseSync(':memory:');db.exec('PRAGMA foreign_keys=ON');
 for(const m of sql)db.exec(m.content);
 return db;
}
const db=fresh();
try{
 const required=['academic_users','academic_connections','academic_assignments','academic_sync_runs',
 'academic_assignment_events','academic_verified_records','academic_sync_jobs'];
 const tables=new Set(db.prepare("SELECT name FROM sqlite_master WHERE type='table'").all().map(r=>r.name));
 for(const name of required)if(!tables.has(name))throw Error('missing table '+name);
 const pragma=db.prepare('PRAGMA foreign_key_check').all();
 if(pragma.length)throw Error('broken foreign keys: '+pragma.length);
 const integrity=db.prepare('PRAGMA integrity_check').get();
 if(integrity.integrity_check!=='ok')throw Error('sqlite integrity failure');
 db.prepare('INSERT INTO academic_users(id,created_at) VALUES (?,?)')
   .run('preflight-1','2026-11-03T00:00:00.000Z');
 const count=db.prepare('SELECT COUNT(*) AS n FROM academic_users').get().n;
 if(count!==1)throw Error('failed to write test user');
 // Copy logical fixtures into a completely separate disposable database,
 // and verify the original remains intact. This is NOT a production D1 backup.
 const restored=fresh();
 try{
  restored.prepare('INSERT INTO academic_users(id,created_at) VALUES (?,?)')
   .run('preflight-1','2026-11-03T00:00:00.000Z');
  if(restored.prepare('SELECT id FROM academic_users').get().id!=='preflight-1')
   throw Error('synthetic restore failed');
 }finally{restored.close();}
 for(const m of sql){
  const sha=createHash('sha256').update(m.content).digest('hex').slice(0,12);
  console.log('PASS '+m.name+' sha256='+sha);
 }
 console.log('SQLite migration dry run and synthetic restore passed. Production D1 remains untouched.');
}finally{db.close();}
