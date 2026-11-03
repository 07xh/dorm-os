// Cloudflare Access JWT verification; fail closed when Access is not configured.
// Never trust email, a caller-supplied user ID or an unsigned Access header.
import {verifyOwnerSession} from './academic-owner-session.mjs';
const decodePart = (part) => {
  if (!/^[A-Za-z0-9_-]+$/.test(part) || part.length > 12000) throw new Error('invalid_token');
  const base64 = part.replace(/-/g, '+').replace(/_/g, '/');
  const bytes = atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '='));
  return Uint8Array.from(bytes, (char) => char.charCodeAt(0));
};
const toJson = (part) => JSON.parse(new TextDecoder().decode(decodePart(part)));
const hex = (bytes) => [...new Uint8Array(bytes)].map(x => x.toString(16).padStart(2, '0')).join('');

export async function verifyAcademicAccess(request, env, {
  fetcher = fetch, clock = () => Date.now(), subtle = crypto.subtle
} = {}) {
  if(env?.ACADEMIC_AUTH_MODE==='owner_password')return verifyOwnerSession(request,env,{clock,subtle});
  if(env?.ACADEMIC_AUTH_MODE && env.ACADEMIC_AUTH_MODE!=='access')return {ok:false,status:503,error:'auth_not_configured'};
  const domain = env?.ACCESS_TEAM_DOMAIN;
  const audience = env?.ACCESS_AUD;
  if (typeof domain !== 'string' ||
      !/^[a-z0-9-]+\.cloudflareaccess\.com$/.test(domain) ||
      typeof audience !== 'string' || !audience.trim()) {
    return {ok: false, status: 503, error: 'auth_not_configured'};
  }
  const token = request.headers.get('Cf-Access-Jwt-Assertion');
  if (!token || token.length > 16000) return {ok: false, status: 401, error: 'unauthorized'};
  try {
    const parts = token.split('.');
    if (parts.length !== 3) throw new Error('invalid_token');
    const header = toJson(parts[0]);
    const payload = toJson(parts[1]);
    const issuer = 'https://' + domain;
    const now = Math.floor(clock() / 1000);
    const aud = Array.isArray(payload.aud) ? payload.aud : [payload.aud];
    if (header.alg !== 'RS256' || typeof header.kid !== 'string' ||
        !header.kid || payload.iss !== issuer ||
        !aud.includes(audience) ||
        typeof payload.exp !== 'number' || payload.exp <= now ||
        (typeof payload.nbf === 'number' && payload.nbf > now) ||
        (typeof payload.iat === 'number' && payload.iat > now + 60) ||
        typeof payload.sub !== 'string' || !payload.sub) {
      throw new Error('invalid_claims');
    }
    const response = await fetcher(issuer + '/cdn-cgi/access/certs', {
      headers: {accept: 'application/json'}
    });
    if (!response.ok) throw new Error('jwks_unavailable');
    const body = await response.json();
    const key = Array.isArray(body.keys) &&
      body.keys.find(k => k.kid === header.kid && k.kty === 'RSA' && (!k.alg || k.alg === 'RS256'));
    if (!key) throw new Error('no_matching_key');
    const imported = await subtle.importKey('jwk', key,
      {name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256'}, false, ['verify']);
    const signed = new TextEncoder().encode(parts[0] + '.' + parts[1]);
    const valid = await subtle.verify('RSASSA-PKCS1-v1_5', imported, decodePart(parts[2]), signed);
    if (!valid) throw new Error('bad_signature');
    const idData = new TextEncoder().encode(issuer + '\n' + payload.sub);
    const userId = hex(await subtle.digest('SHA-256', idData));
    return {ok: true, userId};
  } catch {
    return {ok: false, status: 401, error: 'unauthorized'};
  }
}
