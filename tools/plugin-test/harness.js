// Runs the REAL plugin_runtime.js (the one shipped in the app) inside headless Chromium (same engine family as Android WebView)
// with a mock LumoraHost bridge and a mock network. Usage: node harness.js <runtime.js> <old|new> [provider...]
const { chromium } = require('/home/claude/.npm-global/lib/node_modules/playwright');
const fs = require('fs'), path = require('path'), crypto = require('crypto');
const DIST = '/home/claude/w/vega-providers-main/vega-providers-main/dist';
const URLS = JSON.parse(fs.readFileSync('/home/claude/w/vega-providers-main/vega-providers-main/urls.json', 'utf8'));
const runtimePath = process.argv[2];
const mode = process.argv[3] || 'new';
const only = process.argv.slice(4);
const manifest = JSON.parse(fs.readFileSync('/home/claude/w/vega-providers-main/vega-providers-main/manifest.json', 'utf8'));
const extractorsJs = fs.existsSync('/home/claude/w/build/plugin_extractors.js') ? fs.readFileSync('/home/claude/w/build/plugin_extractors.js', 'utf8') : '';
const COMMON = {
  'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
  'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7',
  'Accept-Language': 'en-US,en;q=0.9'
};

const netLog = [];
function mockNet(req) {
  const url = req.url;
  const b = (s, ct) => ({ status: 200, statusText: 'OK', url, headers: { 'content-type': ct }, setCookies: [], bodyBase64: Buffer.from(s).toString('base64'), body: s });
  netLog.push(req.method + ' ' + url); if (process.env.NETDBG) console.error('NET', req.method, url, JSON.stringify(req.headers).slice(0,150));
  if (process.env.WAF && !/urls\.json$/.test(url)) { const ck = Object.entries(req.headers||{}).find(([k]) => k.toLowerCase()==='cookie'); if (!ck || !/cf_clearance=mock/.test(ck[1])) { netLog.push('403'); return { status: 403, statusText: 'Forbidden', url, headers: { server: 'cloudflare', 'cf-mitigated': 'challenge', 'content-type': 'text/html' }, setCookies: [], bodyBase64: Buffer.from('<html><title>Just a moment...</title></html>').toString('base64'), body: '<html><title>Just a moment...</title></html>' }; } }
  if (/urls\.json$/.test(url)) return b(JSON.stringify(URLS), 'application/json');
  if (/\.(m3u8)(\?|$)/.test(url)) return b('#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360\nlow.m3u8\n', 'application/vnd.apple.mpegurl');
  if (/api|json|graphql|\/ajax|\.php\?|search\?/i.test(url)) return b('{"data":[],"results":[],"items":[],"posts":[],"list":[]}', 'application/json');
  return b('<!doctype html><html><head><title>Mock</title></head><body><div class="post-list"><article class="post"><a href="/movie/test/"><img src="/i.jpg" alt="T"><h2>Test Movie 2020</h2></a></article></div><a href="https://example.com/file">Download</a></body></html>', 'text/html');
}

(async () => {
  const browser = await chromium.launch({ args: ['--no-sandbox'] });
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  await page.route('https://lumora.local/**', async route => {
    const u = new URL(route.request().url());
    if (u.pathname === '/') return route.fulfill({ contentType: 'text/html', body: '<html><head><meta charset="utf-8"></head><body></body></html>' });
    if (u.pathname === '/__net') return route.fulfill({ contentType: 'application/json', body: JSON.stringify(mockNet(JSON.parse(route.request().postData()))) });
    if (u.pathname === '/__crypto') {
      const r = JSON.parse(route.request().postData());
      let out;
      try {
        const B = s => Buffer.from(s || '', 'base64');
        if (r.op === 'digest') out = crypto.createHash(r.alg.replace('-', '').toLowerCase()).update(B(r.dataB64)).digest();
        else if (r.op === 'hmac') out = crypto.createHmac(r.alg.replace('Hmac', '').toLowerCase(), B(r.keyB64)).update(B(r.dataB64)).digest();
        else if (r.op === 'pbkdf2') out = crypto.pbkdf2Sync(B(r.passwordB64), B(r.saltB64), r.cost, r.bits / 8, r.alg || 'sha512');
        else if (r.op === 'aes') {
          const key = B(r.keyB64); const alg = `aes-${key.length * 8}-${r.mode.toLowerCase()}`;
          const iv = r.mode === 'ECB' ? null : B(r.ivB64);
          const c = r.encrypt ? crypto.createCipheriv(alg, key, iv) : crypto.createDecipheriv(alg, key, iv);
          c.setAutoPadding(r.padding !== 'NoPadding');
          out = Buffer.concat([c.update(B(r.dataB64)), c.final()]);
        } else throw new Error('unsupported crypto op');
        return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ result: out.toString('base64') }) });
      } catch (e) { return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ error: e.message }) }); }
    }
    if (u.pathname.startsWith('/__module/')) {
      const [, , plugin, mod] = u.pathname.split('/');
      const f = path.join(DIST, plugin, mod + '.js');
      return route.fulfill({ status: fs.existsSync(f) ? 200 : 404, contentType: 'text/plain', body: fs.existsSync(f) ? fs.readFileSync(f, 'utf8') : '' });
    }
    route.fulfill({ status: 404, body: '' });
  });
  await page.goto('https://lumora.local/');
  await page.evaluate(({ mode, common }) => {
    const nativeFetch = window.fetch.bind(window);
    const results = {}; const kv = {}; window.__calls = {}; window.__hostLogs = [];
    const syncXhr = (method, url, body) => { const x = new XMLHttpRequest(); x.open(method, url, false); x.send(body); return x; };
    window.LumoraHost = {
      httpAsync(id, reqJson) {
        nativeFetch('/__net', { method: 'POST', body: reqJson }).then(r => r.text()).then(t => { results[id] = t; window.__hostResolve(id); });
      },
      takeResult(id) { const r = results[id]; delete results[id]; return r; },
      getModule(p, m) { const x = syncXhr('GET', '/__module/' + p + '/' + m); return x.status === 200 ? x.responseText : ''; },
      finish(id, ok, payload) { window.__calls[id]({ ok, payload }); },
      log(m) { window.__hostLogs.push(m); },
      urlsEndpoint() { return 'https://raw.githubusercontent.com/Zenda-Cross/vega-providers/refs/heads/main/urls.json'; },
      kv(p, op, key, val) {
        const k = p + '|' + key;
        if (op === 'get') return k in kv ? kv[k] : (mode === 'old' ? 'null' : '');
        if (op === 'set') { kv[k] = val; return 'null'; }
        if (op === 'delete') { const had = k in kv; delete kv[k]; return String(had); }
        if (op === 'keys') return JSON.stringify(Object.keys(kv).filter(x => x.startsWith(p + '|')).map(x => x.slice(p.length + 1)));
        if (op === 'clear') { for (const x of Object.keys(kv)) if (x.startsWith(p + '|')) delete kv[x]; return 'null'; }
        return 'null';
      },
      commonHeaders() { return JSON.stringify(common); },
      getCookies() { return '{"cf_clearance":"mock"}'; },
      webViewAsync(id, url, opts) { results[id] = JSON.stringify({ data: '<html>solved</html>', cookies: 'cf_clearance=mock', cookie: 'cf_clearance=mock', cookieMap: { cf_clearance: 'mock' }, url, userAgent: 'MockUA' }); setTimeout(() => window.__hostResolve(id), 5); },
      crypto(j) { return syncXhr('POST', '/__crypto', j).responseText; }
    };
  }, { mode, common: COMMON });
  await page.evaluate(fs.readFileSync(runtimePath, 'utf8'));
  if (mode !== 'old' && extractorsJs) await page.evaluate(extractorsJs);

  let seq = 0;
  const call = (plugin, module, fn, args) => page.evaluate(({ id, plugin, module, fn, args }) => new Promise(res => {
    const t = setTimeout(() => res({ ok: false, payload: 'HARNESS_TIMEOUT' }), 8000);
    window.__calls[id] = r => { clearTimeout(t); res(r); };
    window.__runPlugin(id, plugin, module, fn, JSON.stringify(args));
  }), { id: ++seq, plugin, module, fn, args });

  const providers = manifest.map(m => m.value).filter(v => fs.existsSync(path.join(DIST, v)) && (!only.length || only.includes(v)));
  const report = {};
  for (const p of providers) {
    const base = (URLS[p] && URLS[p].url) || 'https://example.com';
    const link = base.replace(/\/$/, '') + '/movie/test-movie-2020/';
    console.error('>> '+p);
    const r = {};
    const cat = await call(p, 'catalog', 'catalog', {}); r.catalog = cat;
    let filter = ''; try { const c = JSON.parse(cat.payload); if (c[0]) filter = c[0].filter; } catch (e) {}
    r.genres = await call(p, 'catalog', 'genres', {});
    console.error('posts');r.getPosts = await call(p, 'posts', 'getPosts', { filter, page: 1, providerValue: p });
    r.getSearchPosts = await call(p, 'posts', 'getSearchPosts', { searchQuery: 'test', page: 1, providerValue: p });
    console.error('meta');r.getMeta = await call(p, 'meta', 'getMeta', { link, provider: p });
    r.getEpisodes = await call(p, 'episodes', 'getEpisodes', { url: link });
    console.error('stream');r.getStream = await call(p, 'stream', 'getStream', { link, type: 'movie', isDownload: false });
    r.getStreamDownload = await call(p, 'stream', 'getStream', { link, type: 'movie', isDownload: true });
    report[p] = r;
  }
  fs.writeFileSync('/home/claude/w/test/report.' + mode + (process.env.WAF ? '.waf' : '') + '.json', JSON.stringify({ report, hostLogs: await page.evaluate(() => window.__hostLogs) }, null, 1));
  const summary = {};
  for (const [p, r] of Object.entries(report)) for (const [fn, v] of Object.entries(r)) {
    const key = v.ok ? 'ok' : v.payload;
    summary[key] = summary[key] || { n: 0, where: [] };
    summary[key].n++; summary[key].where.push(p + '.' + fn);
  }
  const rows = Object.entries(summary).sort((a, b) => b[1].n - a[1].n);
  for (const [k, v] of rows) console.log(String(v.n).padStart(4), k.slice(0, 150), k === 'ok' ? '' : '  <= ' + v.where.slice(0, 4).join(', '));
  await browser.close();
})().catch(e => { console.error('HARNESS FAILED', e); process.exit(1); });
