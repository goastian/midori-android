const { chromium } = require('playwright');
const fs = require('node:fs');
const cp = require('node:child_process');
(async () => {
  const browser = await chromium.launch({headless:true, args:['--no-sandbox']});
  const result = {};
  try {
    for (const variant of ['baseline','fixed']) {
      const page = await browser.newPage();
      await page.route('http://localhost/**', route => route.fulfill({contentType:'text/html', body:'<!doctype html><main></main>'}));
      await page.goto('http://localhost/');
      const source = variant === 'baseline' ? cp.execFileSync('git',['show',(process.argv[2] || 'daaf9d4c491b8c627b282ec530e640728a57cac0') + ':app/src/main/assets/adblock/content/cosmetic-procedural.js'],{encoding:'utf8'}) : fs.readFileSync('app/src/main/assets/adblock/content/cosmetic-procedural.js','utf8');
      await page.addScriptTag({content:source});
      const session = await page.context().newCDPSession(page);
      await session.send('Performance.enable');
      await page.evaluate(() => {
        const nativeTimeout = window.setTimeout.bind(window);
        window.setTimeout = (callback, ms) => nativeTimeout(callback, Math.min(ms, 1));
        window.cosmetic = MidoriCosmetics.create(() => {});
        cosmetic.update([
          {selector:[{type:'css-selector',arg:'.remove'}],action:{type:'remove'}},
          {selector:[{type:'css-selector',arg:'.attributes'}],action:{type:'remove-attr',arg:'data-ad'}},
          {selector:[{type:'css-selector',arg:'.hidden'}]},
        ].map(JSON.stringify));
      });
      const samples=[];
      for (let cycle=0; cycle<30; cycle++) {
        await page.evaluate(() => {
          const root = document.querySelector('main');
          root.replaceChildren();
          const route = document.createElement('section');
          route.innerHTML=Array.from({length:1000}, (_,i)=>`<div class="remove"><span>advert ${i}</span></div><div class="attributes" data-ad="original">attr</div><div class="hidden">hidden</div>`).join('');
          root.append(route);
        });
        await page.waitForFunction(() => document.querySelector('.hidden')?.getAttributeNames().some(name=>name.startsWith('data-midori-')));
        await page.evaluate(() => document.querySelector('main').replaceChildren());
        await page.waitForTimeout(100);
        await session.send('HeapProfiler.collectGarbage');
        const dom = await session.send('Memory.getDOMCounters');
        const metrics = await session.send('Performance.getMetrics');
        samples.push({cycle, ...dom, heap:metrics.metrics.find(metric=>metric.name==='JSHeapUsedSize').value, retention:await page.evaluate(()=>cosmetic.retention?.() ?? null)});
      }
      const chunks=[];
      session.on('HeapProfiler.addHeapSnapshotChunk', chunk=>chunks.push(chunk.chunk));
      await session.send('HeapProfiler.takeHeapSnapshot',{reportProgress:false});
      fs.writeFileSync(`artifacts/h05-h06-2026-10-29/${variant}.heapsnapshot`,chunks.join(''));
      result[variant]=samples;
      await page.close();
    }
    fs.writeFileSync('artifacts/h05-h06-2026-10-29/heap-comparison.json',JSON.stringify(result,null,2));
    console.log(JSON.stringify(Object.fromEntries(Object.entries(result).map(([key,value])=>[key,{first:value[0],last:value.at(-1)}])),null,2));
  } finally { await browser.close(); }
})().catch(error=>{console.error(error);process.exitCode=1;});
