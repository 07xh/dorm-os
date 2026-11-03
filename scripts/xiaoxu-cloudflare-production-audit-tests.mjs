import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {inspectAcademicPagesProject}
 from './audit-cloudflare-academic-production.mjs';

const config={
 name:'dorm-os',
 production_branch:'main',
 uses_functions:true,
 deployment_configs:{
  production:{
   d1_databases:{ACADEMIC_DB:{id:'synthetic-d1-uuid'}},
   env_vars:{
    ACCESS_TEAM_DOMAIN:{type:'secret_text',value:'DO_NOT_LEAK_ACCESS_ISSUER'},
    ACCESS_AUD:{type:'secret_text',value:'DO_NOT_LEAK_ACCESS_AUDIENCE'}
   }
  },
  preview:{d1_databases:{NOT_THE_PRODUCTION_DB:{id:'synthetic-preview'}}}
 }
};
const success=inspectAcademicPagesProject(config);
assert.deepEqual(success,{
 d1Bound:true,authMode:'access',authSettingsReady:true,
 ownerSecretConfigured:false,accessIssuerConfigured:true,accessAudienceConfigured:true,
 productionBranchMain:true,pagesFunctionsPresent:true
});
assert.equal(JSON.stringify(success).includes('DO_NOT_LEAK'),false);

const missing=inspectAcademicPagesProject({
 ...config,production_branch:'other',uses_functions:false,
 deployment_configs:{
  production:{d1_databases:{OTHER:{id:'another'}},env_vars:{ACCESS_TEAM_DOMAIN:{type:'plain_text',value:'example'}}},
  preview:config.deployment_configs.production
 }
});
assert.deepEqual(missing,{
 d1Bound:false,authMode:'access',authSettingsReady:false,
 ownerSecretConfigured:false,accessIssuerConfigured:true,accessAudienceConfigured:false,
 productionBranchMain:false,pagesFunctionsPresent:false
});

const ownerMode=inspectAcademicPagesProject({...config,deployment_configs:{
 production:{d1_databases:config.deployment_configs.production.d1_databases,
  env_vars:{
   ACADEMIC_AUTH_MODE:{type:'plain_text',value:'owner_password'},
   OWNER_LOGIN_SECRET:{type:'secret_text',value:'DO_NOT_LEAK_OWNER_LOGIN_SECRET'}
  }},
 preview:config.deployment_configs.preview
}});
assert.equal(ownerMode.authMode,'owner_password');
assert.equal(ownerMode.authSettingsReady,true,
 'owner password mode must not require old Cloudflare Access variables');
assert.equal(ownerMode.ownerSecretConfigured,true);
assert.equal(ownerMode.accessAudienceConfigured,false);
assert.equal(ownerMode.accessIssuerConfigured,false);
assert.equal(JSON.stringify(ownerMode).includes('DO_NOT_LEAK'),false);

const noOwnerSecret=inspectAcademicPagesProject({...config,deployment_configs:{
 production:{d1_databases:config.deployment_configs.production.d1_databases,
 env_vars:{ACADEMIC_AUTH_MODE:{type:'plain_text',value:'owner_password'}}}
}});
assert.equal(noOwnerSecret.authMode,'owner_password');
assert.equal(noOwnerSecret.authSettingsReady,false,
 'owner mode without a bound secret must fail closed');

const unknown=inspectAcademicPagesProject({...config,deployment_configs:{
 production:{d1_databases:config.deployment_configs.production.d1_databases,
 env_vars:{ACADEMIC_AUTH_MODE:{type:'secret_text'},OWNER_LOGIN_SECRET:{type:'secret_text'}}}
}});
assert.equal(unknown.authMode,'unverified');
assert.equal(unknown.authSettingsReady,false,'hidden or unknown mode must not silently pass');
assert.throws(()=>inspectAcademicPagesProject({...config,name:'wrong-project'}),/unexpected_pages_project/);

const deploy=readFileSync(new URL('../.github/workflows/deploy-cloudflare.yml',import.meta.url),'utf8');
assert.ok(deploy.includes('scripts/audit-cloudflare-academic-production.mjs'),
 'read-only Pages config check is invoked after release');
assert.ok(deploy.includes('continue-on-error: true'),
 'the config audit must not claim static publish rollback');
const release=readFileSync(new URL('./verify-release.mjs',import.meta.url),'utf8');
assert.ok(release.includes("'/api/v1/health'"),
 'anonymous health endpoint must be covered by live publication guard');
assert.ok(release.includes("'/api/v1/second-class'"));
console.log('Cloudflare audit: owner-password or Access modes checked without secrets, unknown mode fails closed');
