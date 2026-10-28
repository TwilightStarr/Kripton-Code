package com.kriptoncod.app.data.web

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Sayfaya enjekte edilen betikler. Betik eşzamansızdır: `window.__kr` durumunu kurar; Kotlin tarafı
 * [POLL] ile bitişi yoklar (evaluateJavascript söz bekleyemez).
 */
object WebScripts {

    private val json = Json { encodeDefaults = true }

    /** Bitiş yoklaması: bitmediyse boş metin, bittiyse sonuç JSON'u; sayfa yenilendiyse "LOST". */
    const val POLL = "(function(){ if(!window.__kr) return 'LOST'; return window.__kr.done ? JSON.stringify(window.__kr.result) : ''; })()"

    /** localStorage'tan saklanacak anahtarları JSON nesnesi olarak döndürür. */
    fun readLocalStorage(keys: List<String>): String = "(function(){var o={};" + json.encodeToString(keys) +
        ".forEach(function(k){try{var v=localStorage.getItem(k);if(v!==null)o[k]=v;}catch(e){}});return JSON.stringify(o);})()"

    fun writeLocalStorage(values: Map<String, String>): String = "(function(){var d=" + json.encodeToString(values) +
        ";for(var k in d){try{localStorage.setItem(k,d[k]);}catch(e){}}return 'ok';})()"

    /**
     * E-postadaki doğrulama kodunu sayfadaki kod alanına yazar (tek alan ya da basamak başına kutu).
     * Yalnız kullanıcı düğmeye bastığında çalışır; e-posta/şifre alanlarına dokunmaz. [code] yalnız rakamdır.
     */
    fun fillCode(code: String): String = "(function(code){" +
        "var ins=Array.prototype.slice.call(document.querySelectorAll('input')).filter(function(e){" +
        "var r=e.getBoundingClientRect();" +
        "return r.width>0&&r.height>0&&!e.disabled&&!e.readOnly&&['hidden','checkbox','radio','submit','button','password','email'].indexOf(e.type)<0;});" +
        "function setVal(e,v){var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');" +
        "d.set.call(e,v);e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));}" +
        "var boxes=ins.filter(function(e){return e.maxLength===1;});" +
        "if(boxes.length>=code.length){for(var i=0;i<code.length;i++){boxes[i].focus();setVal(boxes[i],code.charAt(i));}return 'ok';}" +
        "var single=ins.filter(function(e){return !(e.maxLength>0&&e.maxLength<code.length);});" +
        "var t=(document.activeElement&&ins.indexOf(document.activeElement)>=0)?document.activeElement:(single[0]||null);" +
        "if(t){t.focus();setVal(t,code);return 'ok';}" +
        "return 'none';})(" + json.encodeToString(code) + ")"

    /** AI Studio sayfa metninde API anahtarı arar. */
    const val FIND_API_KEY =
        "(function(){var t=(document.body?document.body.innerText:'')+' '+Array.from(document.querySelectorAll('input,textarea,[data-key]'))" +
            ".map(function(e){return e.value||e.getAttribute('data-key')||'';}).join(' ');" +
            "var m=t.match(/AIza[0-9A-Za-z_\\-]{35}/);return m?m[0]:'';})()"

    /**
     * Ekrana eklenmemiş WebView'da sayfa "gizli" sayılır: requestAnimationFrame durur, bazı siteler girdiyi çizmez.
     * Belge başlangıcında enjekte edilir; sayfayı görünür gösterir ve rAF'ye zamanlayıcı yedeği ekler.
     */
    const val VISIBILITY_SHIM =
        "(function(){try{" +
            "Object.defineProperty(document,'hidden',{configurable:true,get:function(){return false;}});" +
            "Object.defineProperty(document,'visibilityState',{configurable:true,get:function(){return 'visible';}});" +
            "document.hasFocus=function(){return true;};" +
            "var raf=window.requestAnimationFrame.bind(window),caf=window.cancelAnimationFrame.bind(window),dead={};" +
            "window.requestAnimationFrame=function(cb){var fired=false,id=raf(function(t){if(!fired&&!dead[id]){fired=true;delete dead[id];cb(t);}});" +
            "setTimeout(function(){if(!fired&&!dead[id]){fired=true;cb(performance.now());}},100);return id;};" +
            "window.cancelAnimationFrame=function(i){dead[i]=true;caf(i);};" +
            "}catch(e){}})();"

    fun runChat(sel: SiteSelectors, prompt: String): String {
        val cfg = json.encodeToString(sel)
        val p = json.encodeToString(prompt)
        return "(function(cfg,prompt,peek){$CHAT_BODY})($cfg,$p,false);"
    }

    /**
     * Mesaj GÖNDERMEDEN sohbetteki son asistan yanıtını okur: üretim sürüyorsa bitmesini bekler. Sohbette hiç asistan
     * yanıtı yoksa boş metinli "ok" döner. Sonucu [POLL] ile okunur (runChat ile aynı biçim).
     */
    fun peekChat(sel: SiteSelectors): String {
        val cfg = json.encodeToString(sel)
        return "(function(cfg,prompt,peek){$CHAT_BODY})($cfg,\"\",true);"
    }

    /** Dosya parçalarını biriktirmeye başlar (büyük dizgiler tek seferde köprüden geçmesin diye parça parça taşınır). */
    const val ATTACH_BEGIN = "window.__krup={parts:[]};'ok'"

    /** [b64] bir dosya diliminin base64 metnidir (yalnız A-Za-z0-9+/=). */
    fun attachChunk(b64: String): String = "(function(){if(!window.__krup)return 'none';window.__krup.parts.push(" +
        json.encodeToString(b64) + ");return 'ok';})()"

    /** Biriken parçalardan dosyayı kurup sayfadaki dosya girdisine verir; sonuç `window.__kra` ile [ATTACH_POLL]'dan okunur. */
    fun attachFinish(sel: SiteSelectors, name: String, mime: String): String {
        val cfg = json.encodeToString(sel)
        val n = json.encodeToString(name)
        val m = json.encodeToString(mime)
        return "(function(cfg,name,mime){$ATTACH_BODY})($cfg,$n,$m);"
    }

    /** Ekleme yoklaması: bitmediyse boş, bittiyse "OK" ya da "FAIL". */
    const val ATTACH_POLL = "(function(){var d=window.__kra;return d&&d.done?(d.ok?'OK':'FAIL'):'';})()"

    private val ATTACH_BODY = """
    window.__kra = {done:false, ok:false};
    var sleep = function(ms){ return new Promise(function(r){ setTimeout(r, ms); }); };
    var pick = function(sels){ for (var i=0;i<sels.length;i++){ try{ var e=document.querySelector(sels[i]); if(e) return e; }catch(x){} } return null; };
    (async function(){
      try {
        var parts = (window.__krup && window.__krup.parts) || [];
        var chunks = [];
        for (var i=0;i<parts.length;i++){
          var bin = atob(parts[i]); var arr = new Uint8Array(bin.length);
          for (var j=0;j<bin.length;j++) arr[j] = bin.charCodeAt(j);
          chunks.push(arr);
        }
        var file = new File(chunks, name, {type: mime});
        var inp = null;
        for (var k=0;k<20 && !inp;k++){ inp = pick(cfg.fileInput); if(!inp) await sleep(500); }
        if (!inp) { window.__krup = null; window.__kra.done = true; return; }
        var dt = new DataTransfer(); dt.items.add(file);
        inp.files = dt.files;
        inp.dispatchEvent(new Event('input', {bubbles:true}));
        inp.dispatchEvent(new Event('change', {bubbles:true}));
        window.__krup = null;
        var short = name.length > 18 ? name.substring(0, 18) : name;
        var seen = false;
        for (var t=0;t<90 && !seen;t++){
          await sleep(1000);
          var body = document.body ? (document.body.innerText || '') : '';
          if (body.indexOf(short) >= 0) seen = true;
        }
        if (!seen) { window.__kra.done = true; return; }
        // Yükleme bitene kadar (gönder düğmesi etkin) bekle.
        var ready = 0;
        for (var u=0;u<120 && ready < 3;u++){
          await sleep(1000);
          var b = pick(cfg.send);
          if (b && !b.disabled && b.getAttribute('aria-disabled') !== 'true') ready++; else ready = 0;
        }
        window.__kra.ok = ready >= 3;
        window.__kra.done = true;
      } catch (e) {
        window.__krup = null;
        window.__kra.done = true;
      }
    })();
    """.trimIndent()

    private val CHAT_BODY = """
    window.__kr = {done:false, result:null};
    var sleep = function(ms){ return new Promise(function(r){ setTimeout(r, ms); }); };
    var pick = function(sels){ for (var i=0;i<sels.length;i++){ try{ var e=document.querySelector(sels[i]); if(e) return e; }catch(x){} } return null; };
    var pickAll = function(sels){ for (var i=0;i<sels.length;i++){ try{ var l=document.querySelectorAll(sels[i]); if(l.length) return Array.prototype.slice.call(l); }catch(x){} } return []; };
    var finish = function(o){ window.__kr.result=o; window.__kr.done=true; };
    var visible = function(e){ if(!e) return false; var r=e.getBoundingClientRect(); return r.width>0 && r.height>0; };
    var lower = function(s){ return (s||'').toLowerCase(); };
    var limitRes = cfg.limitPatterns.map(function(p){ return new RegExp(p, 'i'); });
    var contRe = new RegExp(cfg.continueText, 'i');

    function toMd(node){
      var host = document.createElement('div');
      host.style.cssText = 'position:fixed;left:-99999px;top:0;width:900px;white-space:pre-wrap;';
      var c = node.cloneNode(true);
      Array.prototype.slice.call(c.querySelectorAll('pre')).forEach(function(pre){
        var code = pre.querySelector('code') || pre;
        var m = ((code.className||'') + ' ' + (pre.className||'')).match(/language-([\w+#.-]+)/);
        var lang = m ? m[1] : '';
        var t = (code.textContent||'').replace(/\n+$/, '');
        var run = 0, mm = t.match(/`+/g);
        if (mm) mm.forEach(function(x){ if (x.length>run) run=x.length; });
        var fence = new Array(Math.max(3, run+1)+1).join('`');
        pre.replaceWith(document.createTextNode('\n' + fence + lang + '\n' + t + '\n' + fence + '\n'));
      });
      host.appendChild(c);
      document.body.appendChild(host);
      var out = host.innerText || host.textContent || '';
      document.body.removeChild(host);
      return out.trim();
    }

    function limitText(){
      var alerts = pickAll(cfg.alerts);
      for (var i=0;i<alerts.length;i++){
        var t = alerts[i].innerText || '';
        if (t.length < 600) for (var j=0;j<limitRes.length;j++) if (limitRes[j].test(t)) return t;
      }
      return '';
    }

    var sentFlag = false, sendHow = 'none';
    function pickVisible(sels){
      for (var i=0;i<sels.length;i++){ try{ var l=document.querySelectorAll(sels[i]); for (var j=0;j<l.length;j++){ if (visible(l[j])) return l[j]; } }catch(x){} }
      return pick(sels);
    }
    function countSel(sels){ return sels.map(function(s){ try{ return s+'='+document.querySelectorAll(s).length; }catch(x){ return s+'=err'; } }).join(' | '); }
    function pageTail(n){ var b=''; try{ b=(document.body?document.body.innerText:'')||''; }catch(x){} b=b.replace(/\s+/g,' ').trim(); return b.slice(-n); }
    function diag(){
      try {
        return 'url=' + location.href + '\ninput: ' + countSel(cfg.input) + '\nsend: ' + countSel(cfg.send) + '\nstop: ' + countSel(cfg.stop) +
          '\nassistant: ' + countSel(cfg.assistant) + '\nsendHow=' + sendHow + ' visibility=' + document.visibilityState + '\npage: ' + pageTail(400);
      } catch(x){ return 'diag-error ' + x; }
    }
    function tailLimit(){ var t = pageTail(500); for (var j=0;j<limitRes.length;j++) if (limitRes[j].test(t)) return t; return ''; }
    function sendNear(el){
      var node = el;
      for (var up=0; up<7 && node; up++){
        node = node.parentElement; if (!node) break;
        var bs = Array.prototype.slice.call(node.querySelectorAll('button,[role=button]'));
        for (var i=bs.length-1;i>=0;i--){
          var b = bs[i];
          if (!visible(b) || b.disabled || b.getAttribute('aria-disabled') === 'true') continue;
          var label = ((b.getAttribute('aria-label')||'') + ' ' + (b.getAttribute('data-testid')||'') + ' ' + (b.innerText||'')).toLowerCase();
          if (/send|submit|gönder|gonder|发送/.test(label)) return b;
        }
      }
      return null;
    }

    function lastAssistant(){ var l = pickAll(cfg.assistant); return l.length ? l[l.length-1] : null; }
    function assistantCount(){ return pickAll(cfg.assistant).length; }

    function setInput(el, text){
      el.focus();
      if (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT'){
        var proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
        Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, text);
        el.dispatchEvent(new Event('input', {bubbles:true}));
        return;
      }
      var sel = window.getSelection(); var range = document.createRange();
      range.selectNodeContents(el); sel.removeAllRanges(); sel.addRange(range);
      var ok = false;
      try { ok = document.execCommand('insertText', false, text); } catch(x){}
      if (!ok || !(el.innerText||'').trim()){
        el.textContent = text;
        el.dispatchEvent(new InputEvent('input', {bubbles:true, inputType:'insertText', data:text}));
      }
    }

    function downloadLinks(root){
      var res = [], seen = {};
      var re = cfg.downloadHref.map(function(p){ return new RegExp(p, 'i'); });
      Array.prototype.slice.call((root||document).querySelectorAll('a[href]')).forEach(function(a){
        var h = a.href || a.getAttribute('href') || '';
        if (!h || seen[h]) return;
        var hit = re.some(function(r){ return r.test(h); }) || (a.hasAttribute('download') && h.indexOf('http')===0);
        if (hit){ seen[h]=1; res.push(h); }
      });
      return res;
    }

    (async function(){
      try {
        var el = null;
        for (var i=0;i<60 && !el;i++){ el = pickVisible(cfg.input); if(!el) await sleep(750); }
        if (!el){
          var u = lower(location.href);
          var login = cfg.loginUrlHints.some(function(h){ return u.indexOf(h) >= 0; });
          if (!login){ var tl0 = tailLimit(); if (tl0) return finish({status:'rate_limited', text:'', files:[], limit: tl0}); }
          return finish({status: login ? 'needs_login' : 'error', reason: login ? 'login' : 'no_input', text:'', files:[], diag: diag()});
        }
        var before = assistantCount();
        if (peek && before === 0) return finish({status:'ok', text:'', files:[], truncated:false});
        var beforeLinks = {}; if (!peek) downloadLinks(document).forEach(function(h){ beforeLinks[h]=1; });
        if (!peek) {
          setInput(el, prompt);
          await sleep(600);
          var btn = null;
          for (var k=0;k<20 && !btn;k++){
            var b = pick(cfg.send);
            if (b && !b.disabled && b.getAttribute('aria-disabled') !== 'true') btn = b; else await sleep(500);
          }
          if (btn) sendHow = 'button';
          if (!btn){ btn = sendNear(el); if (btn) sendHow = 'fallback_button'; }
          if (btn) btn.click();
          else {
            sendHow = 'enter';
            el.dispatchEvent(new KeyboardEvent('keydown', {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true}));
          }
          sentFlag = true;
        }

        var start = Date.now(), lastText = '', stable = 0, started = !!peek;
        while (Date.now() - start < cfg.timeoutMs){
          await sleep(1000);
          var lim = limitText();
          if (lim) return finish({status:'rate_limited', text:'', files:[], limit: lim});
          var stopEl = pick(cfg.stop);
          var generating = !!stopEl && visible(stopEl);
          var cnt = assistantCount();
          if (cnt > before || generating) started = true;
          if (!started) {
            if (Date.now() - start > 90000){
              var tl1 = tailLimit();
              if (tl1) return finish({status:'rate_limited', text:'', files:[], limit: tl1});
              return finish({status:'error', reason:'not_started', sent:sentFlag, text:'', files:[], diag: diag()});
            }
            continue;
          }
          var node = lastAssistant();
          var text = node ? toMd(node) : '';
          if (generating){ stable = 0; lastText = text; continue; }
          if (text && text === lastText) stable++; else { stable = 0; lastText = text; }
          if (stable >= 3 && text){
            var links = downloadLinks(node).concat(downloadLinks(document).filter(function(h){ return !beforeLinks[h]; }));
            var uniq = []; links.forEach(function(h){ if (uniq.indexOf(h) < 0) uniq.push(h); });
            var trunc = pickAll(['button','[role=button]']).some(function(b){ return visible(b) && contRe.test((b.innerText||'').trim()); });
            if (text.length < 600){
              for (var j=0;j<limitRes.length;j++) if (limitRes[j].test(text)) return finish({status:'rate_limited', text:'', files:[], limit:text});
            }
            return finish({status:'ok', text:text, files:uniq, truncated:trunc});
          }
        }
        finish({status:'timeout', reason: assistantCount() === 0 ? 'assistant_selector_miss' : 'timeout', sent:sentFlag, text:lastText, files:[], diag: diag()});
      } catch (e) {
        var dg = ''; try { dg = 'exception: ' + String(e) + '\n' + diag(); } catch(x){ dg = 'exception: ' + String(e); }
        finish({status:'error', reason:'exception', sent:sentFlag, text:'', files:[], diag: dg});
      }
    })();
    """.trimIndent()
}
