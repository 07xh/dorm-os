/* Consolidated private connections endpoint. Never accepts passwords, Cookies or
 * caller-supplied account IDs. It does not initiate a school login.
 */
import {authorize,json} from './assignments.js';
import {readConnectionCenter} from '../../../lib/academic-connection-center.mjs';
export async function onRequestGet(context){
  const auth=await authorize(context);
  if(auth.response)return auth.response;
  try{return json(await readConnectionCenter(auth.db,auth.userId));}
  catch{return json({error:'database_unavailable'},503);}
}
