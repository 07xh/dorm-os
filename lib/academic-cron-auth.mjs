/* Worker sends an opaque random bearer token over HTTPS.
 * Pages stores and checks only SHA-256; neither D1 nor logs hold the token.
 */
const RX=/^[A-Za-z0-9_-]{43}$/;
export async function verifyCronToken(db,token){
 if(!db?.prepare||typeof token!=='string'||!RX.test(token))return false;
 let known;
 try{
  known=await db.prepare(
   "SELECT token_sha256 FROM academic_cron_identity WHERE name='chaoxing-hourly'"
  ).first();
 }catch{return false;}
 if(!/^[a-f0-9]{64}$/.test(known?.token_sha256||''))return false;
 const data=new TextEncoder().encode(token);
 const digest=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',data)))
  .map(x=>x.toString(16).padStart(2,'0')).join('');
 let mismatch=0;
 for(let i=0;i<64;i++)mismatch|=digest.charCodeAt(i)^known.token_sha256.charCodeAt(i);
 return mismatch===0;
}
