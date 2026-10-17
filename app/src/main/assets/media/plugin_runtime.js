/*
 * Lumora media-plugin runtime (runs inside a hidden WebView).
 * Provides what Vega provider bundles expect: fetch, axios, cheerio, URL helpers, kvStore, providerContext.
 * All network IO and storage go through the native `LumoraHost` bridge (see PluginJsEngine.kt).
 */
(function (global) {
  "use strict";
  var Host = global.LumoraHost;
  var pending = {};
  var seq = 0;

  // ---------- native bridge helpers ----------
  var webViewPending = {};
  global.__webViewResolve = function (id) {
    var cb = webViewPending[id]; delete webViewPending[id];
    if (cb) cb(Host.takeResult(id));
  };
  global.__hostResolve = function (id) {
    var cb = pending[id];
    delete pending[id];
    if (cb) cb(Host.takeResult(id));
  };

  var lastOrigin = "";
  function hostHttp(req) {
    try { var mm = /^(https?:\/\/[^\/]+)/i.exec(String(req.url)); if (mm && !/\.(png|jpe?g|webp|gif|svg)(\?|$)/i.test(req.url)) lastOrigin = mm[1]; } catch (e) { /* ignore */ }
    return new Promise(function (resolve, reject) {
      var id = ++seq;
      pending[id] = function (json) {
        var res;
        try { res = JSON.parse(json); } catch (e) { reject(new Error("Bad response from host")); return; }
        if (res.error) { var err = new Error(res.error); err.isNetworkError = true; reject(err); } else resolve(res);
      };
      Host.httpAsync(id, JSON.stringify(req));
    });
  }

  function b64ToBuf(b64) {
    var bin = atob(b64), len = bin.length, out = new Uint8Array(len);
    for (var i = 0; i < len; i++) out[i] = bin.charCodeAt(i);
    return out.buffer;
  }

  function normHeaders(h) {
    var out = {};
    if (!h) return out;
    if (typeof h.forEach === "function" && !Array.isArray(h) && typeof h.get === "function") {
      h.forEach(function (v, k) { out[k] = String(v); });
      return out;
    }
    if (Array.isArray(h)) { h.forEach(function (p) { out[p[0]] = String(p[1]); }); return out; }
    Object.keys(h).forEach(function (k) { if (h[k] !== undefined && h[k] !== null) out[k] = String(h[k]); });
    return out;
  }

  function encodeBody(data, headers) {
    if (data === undefined || data === null) return null;
    if (typeof data === "string") return data;
    if (typeof URLSearchParams !== "undefined" && data instanceof URLSearchParams) {
      if (!hasHeader(headers, "content-type")) headers["Content-Type"] = "application/x-www-form-urlencoded";
      return data.toString();
    }
    if (typeof data === "object") {
      if (!hasHeader(headers, "content-type")) headers["Content-Type"] = "application/json";
      return JSON.stringify(data);
    }
    return String(data);
  }

  function hasHeader(h, name) {
    name = name.toLowerCase();
    return Object.keys(h).some(function (k) { return k.toLowerCase() === name; });
  }

  // ---------- fetch ----------
  function makeHeadersView(map) {
    return {
      get: function (n) { var v = map[String(n).toLowerCase()]; return v === undefined ? null : v; },
      has: function (n) { return map[String(n).toLowerCase()] !== undefined; },
      forEach: function (cb) { Object.keys(map).forEach(function (k) { cb(map[k], k); }); },
      entries: function () { return Object.keys(map).map(function (k) { return [k, map[k]]; })[Symbol.iterator](); }
    };
  }

  global.fetch = function (input, init) {
    init = init || {};
    var url = typeof input === "string" ? input : (input && input.url) || String(input);
    var headers = normHeaders(init.headers || (input && input.headers));
    var method = String(init.method || (input && input.method) || "GET").toUpperCase();
    var body = encodeBody(init.body, headers);
    if (init.signal && init.signal.aborted) return Promise.reject(new Error("Aborted"));
    return hostHttp({
      method: method, url: url, headers: headers, body: body,
      follow: init.redirect !== "manual", binary: false
    }).then(function (res) {
      var text = res.body || "";
      return {
        ok: res.status >= 200 && res.status < 300,
        status: res.status,
        statusText: res.statusText || "",
        url: res.url || url,
        redirected: !!res.url && res.url !== url,
        headers: makeHeadersView(res.headers || {}),
        text: function () { return Promise.resolve(text); },
        json: function () { try { return Promise.resolve(JSON.parse(text)); } catch (e) { return Promise.reject(e); } },
        arrayBuffer: function () { return hostHttp({ method: method, url: url, headers: headers, body: body, follow: init.redirect !== "manual", binary: true }).then(function (r) { return b64ToBuf(r.bodyBase64 || ""); }); },
        clone: function () { return this; }
      };
    });
  };

  // ---------- axios ----------
  function axiosError(message, config, response, code) {
    var e = new Error(message);
    e.isAxiosError = true;
    e.config = config;
    e.response = response;
    e.code = code;
    e.toJSON = function () { return { message: e.message, code: e.code, status: response && response.status }; };
    return e;
  }

  function joinUrl(base, url) {
    if (!base || /^[a-z][a-z0-9+.-]*:\/\//i.test(url)) return url;
    return base.replace(/\/+$/, "") + "/" + String(url).replace(/^\/+/, "");
  }

  function appendParams(url, params) {
    if (!params) return url;
    var sp;
    if (typeof URLSearchParams !== "undefined" && params instanceof URLSearchParams) sp = params.toString();
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

  function mergeConfig(a, b) {
    var out = {};
    Object.keys(a || {}).forEach(function (k) { out[k] = a[k]; });
    Object.keys(b || {}).forEach(function (k) {
      if (k === "headers") out.headers = Object.assign({}, normHeaders(a && a.headers), normHeaders(b.headers));
      else out[k] = b[k];
    });
    return out;
  }

  function createAxios(defaults) {
    var requestInterceptors = [], responseInterceptors = [];
    function request(config) {
      config = mergeConfig(defaults, config);
      if (config.signal && config.signal.aborted) return Promise.reject(axiosError("canceled", config, undefined, "ERR_CANCELED"));
      var chain = Promise.resolve(config);
      requestInterceptors.slice().reverse().forEach(function (pair) {
        chain = chain.then(pair.fulfilled, pair.rejected);
      });
      return chain.then(function (prepared) {
      config = prepared || config;
      var headers = normHeaders(config.headers);
      var url = appendParams(joinUrl(config.baseURL, config.url || ""), config.params);
      var method = String(config.method || "get").toUpperCase();
      var body = encodeBody(config.data, headers);
      var wantBinary = config.responseType === "arraybuffer" || config.responseType === "blob";
      return hostHttp({
        method: method, url: url, headers: headers, body: body,
        follow: config.maxRedirects !== 0, binary: wantBinary,
        timeout: config.timeout || 0
      }).then(function (res) {
        var data;
        if (wantBinary) data = b64ToBuf(res.bodyBase64 || "");
        else {
          data = res.body || "";
          if (config.responseType !== "text" && typeof data === "string") {
            var t = data.replace(/^\uFEFF/, "").trim();
            if (t && (t[0] === "{" || t[0] === "[" || config.responseType === "json")) {
              try { data = JSON.parse(t); } catch (e) { /* keep text */ }
            }
          }
        }
        var response = {
          data: data, status: res.status, statusText: res.statusText || "",
          headers: res.headers || {}, config: config,
          request: { responseURL: res.url || url, res: { responseUrl: res.url || url } }
        };
        var ok = typeof config.validateStatus === "function"
          ? config.validateStatus(res.status)
          : (res.status >= 200 && res.status < 300);
        if (!ok) throw axiosError("Request failed with status code " + res.status, config, response, res.status >= 500 ? "ERR_BAD_RESPONSE" : "ERR_BAD_REQUEST");
        return response;
      }, function (err) {
        if (err && err.isAxiosError) throw err;
        var e = axiosError(err && err.message ? err.message : "Network Error", config, undefined, "ERR_NETWORK");
        throw e;
      });
      }).then(function (response) {
        var result = Promise.resolve(response);
        responseInterceptors.forEach(function (pair) { result = result.then(pair.fulfilled, pair.rejected); });
        return result;
      }, function (error) {
        var result = Promise.reject(error);
        responseInterceptors.forEach(function (pair) { result = result.then(pair.fulfilled, pair.rejected); });
        return result;
      });
    }
    var instance = function (a, b) {
      return typeof a === "string" ? request(Object.assign({}, b, { url: a })) : request(a);
    };
    instance.request = request;
    ["get", "delete", "head", "options"].forEach(function (m) {
      instance[m] = function (url, config) { return request(Object.assign({}, config, { method: m, url: url })); };
    });
    ["post", "put", "patch"].forEach(function (m) {
      instance[m] = function (url, data, config) { return request(Object.assign({}, config, { method: m, url: url, data: data })); };
    });
    instance.defaults = defaults;
    // Axios-compatible interceptor chains. Provider modules install global request/response
    // interceptors (including Cloudflare cookie cleanup); silently ignoring them breaks auth flows.
    var requestHandlers = [], responseHandlers = [];
    instance.interceptors = {
      request: {
        use: function (fulfilled, rejected) { requestHandlers.push({ fulfilled: fulfilled, rejected: rejected }); return requestHandlers.length - 1; },
        eject: function (id) { if (requestHandlers[id]) requestHandlers[id] = null; }
      },
      response: {
        use: function (fulfilled, rejected) { responseHandlers.push({ fulfilled: fulfilled, rejected: rejected }); return responseHandlers.length - 1; },
        eject: function (id) { if (responseHandlers[id]) responseHandlers[id] = null; }
      }
    };
    var rawRequest = request;
    request = function (config) {
      var chain = Promise.resolve(config || {});
      requestHandlers.forEach(function (h) { if (h) chain = chain.then(h.fulfilled, h.rejected); });
      return chain.then(function (cfg) { return rawRequest(cfg); }).then(function (res) {
        var out = Promise.resolve(res);
        responseHandlers.forEach(function (h) { if (h) out = out.then(h.fulfilled, h.rejected); });
        return out;
      }, function (err) {
        var out = Promise.reject(err);
        responseHandlers.forEach(function (h) { if (h) out = out.then(h.fulfilled, h.rejected); });
        return out;
      });
    };
    instance.request = request;
    instance.get = function (url, config) { return request(Object.assign({}, config, { method: "get", url: url })); };
    instance.delete = function (url, config) { return request(Object.assign({}, config, { method: "delete", url: url })); };
    instance.head = function (url, config) { return request(Object.assign({}, config, { method: "head", url: url })); };
    instance.options = function (url, config) { return request(Object.assign({}, config, { method: "options", url: url })); };
    ["post", "put", "patch"].forEach(function (m) {
      instance[m] = function (url, data, config) { return request(Object.assign({}, config, { method: m, url: url, data: data })); };
    });
    instance.create = function (cfg) { return createAxios(mergeConfig(defaults, cfg)); };
    instance.isAxiosError = function (e) { return !!(e && e.isAxiosError); };
    instance.isCancel = function (e) { return !!(e && e.code === "ERR_CANCELED"); };
    instance.all = function (p) { return Promise.all(p); };
    instance.spread = function (cb) { return function (arr) { return cb.apply(null, arr); }; };
    instance.CancelToken = { source: function () { return { token: {}, cancel: function () {} }; } };
    return instance;
  }
  var axios = createAxios({ headers: {} });

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

  // ---------- misc polyfills used by provider bundles ----------
  if (typeof global.Buffer === "undefined") {
    var Buf = function (bytes) { this.bytes = bytes; this.length = bytes.length; };
    Buf.from = function (v, enc) {
      var bytes;
      if (typeof v === "string") {
        if (enc === "base64") { var bin = atob(v.replace(/-/g, "+").replace(/_/g, "/")); bytes = new Uint8Array(bin.length); for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i); }
        else if (enc === "hex") { bytes = new Uint8Array(v.length / 2); for (var j = 0; j < bytes.length; j++) bytes[j] = parseInt(v.substr(j * 2, 2), 16); }
        else bytes = new TextEncoder().encode(v);
      } else bytes = new Uint8Array(v);
      return new Buf(bytes);
    };
    Buf.prototype.toString = function (enc) {
      if (enc === "base64") { var s = ""; for (var i = 0; i < this.bytes.length; i++) s += String.fromCharCode(this.bytes[i]); return btoa(s); }
      if (enc === "hex") return Array.prototype.map.call(this.bytes, function (b) { return ("0" + b.toString(16)).slice(-2); }).join("");
      return new TextDecoder("utf-8").decode(this.bytes);
    };
    global.Buffer = Buf;
  }

  var consoleStub = {
    log: function () {}, info: function () {}, debug: function () {}, warn: function () {},
    error: function () { try { Host.log(Array.prototype.slice.call(arguments).map(String).join(" ")); } catch (e) { /* ignore */ } }
  };
  global.console = consoleStub;

  // ---------- plugin loading / execution ----------
  var moduleCache = {};

  function sha1HexSync(input) {
    var bytes = Array.prototype.slice.call(new TextEncoder().encode(String(input))), bitLen = bytes.length * 8;
    bytes.push(128); while ((bytes.length % 64) !== 56) bytes.push(0);
    for (var i = 7; i >= 0; i--) bytes.push(i < 4 ? (bitLen >>> (i * 8)) & 255 : 0);
    var h0=0x67452301,h1=0xEFCDAB89,h2=0x98BADCFE,h3=0x10325476,h4=0xC3D2E1F0;
    function rol(x,n){return (x<<n)|(x>>>(32-n));}
    for(var off=0;off<bytes.length;off+=64){var w=new Array(80);for(i=0;i<16;i++){var j=off+i*4;w[i]=((bytes[j]<<24)|(bytes[j+1]<<16)|(bytes[j+2]<<8)|bytes[j+3])|0;}for(i=16;i<80;i++)w[i]=rol(w[i-3]^w[i-8]^w[i-14]^w[i-16],1);var a=h0,b=h1,c=h2,d=h3,e=h4;
      for(i=0;i<80;i++){var f,k;if(i<20){f=(b&c)|((~b)&d);k=0x5A827999;}else if(i<40){f=b^c^d;k=0x6ED9EBA1;}else if(i<60){f=(b&c)|(b&d)|(c&d);k=0x8F1BBCDC;}else{f=b^c^d;k=0xCA62C1D6;}var t=(rol(a,5)+f+e+k+w[i])|0;e=d;d=c;c=rol(b,30);b=a;a=t;}
      h0=(h0+a)|0;h1=(h1+b)|0;h2=(h2+c)|0;h3=(h3+d)|0;h4=(h4+e)|0;
    }
    return [h0,h1,h2,h3,h4].map(function(x){return (x>>>0).toString(16).padStart(8,"0");}).join("");
  }
  var cryptoJsStub = {
    enc: { Hex: { stringify: function (v) { return String(v); } }, Utf8: { stringify: function (v) { return v && v.toString ? v.toString() : String(v); } }, Base64: {} },
    SHA1: function (v) { var hex=sha1HexSync(v); return { toString: function () { return hex; }, words: [], sigBytes: 20 }; },
    AES: { decrypt: function () { throw new Error("CryptoJS AES.decrypt is unsupported; use providerContext.Aes.decrypt"); }, encrypt: function () { throw new Error("CryptoJS AES.encrypt is unsupported; use providerContext.Aes.encrypt"); } }
  };
  function requireStub(name) {
    if (name === "axios") return axios;
    if (name === "cheerio") return cheerio;
    if (name === "crypto-js" || name === "crypto-js/aes" || name === "crypto-js/sha1") return cryptoJsStub;
    if (name === "crypto-js/enc-utf8") return cryptoJsStub.enc.Utf8;
    throw new Error("Module '" + name + "' is not available in plugins");
  }

  function loadModule(pluginId, moduleName) {
    var key = pluginId + "/" + moduleName;
    if (moduleCache[key]) return moduleCache[key];
    var src = Host.getModule(pluginId, moduleName);
    if (!src) throw new Error("Plugin file missing: " + key + ".js");
    var module = { exports: {} };
    var fn = new Function("exports", "module", "require", src + "\n//# sourceURL=plugin/" + key + ".js");
    fn(module.exports, module, requireStub);
    moduleCache[key] = module.exports;
    return module.exports;
  }

  function makeKv(pluginId) {
    function call(op, key, value) { return JSON.parse(Host.kv(pluginId, op, key || "", value === undefined ? "" : JSON.stringify(value)) || "null"); }
    return {
      get: function (k) { return Promise.resolve(call("get", k)); },
      set: function (k, v) { call("set", k, v); return Promise.resolve(); },
      delete: function (k) { return Promise.resolve(!!call("delete", k)); },
      keys: function () { return Promise.resolve(call("keys") || []); },
      clear: function () { call("clear"); return Promise.resolve(); }
    };
  }

  var urlsCache = null;
  function getBaseUrlFor(value) {
    var p = urlsCache ? Promise.resolve(urlsCache) : fetch(Host.urlsEndpoint()).then(function (r) {
      if (!r.ok) throw new Error("URL configuration request failed: " + r.status);
      return r.json();
    }).then(function (d) { urlsCache = d; return d; });
    return p.then(function (d) { return (d[value] && d[value].url) || ""; });
  }

  var commonHeaders = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9",
    "sec-ch-ua": "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
    "sec-ch-ua-mobile": "?0",
    "sec-ch-ua-platform": "\"Windows\"",
    "Sec-Fetch-Dest": "document",
    "Sec-Fetch-Mode": "navigate",
    "Sec-Fetch-Site": "none",
    "Sec-Fetch-User": "?1",
    "Upgrade-Insecure-Requests": "1",
    "Cache-Control": "max-age=0"
  };
  var Aes = {
    sha1: function (value) {
      var bytes = new TextEncoder().encode(String(value));
      return crypto.subtle.digest("SHA-1", bytes).then(function (hash) {
        return Array.prototype.map.call(new Uint8Array(hash), function (b) { return ("0" + b.toString(16)).slice(-2); }).join("");
      });
    }
  };
  function makeContext(pluginId) {
    var commonHeaders = {
      "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
      "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
      "Accept-Language": "en-US,en;q=0.9",
      "sec-ch-ua": '"Google Chrome";v="131", "Chromium";v="131", "Not_A Brand";v="24"',
      "sec-ch-ua-mobile": "?0", "sec-ch-ua-platform": '"Windows"',
      "Sec-Fetch-Dest": "document", "Sec-Fetch-Mode": "navigate", "Sec-Fetch-Site": "none",
      "Upgrade-Insecure-Requests": "1", "Cache-Control": "max-age=0"
    };
    function openWebView(url, options) {
      options = options || {};
      return new Promise(function (resolve, reject) {
        var id = ++seq;
        webViewPending[id] = function (json) {
          var r; try { r = JSON.parse(json); } catch (_) { reject(new Error("Invalid WebView response")); return; }
          if (r.error) { reject(new Error(r.error)); return; }
          resolve({ data: r.html || "", html: r.html || "", cookies: r.cookies || "", cookieMap: {}, url: url, userAgent: r.userAgent || commonHeaders["User-Agent"] });
        };
        try { Host.webViewAsync(id, JSON.stringify({ url: url, options: options })); }
        catch (e) { delete webViewPending[id]; reject(e); }
      });
    }
    // WebCrypto-backed subset of CryptoJS/Expo AES and SHA-1 used by Vega providers.
    var Aes = {
      sha1: function (input) { return crypto.subtle.digest("SHA-1", new TextEncoder().encode(String(input))).then(function (b) { return Array.prototype.map.call(new Uint8Array(b), function (x) { return ("0" + x.toString(16)).slice(-2); }).join(""); }); },
      decrypt: function (cipherText, key, iv) {
        try {
          var raw = atob(String(cipherText).replace(/-/g, "+").replace(/_/g, "/"));
          var bytes = Uint8Array.from(raw, function (c) { return c.charCodeAt(0); });
          var keyBytes = new TextEncoder().encode(String(key));
          if (![16,24,32].includes(keyBytes.length)) return Promise.reject(new Error("AES key must be 16, 24 or 32 UTF-8 bytes"));
          var ivBytes = new Uint8Array(16); ivBytes.set(new TextEncoder().encode(String(iv || "0000000000000000")).slice(0, 16));
          return crypto.subtle.importKey("raw", keyBytes, { name: "AES-CBC" }, false, ["decrypt"]).then(function (k) { return crypto.subtle.decrypt({ name: "AES-CBC", iv: ivBytes }, k, bytes); }).then(function (b) { return new TextDecoder().decode(b); });
        } catch (e) { return Promise.reject(e); }
      },
      encrypt: function (plainText, key, iv) {
        try {
          var keyBytes = new TextEncoder().encode(String(key));
          if (![16,24,32].includes(keyBytes.length)) return Promise.reject(new Error("AES key must be 16, 24 or 32 UTF-8 bytes"));
          var ivBytes = new Uint8Array(16); ivBytes.set(new TextEncoder().encode(String(iv || "0000000000000000")).slice(0, 16));
          return crypto.subtle.importKey("raw", keyBytes, { name: "AES-CBC" }, false, ["encrypt"]).then(function (k) { return crypto.subtle.encrypt({ name: "AES-CBC", iv: ivBytes }, k, new TextEncoder().encode(String(plainText))); }).then(function (b) { var a = new Uint8Array(b), out = ""; for (var i=0;i<a.length;i++) out += String.fromCharCode(a[i]); return btoa(out); });
        } catch (e) { return Promise.reject(e); }
      }
    };
    // Vega extractor contracts. Keep return shapes compatible with providerContext.extractors.
    var extractors = {
      superVideoExtractor: function (html) {
        var m = /eval\(function\((.*?)\)\{.*?return p\}.*?\('(.*?)'\.split/s.exec(String(html));
        if (!m) return Promise.resolve((String(html).match(/file\s*:\s*["']([^"']+\.m3u8[^"']*)["']/i) || [])[1] || "");
        var enc = m[2], p = enc.split("',36,")[0].trim(), rest = enc.split("',36,")[1] || "", k = rest.slice(2).split("|"), c = k.length;
        while (c--) if (k[c]) p = p.replace(new RegExp("\\b" + c.toString(36) + "\\b", "g"), k[c]);
        return Promise.resolve((p.match(/file\s*:\s*["']([^"']+\.m3u8[^"']*)["']/i) || [])[1] || "");
      },
      gofileExtractor: (function () {
        var cachedToken = null, cachedWT = null, cachedWTAt = 0;
        function firstFile(node) { if (!node) return null; if (node.type === "file" && node.link) return node; var children=node.children||{}; var keys=Object.keys(children); for(var i=0;i<keys.length;i++){var found=firstFile(children[keys[i]]);if(found)return found;} return null; }
        function generateWT(ax) {
          if (cachedWT && Date.now()-cachedWTAt < 10800000) return Promise.resolve(cachedWT);
          return ax.get("https://gofile.io/js/wt.obf.js", {headers:{"User-Agent":commonHeaders["User-Agent"],Referer:"https://gofile.io/"}}).then(function(r){
            var runner = new Function("navigator","window","document","location",String(r.data)+"\nreturn generateWT;");
            var nav={userAgent:commonHeaders["User-Agent"],language:"en-US"}, loc={href:"https://gofile.io/",protocol:"https:",host:"gofile.io"}, win={navigator:nav,location:loc};
            var fn=runner(nav,win,{},loc); if(typeof fn!=="function") throw new Error("Gofile website token function missing"); cachedWT=fn;cachedWTAt=Date.now();return fn;
          }).catch(function(){ return function(token){return token;}; });
        }
        return function (id, ax, ctx) {
          var tokenPromise = cachedToken ? Promise.resolve(cachedToken) : Promise.resolve(ctx && ctx.kvStore ? ctx.kvStore.get("gofile_account_token").catch(function(){return null;}) : null).then(function(saved){
            if(saved) { cachedToken=saved; return saved; }
            return ax.post("https://api.gofile.io/accounts", {}, {headers:{"User-Agent":commonHeaders["User-Agent"],Origin:"https://gofile.io",Referer:"https://gofile.io/"}}).then(function(r){var t=r.data&&r.data.data&&r.data.data.token;if(!t)throw new Error("Gofile did not return an account token");cachedToken=t; if(ctx&&ctx.kvStore)ctx.kvStore.set("gofile_account_token",t).catch(function(){});return t;});
          });
          return tokenPromise.then(function(token){return generateWT(ax).then(function(makeWT){return {token:token,wt:makeWT(token)};});}).then(function(auth){
            return ax.get("https://api.gofile.io/contents/"+encodeURIComponent(id), {params:{contentFilter:"",page:1,pageSize:1000,sortField:"name",sortDirection:1},headers:{Accept:"*/*","Accept-Language":"en-US,en;q=0.9",Authorization:"Bearer "+auth.token,Origin:"https://gofile.io",Referer:"https://gofile.io/","User-Agent":commonHeaders["User-Agent"],"X-BL":"en-US","X-Website-Token":auth.wt}}).then(function(r){
              var data=r.data||{};if(data.status!=="ok"){if(data.status==="error-auth"||r.status===401){cachedToken=null;if(ctx&&ctx.kvStore)ctx.kvStore.delete("gofile_account_token").catch(function(){});}throw new Error("Gofile API returned "+(data.status||"invalid data"));}
              var file=firstFile(data.data);if(!file||!file.link)throw new Error("No downloadable file found in Gofile response");return {link:file.link,token:auth.token};
            });
          });
        };
      })(),
      gdflixExtractor: function (link, signal, ax, ch, headers) { return ax.get(link, { headers: headers, signal: signal }).then(function (r) { var $ = ch.load(r.data); var links=[]; $("a[href]").each(function () { var href=$(this).attr("href")||""; if (/https?:/.test(href) && /download|drive|resume|googleusercontent/i.test(href)) links.push({server: $(this).text().trim() || "GDFlix", link: href, type:"mkv"}); }); return links; }); },
      hubcloudExtractor: function (link, signal, ax, ch, headers, ctx) {
        return ax.get(link, { headers: headers, signal: signal }).then(function (r) {
          var html=String(r.data||""), $=ch.load(html), out=[];
          var m=/(?:var|let|const)\s+\w+\s*=\s*atob\(atob\(['"]([^'"]+)['"]\)\)/.exec(html);
          var u=m ? "" : ((html.match(/var\s+url\s*=\s*['"]([^'"]+)['"]/i)||[])[1]||"");
          if(m) { try { u=atob(atob(m[1])); } catch(_){} } else if(u.indexOf("r=")>=0) { try { u=atob(u.split("r=")[1]); } catch(_){} }
          if(u && u.indexOf("/")===0) u=new URL(u, link).href;
          if(u && /^https?:/i.test(u)) out.push({server:"HubCloud",link:u,type:/\.m3u8/i.test(u)?"m3u8":"mkv"});
          $("a[href]").each(function(){var href=$(this).attr("href")||""; if(/^https?:/i.test(href)&&(/gofile\.io|pixeldrain|drive\.google|\.mkv|\.mp4/i.test(href))) out.push({server:$(this).text().trim()||"HubCloud",link:href,type:/\.mp4/i.test(href)?"mp4":"mkv"});});
          return out.filter(function(x,i,a){return a.findIndex(function(y){return y.link===x.link;})===i;});
        });
      }
    };
    return { axios: axios, cheerio: cheerio, getBaseUrl: getBaseUrlFor, commonHeaders: commonHeaders,
      kvStore: makeKv(pluginId), openWebView: openWebView, Aes: Aes, extractors: extractors,
      Crypto: Aes, fetch: global.fetch };
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
        finish(true, JSON.stringify(r === undefined ? null : r));
      }, function (e) {
        finish(false, String((e && e.message) || e));
      });
    } catch (e) {
      finish(false, String((e && e.message) || e));
    }
  };

  global.__dropPluginCache = function (pluginId) {
    Object.keys(moduleCache).forEach(function (k) { if (k.indexOf(pluginId + "/") === 0) delete moduleCache[k]; });
  };
})(window);
