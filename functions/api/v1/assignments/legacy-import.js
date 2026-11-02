// Explicit private legacy-task migration. Never automatically reads localStorage,
// never deletes local tasks, and never claims a platform was verified.
import {authorize,json} from '../assignments.js';
import {
  LegacyMigrationError,migrateLegacyBatch
} from '../../../../lib/academic-legacy-migration.mjs';

export async function onRequestPost(context) {
  const auth=await authorize(context);
  if(auth.response)return auth.response;
  const req=context.request;
  const origin=req.headers.get('origin');
  if((origin&&origin!==new URL(req.url).origin) ||
      req.headers.get('sec-fetch-site')==='cross-site') {
    return json({error:'forbidden_origin'},403);
  }
  if(!(req.headers.get('content-type')||'').toLowerCase().startsWith('application/json')) {
    return json({error:'json_required'},415);
  }
  try {
    const bodyText=await req.text();
    if(bodyText.length>32000)return json({error:'payload_too_large'},413);
    let parsed;
    try {parsed=JSON.parse(bodyText);}
    catch{return json({error:'invalid_json'},400);}
    const result=await migrateLegacyBatch(auth.db,auth.userId,parsed);
    if(result.conflicts.length)return json({error:'legacy_conflict',...result},409);
    return json(result,result.mode==='commit'&&result.toCreate?201:200);
  } catch(error) {
    if(error instanceof LegacyMigrationError) {
      return json({error:'invalid_legacy_batch',message:error.message},422);
    }
    return json({error:'migration_unavailable'},503);
  }
}
