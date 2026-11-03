import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {
 findPagesBoundD1,classifyProductionSchema,queryRows
} from './bootstrap-academic-d1-production.mjs';

const uuid='01234567-89ab-cdef-0123-456789abcdef';
const config={name:'dorm-os',deployment_configs:{
 production:{d1_databases:{ACADEMIC_DB:{id:uuid}}},
 preview:{d1_databases:{ACADEMIC_DB:{id:'ffffffff-ffff-ffff-ffff-ffffffffffff'}}}
}};
assert.equal(findPagesBoundD1(config),uuid);
assert.throws(()=>findPagesBoundD1({...config,name:'other'}),/unexpected/);
assert.throws(()=>findPagesBoundD1({
 ...config,deployment_configs:{production:{d1_databases:{}},preview:config.deployment_configs.production}
}),/production_d1_binding_missing/);
assert.throws(()=>findPagesBoundD1({
 ...config,deployment_configs:{production:{d1_databases:{ACADEMIC_DB:{id:'not-id'}}}}
}),/production_d1_binding_missing/);

assert.equal(classifyProductionSchema([],[]),'empty');
assert.equal(classifyProductionSchema([{name:'sqlite_sequence'},{name:'_cf_INTERNAL'}],[]),'empty');
assert.equal(classifyProductionSchema([{name:'academic_users'}],[]),'partial_existing_schema');
assert.equal(classifyProductionSchema([{name:'unrecognized_private_table'}],[]),'partial_existing_schema');
const allTables=[
 'academic_users','academic_connections','academic_courses','academic_assignments',
 'academic_assignment_sources','academic_sync_runs','academic_assignment_events',
 'academic_legacy_links','academic_verified_batches','academic_verified_records',
 'academic_verified_ingests','academic_sync_jobs','academic_reminder_preferences',
 'academic_reminder_receipts','academic_second_class_sync_audit',
 'academic_second_class_official_scores'
].map(name=>({name}));
assert.equal(classifyProductionSchema(allTables,[{name:'provenance'}]),'ready');
assert.equal(classifyProductionSchema(allTables,[]),'partial_existing_schema');
assert.equal(classifyProductionSchema(allTables.slice(0,-1),[{name:'provenance'}]),'partial_existing_schema');
assert.throws(()=>classifyProductionSchema('not array',[]),/invalid_schema_response/);
assert.throws(()=>classifyProductionSchema([{name:5}],[]),/invalid_schema_response/);
assert.deepEqual(queryRows(JSON.stringify([{results:[{name:'academic_users'}],success:true}])),
 [{name:'academic_users'}]);
assert.deepEqual(queryRows([{result:{results:[]},success:true}]),[]);
assert.throws(()=>queryRows([{success:false,results:[]}]),/d1_read_failed/);
assert.throws(()=>queryRows([{success:true}]),/d1_read_malformed/);
assert.throws(()=>queryRows('{invalid json'),/wrangler_output_not_json/);

const workflow=readFileSync(new URL('../.github/workflows/bootstrap-academic-d1.yml',import.meta.url),'utf8');
assert.ok(workflow.includes("paths:\n      - '.github/workflows/bootstrap-academic-d1.yml'"));
assert.ok(workflow.includes('workflow_dispatch:'));
assert.ok(workflow.includes("github.repository == '07xh/dorm-os'"));
assert.ok(workflow.includes('scripts/bootstrap-academic-d1-production.mjs'));
assert.ok(workflow.includes('CLOUDFLARE_API_TOKEN'));
assert.ok(workflow.includes('CLOUDFLARE_API_TOKEN: ${{ secrets.CLOUDFLARE_API_TOKEN }}'));
assert.ok(workflow.includes('CLOUDFLARE_ACCOUNT_ID: ${{ secrets.CLOUDFLARE_ACCOUNT_ID }}'));
assert.ok(!workflow.includes('\\${{'),'GitHub Secrets interpolation must not be escaped');
const script=readFileSync(new URL('./bootstrap-academic-d1-production.mjs',import.meta.url),'utf8');
assert.ok(script.includes("status!=='empty'"));
assert.ok(script.includes("existing_schema_requires_manual_review"));
assert.ok(script.includes("post_migration_schema_incomplete"));
assert.ok(script.includes("'PRAGMA foreign_key_check'"));
assert.ok(!script.includes("console.log(token)"));
console.log('Production D1 bootstrap: exact bound UUID, empty-only, preexisting-schema protection, no output leak, remote response validation passed');
