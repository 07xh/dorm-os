/* Strict preview of browser-imported second-class school-page totals.
 * It MUST NOT be passed to or stored as independently trusted official data.
 */
export class PersonalTotalsError extends Error{
 constructor(){super('invalid_browser_totals');this.name='PersonalTotalsError';this.code='invalid_browser_totals';}
}
const valid=x=>x===null||(typeof x==='number'&&Number.isFinite(x)&&x>=0&&x<=100000);
export function parsePersonalTotals(input){
 if(input===null||input===undefined)return null;
 if(!input||typeof input!=='object'||Array.isArray(input)||input.origin!=='unverified_local_browser_export')
  throw new PersonalTotalsError();
 const keys=Object.keys(input);
 if(keys.some(key=>!['qualityScore','volunteerHours','origin'].includes(key)))
  throw new PersonalTotalsError();
 const qualityScore=input.qualityScore??null,volunteerHours=input.volunteerHours??null;
 if(!valid(qualityScore)||!valid(volunteerHours))throw new PersonalTotalsError();
 if(qualityScore===null&&volunteerHours===null)return null;
 return {qualityScore,volunteerHours};
}
