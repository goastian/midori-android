const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
(async () => {
  const root = path.resolve(__dirname, '../..');
  const browser = await chromium.launch({headless:true, args:['--no-sandbox']});
  try {
    const page = await browser.newPage();
    let html = fs.readFileSync(path.join(root,'app/src/androidTest/assets/cosmetic-retention.html'),'utf8');
    html = html.replace('/*SOURCE*/', () => fs.readFileSync(path.join(root,'app/src/main/assets/adblock/content/cosmetic-procedural.js'),'utf8'));
    await page.route('http://localhost/**', route => route.fulfill({contentType:'text/html', body:html}));
    await page.goto('http://localhost/fixture');
    const result = await page.evaluate(() => runRetention(20));
    fs.writeFileSync(path.join(root,'artifacts/h05-h06-2026-10-29/retention-browser.json'), JSON.stringify(result,null,2));
    if (!result.ok) throw new Error(result.error);
    console.log(JSON.stringify({ok:result.ok, cycles:result.cycles, elapsed:result.elapsed}));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode=1; });
