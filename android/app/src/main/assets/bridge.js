/* 雪线之上 · WebView 原生桥接（零侵入网页）
 * 1. fetch 补丁：pone 上传 / 360 转存 / bugpk 解析全部本地直连，绕过 CORS（异步，不卡页面）
 * 2. 解析按钮拦截：抖音/小红书本地解析+全部转存，回填“标题\n链接”后由网页直接收藏（无选择弹窗）
 * 3. 导出拦截：blob 下载转 SAF 保存
 * 4. 锁定横向滚动
 */
(function () {
  'use strict';
  if (window.__snowBridgeInstalled) return;
  window.__snowBridgeInstalled = true;

  var B = window.SnowBridge, seq = 0, cbs = {};

  // 原生回调入口
  B._emit = function (id, type, payload) {
    var c = cbs[id];
    if (!c) return;
    if (type === 'progress') { c.progress && c.progress(payload); return; }
    delete cbs[id];
    if (type === 'error') c.rej(payload); else c.res(payload);
  };
  function call(method, args, onProgress) {
    return new Promise(function (res, rej) {
      var id = 'c' + (++seq);
      cbs[id] = { res: res, rej: rej, progress: onProgress };
      B[method].apply(B, args.concat([id]));
    });
  }

  // ---------- 工具 ----------
  function b64(blob) {
    return new Promise(function (res, rej) {
      var fr = new FileReader();
      fr.onload = function () { res(String(fr.result).split(',')[1]); };
      fr.onerror = rej;
      fr.readAsDataURL(blob);
    });
  }
  function realUrl(u) {
    ['https://api.yujn.cn', 'https://api.bugpk.com', 'https://pone.rs'].forEach(function (t) {
      var i = u.indexOf(t);
      if (i > 0) u = u.substring(i);
    });
    return u;
  }
  function overlay(show, msg) {
    var el = document.getElementById('__snow_ov');
    if (!show) { if (el) el.remove(); return; }
    if (el) { el.querySelector('span.snow-tx').textContent = msg; return; }
    el = document.createElement('div');
    el.id = '__snow_ov';
    el.setAttribute('style', 'position:fixed;inset:0;z-index:99999;display:flex;align-items:center;justify-content:center;background:rgba(10,13,20,.55);');
    var p = document.createElement('div');
    p.setAttribute('style', 'display:inline-flex;align-items:center;gap:10px;padding:14px 22px;border-radius:999px;background:rgba(44,48,59,.92);border:1px solid rgba(255,255,255,.18);color:#F6F1E8;font-size:14px;');
    var sp = document.createElement('span');
    sp.setAttribute('style', 'width:18px;height:18px;border:2px solid rgba(246,241,232,.3);border-top-color:#C78444;border-radius:50%;display:inline-block;animation:__spin .8s linear infinite;');
    var tx = document.createElement('span');
    tx.className = 'snow-tx';
    tx.textContent = msg;
    p.appendChild(sp); p.appendChild(tx); el.appendChild(p);
    document.body.appendChild(el);
    if (!document.getElementById('__snow_kf')) {
      var kf = document.createElement('style');
      kf.id = '__snow_kf';
      kf.textContent = '@keyframes __spin{to{transform:rotate(360deg)}}';
      document.head.appendChild(kf);
    }
  }

  // ---------- 1. fetch 补丁（异步本地直连） ----------
  var ofetch = window.fetch.bind(window);
  window.fetch = async function (input, init) {
    var url = typeof input === 'string' ? input : ((input && input.url) || '');
    try {
      if (url.indexOf('pone.rs/upload') >= 0) {
        var fd = init && init.body, parts = [];
        if (fd && typeof fd.entries === 'function') {
          var entries = Array.from(fd.entries());
          for (var k = 0; k < entries.length; k++) {
            var en = entries[k], v = en[1];
            if (v instanceof Blob) {
              parts.push({
                filename: v.name || en[0] || ('file_' + Date.now()),
                mime: v.type || '', b64: await b64(v)
              });
            }
          }
        }
        var json = await call('poneUpload', [JSON.stringify(parts)]);
        return new Response(json, { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (url.indexOf('api.bugpk.com') >= 0 || url.indexOf('/api/360_img.php') >= 0) {
        var body = await call('httpGet', [realUrl(url)]);
        return new Response(body, { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
    } catch (err) { console.log('snow bridge fetch fallback:', err); }
    return ofetch(input, init);
  };

  // ---------- 2. 解析按钮拦截 ----------
  function setupParser() {
    var secs = document.querySelectorAll('section.parser');
    if (!secs.length) return false;
    var sec = secs[0];
    var ta = sec.querySelector('textarea');
    var btn = sec.querySelector('.parser-row button.btn.primary');
    if (!ta || !btn || btn.__snowHooked) return !!ta && !!btn;
    btn.__snowHooked = true;
    window.__snowGo = false;

    function fillTa(text) {
      var set = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set;
      set.call(ta, text);
      ta.dispatchEvent(new Event('input', { bubbles: true }));
    }
    function passThrough() {
      window.__snowGo = true;
      btn.click();
      setTimeout(function () { window.__snowGo = false; }, 80);
    }

    btn.addEventListener('click', async function (ev) {
      if (window.__snowGo) return; // 放行：网页自身逻辑
      ev.preventDefault();
      ev.stopImmediatePropagation();
      var raw = ta.value || '';
      overlay(true, '本地解析转存中，请稍候…');
      try {
        var r = JSON.parse(await call('nativeParse', [raw], function (m) { overlay(true, m); }));
        overlay(false);
        if (r.passthrough) { passThrough(); }
        else if (r.ok) { fillTa(r.text); passThrough(); }
        else alert(r.error || '处理失败');
      } catch (e) {
        overlay(false);
        alert((e && e.message) || '处理失败');
      }
    }, true);
    return true;
  }

  // ---------- 3. 导出 blob → SAF ----------
  var blobMap = new Map();
  var ocou = URL.createObjectURL.bind(URL);
  URL.createObjectURL = function (blob) {
    var u = ocou(blob);
    blobMap.set(u, blob);
    return u;
  };
  // 处理导出锚点：读取 blob → 原生 SAF 保存；返回 true 表示已接管
  function handleAnchor(a) {
    var href = a.href || '';
    if (href.indexOf('blob:') !== 0) return false;
    var blob = blobMap.get(href);
    if (!blob) { alert('导出失败：找不到内容'); return true; }
    var fr = new FileReader();
    fr.onload = function () {
      call('saveExport', [a.download || 'snowline-images.txt', String(fr.result)])
        .catch(function () { /* 用户取消 */ });
    };
    fr.readAsText(blob);
    return true;
  }

  // 1) 网页导出用的是“未挂载到 DOM 的 a.click()”，事件不会冒泡到 document：
  //    直接劫持 HTMLAnchorElement.prototype.click
  var aClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () {
    if (this.download && handleAnchor(this)) return;
    return aClick.apply(this, arguments);
  };
  // 2) 兜底：页面内真实点击的下载链接
  document.addEventListener('click', function (ev) {
    var t = ev.target;
    var a = t && t.closest ? t.closest('a[download]') : null;
    if (a && handleAnchor(a)) { ev.preventDefault(); ev.stopPropagation(); }
  }, true);

  // ---------- 4. 锁定横向滚动 ----------
  var lock = document.createElement('style');
  lock.textContent = 'html,body{overflow-x:hidden !important;overscroll-behavior-x:none;}';
  (document.head || document.documentElement).appendChild(lock);

  // ---------- 等待 React 渲染后挂载 ----------
  var tries = 0;
  var timer = setInterval(function () {
    if (setupParser() || ++tries > 60) clearInterval(timer);
  }, 300);
})();
