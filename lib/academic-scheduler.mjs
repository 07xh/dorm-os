/* Retry timing shared by the active owner-opted-in Chaoxing auto cycle.
 * D1 leases and dispatch belong only to academic-chaoxing-auto-sync.mjs. */
const ISO=/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{3})?Z$/;
const date=s=>typeof s==='string'&&ISO.test(s)&&Number.isFinite(Date.parse(s));
const stamp=ms=>new Date(ms).toISOString();
export function nextAttempt(now,intervalMinutes,failures=0,result='success'){
 if(!date(now)||!Number.isInteger(intervalMinutes)||intervalMinutes<30||intervalMinutes>1440||
 !Number.isInteger(failures)||failures<0||failures>20)throw Error('invalid_scheduler_values');
 const minutes=result==='success'?intervalMinutes:Math.min(1440,Math.max(15,15*2**Math.min(failures,7)));
 return stamp(Date.parse(now)+minutes*60_000);
}
