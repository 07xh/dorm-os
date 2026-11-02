import {authorize,json} from './assignments.js';
import {readSourceHealth} from '../../../lib/academic-workspace.mjs';
export async function onRequestGet(context){
 const auth=await authorize(context);
 if(auth.response)return auth.response;
 try{return json(await readSourceHealth(auth.db,auth.userId));}
 catch{return json({error:'database_unavailable'},503);}
}
