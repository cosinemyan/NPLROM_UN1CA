// Copyright (c) 2026 Rezoss
// SPDX-License-Identifier: GPL-3.0-or-later
// Real upstream DOM/controllers with an in-memory ROM transport; no Android/device writes.
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'file://' + process.env.HOME + '/.cache/ms-playwright-go/1.57.0/package/index.mjs');
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../assets/webui');
const server = http.createServer((req,res)=>{
  const file = path.join(root, req.url === '/' ? 'index.html' : req.url.split('?')[0]);
  if(!file.startsWith(root + '/') || !fs.existsSync(file)){res.writeHead(404);res.end();return;}
  res.setHeader('Content-Type', file.endsWith('.js') ? 'text/javascript' : file.endsWith('.css') ? 'text/css' : 'text/html');
  res.setHeader('Content-Security-Policy', "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'none'; object-src 'none'; base-uri 'none'");
  res.end(fs.readFileSync(file));
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const browser = await chromium.launch({headless:true,args:['--no-sandbox']});
try {
  const page=await browser.newPage({viewport:{width:412,height:915},deviceScaleFactor:1});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(()=>{
    window.calls=[];
    let config={version:1,_romRevision:'r1',profiles:{unica:{keybox:'ROM-current.xml',mode:'patch',patchLevel:{system:'2026-11-05',vendor:'system_property',boot:'system_property'},osVersion:'system_property',apps:['com.google.android.gms','com.android.vending'],autoIncludeNewApps:false}}};
    window.RomBridge={request(id,action,raw){
      const a=JSON.parse(raw);window.calls.push(action);
      let result={ok:true};
      if(action==='configLoad')result.config=JSON.parse(JSON.stringify(config));
      else if(action==='configSave'){config=JSON.parse(JSON.stringify(a.config));config._romRevision='r2';result.revision='r2';}
      else if(action==='keyboxList')result.names=['ROM-current.xml','Saved.xml'];
      else if(action==='patchStatus')Object.assign(result,{profiles:{unica:{effective:'2026-11',pif:'2026-11-05',state:'match'}},enabled:'true'});
      else if(action==='status')Object.assign(result,{version:'UN1CA ROM backend',lib:{hook:'Binder',api:37},harvest:{osVersion:170000,osPatchLevel:202610,vendorPatchLevel:20261005,bootPatchLevel:20260101,brand:'samsung',model:'SM-S918B',deviceLocked:false,verifiedBootState:2,strongBoxAvailable:true,overrides:{}}});
      else if(action==='packages')Object.assign(result,{firstAppUid:10000,users:[{id:0,name:'Owner'}],apps:[{uid:10319,userId:0,packages:['com.google.android.gms'],label:'Google Play services',system:true,enabled:true,launchable:false},{uid:10265,userId:0,packages:['com.android.vending'],label:'Google Play Store',system:true,enabled:true,launchable:true}]});
      else if(action==='scope')Object.assign(result,{baselineReady:true,profiles:[{id:'unica',packages:['com.google.android.gms','com.android.vending'],explicitUids:[10319,10265],autoUids:[]}]});
      else if(action==='keysDb')Object.assign(result,{available:true,apiLevel:37,total:1,keys:[{id:'1234',uid:10319,package:'com.google.android.gms',alias:'Example key',class:'patched',keybox:'Saved.xml'}]});
      else if(action==='logs')Object.assign(result,{lines:[{seq:1,level:'I',tag:'ROM',text:'Configuration acknowledged'}],nextAfter:1});
      else if(action==='canary')Object.assign(result,{installedVersion:'UN1CA ROM backend',updateAvailable:false});
      else if(action==='overridesLoad')result.overrides={};
      else if(action!=='rescan')result={ok:false,error:'Disabled by ROM protections'};
      setTimeout(()=>window.romReply(id,JSON.stringify(result)),0);
    },saveLogs(){}};
  });
  await page.goto(`http://127.0.0.1:${server.address().port}/`);
  await page.getByText('unica: TEE 2026-11 · PIF 2026-11-05 · Match').waitFor();
  await page.locator('#tab-config').getByText('unica',{exact:true}).waitFor();
  for(const tab of ['keyboxes','keys','system','logs','config']){
    await page.locator(`[data-nav="${tab}"]`).click();
    await page.waitForTimeout(120);
    if(!(await page.locator(`#tab-${tab}`).isVisible()))throw new Error('Tab did not open: '+tab);
  }
  if(errors.length)throw new Error(errors.join('\n'));
  await page.locator('#tab-config').getByText('unica',{exact:true}).click();
  await page.waitForTimeout(120);
  await page.screenshot({path:process.env.TEE_SCREENSHOT || '/tmp/tee-settings-profile.png',fullPage:true});
  if(await page.locator('input[name="verifiedBootKey"]').count())throw new Error('Frozen boot input exposed');
  const source=await page.content();
  if(!source.includes('2026-11-05'))throw new Error('Profile patch value missing');
  await page.getByRole('button',{name:'Save',exact:true}).click();
  await page.waitForFunction(()=>window.calls.includes('configSave'));
  await page.emulateMedia({colorScheme:'dark'});
  await page.screenshot({path:'/tmp/tee-settings-dark.png',fullPage:true});
  if(errors.length)throw new Error(errors.join('\n'));
  console.log('PASS upstream WebUI: all five tabs, ROM patch comparison, profile editor, light/dark rendering; no JavaScript errors');
} finally {await browser.close();server.close();}
