// First private Academic OS API. The legacy task UI remains operational.
// No Cloudflare Access config or D1 binding => fail closed, never expose private data.
import {verifyAcademicAccess} from '../../../lib/academic-access.mjs';
import {
  AcademicInputError,createManualAssignment,listAssignments
} from '../../../lib/academic-assignments.mjs';

export const json = (body,status=200) => new Response(JSON.stringify(body),{
  status,headers:{
    'content-type':'application/json; charset=utf-8',
    'cache-control':'private, no-store',
    'x-content-type-options':'nosniff'
  }
});
export async function authorize(context) {
  const result = await verifyAcademicAccess(context.request,context.env);
  if (!result.ok) return {response:json({error:result.error},result.status)};
  if (!context.env?.ACADEMIC_DB) return {response:json({error:'database_not_configured'},503)};
  return {userId:result.userId, db:context.env.ACADEMIC_DB};
}
export async function onRequestGet(context) {
  const auth = await authorize(context);
  if (auth.response) return auth.response;
  try {
    const query = new URL(context.request.url).searchParams;
    const limit = query.has('limit') ? Number(query.get('limit')) : 50;
    const offset = query.has('offset') ? Number(query.get('offset')) : 0;
    const status = query.has('status') ? query.get('status') : null;
    const items = await listAssignments(auth.db,auth.userId,{limit,offset,status});
    return json({items,limit,offset});
  } catch (error) {
    if (error instanceof AcademicInputError) return json({error:'invalid_query'},400);
    return json({error:'database_unavailable'},503);
  }
}
export async function onRequestPost(context) {
  const auth = await authorize(context);
  if (auth.response) return auth.response;
  const request = context.request;
  const origin = request.headers.get('origin');
  if ((origin && origin !== new URL(request.url).origin) ||
      request.headers.get('sec-fetch-site') === 'cross-site') {
    return json({error:'forbidden_origin'},403);
  }
  if (!(request.headers.get('content-type')||'').toLowerCase().startsWith('application/json')) {
    return json({error:'json_required'},415);
  }
  try {
    const raw = await request.text();
    if (raw.length > 16000) return json({error:'payload_too_large'},413);
    let body;
    try { body = JSON.parse(raw); }
    catch { return json({error:'invalid_json'},400); }
    const created = await createManualAssignment(auth.db,auth.userId,body);
    return json({item:created},201);
  } catch (error) {
    if (error instanceof AcademicInputError) {
      return json({error:'invalid_assignment',message:error.message},422);
    }
    return json({error:'database_unavailable'},503);
  }
}
