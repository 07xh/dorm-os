/* Public, unauthenticated CAS form compatibility probe.
 * No account, password, Cookie, ticket, QR data, HTML or response payload
 * is printed or persisted. This cannot establish a personal school session.
 */
import {makeNcwuSecondClassServerAdapter} from '../lib/academic-ncwu-secondclass-server.mjs';

const adapter=makeNcwuSecondClassServerAdapter();
const result=await adapter.preflight();
const reasons=new Set(['cas_handoff_unrecognized','cas_login_page_unavailable',
 'password_form_unavailable','interactive_challenge_required',
 'password_transform_required','unexpected_cas_form_action',
 'school_unreachable','unrecognized_auth_flow']);
const mode=['password_form_verified','interactive_login_required','upstream_unavailable']
 .includes(result?.mode)?result.mode:'unexpected_flow';
const reason=reasons.has(result?.reason)?result.reason:'none';
console.log('NCWU second-class public CAS compatibility: '+JSON.stringify({
 mode,reason,serverPasswordLoginSupported:result?.loginSupported===true
}));
