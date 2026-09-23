/* 雪线之上 · WebView 原生桥接（零侵入网页）
 * 1. fetch 补丁：pone 上传 / 360 转存 / bugpk 解析全部本地直连，绕过 CORS
 * 2. 解析按钮拦截：抖音/小红书本地解析+全部转存，回填“标题\n链接”后由网页直接收藏（无选择弹窗）
 * 3. 导出拦截：blob 下载转 SAF 保存
 */
(function () {
  'use strict';
  if (window.__snowBridgeInstalled) return;
  window.__snowBridgeInstalled = true;

  var B = window.SnowBridge;

  // ---------- 工具 ----------
  function b64(blob) {
    return new Promise(function (res, rej) {
      var fr = new FileReader();
      fr.onload = function () { res(String(fr.result).split(',')[1]); };
      fr.onerror = rej;
      fr.readAsDataURL(blob);
    });
  }
  // 去掉代理前缀，取出真实目标地址
  function realUrl(u) {
    ['https://api.yujn.cn', 'https://api.bugpk.com', 'https://pone.rs'].forEach(function (t) {
      var i = u.indexOf(t);
      if (i > 0) u = u.substring(i);
    });
    return u;
  }
  // 简易忙碌提示（独立 DOM，不修改网页结构）
  function overlay(show, msg) {
    var el = document.getElementById('__snow_ov');
    if (!show) { if (el) el.remove(); return; }
    if (el) { el.querySelector('span').textContent = msg; return; }
    el = document.createElement('div');
    el.id = '__snow_ov';
    el.setAttribute('style', 'position:fixed;inset:0;z-index:99999;display:flex;align-items:center;justify-content:center;background:rgba(10,13,20,.55);');
    var p = document.createElement('div');
    p.setAttribute('style', 'display:inline-flex;align-items:center;gap:10px;padding:14px 22px;border-radius:999px;background:rgba(44,48,59,.92);border:1px solid rgba(255,255,255,.18);color:#F6F1E8;font-size:14px;');
    var sp = document.createElement('span'); sp.className = 'spin';
    sp.setAttribute('style', 'width:18px;height:18px;border:2px solid rgba(246,241,232,.3);border-top-color:#C78444;border-radius:50%;display:inline-block;animation:__spin .8s linear infinite;');
    var tx = document.createElement('span'); tx.textContent = msg;
    p.appendChild(sp); p.appendChild(tx); el.appendChild(p);
    document.body.appendChild(el);
    if (!document.getElementById('__snow_kf')) {
      var st = document.createElement('style');
      st.id = '__snow_kf';
      st.textContent = '@keyframes __spin{to{transform:rotate(360deg)}}';
      document.head.appendChild(st);
    }
  }

  // ---------- 1. fetch 补丁 ----------
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
        var json = B.poneUpload(JSON.stringify(parts));
        return new Response(json, { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (url.indexOf('api.bugpk.com') >= 0 || url.indexOf('/api/360_img.php') >= 0) {
        var body = B.httpGet(realUrl(url));
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
        await new Promise(function (r) { requestAnimationFrame(r); });
        var r = JSON.parse(B.nativeParse(raw));
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
  document.addEventListener('click', function (ev) {
    var t = ev.target;
    var a = t && t.closest ? t.closest('a[download]') : null;
    if (!a) return;
    var href = a.href || '';
    if (href.indexOf('blob:') !== 0) return;
    ev.preventDefault();
    ev.stopPropagation();
    var blob = blobMap.get(href);
    if (!blob) { alert('导出失败：找不到内容'); return; }
    var fr = new FileReader();
    fr.onload = function () {
      var ok = B.saveExport(a.download || 'snowline-images.txt', String(fr.result));
      if (!ok) console.log('export canceled');
    };
    fr.readAsText(blob);
  }, true);

  // ---------- 等待 React 渲染后挂载 ----------
  var tries = 0;
  var timer = setInterval(function () {
    if (setupParser() || ++tries > 60) clearInterval(timer);
  }, 300);
})();
