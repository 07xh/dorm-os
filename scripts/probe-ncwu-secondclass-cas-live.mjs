/* Public, unauthenticated CAS compatibility / network probe.
 * No credentials, cookies, tickets, QR payload, HTML, school identifiers or
 * response bodies are printed. Reports only allowlisted host, HTTP status,
 * and a coarse Node network failure category.
 */
import {makeNcwuSecondClassServerAdapter} from '../lib/academic-ncwu-secondclass-server.mjs';
const hosts=new Set(['dekt.ncwu.edu.cn','authserver.ncwu.edu.cn','sec.ncwu.edu.cn']);
const networkCodes=new Set(['ENOTFOUND','EAI_AGAIN','ECONNREFUSED','ECONNRESET',
 'ETIMEDOUT','UND_ERR_CONNECT_TIMEOUT','UND_ERR_SOCKET','CERT_HAS_EXPIRED',
 'UNABLE_TO_VERIFY_LEAF_SIGNATURE','ERR_TLS_CERT_ALTNAME_INVALID']);
const trace=[];
async function fetcher(url,options){
 const host=new URL(url).hostname;
 if(!hosts.has(host))throw Error('unexpected_host');
 try{
  const response=await fetch(url,options);
  trace.push({host,status:response.status});
  return response;
 }catch(error){
  const code=error?.cause?.code||error?.code||'';
  trace.push({host,networkError:networkCodes.has(code)?code:'other_network_error'});
  throw error;
 }
}
const adapter=makeNcwuSecondClassServerAdapter({fetcher});
const result=await adapter.preflight();
const reasons=new Set(['cas_handoff_unrecognized','cas_login_page_unavailable',
 'password_form_unavailable','interactive_challenge_required',
 'password_transform_required','unexpected_cas_form_action',
 'school_unreachable','unrecognized_auth_flow']);
const mode=['password_form_verified','interactive_login_required','upstream_unavailable']
 .includes(result?.mode)?result.mode:'unexpected_flow';
const reason=reasons.has(result?.reason)?result.reason:'none';
console.log('NCWU second-class public CAS compatibility: '+JSON.stringify({
 mode,reason,serverPasswordLoginSupported:result?.loginSupported===true,trace
}));
