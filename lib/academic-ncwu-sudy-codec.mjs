/* Compatibility with the school's published RSAUtils wire format.
 * This is only the university's existing password transport, never a general
 * encryption primitive. No downloaded JavaScript is evaluated by the server.
 */
const SOURCE_SHA256='a6e67d9ed0dd24f343acef575379b64f111aa320b23c5cdc646fda31dc583ba3';
const MODULUS=BigInt('0x008aed7e057fe8f14c73550b0e6467b023616ddc8fa91846d2613cdb7f7621e3cada4cd5d812d627af6b87727ade4e26d26208b7326815941492b2204c3167ab2d53df1e3a2c9153bdb7c8c2e968df97a5e7e01cc410f92c4c2c2fba529b3ee988ebc1fca99ff5119e036d732c368acf8beba01aa2fdafa45b21e4de4928d0d403');
export async function verifyNcwuSudySource(source){
 if(typeof source!=='string'||source.length>300000)return false;
 const bytes=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(source));
 return Array.from(new Uint8Array(bytes),b=>b.toString(16).padStart(2,'0')).join('')===SOURCE_SHA256;
}
export function encodeNcwuSudyPassword(password){
 if(typeof password!=='string'||!password||password.length>512||
    Array.from(password).some(c=>c.charCodeAt(0)>255))throw Error('invalid_credentials');
 // checkPassLogin leaves an exactly 256-character input unchanged.
 if(password.length===256)return password;
 const blocks=[];
 for(let offset=0;offset<password.length;offset+=126){
  let block=0n;
  for(let i=0;i<126&&offset+i<password.length;i++)
   block+=BigInt(password.charCodeAt(offset+i))<<BigInt(8*i);
  let result=1n,base=block%MODULUS,exponent=65537n;
  while(exponent){
   if(exponent&1n)result=result*base%MODULUS;
   base=base*base%MODULUS;exponent>>=1n;
  }
  const hex=result.toString(16);
  blocks.push(hex.padStart(Math.ceil(hex.length/4)*4,'0'));
 }
 return blocks.join(' ');
}
