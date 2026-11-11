import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
const html=readFileSync(new URL('../public/index.html',import.meta.url),'utf8');
const start=html.indexOf('async function fetchWeather(');
const end=html.indexOf('function escapeIcsText(',start);
const weatherFunction=html.slice(start,end);
for(const currentPage of ['settings','tasks','dashboard']){
 let renders=0,saved=0;
 const context=vm.createContext({page:currentPage,weatherLoading:false,weather:null,weatherError:'',
  state:{settings:{campus:'jianghuai'}},CAMPUSES:{jianghuai:{lat:32,lon:114}},
  weatherCacheKey:()=> 'weather-test',loadWeather:()=>{},render:()=>renders++,
  localStorage:{setItem:()=>saved++},AbortController,URL,URLSearchParams,setTimeout,clearTimeout,Date,
  fetch:async()=>({ok:true,json:async()=>({current:{temperature_2m:20,apparent_temperature:20}})})});
 await vm.runInContext(weatherFunction+';fetchWeather()',context);
 assert.equal(saved,1,'Weather data still updates on every page');
 assert.equal(renders,currentPage==='dashboard'?2:0,'Weather refresh must only redraw dashboard: '+currentPage);
}
console.log('Weather refresh preserves unrelated page DOM and still updates dashboard');
