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
    fun readLocalStorage(keys: List<String>): String =
        "(function(){var o={};" + json.encodeToString(keys) +
            ".forEach(function(k){try{var v=localStorage.getItem(k);if(v!==null)o[k]=v;}catch(e){}});return JSON.stringify(o);})()"

    fun writeLocalStorage(values: Map<String, String>): String =
        "(function(){var d=" + json.encodeToString(values) +
            ";for(var k in d){try{localStorage.setItem(k,d[k]);}catch(e){}}return 'ok';})()"

    /** AI Studio sayfa metninde API anahtarı arar. */
    const val FIND_API_KEY =
        "(function(){var t=(document.body?document.body.innerText:'')+' '+Array.from(document.querySelectorAll('input,textarea,[data-key]'))" +
            ".map(function(e){return e.value||e.getAttribute('data-key')||'';}).join(' ');" +
            "var m=t.match(/AIza[0-9A-Za-z_\\-]{35}/);return m?m[0]:'';})()"

    fun runChat(sel: SiteSelectors, prompt: String): String {
        val cfg = json.encodeToString(sel)
        val p = json.encodeToString(prompt)
        return "(function(cfg,prompt){$CHAT_BODY})($cfg,$p);"
    }

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
        for (var i=0;i<60 && !el;i++){ el = pick(cfg.input); if(!el) await sleep(750); }
        if (!el){
          var u = lower(location.href);
          var login = cfg.loginUrlHints.some(function(h){ return u.indexOf(h) >= 0; });
          return finish({status: login ? 'needs_login' : 'error', text:'', files:[]});
        }
        var before = assistantCount();
        var beforeLinks = {}; downloadLinks(document).forEach(function(h){ beforeLinks[h]=1; });
        setInput(el, prompt);
        await sleep(600);
        var btn = null;
        for (var k=0;k<20 && !btn;k++){
          var b = pick(cfg.send);
          if (b && !b.disabled && b.getAttribute('aria-disabled') !== 'true') btn = b; else await sleep(500);
        }
        if (btn) btn.click();
        else el.dispatchEvent(new KeyboardEvent('keydown', {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true}));

        var start = Date.now(), lastText = '', stable = 0, started = false;
        while (Date.now() - start < cfg.timeoutMs){
          await sleep(1000);
          var lim = limitText();
          if (lim) return finish({status:'rate_limited', text:'', files:[], limit: lim});
          var stopEl = pick(cfg.stop);
          var generating = !!stopEl && visible(stopEl);
          var cnt = assistantCount();
          if (cnt > before || generating) started = true;
          if (!started) { if (Date.now() - start > 90000) return finish({status:'error', text:'', files:[]}); continue; }
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
        finish({status:'timeout', text:lastText, files:[]});
      } catch (e) {
        finish({status:'error', text:String(e), files:[]});
      }
    })();
    """.trimIndent()
}
