/* Fixed, non-sensitive marker evidence from an already received CAS 5xx.
 * Marker presence is NOT a root-cause diagnosis or a wrong-password signal.
 * Never export exception text, stack traces, HTML, account values or tokens.
 */
const KINDS=Object.freeze(['flow_execution_marker','crypto_marker','flow_and_crypto_markers']);
export function summarizeCasFailureMarkers(body){
 if(typeof body!=='string'||body.length>300000)return undefined;
 const visible=body.replace(/<script\b[^>]*>[\s\S]*?<\/script\s*>/gi,' ')
  .replace(/<style\b[^>]*>[\s\S]*?<\/style\s*>/gi,' ')
  .replace(/<!--[\s\S]*?-->/g,' ').replace(/<[^>]*>/g,' ');
 const flow=/\b(?:NoSuchFlowExecutionException|FlowExecutionRestorationFailureException|NoMatchingTransitionException)\b/.test(visible);
 const crypto=/\b(?:BadPaddingException|IllegalBlockSizeException)\b/.test(visible);
 return flow&&crypto?'flow_and_crypto_markers':flow?'flow_execution_marker':
  crypto?'crypto_marker':undefined;
}
export function safeCasErrorKind(error){
 return error?.code==='authorization_failed'&&error?.failureStage==='cas_return'&&
  error?.casReturnReason==='cas_server_failure'&&error?.casReturnHost==='cas_auth_server'&&
  KINDS.includes(error?.casErrorKind)?error.casErrorKind:undefined;
}
