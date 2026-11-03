// Owner-session verifier for a future self-hosted login flow.
// Not yet wired to production routes: Access JWT remains the only active gate.
// A token is a 32-byte random base64url value; only its SHA-256 hash is stored.
const DENIED = Object.freeze({ok:false,status:401,error:'unauthorized'});
const MISCONFIGURED = Object.freeze({ok:false,status:503,error:'auth_not_configured'});
const COOKIE = '__Host-xiaoxu_session';

export async function verifyOwnerSession(request, env, {clock=()=>Date.now(),subtle=crypto.subtle}={}) {
  const db=env?.ACADEMIC_DB;
  if (!db?.prepare) return MISCONFIGURED;
  const raw=request.headers.get('cookie')||'';
  if(raw.length>8192) return DENIED;
  const matches=raw.split(';').map(s=>s.trim()).filter(s=>s.startsWith(COOKIE+'='));
  if(matches.length!==1) return DENIED;
  const token=matches[0].slice(COOKIE.length+1);
  if(!/^[A-Za-z0-9_-]{43}$/.test(token)) return DENIED;
  const bytes=new TextEncoder().encode(token);
  const digest=new Uint8Array(await subtle.digest('SHA-256',bytes));
  const hash=Array.from(digest,b=>b.toString(16).padStart(2,'0')).join('');
  try {
    const record=await db.prepare(
      `SELECT i.user_id AS user_id, s.expires_at AS expires_at
       FROM academic_auth_sessions s
       JOIN academic_auth_identities i ON i.id=s.identity_id
       WHERE s.token_hash=? AND s.revoked_at IS NULL`
    ).bind(hash).first();
    if(!record || typeof record.user_id!=='string' || !record.user_id ||
       typeof record.expires_at!=='string' ||
       !Number.isFinite(Date.parse(record.expires_at)) ||
       Date.parse(record.expires_at)<=clock()) return DENIED;
    return {ok:true,userId:record.user_id,expiresAt:record.expires_at};
  } catch { return MISCONFIGURED; }
}
export const OWNER_SESSION_COOKIE=COOKIE;
