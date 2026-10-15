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
    function request(config) {
      config = mergeConfig(defaults, config);
      if (config.signal && config.signal.aborted) return Promise.reject(axiosError("canceled", config, undefined, "ERR_CANCELED"));
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
    instance.interceptors = { request: { use: function () {}, eject: function () {} }, response: { use: function () {}, eject: function () {} } };
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

  function requireStub(name) {
    if (name === "axios") return axios;
    if (name === "cheerio") return cheerio;
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

  function makeContext(pluginId) {
    return {
      axios: axios,
      cheerio: cheerio,
      commonHeaders: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "Accept-Language": "en-US,en;q=0.9"
      },
      getBaseUrl: getBaseUrlFor,
      kvStore: makeKv(pluginId),
      openWebView: function () { return Promise.reject(new Error("This site needs a browser check (WebView) which is not supported yet")); },
      Aes: null
    };
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
