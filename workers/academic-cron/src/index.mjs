/* Scheduled-only Worker. No public fetch access, credentials or upstream cookies.
 * The Pages Function retains the only school session decryption key.
 */
const TARGET='https://dorm-os-dk9.pages.dev/api/v1/internal/chaoxing-hourly';
const CRON='17 * * * *';
export default {
 async fetch(){return new Response('Not found',{status:404});},
 async scheduled(event,env){
  if(event?.cron!==CRON||typeof env?.CRON_SYNC_TOKEN!=='string'||
     !/^[A-Za-z0-9_-]{43}$/.test(env.CRON_SYNC_TOKEN))
   throw Error('cron_configuration_unavailable');
  const response=await fetch(TARGET,{
   method:'POST',redirect:'error',cache:'no-store',
   headers:{'content-type':'application/json','x-xu-cron-key':env.CRON_SYNC_TOKEN},
   body:'{}',signal:AbortSignal.timeout(55000)
  });
  if(!response.ok)throw Error('hourly_sync_endpoint_http_'+response.status);
  const data=await response.json().catch(()=>null);
  if(!data||data.schema!=='xiaoxu/chaoxing-hourly-v1'||
     !['idle','success','partial','failed','expired'].includes(data.status))
   throw Error('hourly_sync_response_invalid');
  // Summary contains no school identifiers or private course titles.
  console.log('Chaoxing scheduled sync:',data.status,data.coursesChecked??0);
 }
};
