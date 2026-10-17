/*
 * Lumora media-plugin runtime (runs inside a hidden WebView).
 * Gives a Vega provider bundle the same world it gets inside Vega's sandbox:
 *   providerContext = { axios, cheerio, Crypto, Aes, commonHeaders, getBaseUrl, openWebView, getCookies, kvStore, extractors }
 *   plus fetch / require() / providerGlobal, and WITHOUT window / document / navigator / Buffer / globalThis (Vega hides them too).
 * All network IO, cookies, crypto, storage and the browser-check WebView go through the native `LumoraHost` bridge (PluginJsEngine.kt).
 */
(function (global) {
  "use strict";
  var Host = global.LumoraHost;
  var pending = {};
  var seq = 0;

  // ---------- native bridge helpers ----------
  global.__hostResolve = function (id) {
    var cb = pending[id];
    delete pending[id];
    if (cb) cb(Host.takeResult(id));
  };

  function hostAsync(start) {
    return new Promise(function (resolve, reject) {
      var id = ++seq;
      pending[id] = function (json) {
        var res;
        try { res = JSON.parse(json); } catch (e) { reject(new Error("Bad response from host")); return; }
        if (res.error) { var err = new Error(res.error); err.isNetworkError = true; reject(err); } else resolve(res);
      };
      try { start(id); } catch (e) { delete pending[id]; reject(e); }
    });
  }

  var lastOrigin = "";
  function hostHttp(req) {
    try { var mm = /^(https?:\/\/[^\/]+)/i.exec(String(req.url)); if (mm && !/\.(png|jpe?g|webp|gif|svg)(\?|$)/i.test(req.url)) lastOrigin = mm[1]; } catch (e) { /* ignore */ }
    return hostAsync(function (id) { Host.httpAsync(id, JSON.stringify(req)); });
  }

  // ---------- bytes / base64 ----------
  function b64ToBytes(b64) {
    var bin = atob(b64), len = bin.length, out = new Uint8Array(len);
    for (var i = 0; i < len; i++) out[i] = bin.charCodeAt(i);
    return out;
  }
  function bytesToB64(bytes) {
    var s = "", CH = 0x8000;
    for (var i = 0; i < bytes.length; i += CH) s += String.fromCharCode.apply(null, bytes.subarray(i, i + CH));
    return btoa(s);
  }
  function bytesToHex(bytes) {
    var s = "";
    for (var i = 0; i < bytes.length; i++) s += (bytes[i] < 16 ? "0" : "") + bytes[i].toString(16);
    return s;
  }
  function hexToBytes(hex) {
    hex = String(hex || "");
    var out = new Uint8Array(Math.floor(hex.length / 2));
    for (var i = 0; i < out.length; i++) out[i] = parseInt(hex.substr(i * 2, 2), 16);
    return out;
  }
  function utf8(str) { return new TextEncoder().encode(String(str)); }
  function toBytes(x) {
    if (x instanceof Uint8Array) return x;
    if (x instanceof ArrayBuffer) return new Uint8Array(x);
    if (x && x.buffer instanceof ArrayBuffer && typeof x.byteLength === "number") return new Uint8Array(x.buffer, x.byteOffset, x.byteLength);
    return utf8(x);
  }

  function normHeaders(h) {
    var out = {};
    if (!h) return out;
    if (typeof h.forEach === "function" && !Array.isArray(h) && typeof h.get === "function") {
      h.forEach(function (v, k) { out[k] = String(v); });
      return out;
    }
    if (Array.isArray(h)) { h.forEach(function (p) { out[p[0]] = String(p[1]); }); return out; }
    Object.keys(h).forEach(function (k) { if (h[k] !== undefined && h[k] !== null && typeof h[k] !== "object") out[k] = String(h[k]); });
    return out;
  }

  function hasHeader(h, name) {
    name = name.toLowerCase();
    return Object.keys(h).some(function (k) { return k.toLowerCase() === name; });
  }
  function deleteHeader(h, name) {
    name = name.toLowerCase();
    Object.keys(h).forEach(function (k) { if (k.toLowerCase() === name) delete h[k]; });
  }

  /** axios' toURLEncodedForm: nested keys as a[b]=c, arrays as a[]=1&a[]=2. */
  function toUrlEncodedForm(obj) {
    var parts = [];
    function add(key, value) {
      if (value === undefined || value === null) return;
      if (Array.isArray(value)) { value.forEach(function (v) { add(key + "[]", v); }); return; }
      if (typeof value === "object" && !(value instanceof Date)) {
        Object.keys(value).forEach(function (k) { add(key ? key + "[" + k + "]" : k, value[k]); });
        return;
      }
      parts.push(encodeURIComponent(key) + "=" + encodeURIComponent(value instanceof Date ? value.toISOString() : value));
    }
    Object.keys(obj).forEach(function (k) { add(k, obj[k]); });
    return parts.join("&");
  }

  /**
   * Request body -> { body (text) | bodyBase64, contentType }. Same kinds Vega's sandbox serializes:
   * string, Uint8Array / ArrayBuffer, URLSearchParams, FormData (multipart, with its boundary), plain objects (JSON).
   */
  function encodeBody(data, headers, forAxios) {
    if (data === undefined || data === null) return {};
    if (typeof data === "string") return { body: data };
    if (data instanceof Uint8Array || data instanceof ArrayBuffer) return { bodyBase64: bytesToB64(toBytes(data)) };
    if (typeof URLSearchParams !== "undefined" && data instanceof URLSearchParams) {
      if (!hasHeader(headers, "content-type")) headers["Content-Type"] = "application/x-www-form-urlencoded;charset=utf-8";
      return { body: data.toString() };
    }
    if (typeof FormData !== "undefined" && data instanceof FormData) {
      var r = new Response(data);
      var ct = r.headers.get("content-type");
      if (ct) { deleteHeader(headers, "content-type"); headers["Content-Type"] = ct; }
      return { asyncBody: r.arrayBuffer().then(function (ab) { return { bodyBase64: bytesToB64(new Uint8Array(ab)) }; }) };
    }
    if (typeof Blob !== "undefined" && data instanceof Blob) {
      return { asyncBody: data.arrayBuffer().then(function (ab) { return { bodyBase64: bytesToB64(new Uint8Array(ab)) }; }) };
    }
    if (typeof data === "object") {
      var ctype = "";
      Object.keys(headers).forEach(function (k) { if (k.toLowerCase() === "content-type") ctype = String(headers[k]); });
      if (forAxios && ctype.indexOf("application/x-www-form-urlencoded") >= 0) return { body: toUrlEncodedForm(data) };
      if (!ctype) headers["Content-Type"] = "application/json";
      return { body: JSON.stringify(data) };
    }
    return { body: String(data) };
  }

  function sendRequest(opts) {
    // opts: { method, url, headers, data, follow, timeout, forAxios }
    var enc = encodeBody(opts.data, opts.headers, opts.forAxios);
    return Promise.resolve(enc.asyncBody || enc).then(function (e) {
      var req = {
        method: opts.method, url: opts.url, headers: opts.headers,
        follow: opts.follow, binary: true, timeout: opts.timeout || 0
      };
      if (e.body !== undefined) req.body = e.body;
      if (e.bodyBase64 !== undefined) req.bodyBase64 = e.bodyBase64;
      return hostHttp(req);
    }).then(function (res) {
      var bytes = res.bodyBase64 ? b64ToBytes(res.bodyBase64) : new Uint8Array(0);
      res.bytes = bytes;
      return res;
    });
  }

  function decodeText(bytes) { return new TextDecoder("utf-8").decode(bytes); } // strips a BOM, like Response.text()

  function abortError(msg) { var e = new Error(msg || "The operation was aborted"); e.name = "AbortError"; return e; }
  function withSignal(signal, promise) {
    if (!signal) return promise;
    if (signal.aborted) return Promise.reject(abortError());
    return new Promise(function (resolve, reject) {
      var onAbort = function () { reject(abortError()); };
      if (typeof signal.addEventListener === "function") signal.addEventListener("abort", onAbort, { once: true });
      promise.then(function (v) { resolve(v); }, function (e) { reject(e); });
    });
  }

  // ---------- fetch ----------
  global.fetch = function (input, init) {
    init = init || {};
    var url = typeof input === "string" ? input : (input && input.url) || String(input);
    var headers = Object.assign({}, normHeaders(input && input.headers), normHeaders(init.headers));
    var method = String(init.method || (input && input.method) || "GET").toUpperCase();
    var body = init.body !== undefined ? init.body : (input && input.body);
    var p = sendRequest({ method: method, url: url, headers: headers, data: body, follow: init.redirect !== "manual", timeout: 0 })
      .then(function (res) {
        var nullBody = res.status === 204 || res.status === 304 || method === "HEAD";
        var status = res.status >= 200 && res.status <= 599 ? res.status : 200;
        var rh = [];
        Object.keys(res.headers || {}).forEach(function (k) { rh.push([k, String(res.headers[k])]); });
        var r;
        try {
          r = new Response(nullBody ? null : res.bytes.buffer, { status: status, statusText: res.statusText || "", headers: rh });
        } catch (e) {
          r = new Response(nullBody ? null : res.bytes.buffer, { status: status, statusText: res.statusText || "" });
        }
        try { Object.defineProperty(r, "url", { value: res.url || url }); } catch (e) { /* ignore */ }
        try { Object.defineProperty(r, "redirected", { value: !!res.url && res.url !== url }); } catch (e) { /* ignore */ }
        var raw = [];
        Object.keys(res.headers || {}).forEach(function (k) { raw.push([k, String(res.headers[k])]); });
        (res.setCookies || []).forEach(function (c) { raw.push(["set-cookie", String(c)]); });
        try { Object.defineProperty(r, "rawHeaders", { value: raw }); } catch (e) { /* ignore */ }
        return r;
      });
    return withSignal(init.signal, p);
  };

  // ---------- axios ----------
  function axiosError(message, config, response, code) {
    var e = new Error(message);
    e.isAxiosError = true;
    e.name = "AxiosError";
    e.config = config;
    e.response = response;
    e.code = code;
    e.status = response ? response.status : undefined;
    e.toJSON = function () { return { message: e.message, code: e.code, status: response && response.status }; };
    return e;
  }

  function joinUrl(base, url) {
    if (!base || /^[a-z][a-z0-9+.-]*:\/\//i.test(url)) return url;
    return base.replace(/\/+$/, "") + "/" + String(url).replace(/^\/+/, "");
  }

  function appendParams(url, params, serializer) {
    if (!params) return url;
    var sp;
    if (serializer && typeof serializer.serialize === "function") sp = serializer.serialize(params);
    else if (typeof URLSearchParams !== "undefined" && params instanceof URLSearchParams) sp = params.toString();
    else {
      var parts = [];
      Object.keys(params).forEach(function (k) {
        var v = params[k];
        if (v === undefined || v === null) return;
        if (Array.isArray(v)) v.forEach(function (x) { parts.push(encodeURIComponent(k) + "=" + encodeURIComponent(x)); });
        else parts.push(encodeURIComponent(k) + "=" + encodeURIComponent(v));
      });
      sp = parts.join("&");
    }
    if (!sp) return url;
    return url + (url.indexOf("?") >= 0 ? "&" : "?") + sp;
  }

  /** axios headers may be flat or {common:{}, get:{}, post:{}, ...}: flatten for one method. */
  function flattenHeaders(h, method) {
    var out = {};
    if (!h) return out;
    var m = String(method || "get").toLowerCase();
    var METHODS = ["common", "get", "post", "put", "patch", "delete", "head", "options"];
    if (h.common && typeof h.common === "object") Object.assign(out, normHeaders(h.common));
    if (h[m] && typeof h[m] === "object") Object.assign(out, normHeaders(h[m]));
    Object.keys(h).forEach(function (k) {
      if (METHODS.indexOf(k) >= 0 && typeof h[k] === "object") return;
      if (h[k] !== undefined && h[k] !== null && typeof h[k] !== "object") out[k] = String(h[k]);
    });
    return out;
  }

  function mergeConfig(a, b) {
    var out = {};
    Object.keys(a || {}).forEach(function (k) { out[k] = a[k]; });
    Object.keys(b || {}).forEach(function (k) {
      if (k === "headers") {
        var ah = (a && a.headers) || {}, bh = b.headers || {};
        var merged = {};
        ["common", "get", "post", "put", "patch", "delete", "head", "options"].forEach(function (mk) {
          var x = Object.assign({}, ah[mk] || {}, bh[mk] || {});
          if (Object.keys(x).length) merged[mk] = x;
        });
        Object.keys(ah).forEach(function (hk) { if (!merged[hk] && typeof ah[hk] !== "object") merged[hk] = ah[hk]; });
        Object.keys(bh).forEach(function (hk) { if (!merged[hk] && typeof bh[hk] !== "object") merged[hk] = bh[hk]; });
        if (typeof bh.forEach === "function" && typeof bh.get === "function") Object.assign(merged, normHeaders(bh));
        out.headers = merged;
      } else if (b[k] !== undefined) out[k] = b[k];
    });
    return out;
  }

  function InterceptorManager() { this.handlers = []; }
  InterceptorManager.prototype.use = function (fulfilled, rejected, options) {
    this.handlers.push({ fulfilled: fulfilled, rejected: rejected, options: options || {} });
    return this.handlers.length - 1;
  };
  InterceptorManager.prototype.eject = function (id) { if (this.handlers[id]) this.handlers[id] = null; };
  InterceptorManager.prototype.clear = function () { this.handlers = []; };
  InterceptorManager.prototype.forEach = function (fn) { this.handlers.forEach(function (h) { if (h !== null) fn(h); }); };

  // axios 1.x (what Vega bundles): only Accept is a default; Content-Type is derived from the body.
  var AXIOS_DEFAULT_HEADERS = { common: { "Accept": "application/json, text/plain, */*" } };

  function createAxios(defaults) {
    var interceptors = { request: new InterceptorManager(), response: new InterceptorManager() };

    function dispatch(config) {
      if (config.signal && config.signal.aborted) return Promise.reject(axiosError("canceled", config, undefined, "ERR_CANCELED"));
      var method = String(config.method || "get").toUpperCase();
      var headers = flattenHeaders(config.headers, method);
      var url = appendParams(joinUrl(config.baseURL, config.url || ""), config.params, config.paramsSerializer);
      var wantBinary = config.responseType === "arraybuffer" || config.responseType === "blob";
      var data = config.data;
      if (typeof config.transformRequest === "function") data = config.transformRequest(data, headers);
      else if (Array.isArray(config.transformRequest)) config.transformRequest.forEach(function (t) { if (typeof t === "function") data = t(data, headers); });
      var p = sendRequest({
        method: method, url: url, headers: headers, data: data,
        follow: config.maxRedirects !== 0, timeout: config.timeout || 0, forAxios: true
      }).then(function (res) {
        var out;
        if (wantBinary) out = res.bytes.buffer;
        else {
          out = decodeText(res.bytes);
          if (config.responseType !== "text") {
            if (out) { try { out = JSON.parse(out); } catch (e) { /* keep text */ } }
          }
        }
        var rh = {};
        Object.keys(res.headers || {}).forEach(function (k) { rh[k] = res.headers[k]; });
        if (res.setCookies && res.setCookies.length) { rh["set-cookie"] = res.setCookies.slice(); rh["x-set-cookie"] = res.setCookies.slice(); }
        var response = {
          data: out, status: res.status, statusText: res.statusText || "",
          headers: rh, config: config,
          request: { url: url, responseURL: res.url || url, res: { responseUrl: res.url || url } }
        };
        if (typeof config.transformResponse === "function") response.data = config.transformResponse(response.data, rh, res.status);
        else if (Array.isArray(config.transformResponse)) config.transformResponse.forEach(function (t) { if (typeof t === "function") response.data = t(response.data, rh, res.status); });
        var ok = typeof config.validateStatus === "function"
          ? config.validateStatus(res.status)
          : (res.status >= 200 && res.status < 300);
        if (!ok) throw axiosError("Request failed with status code " + res.status, config, response, res.status >= 500 ? "ERR_BAD_RESPONSE" : "ERR_BAD_REQUEST");
        return response;
      }, function (err) {
        if (err && err.isAxiosError) throw err;
        if (err && err.name === "AbortError") throw axiosError("canceled", config, undefined, "ERR_CANCELED");
        var msg = err && err.message ? err.message : "Network Error";
        var code = /timeout|timed out/i.test(msg) ? "ECONNABORTED" : "ERR_NETWORK";
        throw axiosError(code === "ECONNABORTED" ? "timeout of " + (config.timeout || 0) + "ms exceeded" : msg, config, undefined, code);
      });
      return withSignal(config.signal, p).catch(function (e) {
        if (e && e.name === "AbortError") throw axiosError("canceled", config, undefined, "ERR_CANCELED");
        throw e;
      });
    }

    function request(config) {
      config = mergeConfig(defaults, config);
      var chain = [dispatch, undefined];
      interceptors.request.forEach(function (h) { chain.unshift(h.fulfilled, h.rejected); });
      interceptors.response.forEach(function (h) { chain.push(h.fulfilled, h.rejected); });
      var promise = Promise.resolve(config);
      for (var i = 0; i < chain.length; i += 2) promise = promise.then(chain[i], chain[i + 1]);
      return promise;
    }
    var instance = function (a, b) {
      return typeof a === "string" ? request(Object.assign({}, b, { url: a })) : request(a);
    };
    instance.request = request;
    instance.getUri = function (c) { c = mergeConfig(defaults, c); return appendParams(joinUrl(c.baseURL, c.url || ""), c.params); };
    ["get", "delete", "head", "options"].forEach(function (m) {
      instance[m] = function (url, config) { return request(Object.assign({}, config, { method: m, url: url })); };
    });
    ["post", "put", "patch"].forEach(function (m) {
      instance[m] = function (url, data, config) { return request(Object.assign({}, config, { method: m, url: url, data: data })); };
    });
    ["postForm", "putForm", "patchForm"].forEach(function (m) {
      instance[m] = function (url, data, config) { return request(Object.assign({}, config, { method: m.replace("Form", ""), url: url, data: data, headers: Object.assign({}, config && config.headers) })); };
    });
    instance.defaults = defaults;
    instance.interceptors = interceptors;
    instance.create = function (cfg) { return createAxios(mergeConfig(defaults, cfg)); };
    instance.isAxiosError = function (e) { return !!(e && e.isAxiosError); };
    instance.isCancel = function (e) { return !!(e && e.code === "ERR_CANCELED"); };
    instance.all = function (p) { return Promise.all(p); };
    instance.spread = function (cb) { return function (arr) { return cb.apply(null, arr); }; };
    instance.CancelToken = { source: function () { return { token: {}, cancel: function () {} }; } };
    instance.AxiosError = function (message, code, config, req, response) { return axiosError(message, config, response, code); };
    return instance;
  }
  var axios = createAxios({ headers: JSON.parse(JSON.stringify(AXIOS_DEFAULT_HEADERS)) });
  axios.default = axios;


  // ---------- cheerio (over the browser DOM) ----------
  var containsIds = {}; // needle -> id
  var containsSeq = 0;

  function markContains(doc, needle) {
    var id = containsIds[needle];
    if (id === undefined) id = containsIds[needle] = ++containsSeq;
    var key = "__cm" + id;
    if (doc[key]) return id;
    doc[key] = true;
    var all = doc.getElementsByTagName("*");
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      if ((el.textContent || "").indexOf(needle) >= 0) {
        var cur = el.getAttribute("data-lcn");
        el.setAttribute("data-lcn", (cur ? cur + " " : "") + id);
      }
    }
    return id;
  }

  function fixSelector(sel, doc) {
    sel = String(sel);
    if (sel.indexOf(":contains(") >= 0) {
      sel = sel.replace(/:contains\(\s*(?:"([^"]*)"|'([^']*)'|([^)]*?))\s*\)/g, function (m, a, b, c) {
        var needle = a !== undefined ? a : (b !== undefined ? b : c);
        return '[data-lcn~="' + markContains(doc, needle) + '"]';
      });
    }
    if (sel.indexOf(":header") >= 0) sel = sel.replace(/:header/g, ":is(h1,h2,h3,h4,h5,h6)");
    return sel;
  }

  function docOf(node) { return node.nodeType === 9 ? node : node.ownerDocument; }

  function selectAll(root, sel) {
    var doc = docOf(root);
    var first = false;
    var s = String(sel);
    if (/:first\s*$/.test(s)) { s = s.replace(/:first\s*$/, ""); first = true; }
    var res = Array.prototype.slice.call(root.querySelectorAll(fixSelector(s, doc)));
    return first ? res.slice(0, 1) : res;
  }

  function matchesSel(el, sel) {
    if (el.nodeType !== 1) return false;
    try { return el.matches(fixSelector(sel, el.ownerDocument)); } catch (e) { return false; }
  }

  function uniqueSort(arr) {
    var seen = new Set(), out = [];
    arr.forEach(function (n) { if (!seen.has(n)) { seen.add(n); out.push(n); } });
    out.sort(function (a, b) {
      if (a === b || !a.compareDocumentPosition || !b.compareDocumentPosition) return 0;
      var p = a.compareDocumentPosition(b);
      if (p & 4) return -1;
      if (p & 2) return 1;
      return 0;
    });
    return out;
  }

  function isNode(x) { return x && typeof x === "object" && typeof x.nodeType === "number"; }

  function Cheerio(items, doc) {
    this._doc = doc;
    this.length = items.length;
    for (var i = 0; i < items.length; i++) this[i] = items[i];
  }
  var CP = Cheerio.prototype;
  CP._items = function () { return Array.prototype.slice.call(this); };
  CP._wrap = function (arr) { return new Cheerio(arr, this._doc); };
  CP._elements = function () { return this._items().filter(function (n) { return isNode(n) && (n.nodeType === 1 || n.nodeType === 9); }); };

  CP.find = function (sel) {
    var out = [];
    this._elements().forEach(function (el) { out = out.concat(selectAll(el, sel)); });
    return this._wrap(uniqueSort(out));
  };
  CP.children = function (sel) {
    var out = [];
    this._elements().forEach(function (el) {
      for (var i = 0; i < el.children.length; i++) out.push(el.children[i]);
    });
    if (sel) out = out.filter(function (n) { return matchesSel(n, sel); });
    return this._wrap(uniqueSort(out));
  };
  CP.contents = function () {
    var out = [];
    this._elements().forEach(function (el) { out = out.concat(Array.prototype.slice.call(el.childNodes)); });
    return this._wrap(out);
  };
  CP.parent = function (sel) {
    var out = [];
    this._elements().forEach(function (el) { var p = el.parentElement; if (p && (!sel || matchesSel(p, sel))) out.push(p); });
    return this._wrap(uniqueSort(out));
  };
  CP.parents = function (sel) {
    var out = [];
    this._elements().forEach(function (el) {
      var p = el.parentElement;
      while (p) { if (!sel || matchesSel(p, sel)) out.push(p); p = p.parentElement; }
    });
    return this._wrap(uniqueSort(out));
  };
  CP.closest = function (sel) {
    var out = [];
    this._elements().forEach(function (el) {
      if (el.nodeType !== 1) return;
      var c = null;
      try { c = el.closest(fixSelector(sel, el.ownerDocument)); } catch (e) { c = null; }
      if (c) out.push(c);
    });
    return this._wrap(uniqueSort(out));
  };
  function sibling(self, prop, all, sel) {
    var out = [];
    self._elements().forEach(function (el) {
      var n = el[prop];
      while (n) {
        if (!sel || matchesSel(n, sel)) out.push(n);
        if (!all) break;
        n = n[prop];
      }
    });
    return self._wrap(uniqueSort(out));
  }
  CP.next = function (sel) { return sibling(this, "nextElementSibling", false, sel); };
  CP.nextAll = function (sel) { return sibling(this, "nextElementSibling", true, sel); };
  CP.prev = function (sel) { return sibling(this, "previousElementSibling", false, sel); };
  CP.prevAll = function (sel) { return sibling(this, "previousElementSibling", true, sel); };
  CP.siblings = function (sel) {
    var out = [];
    this._elements().forEach(function (el) {
      var p = el.parentElement;
      if (!p) return;
      for (var i = 0; i < p.children.length; i++) {
        var c = p.children[i];
        if (c !== el && (!sel || matchesSel(c, sel))) out.push(c);
      }
    });
    return this._wrap(uniqueSort(out));
  };
  CP.nextUntil = function (sel) {
    var out = [];
    this._elements().forEach(function (el) {
      var n = el.nextElementSibling;
      while (n && !(sel && matchesSel(n, sel))) { out.push(n); n = n.nextElementSibling; }
    });
    return this._wrap(uniqueSort(out));
  };
  CP.first = function () { return this._wrap(this.length ? [this[0]] : []); };
  CP.last = function () { return this._wrap(this.length ? [this[this.length - 1]] : []); };
  CP.eq = function (i) { i = +i; if (i < 0) i += this.length; return this._wrap(i >= 0 && i < this.length ? [this[i]] : []); };
  CP.slice = function (a, b) { return this._wrap(this._items().slice(a, b)); };
  CP.get = function (i) { return i === undefined ? this._items() : (i < 0 ? this[this.length + i] : this[i]); };
  CP.toArray = function () { return this._items(); };
  CP.index = function () {
    var el = this[0];
    if (!el || !el.parentElement) return -1;
    return Array.prototype.indexOf.call(el.parentElement.children, el);
  };
  CP.each = function (fn) {
    for (var i = 0; i < this.length; i++) {
      if (fn.call(this[i], i, this[i]) === false) break;
    }
    return this;
  };
  CP.map = function (fn) {
    var out = [];
    for (var i = 0; i < this.length; i++) {
      var r = fn.call(this[i], i, this[i]);
      if (r === null || r === undefined) continue;
      if (Array.isArray(r)) out = out.concat(r); else out.push(r);
    }
    return this._wrap(out);
  };
  function toMatcher(sel) {
    if (typeof sel === "function") return function (el, i) { return !!sel.call(el, i, el); };
    if (typeof sel === "string") return function (el) { return matchesSel(el, sel); };
    if (sel instanceof Cheerio) { var set = new Set(sel._items()); return function (el) { return set.has(el); }; }
    if (isNode(sel)) return function (el) { return el === sel; };
    return function () { return false; };
  }
  CP.filter = function (sel) { var m = toMatcher(sel); return this._wrap(this._items().filter(function (el, i) { return m(el, i); })); };
  CP.not = function (sel) { var m = toMatcher(sel); return this._wrap(this._items().filter(function (el, i) { return !m(el, i); })); };
  CP.is = function (sel) { var m = toMatcher(sel); return this._items().some(function (el, i) { return m(el, i); }); };
  CP.has = function (sel) {
    return this._wrap(this._items().filter(function (el) {
      if (!isNode(el) || el.nodeType !== 1) return false;
      if (typeof sel === "string") {
        try { return selectAll(el, sel).length > 0; } catch (e) { return false; }
      }
      var target = sel instanceof Cheerio ? sel[0] : sel;
      return !!target && el !== target && el.contains(target);
    }));
  };
  CP.add = function (sel, ctx) {
    var more;
    if (typeof sel === "string") more = selectAll((ctx && ctx[0]) || this._doc, sel);
    else if (sel instanceof Cheerio) more = sel._items();
    else if (isNode(sel)) more = [sel];
    else if (Array.isArray(sel)) more = sel;
    else more = [];
    return this._wrap(uniqueSort(this._items().concat(more)));
  };
  CP.end = function () { return this; };
  CP.hasClass = function (c) { return this._elements().some(function (el) { return el.nodeType === 1 && el.classList.contains(c); }); };
  CP.addClass = function (c) { this._elements().forEach(function (el) { String(c).split(/\s+/).forEach(function (x) { if (x && el.classList) el.classList.add(x); }); }); return this; };
  CP.removeClass = function (c) { this._elements().forEach(function (el) { String(c).split(/\s+/).forEach(function (x) { if (x && el.classList) el.classList.remove(x); }); }); return this; };
  CP.attr = function (name, value) {
    if (value === undefined && (typeof name === "string")) {
      var el = this[0];
      if (!isNode(el) || el.nodeType !== 1) return undefined;
      var v = el.getAttribute(name);
      return v === null ? undefined : v;
    }
    var self = this;
    this._elements().forEach(function (el) {
      if (el.nodeType !== 1) return;
      if (typeof name === "object") Object.keys(name).forEach(function (k) { el.setAttribute(k, name[k]); });
      else if (value === null) el.removeAttribute(name);
      else el.setAttribute(name, value);
    });
    return self;
  };
  CP.removeAttr = function (name) { this._elements().forEach(function (el) { if (el.nodeType === 1) el.removeAttribute(name); }); return this; };
  CP.prop = function (name) {
    var el = this[0];
    if (!isNode(el)) return undefined;
    if (name === "tagName" || name === "nodeName") return el.nodeName ? el.nodeName.toUpperCase() : undefined;
    if (name === "outerHTML") return el.outerHTML;
    if (name === "innerHTML") return el.innerHTML;
    return el[name];
  };
  CP.data = function (name) {
    var el = this[0];
    if (!isNode(el) || el.nodeType !== 1) return undefined;
    if (name === undefined) { var o = {}; Object.keys(el.dataset || {}).forEach(function (k) { o[k] = el.dataset[k]; }); return o; }
    var key = String(name).replace(/-([a-z])/g, function (m, c) { return c.toUpperCase(); });
    var v = el.dataset ? el.dataset[key] : undefined;
    if (v === undefined) return undefined;
    if (v === "true") return true;
    if (v === "false") return false;
    if (v !== "" && !isNaN(v) && String(+v) === v) return +v;
    if (/^[\[{]/.test(v)) { try { return JSON.parse(v); } catch (e) { /* plain */ } }
    return v;
  };
  CP.val = function (v) {
    if (v !== undefined) { this._elements().forEach(function (el) { el.value = v; }); return this; }
    var el = this[0];
    if (!isNode(el) || el.nodeType !== 1) return undefined;
    if (el.nodeName === "SELECT" || el.nodeName === "INPUT" || el.nodeName === "TEXTAREA" || el.nodeName === "BUTTON" || el.nodeName === "OPTION") return el.value;
    return el.getAttribute("value") || undefined;
  };
  function nodeText(n) {
    if (n.nodeType === 3 || n.nodeType === 4) return n.nodeValue || "";
    if (n.nodeType === 1 || n.nodeType === 9 || n.nodeType === 11) {
      var out = "";
      for (var c = n.firstChild; c; c = c.nextSibling) {
        if (c.nodeType === 8) continue;
        out += nodeText(c);
      }
      return out;
    }
    return "";
  }
  CP.text = function (value) {
    if (value !== undefined) { this._elements().forEach(function (el) { el.textContent = value; }); return this; }
    var out = "";
    for (var i = 0; i < this.length; i++) if (isNode(this[i])) out += nodeText(this[i]);
    return out;
  };
  CP.html = function (value) {
    if (value !== undefined) { this._elements().forEach(function (el) { el.innerHTML = value; }); return this; }
    var el = this[0];
    if (!isNode(el)) return null;
    if (el.nodeType === 9) return el.documentElement ? el.documentElement.outerHTML : "";
    return el.nodeType === 1 ? el.innerHTML : null;
  };
  CP.remove = function () { this._items().forEach(function (n) { if (isNode(n) && n.parentNode) n.parentNode.removeChild(n); }); return this; };
  CP.clone = function () { return this._wrap(this._items().map(function (n) { return isNode(n) ? n.cloneNode(true) : n; })); };
  CP.toString = function () { return this._items().map(function (n) { return isNode(n) ? (n.outerHTML || n.nodeValue || "") : String(n); }).join(""); };
  CP[Symbol.iterator] = function () { return this._items()[Symbol.iterator](); };

  function load(html) {
    var doc = new DOMParser().parseFromString(String(html == null ? "" : html), "text/html");
    function $(sel, ctx) {
      if (sel === undefined || sel === null || sel === "") return new Cheerio([], doc);
      if (sel instanceof Cheerio) return sel;
      if (isNode(sel)) return new Cheerio([sel], doc);
      if (Array.isArray(sel)) return new Cheerio(sel, doc);
      if (typeof sel === "string") {
        var s = sel.trim();
        if (s.charAt(0) === "<") {
          var tpl = doc.createElement("template");
          tpl.innerHTML = s;
          return new Cheerio(Array.prototype.slice.call(tpl.content.childNodes), doc);
        }
        if (ctx !== undefined) {
          var c = ctx instanceof Cheerio ? ctx : $(ctx);
          return c.find(s);
        }
        return new Cheerio(uniqueSort(selectAll(doc, s)), doc);
      }
      return new Cheerio([], doc);
    }
    $.root = function () { return new Cheerio([doc], doc); };
    $.html = function (target) {
      if (target === undefined) return doc.documentElement ? doc.documentElement.outerHTML : "";
      var c = target instanceof Cheerio ? target : $(target);
      return c.toString();
    };
    $.text = function (target) { return (target === undefined ? $.root() : $(target)).text(); };
    $.xml = $.html;
    $.contains = function (a, b) { return isNode(a) && isNode(b) && a !== b && a.contains(b); };
    $.parseHTML = function (h) { return $(h).get(); };
    return $;
  }
  var cheerio = { load: load, default: { load: load } };


  // ---------- Vega's common headers (single source: PluginJsEngine.COMMON_HEADERS) ----------
  var COMMON = {};
  try { COMMON = JSON.parse(Host.commonHeaders()); } catch (e) { COMMON = { "User-Agent": "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36", "Accept-Language": "en-US,en;q=0.9" }; }

  // ---------- crypto: expo-crypto style `Crypto` (what Vega passes) and react-native-aes-crypto style `Aes` ----------
  function hostCrypto(obj) {
    var r = JSON.parse(Host.crypto(JSON.stringify(obj)));
    if (r.error) throw new Error(r.error);
    return r.result;
  }
  function digestBytes(alg, bytes) { return b64ToBytes(hostCrypto({ op: "digest", alg: alg, dataB64: bytesToB64(bytes) })); }
  function hmacBytes(alg, keyBytes, bytes) { return b64ToBytes(hostCrypto({ op: "hmac", alg: alg, keyB64: bytesToB64(keyBytes), dataB64: bytesToB64(bytes) })); }
  function aesBytes(o) { return b64ToBytes(hostCrypto(Object.assign({ op: "aes" }, o))); }

  var Crypto = Object.freeze({
    CryptoDigestAlgorithm: Object.freeze({ SHA1: "SHA-1", SHA256: "SHA-256", SHA384: "SHA-384", SHA512: "SHA-512", MD5: "MD5" }),
    CryptoEncoding: Object.freeze({ HEX: "hex", BASE64: "base64" }),
    digestStringAsync: function (algorithm, data, options) {
      return Promise.resolve().then(function () {
        var out = digestBytes(algorithm, utf8(data));
        return options && options.encoding === "base64" ? bytesToB64(out) : bytesToHex(out);
      });
    },
    getRandomBytesAsync: function (n) { return Promise.resolve().then(function () { var a = new Uint8Array(n); crypto.getRandomValues(a); return a; }); },
    randomUUID: function () {
      var hex = "0123456789abcdef", uuid = "";
      for (var i = 0; i < 36; i++) {
        if (i === 8 || i === 13 || i === 18 || i === 23) uuid += "-";
        else if (i === 14) uuid += "4";
        else uuid += hex[Math.floor(Math.random() * 16)];
      }
      return uuid;
    }
  });

  function parseAesAlgorithm(algorithm) {
    var m = /^aes-(128|192|256)-(cbc|ecb|ctr|gcm)$/i.exec(String(algorithm || "aes-256-cbc"));
    if (!m) throw new Error("Unsupported AES algorithm: " + algorithm);
    return { bits: +m[1], mode: m[2].toUpperCase() };
  }
  var Aes = Object.freeze({
    sha1: function (t) { return Promise.resolve().then(function () { return bytesToHex(digestBytes("SHA-1", toBytes(t))); }); },
    sha256: function (t) { return Promise.resolve().then(function () { return bytesToHex(digestBytes("SHA-256", toBytes(t))); }); },
    sha512: function (t) { return Promise.resolve().then(function () { return bytesToHex(digestBytes("SHA-512", toBytes(t))); }); },
    md5: function (t) { return Promise.resolve().then(function () { return bytesToHex(digestBytes("MD5", toBytes(t))); }); },
    hmac256: function (t, keyHex) { return Promise.resolve().then(function () { return bytesToHex(hmacBytes("HmacSHA256", hexToBytes(keyHex), toBytes(t))); }); },
    hmac512: function (t, keyHex) { return Promise.resolve().then(function () { return bytesToHex(hmacBytes("HmacSHA512", hexToBytes(keyHex), toBytes(t))); }); },
    randomKey: function (length) { return Promise.resolve().then(function () { var a = new Uint8Array(length); crypto.getRandomValues(a); return bytesToHex(a); }); },
    /** password, salt: text; cost: iterations; length: bits (256 -> 64 hex chars). */
    pbkdf2: function (password, salt, cost, length, algorithm) {
      return Promise.resolve().then(function () {
        return bytesToHex(b64ToBytes(hostCrypto({ op: "pbkdf2", passwordB64: bytesToB64(utf8(password)), saltB64: bytesToB64(utf8(salt)), cost: cost, bits: length || 256, alg: algorithm || "sha512" })));
      });
    },
    /** text -> base64 cipher text; key / iv are hex strings (like react-native-aes-crypto). */
    encrypt: function (text, keyHex, ivHex, algorithm) {
      return Promise.resolve().then(function () {
        var a = parseAesAlgorithm(algorithm);
        return bytesToB64(aesBytes({ mode: a.mode, padding: a.mode === "CBC" || a.mode === "ECB" ? "PKCS5Padding" : "NoPadding", encrypt: true, keyB64: bytesToB64(hexToBytes(keyHex)), ivB64: ivHex ? bytesToB64(hexToBytes(ivHex)) : "", dataB64: bytesToB64(toBytes(text)) }));
      });
    },
    /** base64 cipher text -> text */
    decrypt: function (cipherB64, keyHex, ivHex, algorithm) {
      return Promise.resolve().then(function () {
        var a = parseAesAlgorithm(algorithm);
        return decodeText(aesBytes({ mode: a.mode, padding: a.mode === "CBC" || a.mode === "ECB" ? "PKCS5Padding" : "NoPadding", encrypt: false, keyB64: bytesToB64(hexToBytes(keyHex)), ivB64: ivHex ? bytesToB64(hexToBytes(ivHex)) : "", dataB64: cipherB64 }));
      });
    }
  });

  // ---------- crypto-js (require("crypto-js")): the commonly used part, synchronous like the real one ----------
  var CryptoJS = (function () {
    function WordArray(bytes) { this._b = bytes; }
    Object.defineProperty(WordArray.prototype, "sigBytes", { get: function () { return this._b.length; } });
    Object.defineProperty(WordArray.prototype, "words", { get: function () {
      var w = [], b = this._b;
      for (var i = 0; i < b.length; i++) w[i >>> 2] = (w[i >>> 2] || 0) | (b[i] << (24 - (i % 4) * 8));
      return w;
    } });
    WordArray.prototype.toString = function (enc) { return (enc || Enc.Hex).stringify(this); };
    WordArray.prototype.clone = function () { return new WordArray(this._b.slice()); };
    WordArray.prototype.concat = function (o) { var n = new Uint8Array(this._b.length + o._b.length); n.set(this._b, 0); n.set(o._b, this._b.length); this._b = n; return this; };
    function wa(bytes) { return new WordArray(bytes); }
    function bytesOf(x) { return x instanceof WordArray ? x._b : utf8(x); }
    var Enc = {
      Hex: { stringify: function (w) { return bytesToHex(w._b); }, parse: function (s) { return wa(hexToBytes(s)); } },
      Base64: { stringify: function (w) { return bytesToB64(w._b); }, parse: function (s) { return wa(b64ToBytes(String(s).replace(/-/g, "+").replace(/_/g, "/"))); } },
      Utf8: { stringify: function (w) { return decodeText(w._b); }, parse: function (s) { return wa(utf8(s)); } },
      Latin1: { stringify: function (w) { var s = ""; for (var i = 0; i < w._b.length; i++) s += String.fromCharCode(w._b[i]); return s; },
                parse: function (s) { var b = new Uint8Array(s.length); for (var i = 0; i < s.length; i++) b[i] = s.charCodeAt(i) & 255; return wa(b); } }
    };
    function hash(alg) { return function (msg) { return wa(digestBytes(alg, bytesOf(msg))); }; }
    function hmac(alg) { return function (msg, key) { return wa(hmacBytes(alg, bytesOf(key), bytesOf(msg))); }; }
    function evpKdf(pass, salt, keyLen, ivLen) {
      var out = new Uint8Array(0), block = new Uint8Array(0);
      while (out.length < keyLen + ivLen) {
        var inp = new Uint8Array(block.length + pass.length + salt.length);
        inp.set(block, 0); inp.set(pass, block.length); inp.set(salt, block.length + pass.length);
        block = digestBytes("MD5", inp);
        var n = new Uint8Array(out.length + block.length); n.set(out, 0); n.set(block, out.length); out = n;
      }
      return { key: out.slice(0, keyLen), iv: out.slice(keyLen, keyLen + ivLen) };
    }
    var MODE = { CBC: { n: "CBC" }, ECB: { n: "ECB" }, CTR: { n: "CTR" } };
    var PAD = { Pkcs7: { n: "PKCS5Padding" }, NoPadding: { n: "NoPadding" } };
    function CipherParams(o) { Object.assign(this, o); }
    CipherParams.prototype.toString = function (fmt) {
      if (fmt && typeof fmt.stringify === "function") return fmt.stringify(this);
      if (this.salt) { var head = utf8("Salted__"), all = new Uint8Array(16 + this.ciphertext._b.length); all.set(head, 0); all.set(this.salt._b, 8); all.set(this.ciphertext._b, 16); return bytesToB64(all); }
      return bytesToB64(this.ciphertext._b);
    };
    function AesApi() {}
    AesApi.encrypt = function (msg, key, cfg) {
      cfg = cfg || {};
      var data = bytesOf(msg), keyB, ivB, salt = null;
      if (typeof key === "string") {
        salt = new Uint8Array(8); crypto.getRandomValues(salt);
        var d = evpKdf(utf8(key), salt, 32, 16); keyB = d.key; ivB = d.iv;
      } else { keyB = key._b; ivB = cfg.iv ? cfg.iv._b : new Uint8Array(0); }
      var mode = (cfg.mode || MODE.CBC).n, pad = (cfg.padding || PAD.Pkcs7).n;
      var ct = aesBytes({ mode: mode, padding: pad, encrypt: true, keyB64: bytesToB64(keyB), ivB64: bytesToB64(ivB), dataB64: bytesToB64(data) });
      return new CipherParams({ ciphertext: wa(ct), key: wa(keyB), iv: wa(ivB), salt: salt ? wa(salt) : undefined });
    };
    AesApi.decrypt = function (cipher, key, cfg) {
      cfg = cfg || {};
      var ctB, keyB, ivB, salt = null;
      if (typeof cipher === "string") {
        var raw = b64ToBytes(cipher.replace(/-/g, "+").replace(/_/g, "/"));
        if (raw.length > 16 && decodeText(raw.subarray(0, 8)) === "Salted__") { salt = raw.slice(8, 16); ctB = raw.slice(16); } else ctB = raw;
      } else { ctB = cipher.ciphertext._b; if (cipher.salt) salt = cipher.salt._b; }
      if (typeof key === "string") { var d = evpKdf(utf8(key), salt || new Uint8Array(0), 32, 16); keyB = d.key; ivB = d.iv; }
      else { keyB = key._b; ivB = cfg.iv ? cfg.iv._b : new Uint8Array(0); }
      var mode = (cfg.mode || MODE.CBC).n, pad = (cfg.padding || PAD.Pkcs7).n;
      return wa(aesBytes({ mode: mode, padding: pad, encrypt: false, keyB64: bytesToB64(keyB), ivB64: bytesToB64(ivB), dataB64: bytesToB64(ctB) }));
    };
    var api = {
      MD5: hash("MD5"), SHA1: hash("SHA-1"), SHA256: hash("SHA-256"), SHA384: hash("SHA-384"), SHA512: hash("SHA-512"),
      HmacMD5: hmac("HmacMD5"), HmacSHA1: hmac("HmacSHA1"), HmacSHA256: hmac("HmacSHA256"), HmacSHA512: hmac("HmacSHA512"),
      enc: Enc, mode: MODE, pad: PAD, AES: AesApi,
      lib: { WordArray: { create: function (bytes) { return wa(bytes ? toBytes(bytes) : new Uint8Array(0)); }, random: function (n) { var b = new Uint8Array(n); crypto.getRandomValues(b); return wa(b); } }, CipherParams: CipherParams }
    };
    api.default = api;
    return api;
  })();

  // ---------- plugin loading / execution ----------
  var moduleCache = {};
  var providerGlobals = {}; // pluginId -> object a provider may keep between calls (Vega's `providerGlobal`)

  function requireStub(name) {
    if (name === "axios") return axios;
    if (name === "cheerio") return cheerio;
    if (name === "crypto-js") return CryptoJS;
    if (typeof name === "string" && name.indexOf("crypto-js/") === 0) {
      var sub = name.slice(10);
      var key = { "enc-hex": "Hex", "enc-base64": "Base64", "enc-utf8": "Utf8" }[sub];
      return key ? CryptoJS.enc[key] : CryptoJS;
    }
    return {}; // like Vega's sandbox, any other module is an empty object
  }

  // Names Vega's sandbox hides from provider code (they are `undefined` there, so `typeof window === "undefined"`
  // branches behave the same here). Buffer / process are not provided by Vega either.
  var HIDDEN = ["globalThis", "self", "window", "document", "navigator", "location", "postMessage", "XMLHttpRequest", "WebSocket",
    "EventSource", "Worker", "SharedWorker", "BroadcastChannel", "indexedDB", "caches", "importScripts", "localStorage",
    "sessionStorage", "Buffer", "process"];

  function __awaiter(thisArg, args, PromiseConstructor, generator) {
    var C = PromiseConstructor || Promise;
    function adopt(value) { return value instanceof C ? value : new C(function (resolve) { resolve(value); }); }
    return new C(function (resolve, reject) {
      function fulfilled(value) { try { step(iterator.next(value)); } catch (e) { reject(e); } }
      function rejected(value) { try { step(iterator["throw"](value)); } catch (e) { reject(e); } }
      function step(result) { if (result.done) resolve(result.value); else adopt(result.value).then(fulfilled, rejected); }
      var iterator = generator.apply(thisArg, args || []);
      step(iterator.next());
    });
  }

  function loadModule(pluginId, moduleName) {
    var key = pluginId + "/" + moduleName;
    if (moduleCache[key]) return moduleCache[key];
    var src = Host.getModule(pluginId, moduleName);
    if (!src) throw new Error("Plugin file missing: " + key + ".js");
    var module = { exports: {} };
    var params = ["exports", "module", "require", "console", "Promise", "setTimeout", "clearTimeout", "setInterval", "clearInterval", "fetch", "__awaiter", "providerGlobal"].concat(HIDDEN);
    var fn = new Function(params.join(","), "\"use strict\";\n" + src + "\n//# sourceURL=plugin/" + key + ".js");
    var args = [module.exports, module, requireStub, providerConsole, Promise, global.setTimeout.bind(global), global.clearTimeout.bind(global),
      global.setInterval.bind(global), global.clearInterval.bind(global), global.fetch, __awaiter, providerGlobals[pluginId] || (providerGlobals[pluginId] = {})];
    for (var i = 0; i < HIDDEN.length; i++) args.push(undefined);
    fn.apply(undefined, args);
    moduleCache[key] = module.exports;
    return module.exports;
  }

  var providerConsole = Object.freeze({
    log: function () {}, info: function () {}, debug: function () {}, warn: function () {},
    error: function () { try { Host.log(Array.prototype.slice.call(arguments).map(function (a) { return a && a.message ? a.message : String(a); }).join(" ")); } catch (e) { /* ignore */ } }
  });

  function makeKv(pluginId) {
    function validate(key) {
      if (typeof key !== "string" || !key.trim() || key.length > 256) throw new Error("Invalid KV key: must be a non-empty string <= 256 characters");
      return key;
    }
    function call(op, key, valueJson) { return Host.kv(pluginId, op, key || "", valueJson === undefined ? "" : valueJson); }
    return Object.freeze({
      get: function (k) {
        return Promise.resolve().then(function () {
          var raw = call("get", validate(k));
          if (raw === "") return undefined; // missing key: undefined (Vega), not null
          try { return JSON.parse(raw); } catch (e) { return raw; }
        });
      },
      set: function (k, v) {
        return Promise.resolve().then(function () {
          validate(k);
          if (v === undefined) { call("delete", k); return; }
          var s = JSON.stringify(v);
          if (s === undefined) throw new Error("KV value must be JSON-serializable");
          if (s.length > 1000000) throw new Error("KV value exceeds limit of 1000000 bytes");
          call("set", k, s);
        });
      },
      delete: function (k) { return Promise.resolve().then(function () { return call("delete", validate(k)) === "true"; }); },
      keys: function () { return Promise.resolve().then(function () { return JSON.parse(call("keys") || "[]"); }); },
      clear: function () { return Promise.resolve().then(function () { call("clear"); }); }
    });
  }

  var urlsCache = null, urlsExpires = 0, urlsRequest = null;
  function getBaseUrlFor(value) {
    var fresh = urlsCache && Date.now() < urlsExpires;
    var p = fresh ? Promise.resolve(urlsCache) : (urlsRequest || (urlsRequest = fetch(Host.urlsEndpoint()).then(function (r) {
      if (!r.ok) throw new Error("URL configuration request failed: " + r.status);
      return r.json();
    }).then(function (d) { urlsCache = d; urlsExpires = Date.now() + 60 * 60 * 1000; return d; })
      .catch(function (e) { if (urlsCache) return urlsCache; throw e; })
      .then(function (d) { urlsRequest = null; return d; }, function (e) { urlsRequest = null; throw e; })));
    return p.then(function (d) { return (d[value] && d[value].url) || ""; });
  }

  /** Cloudflare / browser check: a hidden native WebView loads the page and hands back the HTML and the cookies. */
  function openWebView(url, options) {
    return hostAsync(function (id) { Host.webViewAsync(id, String(url), JSON.stringify(options || {})); }).then(function (r) {
      if (r.cookie === undefined) r.cookie = r.cookies;
      return r;
    });
  }
  function getCookies(url) {
    return Promise.resolve().then(function () { return JSON.parse(Host.getCookies(String(url)) || "{}"); });
  }

  function makeContext(pluginId) {
    return Object.freeze({
      axios: axios,
      cheerio: cheerio,
      Crypto: Crypto,
      Aes: Aes,
      commonHeaders: Object.assign({}, COMMON), // a fresh copy per call: providers write their Cookie / Referer into it
      getBaseUrl: getBaseUrlFor,
      openWebView: openWebView,
      getCookies: getCookies,
      kvStore: makeKv(pluginId),
      // not part of Vega's own context (its provider bundles inline them); kept for providers that still read it
      extractors: global.__lumoraExtractors || {}
    });
  }

  /** fnName 'catalog' / 'genres' may be a plain array export; anything else is a function export. */
  global.__runPlugin = function (callId, pluginId, moduleName, fnName, argsJson) {
    var done = false;
    function finish(ok, payload) { if (done) return; done = true; Host.finish(callId, ok, payload); }
    try {
      var mod = loadModule(pluginId, moduleName);
      var target = mod[fnName];
      var args = argsJson ? JSON.parse(argsJson) : {};
      args.providerContext = makeContext(pluginId);
      args.signal = (typeof AbortController !== "undefined") ? new AbortController().signal : undefined;
      var result;
      if (typeof target === "function") result = target(args);
      else if (target === undefined && fnName === "genres") result = [];
      else if (target === undefined) throw new Error("Plugin has no '" + fnName + "' in " + moduleName + ".js");
      else result = target;
      Promise.resolve(result).then(function (r) {
        if (Array.isArray(r)) {
          r.forEach(function (it) {
            if (it && typeof it.image === "string") {
              if (it.image.indexOf("//") === 0) it.image = "https:" + it.image;
              else if (it.image.charAt(0) === "/" && lastOrigin) it.image = lastOrigin + it.image;
            }
          });
        }
        var json;
        try { json = JSON.stringify(r === undefined ? null : r); } catch (e) { json = "null"; }
        finish(true, json === undefined ? "null" : json);
      }, function (e) {
        finish(false, String((e && e.message) || e));
      });
    } catch (e) {
      finish(false, String((e && e.message) || e));
    }
  };

  global.__dropPluginCache = function (pluginId) {
    Object.keys(moduleCache).forEach(function (k) { if (k.indexOf(pluginId + "/") === 0) delete moduleCache[k]; });
    delete providerGlobals[pluginId];
  };

  /** Repository (re)loaded: every cached module, every provider's own cached urls.json and the runtime's one are dropped, so the newest domains / code are used. */
  global.__dropAllPluginCaches = function () {
    Object.keys(moduleCache).forEach(function (k) { delete moduleCache[k]; });
    Object.keys(providerGlobals).forEach(function (k) { delete providerGlobals[k]; });
    urlsCache = null; urlsExpires = 0; urlsRequest = null;
  };
})(window);
