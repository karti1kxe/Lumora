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


  const res = await page.evaluate(async () => {
    const out = {}; const t = (n, f) => Promise.resolve().then(f).then(v => { out[n] = v; }, e => { out[n] = 'ERR ' + e.message; });
    // get the real providerContext through a tiny fake plugin module
    window.__ctxp = null;
    const Host = window.LumoraHost; const orig = Host.getModule;
    Host.getModule = (p, m) => p === 'selftest' ? 'exports.getPosts = async function(a){ fetch.__ctx = a.providerContext; return []; }; exports.require = function(n){ return require(n); }; exports.scope = function(){ return [typeof window, typeof document, typeof Buffer, typeof globalThis, typeof fetch, typeof require("x")].join(","); }' : orig(p, m);
    await new Promise(r => { window.__calls[1] = r; window.__runPlugin(1, 'selftest', 'posts', 'getPosts', '{}'); });
    const c = window.fetch.__ctx;
    out.keys = Object.keys(c).join(',');
    await t('sha1', () => c.Aes.sha1('abc'));
    await t('md5', () => c.Aes.md5('abc'));
    await t('sha256', () => c.Crypto.digestStringAsync('SHA-256', 'abc'));
    await t('hmac256', () => c.Aes.hmac256('data', '0b0b0b0b'));
    await t('pbkdf2', () => c.Aes.pbkdf2('pw', 'salt', 1000, 256, 'sha1'));
    const key = '00112233445566778899aabbccddeeff', iv = 'ffeeddccbbaa99887766554433221100';
    await t('encCbc', () => c.Aes.encrypt('hello world', key, iv, 'aes-128-cbc'));
    await t('roundtrip', async () => c.Aes.decrypt(await c.Aes.encrypt('héllo wörld', key + key, iv, 'aes-256-cbc'), key + key, iv, 'aes-256-cbc'));
    window.__CJ = null;
    Host.getModule = (p, m) => p === 'selftest2' ? 'const CJ = require("crypto-js"); exports.run = async function(){ const k = CJ.enc.Utf8.parse("0123456789abcdef"); const iv = CJ.enc.Utf8.parse("fedcba9876543210"); const e = CJ.AES.encrypt("secret text", k, {iv}); const d = CJ.AES.decrypt(e, k, {iv}).toString(CJ.enc.Utf8); const pass = CJ.AES.encrypt("pp text", "passphrase").toString(); const pd = CJ.AES.decrypt(pass, "passphrase").toString(CJ.enc.Utf8); return {md5: CJ.MD5("abc").toString(), sha256b64: CJ.SHA256("abc").toString(CJ.enc.Base64), hmac: CJ.HmacSHA256("m","k").toString(), enc: e.toString(), d, pd, saltedPrefix: pass.slice(0,10)}; }' : orig(p, m);
    const r2 = await new Promise(r => { window.__calls[2] = r; window.__runPlugin(2, 'selftest2', 'x', 'run', '{}'); });
    out.cryptojs = r2.ok ? JSON.parse(r2.payload) : 'ERR ' + r2.payload;
    const r3 = await new Promise(r => { window.__calls[3] = r; window.__runPlugin(3, 'selftest', 'posts', 'scope', '{}'); });
    out.scope = r3.payload;
    // interceptors, kv, openWebView, getCookies, FormData body, redirects of fetch
    await t('kv', async () => { const kv = c.kvStore; const a = await kv.get('nokey'); await kv.set('k', {a: 1}); const b = await kv.get('k'); await kv.set('k', undefined); const d = await kv.get('k'); return [String(a), JSON.stringify(b), String(d)].join('|'); });
    await t('openWebView', async () => JSON.stringify(await c.openWebView('https://x.test/', {waitForCookie: 'cf_clearance'})));
    await t('getCookies', async () => JSON.stringify(await c.getCookies('https://x.test/')));
    await t('interceptor', async () => { const ax = c.axios.create(); const order = []; ax.interceptors.request.use(cf => { order.push('req1'); cf.headers = Object.assign({}, cf.headers, {'X-A': '1'}); return cf; }); ax.interceptors.request.use(cf => { order.push('req2'); return cf; }); ax.interceptors.response.use(r => { order.push('res1'); r.data = {wrapped: typeof r.data}; return r; }); const r = await ax.get('https://x.test/api/json'); return order.join(',') + ' ' + JSON.stringify(r.data); });
    await t('formdata', async () => { const fd = new FormData(); fd.append('a', 'b'); const r = await c.axios.post('https://x.test/post', fd); return String(r.status); });
    await t('baseUrl', () => c.getBaseUrl('vega'));
    return out;
  });
  console.log(JSON.stringify(res, null, 1));
  const nodeC = require('crypto');
  console.log('expect sha1', nodeC.createHash('sha1').update('abc').digest('hex'));
  console.log('expect md5', nodeC.createHash('md5').update('abc').digest('hex'));
  console.log('expect sha256', nodeC.createHash('sha256').update('abc').digest('hex'));
  console.log('expect hmac', nodeC.createHmac('sha256', Buffer.from('0b0b0b0b', 'hex')).update('data').digest('hex'));
  console.log('expect pbkdf2', nodeC.pbkdf2Sync('pw', 'salt', 1000, 32, 'sha1').toString('hex'));
  const ci = nodeC.createCipheriv('aes-128-cbc', Buffer.from('00112233445566778899aabbccddeeff', 'hex'), Buffer.from('ffeeddccbbaa99887766554433221100', 'hex'));
  console.log('expect encCbc', Buffer.concat([ci.update('hello world'), ci.final()]).toString('base64'));
  console.log('expect cryptojs sha256b64', nodeC.createHash('sha256').update('abc').digest('base64'), 'hmac', nodeC.createHmac('sha256', 'k').update('m').digest('hex'));
  await browser.close();
})().catch(e => { console.error('SELFTEST FAILED', e); process.exit(1); });
