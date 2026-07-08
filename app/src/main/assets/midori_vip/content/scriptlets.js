(()=>{function Y(){return`(function() {
    if (window.__midoriYtAdPrunerInstalled) return;
    window.__midoriYtAdPrunerInstalled = true;

    // \u2500\u2500 CONFIG \u2500\u2500
    var SKIP_BTN = [
      '.ytp-ad-skip-button',
      '.ytp-ad-skip-button-modern',
      '.ytp-skip-ad-button',
      'button.ytp-ad-skip-button-modern',
      '.ytp-ad-skip-button-slot button',
      '.ytp-ad-skip-button-slot .ytp-ad-skip-button-container',
      '.ytp-ad-skip-button-slot .ytp-ad-skip-button-text',
      'button[id^="skip-button"]',
      '.videoAdUiSkipButton',
      '.ytp-ad-skip-button-slot',
    ].join(',');
    var OVERLAY_CLOSE = '.ytp-ad-overlay-close-button, .ytp-ad-overlay-close-container button, .ytp-ad-overlay-close-button svg';
    var userWasMuted = false;
    var savedState = false;
    var lastAdSeenAt = 0;
    var lastTickAt = 0;
    var lastAdaptiveScanAt = 0;
    var heartbeatTimer = 0;
    var playerObserver = null;
    var enforcementObserver = null;
    var videoEventsBound = false;
    var learnedSkipSelectors = [];
    var LEARNED_SELECTOR_KEY = '__midoriYtLearnedSkipSelectors';
    var MAX_LEARNED_SELECTORS = 12;
    var SKIP_TEXT_RE = /\\b(skip|skip ads?|skip ad|omitir|saltar|saltar anuncio|saltar anuncios|ignorar|pular|passer|ignorer|\xFCberspringen|annonce \xFCberspringen|salta|salta annuncio)\\b/i;
    var AD_LABEL_RE = /\\b(ad|ads|advertisement|advertising|sponsored|publicidad|anuncio|anuncios|patrocinado|propaganda|pubblicit[a\xE0]|annonce|werbung)\\b/i;

    function loadLearnedSelectors() {
      try {
        var raw = localStorage.getItem(LEARNED_SELECTOR_KEY);
        var parsed = raw ? JSON.parse(raw) : [];
        if (!Array.isArray(parsed)) return;
        for (var i = 0; i < parsed.length && learnedSkipSelectors.length < MAX_LEARNED_SELECTORS; i++) {
          if (typeof parsed[i] === 'string' && parsed[i].length < 180) learnedSkipSelectors.push(parsed[i]);
        }
      } catch(e) {}
    }

    function saveLearnedSelectors() {
      try { localStorage.setItem(LEARNED_SELECTOR_KEY, JSON.stringify(learnedSkipSelectors.slice(0, MAX_LEARNED_SELECTORS))); } catch(e) {}
    }

    function cssEscapeValue(value) {
      try {
        if (window.CSS && typeof window.CSS.escape === 'function') return window.CSS.escape(value);
      } catch(e) {}
      return String(value || '').replace(/[^a-zA-Z0-9_-]/g, '\\\\$&');
    }

    function compactClassSelector(el) {
      if (!el || !el.classList || el.classList.length === 0) return '';
      var parts = [];
      for (var i = 0; i < el.classList.length && parts.length < 3; i++) {
        var cls = String(el.classList[i] || '');
        if (!cls || cls.length > 60) continue;
        if (/^(ytp-|yt-|yt-spec-|yt-core-|html5-|videoAdUi)/.test(cls) || /skip|ad/i.test(cls)) {
          parts.push('.' + cssEscapeValue(cls));
        }
      }
      return parts.join('');
    }

    function stableSelectorFor(el) {
      if (!el || !el.matches) return '';
      var tag = String(el.localName || 'button').toLowerCase();
      var id = String(el.id || '');
      if (id && id.length <= 80 && /skip|ad/i.test(id)) return tag + '#' + cssEscapeValue(id);

      var testId = el.getAttribute('data-testid') || el.getAttribute('data-a-target') || el.getAttribute('aria-label');
      if (testId && testId.length <= 80 && /skip|ad|saltar|omitir|pular|annonce|werbung/i.test(testId)) {
        var attr = el.getAttribute('data-testid') ? 'data-testid' : (el.getAttribute('data-a-target') ? 'data-a-target' : 'aria-label');
        return tag + '[' + attr + '="' + cssEscapeValue(testId) + '"]';
      }

      var classSelector = compactClassSelector(el);
      if (classSelector) return tag + classSelector;
      return '';
    }

    function rememberSkipSelector(el) {
      var selector = stableSelectorFor(el);
      if (!selector || learnedSkipSelectors.indexOf(selector) !== -1) return;
      learnedSkipSelectors.unshift(selector);
      if (learnedSkipSelectors.length > MAX_LEARNED_SELECTORS) learnedSkipSelectors.length = MAX_LEARNED_SELECTORS;
      saveLearnedSelectors();
    }

    function visibleText(el) {
      if (!el) return '';
      return [
        el.getAttribute && (el.getAttribute('aria-label') || ''),
        el.getAttribute && (el.getAttribute('title') || ''),
        el.textContent || '',
        el.id || '',
        el.className || ''
      ].join(' ').replace(/\\s+/g, ' ').trim();
    }

    function shouldRunTick(minGapMs) {
      var now = Date.now();
      var gap = typeof minGapMs === 'number' ? minGapMs : 100;
      if ((now - lastTickAt) < gap) return false;
      lastTickAt = now;
      return true;
    }

    // \u2500\u2500 INJECT CSS to hide feed-level ad elements instantly \u2500\u2500
    // NOTE: Do NOT hide .ytp-ad-module, .ytp-ad-overlay-container,
    // .ytp-ad-overlay-slot, .ytp-ad-player-overlay \u2014 these are part of the
    // player control layer; hiding them breaks pause/click/spacebar.
    var s = document.createElement('style');
    s.textContent = [
      'ytd-promoted-sparkles-web-renderer,',
      'ytd-promoted-video-renderer,',
      'ytd-compact-promoted-video-renderer,',
      'ytd-banner-promo-renderer,',
      'ytd-statement-banner-renderer,',
      'ytd-in-feed-ad-layout-renderer,',
      'ytd-ad-slot-renderer,',
      'ytd-rich-item-renderer:has(ytd-ad-slot-renderer),',
      '#masthead-ad,',
      '#player-ads,',
      '#panels .ytd-ads-engagement-panel-content-renderer,',
      'tp-yt-paper-dialog:has(ytd-enforcement-message-view-model),',
      'ytd-reel-video-renderer ytd-ad-slot-renderer,',
      'ytd-reel-video-renderer [is-ad],',
      'ytd-reel-video-renderer .ytd-ad-slot-renderer',
      '{ display: none !important; }'
    ].join('');
    (document.head || document.documentElement).appendChild(s);
    loadLearnedSelectors();

    // \u2500\u2500 AD DETECTION \u2500\u2500
    function isAdShowing() {
      var p = document.getElementById('movie_player');
      if (!p) return false;
      // Only trust the definitive 'ad-showing' class \u2014 overlay elements
      // can persist in DOM after ads end and cause false positives.
      if (!p.classList.contains('ad-showing')) {
        return hasVisibleAdSignal(p);
      }
      // Secondary check: guard against race condition where 'ad-showing'
      // class lingers briefly after the ad finishes. If the video has
      // already ended or is within 0.5s of its end, the ad is over.
      try {
        var v = p.querySelector('video');
        if (v && v.duration && isFinite(v.duration) && v.duration > 0) {
          if (v.ended || v.currentTime >= v.duration - 0.5) return false;
        }
      } catch(e) {}
      return true;
    }

    function hasVisibleAdSignal(player) {
      try {
        if (document.querySelector(SKIP_BTN)) return true;
        var signs = player.querySelectorAll([
          '.ytp-ad-player-overlay',
          '.ytp-ad-preview-container',
          '.ytp-ad-text',
          '.ytp-ad-simple-ad-badge',
          '.ytp-ad-duration-remaining',
          '.video-ads .ytp-ad-module',
          '[class*="ad-showing"]',
          '[id*="ad_creative"]'
        ].join(','));
        for (var i = 0; i < signs.length; i++) {
          if (isElementVisible(signs[i]) && AD_LABEL_RE.test(visibleText(signs[i]))) return true;
        }
      } catch(e) {}
      return false;
    }

    // \u2500\u2500 SKIP LOGIC \u2500\u2500
    function tryClickSkip() {
      for (var s = 0; s < learnedSkipSelectors.length; s++) {
        try {
          var learned = document.querySelectorAll(learnedSkipSelectors[s]);
          for (var l = 0; l < learned.length; l++) {
            if (isElementVisible(learned[l])) {
              learned[l].click();
              return true;
            }
          }
        } catch(e) {}
      }

      var btns = document.querySelectorAll(SKIP_BTN);
      for (var i = 0; i < btns.length; i++) {
        if (isElementVisible(btns[i])) {
          rememberSkipSelector(btns[i]);
          btns[i].click();
          return true;
        }
      }

      return tryClickAdaptiveSkip();
    }

    function tryClickAdaptiveSkip() {
      var now = Date.now();
      if ((now - lastAdaptiveScanAt) < 300) return false;
      lastAdaptiveScanAt = now;

      try {
        var scope = document.getElementById('movie_player') || document;
        var candidates = scope.querySelectorAll('button, [role="button"], [tabindex], a');
        for (var i = 0; i < candidates.length; i++) {
          var el = candidates[i];
          if (!isElementVisible(el)) continue;
          var text = visibleText(el);
          if (!SKIP_TEXT_RE.test(text)) continue;
          rememberSkipSelector(el);
          el.click();
          return true;
        }
      } catch(e) {}
      return false;
    }

    function tryAPISkip() {
      try {
        var p = document.getElementById('movie_player');
        if (p && typeof p.skipAd === 'function') { p.skipAd(); return true; }
        if (p && typeof p.cancelPlayback === 'function' && isAdShowing()) {
          p.cancelPlayback();
          return true;
        }
      } catch(e) {}
      return false;
    }

    function forceSkipVideo() {
      try {
        // Try main player first, then Shorts reel player as fallback
        var v = document.querySelector('video.html5-main-video')
             || document.querySelector('ytd-reel-video-renderer video');
        if (!v || !v.duration || !isFinite(v.duration) || v.duration <= 0) return;

        // Save user state once when ad starts
        if (!savedState) {
          userWasMuted = v.muted;
          savedState = true;
        }

        // Only mute + jump near the end of the ad segment. Avoid touching
        // playbackRate to keep user play/pause/seek interactions intact.
        v.muted = true;
        v.currentTime = Math.max(v.duration - 0.1, 0);
      } catch(e) {}
    }

    function restoreState() {
      if (!savedState) return;
      try {
        var v = document.querySelector('video.html5-main-video');
        if (!v) return;
        v.muted = userWasMuted;
        savedState = false;
      } catch(e) {}
    }

    // \u2500\u2500 OVERLAY ADS \u2500\u2500
    function closeOverlays() {
      try {
        var btns = document.querySelectorAll(OVERLAY_CLOSE);
        for (var i = 0; i < btns.length; i++) btns[i].click();
      } catch(e) {}
    }

    // \u2500\u2500 SURVEY ADS \u2500\u2500
    function dismissSurveys() {
      try {
        var sur = document.querySelectorAll('.ytp-ad-survey, .ytp-ad-feedback-dialog-renderer');
        for (var i = 0; i < sur.length; i++) sur[i].remove();
        // Click "Skip Survey" or "No thanks" if visible
        var surBtns = document.querySelectorAll('.ytp-ad-survey .ytp-ad-skip-button, .ytp-ad-feedback-dialog-renderer button');
        for (var j = 0; j < surBtns.length; j++) surBtns[j].click();
      } catch(e) {}
    }

    function pruneAdaptiveAdNodes() {
      try {
        var nodes = document.querySelectorAll([
          'ytd-rich-item-renderer:has(ytd-ad-slot-renderer)',
          'ytd-video-renderer:has(ytd-ad-slot-renderer)',
          'ytd-compact-video-renderer:has(ytd-ad-slot-renderer)',
          'ytd-reel-video-renderer:has(ytd-ad-slot-renderer)',
          '[is-ad]',
          '[data-ad-slot]',
          '[data-google-av-cxn]',
          '[aria-label*="Advertisement" i]',
          '[aria-label*="Sponsored" i]',
          '[aria-label*="Publicidad" i]',
          '[aria-label*="Anuncio" i]'
        ].join(','));
        for (var i = 0; i < nodes.length; i++) {
          var node = nodes[i];
          if (!node || !node.isConnected) continue;
          var host = node.closest('ytd-rich-item-renderer, ytd-video-renderer, ytd-compact-video-renderer, ytd-reel-video-renderer') || node;
          if (host && host.style) host.style.setProperty('display', 'none', 'important');
        }
      } catch(e) {}
    }

    function isElementVisible(el) {
      if (!el || !el.isConnected) return false;
      try {
        var style = getComputedStyle(el);
        if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
        var rect = el.getBoundingClientRect();
        return rect.width > 0 && rect.height > 0;
      } catch(e) {
        return false;
      }
    }

    function hasOpenNonEnforcementDialog() {
      var dialogs = document.querySelectorAll([
        'tp-yt-paper-dialog[opened]',
        'tp-yt-paper-dialog[aria-hidden="false"]',
        'ytd-popup-container tp-yt-paper-dialog',
        'ytd-modal-with-title-and-button-renderer',
        'ytd-confirm-dialog-renderer',
        'ytd-unified-share-panel-renderer'
      ].join(','));

      for (var i = 0; i < dialogs.length; i++) {
        var dialog = dialogs[i];
        if (!isElementVisible(dialog)) continue;
        if (dialog.querySelector('ytd-enforcement-message-view-model, .yt-about-this-ad-renderer')) continue;
        return true;
      }
      return false;
    }

    function clearStaleScrollLock() {
      if (hasOpenNonEnforcementDialog()) return;

      var nodes = [
        document.documentElement,
        document.body,
        document.querySelector('ytd-app'),
        document.querySelector('ytd-page-manager')
      ];

      for (var i = 0; i < nodes.length; i++) {
        var node = nodes[i];
        if (!node || !node.style) continue;
        try {
          if (node.style.overflow === 'hidden') node.style.overflow = '';
          if (node.style.overflowY === 'hidden') node.style.overflowY = '';
          if (node.style.position === 'fixed' && (node === document.body || node === document.documentElement)) {
            node.style.position = '';
            node.style.top = '';
            node.style.width = '';
          }
          if (node.classList) {
            node.classList.remove('iron-overlay-no-scroll', 'no-scroll', 'scroll-disabled');
          }
        } catch(e) {}
      }
    }

    // \u2500\u2500 ENFORCEMENT MODAL REMOVAL \u2500\u2500
    function removeEnforcement() {
      var removed = false;
      var els = document.querySelectorAll('ytd-enforcement-message-view-model');
      for (var i = 0; i < els.length; i++) {
        els[i].remove();
        removed = true;
      }

      // Compatibility hardening: only remove dialogs that explicitly contain
      // enforcement/ad-info components. Text-based sweeping was too broad
      // and could remove legitimate YouTube dialogs used by channel links.
      var dialogs = document.querySelectorAll(
        'tp-yt-paper-dialog:has(ytd-enforcement-message-view-model), ' +
        'tp-yt-paper-dialog:has(.yt-about-this-ad-renderer)'
      );
      for (var d = 0; d < dialogs.length; d++) {
        dialogs[d].remove();
        removed = true;
      }

      if (removed) {
        var bds = document.querySelectorAll('tp-yt-iron-overlay-backdrop[opened]');
        for (var b = 0; b < bds.length; b++) bds[b].style.display = 'none';
        clearStaleScrollLock();
        setTimeout(clearStaleScrollLock, 50);
      }

      // NOTE: Do NOT auto-play here \u2014 this observer fires on every DOM
      // mutation and would override the user's manual pause.
    }

    // \u2500\u2500 MAIN TICK \u2500\u2500
    function tick() {
      if (isAdShowing()) {
        lastAdSeenAt = Date.now();
        closeOverlays();
        dismissSurveys();
        pruneAdaptiveAdNodes();
        // Try skip methods in order of preference
        if (!tryClickSkip() && !tryAPISkip()) {
          forceSkipVideo();
        }
      } else {
        pruneAdaptiveAdNodes();
        restoreState();
      }
    }

    function startHeartbeat() {
      if (heartbeatTimer) return;
      heartbeatTimer = setInterval(function() {
        // Keep a low-frequency safety tick only when ads were seen recently.
        if ((Date.now() - lastAdSeenAt) <= 12000) {
          if (shouldRunTick(200)) tick();
        }
      }, 1200);
    }

    function bindVideoEvents() {
      if (videoEventsBound) return;
      var video = document.querySelector('video.html5-main-video');
      if (!video) return;
      videoEventsBound = true;
      var onVideoEvent = function() {
        if (shouldRunTick(250)) tick();
      };
      video.addEventListener('loadedmetadata', onVideoEvent, true);
      video.addEventListener('playing', onVideoEvent, true);
      video.addEventListener('waiting', onVideoEvent, true);
      video.addEventListener('durationchange', onVideoEvent, true);
    }

    // \u2500\u2500 OBSERVERS \u2500\u2500
    // Event-driven first, with low-frequency heartbeat fallback.
    startHeartbeat();

    var pendingRAF = 0;

    // MutationObserver for instant reaction to ad-showing class change
    function startObserver() {
      var player = document.getElementById('movie_player');
      if (!player) { setTimeout(startObserver, 500); return; }

      if (playerObserver) playerObserver.disconnect();
      playerObserver = new MutationObserver(function(muts) {
        var needsTick = false;
        for (var i = 0; i < muts.length; i++) {
          if (muts[i].attributeName === 'class') {
            needsTick = true;
            break;
          }
          if (muts[i].addedNodes.length > 0) {
            bindVideoEvents();
            needsTick = true;
            break;
          }
        }
        if (needsTick && !pendingRAF) {
          pendingRAF = requestAnimationFrame(function() {
            pendingRAF = 0;
            if (shouldRunTick(120)) tick();
          });
        }
      });

      playerObserver.observe(player, {
        attributes: true, attributeFilter: ['class'],
        childList: true, subtree: true
      });

      bindVideoEvents();
    }
    startObserver();

    // Enforcement modal observer (separate, on body)
    function startEnforcementObserver() {
      var body = document.body;
      if (!body) { setTimeout(startEnforcementObserver, 500); return; }

      if (enforcementObserver) enforcementObserver.disconnect();
      enforcementObserver = new MutationObserver(function(muts) {
        var hasAdded = false;
        for (var i = 0; i < muts.length; i++) {
          if (muts[i].addedNodes.length > 0) { hasAdded = true; break; }
        }
        if (hasAdded) {
          // Enforcement removal must be immediate (no rAF) for UX
          removeEnforcement();
          pruneAdaptiveAdNodes();
          // Defer tick to next animation frame to reduce CPU churn
          if (!pendingRAF) {
            pendingRAF = requestAnimationFrame(function() {
              pendingRAF = 0;
              if (shouldRunTick(250)) tick();
            });
          }
        }
      });

      enforcementObserver.observe(body, { childList: true, subtree: true });
    }
    startEnforcementObserver();

    // YouTube SPA events (watch pages, shorts, playlist transitions)
    document.addEventListener('yt-navigate-finish', function() {
      bindVideoEvents();
      removeEnforcement();
      if (shouldRunTick(150)) tick();
    }, true);
    document.addEventListener('yt-page-data-updated', function() {
      if (shouldRunTick(180)) tick();
    }, true);
    window.addEventListener('popstate', function() {
      if (shouldRunTick(180)) tick();
    }, true);

    // Initial tick after handlers are installed.
    setTimeout(function() {
      removeEnforcement();
      tick();
    }, 0);
  })();`}(function(){"use strict";let M=!1;try{M=window.top===window}catch{M=!1}if(!M)return;let K=performance.now();function J(e){try{let t={action:"record-content-script-kpi",script:"scriptlets",hostname:window.location.hostname||"",durationMs:e},r=chrome.runtime.sendMessage(t);r&&typeof r.then=="function"&&r.catch(()=>{})}catch{}}function E(e){return new Promise(function(t){try{let r=chrome.runtime.sendMessage(e,function(s){t(s||null)});r&&typeof r.then=="function"&&r.then(t).catch(function(){t(null)})}catch{t(null)}})}function O(e){!e||typeof e!="object"||E({action:"ia-shield-risk-event",event:e})}function U(e){if(!e)return!1;var t=String(e.tagName||"").toLowerCase();if(t==="textarea")return!0;if(t==="input"){var r=String(e.type||"text").toLowerCase();return r==="text"||r==="search"||r==="url"}return!!e.isContentEditable}function _(e){return String(e||"").replace(/\s+/g," ").trim()}function Z(e){return String(e||"").replace(/[\u200B-\u200F\u202A-\u202E\u2060-\u206F\uFEFF]/g,"")}function Q(e){for(var t=[],r=String(e||""),s=r.match(/\b[A-Za-z0-9+/]{32,}={0,2}\b/g)||[],d=0;d<Math.min(s.length,4);d++)try{var p=atob(s[d]);/[\x09\x0A\x0D\x20-\x7E]{12,}/.test(p)&&t.push(p)}catch{}for(var v=r.match(/(?:%[0-9a-f]{2}){8,}/gi)||[],f=0;f<Math.min(v.length,4);f++)try{t.push(decodeURIComponent(v[f]))}catch{}return t.join(`
`)}function C(e){var t=String(e||""),r=Z(t),s=Q(t),d=t+`
`+r+`
`+s,p=d.toLowerCase(),v=[],f=0,m=[];function g(A,N,T){f+=A,v.push(N),T&&m.push(T)}for(var k=[{re:/ignore\s+(all\s+)?(previous|prior|above)\s+instructions?/i,f:"ignore-previous-instructions",p:3},{re:/disregard\s+(all\s+)?(prior|previous|above)\s+instructions?/i,f:"disregard-instructions",p:3},{re:/(developer|god|sudo|admin)\s+mode/i,f:"jailbreak-mode",p:2},{re:/(reveal|print|dump|show)\s+(the\s+)?(system|developer)\s+(prompt|instructions?)/i,f:"system-prompt-request",p:3},{re:/hidden\s+instructions?/i,f:"hidden-instruction-reference",p:2},{re:/do\s+not\s+follow\s+the\s+(rules|policy|guardrails)/i,f:"rule-bypass",p:3},{re:/bypass\s+(all\s+)?(safety|policy|guardrails|filters?)/i,f:"safety-bypass",p:3},{re:/jailbreak|dan\s+mode|unfiltered\s+answer/i,f:"jailbreak-keyword",p:3},{re:/exfiltrat(e|ion)|send\s+all\s+(data|history|context|prompts?)\s+to/i,f:"exfil-instruction",p:4},{re:/(base64|rot13|hex|url\s*encoded|decode\s+this)/i,f:"encoded-payload-reference",p:1},{re:/prompt\s+chain(ing)?|step\s*\d+\s*[:.)-]/i,f:"prompt-chain",p:1},{re:/ignora\s+(todas\s+)?(las\s+)?instrucciones\s+(anteriores|previas)/i,f:"spanish-ignore-instructions",p:3},{re:/olvida\s+(todas\s+)?(las\s+)?reglas|muestra\s+(el\s+)?prompt\s+del\s+sistema/i,f:"spanish-jailbreak",p:3},{re:/ignorez\s+(toutes\s+)?les\s+instructions\s+precedentes|oublie\s+les\s+regles/i,f:"french-jailbreak",p:3},{re:/ignoriere\s+(alle\s+)?vorherigen\s+anweisungen/i,f:"german-jailbreak",p:3},{re:/ignore\s+as\s+instrucoes\s+anteriores|modo\s+desenvolvedor/i,f:"portuguese-jailbreak",p:3}],y=0;y<k.length;y++)k[y].re.test(d)&&g(k[y].p,k[y].f,k[y].f);/[\u200B-\u200F\u202A-\u202E\u2060-\u206F\uFEFF]/.test(t)&&g(2,"invisible-or-bidi-unicode","hidden Unicode control characters"),/\$\\color\{\s*(white|#fff|#ffffff|transparent)\s*\}\{[^}]{3,}\}/i.test(t)&&g(2,"hidden-latex","text hidden with Markdown/LaTeX color"),/(<[^>]+style\s*=\s*["'][^"']*(color\s*:\s*(white|#fff|#ffffff|transparent)|display\s*:\s*none|opacity\s*:\s*0|font-size\s*:\s*0)[^"']*["'][^>]*>)/i.test(t)&&g(3,"hidden-html-style","HTML appears to hide instructional text"),/<!--[\s\S]{12,}-->|<template[\s\S]{12,}<\/template>/i.test(t)&&g(2,"hidden-html-comment","hidden HTML/comment content"),/\b[A-Za-z0-9+/]{40,}={0,2}\b/.test(t)&&g(s?3:2,"base64-like","encoded payload-like text"),/\b(?:[A-Fa-f0-9]{2}){20,}\b/.test(t)&&g(2,"hex-like","hex payload-like text"),/ignroe|ig nore|bpyass|by pass|revael|reve al|syts?em\s+prompt|syst[e3]m\s+pr[o0]mpt/i.test(p)&&g(1,"typosquatting-instruction","misspelled jailbreak terms"),/\b(step\s*\d+|first\s*[:,]|then\s*[:,]|after\s+that\s*[:,]|next\s*[:,])\b/i.test(t)&&g(1,"prompt-chaining","multi-step instruction chain");var S="low";return f>=10?S="critical":f>=7?S="high":f>=4&&(S="medium"),{score:f,severity:S,findings:v,reason:m.slice(0,3).join(", ")}}function ee(e){var t=String(e||""),r=!1,s=[],d=t;t=t.replace(/[\u200B-\u200F\u202A-\u202E\u2060-\u206F\uFEFF]/g,""),t!==d&&(r=!0,s.push("removed-invisible-unicode")),d=t,t=t.replace(/\b[A-Za-z0-9+/]{60,}={0,2}\b/g,"[encoded-payload-removed]"),t!==d&&(r=!0,s.push("masked-base64")),d=t,t=t.replace(/\b(?:[A-Fa-f0-9]{2}){24,}\b/g,"[hex-payload-removed]"),t!==d&&(r=!0,s.push("masked-hex"));for(var p=[/ignore\s+(all\s+)?previous\s+instructions?/gi,/disregard\s+(all\s+)?(prior|previous)\s+instructions?/gi,/ignore\s+(all\s+)?(above|prior)\s+instructions?/gi,/reveal\s+(the\s+)?system\s+prompt/gi,/show\s+(me\s+)?(your\s+)?hidden\s+instructions?/gi,/bypass\s+(all\s+)?safety/gi,/you\s+are\s+now\s+in\s+developer\s+mode/gi,/ignora\s+(todas\s+)?(las\s+)?instrucciones\s+(anteriores|previas)/gi,/muestra\s+(el\s+)?prompt\s+del\s+sistema/gi],v=0;v<p.length;v++)d=t,t=t.replace(p[v],"[filtered-instruction]"),t!==d&&(r=!0,s.push("filtered-dangerous-instruction"));var f=t.slice(0,12e3);return f.length!==t.length&&(r=!0,s.push("trimmed-length"),t=f),{text:t,changed:r,findings:s}}function te(e,t){if(e){var r=String(t||"");if(e.tagName==="TEXTAREA"||e.tagName==="INPUT"){var s=Number.isFinite(e.selectionStart)?e.selectionStart:e.value.length,d=Number.isFinite(e.selectionEnd)?e.selectionEnd:s;if(typeof e.setRangeText=="function")e.setRangeText(r,s,d,"end");else{var p=e.value.slice(0,s),v=e.value.slice(d);e.value=p+r+v}e.dispatchEvent(new Event("input",{bubbles:!0}));return}if(e.isContentEditable){try{document.execCommand("insertText",!1,r)}catch{e.textContent=(e.textContent||"")+r}e.dispatchEvent(new Event("input",{bubbles:!0}))}}}function re(){var e=document.createElement("div");return e.className="midori-ia-banner",e.style.display="none",e.innerHTML='<strong>IA Shield:</strong> <span class="midori-ia-banner-msg"></span>',(document.documentElement||document.body).appendChild(e),e}function ne(e){if(!e||e.enabled!==!0||window.__midoriIaShieldInstalled)return;window.__midoriIaShieldInstalled=!0;var t=e.strict===!0,r=e.monitor||{},s=e.isolate||{},d=e.chatbotHost===!0||e.protectedHost===!0,p=e.documentHost===!0,v=e.sanitizeOnPaste!==!1,f=0,m={},g={},k=document.createElement("style");k.textContent=[".midori-ia-banner{position:fixed;left:14px;right:14px;top:10px;z-index:2147483646;background:#1c2b1f;color:#e7f8ea;border:1px solid #4e9f62;border-radius:10px;padding:10px 12px;font:600 12px/1.4 -apple-system,BlinkMacSystemFont,Segoe UI,sans-serif;box-shadow:0 6px 18px rgba(0,0,0,.25)}",".midori-ia-isolated-warn{outline:2px dashed #f39c12 !important;filter:blur(1px) !important}",".midori-ia-isolated-block{display:none !important}"].join(""),(document.head||document.documentElement).appendChild(k);var y=null;function S(n){if(n){var i=Date.now();if(!(i-f<700)){f=i,y||(y=re());var a=y.querySelector(".midori-ia-banner-msg");a&&(a.textContent=n),y.style.display="block",clearTimeout(y._hideTimer),y._hideTimer=setTimeout(function(){y.style.display="none"},t?5200:3200)}}}function A(n){for(var i=String(n||"").slice(0,256),a=0,c=0;c<i.length;c++)a=(a<<5)-a+i.charCodeAt(c),a|=0;return String(a)}function N(n){var i=Date.now(),a=m[n]||0;if(i-a<6e4)return!1;m[n]=i;var c=Object.keys(m);if(c.length>120)for(var u=0;u<c.length-120;u++)delete m[c[u]];return!0}function T(n,i,a){var c=C(n);if(c.score<4)return c;var u=A(i+":"+n);return N(u)&&O({type:"prompt_injection_detected",severity:c.severity,timestamp:Date.now(),payload:{source:i,contentHash:A(a||n||""),score:c.score,reason:c.reason,findings:c.findings,strict:t}}),S(t?"Contenido sospechoso aislado. Modo estricto activo.":"Posible prompt-injection detectado en esta pagina IA."),c}function se(n){if(!n||n.nodeType!==1)return!1;var i=window.getComputedStyle(n);if(!i)return!1;if(i.position==="fixed"||i.position==="sticky")return!0;var a=String(n.className||"").toLowerCase();return!!/(overlay|modal|banner|toast|popover|dialog)/.test(a)}function de(n){if(!n||n.nodeType!==1)return!1;var i=["main",'[role="main"]',"form","textarea",'[contenteditable="true"]','[data-testid*="composer"]','[data-testid*="conversation"]','[class*="composer"]','[class*="conversation"]','[class*="chat"]','[id*="composer"]','[id*="conversation"]'].join(",");try{return!!(n.matches&&n.matches(i))||!!(n.closest&&n.closest('main,[role="main"],form'))}catch{return!1}}function q(n){if(!(!n||n.nodeType!==1)&&s.enabled!==!1&&!n.__midoriIaChecked&&(n.__midoriIaChecked=!0,!de(n))){var i=_(n.textContent||"").slice(0,4e3);if(!(!i||i.length<30)){var a=C(i);if(!(a.score<5)){var c=se(n);!c&&!t||(t&&s.mode==="block"?n.classList.add("midori-ia-isolated-block"):n.classList.add("midori-ia-isolated-warn"),T(i,"dom-overlay",i.slice(0,180)),O({type:"suspicious_overlay_isolated",severity:t?"high":"medium",timestamp:Date.now(),payload:{source:"dom-overlay",findings:a.findings,contentHash:A(i),score:a.score,reason:a.reason,strict:t}}))}}}}function ce(n){for(var i=0;i<n.length;i++)for(var a=n[i],c=0;c<a.addedNodes.length;c++){var u=a.addedNodes[c];if(!(!u||u.nodeType!==1)){q(u);for(var h=u.querySelectorAll?u.querySelectorAll('[role="dialog"],[role="alert"],div,aside,section'):[],b=0;b<Math.min(h.length,18);b++)q(h[b])}}}var $=null;d&&r.dom!==!1&&($=new MutationObserver(ce),$.observe(document.documentElement||document,{childList:!0,subtree:!0}),setTimeout(function(){for(var n=document.querySelectorAll('[role="dialog"],[role="alert"],.modal,.overlay,.banner,aside,section'),i=0;i<Math.min(n.length,50);i++)q(n[i])},350));function le(n,i,a){if(t)return a.score>=7;var c=_(n).slice(0,260),u=_(i).slice(0,260),h=["IA Shield encontro posible prompt injection.","","Hallazgos: "+a.findings.slice(0,4).join(", "),"","Vista previa original:",c,"","Vista previa sanitizada:",u,"","\xBFPegar la version sanitizada?"].join(`
`);try{return window.confirm(h)}catch{return!1}}d&&v&&r.paste!==!1&&document.addEventListener("paste",function(n){var i=n&&n.target;if(U(i)){var a=n.clipboardData||window.clipboardData,c=a&&a.getData?a.getData("text/plain"):"";if(c){var u=C(c);if(!(u.score<3)){var h=ee(c);if(h.changed){if(!le(c,h.text,u)){O({type:"prompt_injection_detected",severity:u.severity,timestamp:Date.now(),payload:{source:"paste",findings:u.findings,contentHash:A(c),score:u.score,reason:u.reason,fieldType:(i.tagName||"").toLowerCase(),strict:t}}),S("IA Shield detecto un paste sospechoso y no modifico el texto.");return}n.preventDefault(),te(i,h.text),S("Prompt sanitizado localmente para reducir riesgo de injection."),O({type:"prompt_sanitized",severity:u.severity,timestamp:Date.now(),payload:{source:"paste",findings:u.findings.concat(h.findings),contentHash:A(c),score:u.score,reason:u.reason,fieldType:(i.tagName||"").toLowerCase(),strict:t}})}}}}},!0),d&&r.input!==!1&&document.addEventListener("input",function(n){var i=n&&n.target;if(U(i)){var a="";i.tagName==="TEXTAREA"||i.tagName==="INPUT"?a=i.value||"":a=i.textContent||"",!(!a||a.length<24)&&T(a.slice(0,2e3),"prompt-input",a.slice(0,120))}},!0),p&&r.copy!==!1&&document.addEventListener("copy",function(n){var i="";try{i=String(window.getSelection?window.getSelection():"")}catch{i=""}if(!(!i||i.length<24)){var a=C(i);a.score<4||(O({type:"prompt_injection_detected",severity:a.severity,timestamp:Date.now(),payload:{source:"copy",findings:a.findings,contentHash:A(i),score:a.score,reason:a.reason,strict:t}}),S("IA Shield detecto instrucciones sospechosas en el texto copiado."))}},!0);function W(n){if(!n)return{block:!1,score:0,findings:[]};var i;try{i=new URL(n,location.href)}catch{return{block:!1,score:0,findings:[]}}var a=String(i.hostname||"").toLowerCase(),c=String(i.pathname||"").toLowerCase(),u=i.origin===location.origin||a===location.hostname||a.endsWith("."+location.hostname),h=[],b=0;if(u)return{block:!1,score:0,findings:["first-party"]};for(var V=["openai.com","chatgpt.com","google.com","googleapis.com","gstatic.com","claude.ai","anthropic.com","microsoft.com","bing.com","perplexity.ai","poe.com","mistral.ai","deepseek.com","x.ai","grok.com","github.com"],j=0;j<V.length;j++)if(a===V[j]||a.endsWith("."+V[j]))return{block:!1,score:0,findings:["known-ai-first-party"]};for(var X=["webhook.site","hookbin","requestbin","ngrok","trycloudflare","pipedream","beeceptor","interact.sh","oast","burpcollaborator","webtask.io","discord.com/api/webhooks","slack.com/api"],B=0;B<X.length;B++)(a+c).indexOf(X[B])!==-1&&(b+=5,h.push("known-exfil-destination"));/\/(collect|exfil|steal|dump|leak|prompt|prompts|history|conversation|memory|token|session)\b/.test(c)&&(b+=3,h.push("suspicious-path"));var ve=i.searchParams,G=["prompt","system_prompt","history","chat_history","conversation","api_key","token","authorization","cookie","session"],R=[];ve.forEach(function(he,ye){R.push(ye)});for(var H=0;H<R.length;H++)for(var me=R[H].toLowerCase(),P=0;P<G.length;P++)me.indexOf(G[P])!==-1&&(b+=3,h.push("sensitive-param:"+G[P]));return g[a]||(g[a]=Date.now(),R.length>0&&/[a-z0-9-]{12,}\.(?:com|net|app|dev|io|site|xyz)$/i.test(a)&&(b+=1,h.push("new-cross-origin-destination"))),{block:b>=5,score:b,findings:h}}function ge(n){return W(n).block}function F(n,i){var a=W(n);return a.block?(O({type:"exfil_request_blocked",severity:"high",timestamp:Date.now(),payload:{source:i,blockedUrl:String(n||"").slice(0,280),findings:a.findings,score:a.score,reason:"cross-origin request matched exfil scoring",strict:t}}),S("Solicitud de exfiltracion bloqueada por IA Shield."),!0):!1}var z=window.fetch;typeof z=="function"&&(window.fetch=function(n,i){var a="";return typeof n=="string"?a=n:n&&n.url&&(a=n.url),F(a,"fetch")?Promise.resolve(new Response("",{status:204,statusText:"No Content"})):z.apply(this,arguments)});var ue=XMLHttpRequest.prototype.open,fe=XMLHttpRequest.prototype.send;if(XMLHttpRequest.prototype.open=function(n,i){return this.__midoriIaUrl=String(i||""),ue.apply(this,arguments)},XMLHttpRequest.prototype.send=function(){if(this.__midoriIaUrl&&F(this.__midoriIaUrl,"xhr")){try{this.abort()}catch{}return}return fe.apply(this,arguments)},typeof navigator.sendBeacon=="function"){var pe=navigator.sendBeacon.bind(navigator);navigator.sendBeacon=function(n){return F(n,"sendBeacon")?!1:pe.apply(null,arguments)}}}let o={};function l(e){if(e==null)return"";let t=String(e);return/[\x00-\x08\x0e-\x1f]/.test(t)?"":JSON.stringify(t).slice(1,-1)}o["abort-on-property-read"]=o.aopr=function(e){return e?`(function() {
      var rid = '${Math.random().toString(36).slice(2)}';
      var abortOnRead = function(obj, chain) {
        var parts = chain.split('.');
        var current = parts[0];
        if (parts.length === 1) {
          var desc = Object.getOwnPropertyDescriptor(obj, current);
          if (desc && desc.get) return;
          var val = obj[current];
          Object.defineProperty(obj, current, {
            get: function() { throw new ReferenceError(rid); },
            set: function(v) { val = v; },
            configurable: true
          });
          return;
        }
        var owner = obj[current];
        if (owner instanceof Object) {
          abortOnRead(owner, parts.slice(1).join('.'));
        } else {
          var origValue = owner;
          Object.defineProperty(obj, current, {
            get: function() { return origValue; },
            set: function(v) {
              origValue = v;
              if (v instanceof Object) {
                abortOnRead(v, parts.slice(1).join('.'));
              }
            },
            configurable: true
          });
        }
      };
      try { abortOnRead(window, '${l(e)}'); } catch(e) {}
    })();`:""},o["abort-on-property-write"]=o.aopw=function(e){return e?`(function() {
      var rid = '${Math.random().toString(36).slice(2)}';
      var abortOnWrite = function(obj, chain) {
        var parts = chain.split('.');
        var current = parts[0];
        if (parts.length === 1) {
          var val = obj[current];
          Object.defineProperty(obj, current, {
            get: function() { return val; },
            set: function() { throw new ReferenceError(rid); },
            configurable: true
          });
          return;
        }
        var owner = obj[current];
        if (owner instanceof Object) {
          abortOnWrite(owner, parts.slice(1).join('.'));
        } else {
          var origValue = owner;
          Object.defineProperty(obj, current, {
            get: function() { return origValue; },
            set: function(v) {
              origValue = v;
              if (v instanceof Object) {
                abortOnWrite(v, parts.slice(1).join('.'));
              }
            },
            configurable: true
          });
        }
      };
      try { abortOnWrite(window, '${l(e)}'); } catch(e) {}
    })();`:""},o["abort-current-inline-script"]=o.acis=function(e,t){return e?`(function() {
      var rid = '${Math.random().toString(36).slice(2)}';
      var prop = '${l(e)}';
      var search = '${l(t||"")}';
      var magic = rid;
      var abort = function() { throw new ReferenceError(magic); };
      var init = function(obj, chain) {
        var parts = chain.split('.');
        var current = parts[0];
        if (parts.length > 1) {
          var owner = obj[current];
          if (owner instanceof Object === false) {
            var val = owner;
            Object.defineProperty(obj, current, {
              get: function() { return val; },
              set: function(v) {
                val = v;
                if (v instanceof Object) init(v, parts.slice(1).join('.'));
              },
              configurable: true
            });
            return;
          }
          init(owner, parts.slice(1).join('.'));
          return;
        }
        var desc = Object.getOwnPropertyDescriptor(obj, current);
        var origGet = desc && desc.get;
        var origSet = desc && desc.set;
        var origVal = desc ? desc.value : obj[current];
        Object.defineProperty(obj, current, {
          get: function() {
            if (search === '') { abort(); }
            else {
              var e = new Error();
              if (e.stack && e.stack.indexOf(search) !== -1) abort();
            }
            if (origGet) return origGet.call(this);
            return origVal;
          },
          set: function(v) {
            if (origSet) origSet.call(this, v);
            else origVal = v;
          },
          configurable: true
        });
      };
      try { init(window, prop); } catch(e) {}
    })();`:""},o["set-constant"]=o.set=function(e,t){if(!e)return"";let r;switch(t){case"true":r="true";break;case"false":r="false";break;case"undefined":r="undefined";break;case"null":r="null";break;case"noopFunc":r="(function(){})";break;case"trueFunc":r="(function(){return true})";break;case"falseFunc":r="(function(){return false})";break;case"emptyObj":r="({})";break;case"emptyArr":r="([])";break;case"emptyStr":r="('')";break;case"":r="('')";break;case"0":r="0";break;case"1":r="1";break;case"-1":r="-1";break;case"NaN":r="NaN";break;case"Infinity":r="Infinity";break;default:r=isNaN(t)?"'"+l(t)+"'":t;break}return`(function() {
      var cValue = ${r};
      var chain = '${l(e)}'.split('.');
      var setConst = function(obj, parts) {
        if (parts.length === 0) return;
        var current = parts[0];
        if (parts.length === 1) {
          try {
            Object.defineProperty(obj, current, {
              get: function() { return cValue; },
              set: function() {},
              configurable: false
            });
          } catch(e) {
            obj[current] = cValue;
          }
          return;
        }
        if (!(current in obj) || obj[current] === null || obj[current] === undefined) {
          obj[current] = {};
        }
        var next = obj[current];
        if (next instanceof Object) {
          setConst(next, parts.slice(1));
        } else {
          var val = next;
          Object.defineProperty(obj, current, {
            get: function() { return val; },
            set: function(v) {
              val = v;
              if (v instanceof Object) setConst(v, parts.slice(1));
            },
            configurable: true
          });
        }
      };
      try { setConst(window, chain); } catch(e) {}
    })();`},o["remove-attr"]=o.ra=function(e,t){if(!e)return"";let r=t||"["+e+"]";return`(function() {
      var attr = '${l(e)}';
      var selector = '${l(r)}';
      var removeAttr = function() {
        var nodes = document.querySelectorAll(selector);
        for (var i = 0; i < nodes.length; i++) {
          nodes[i].removeAttribute(attr);
        }
      };
      removeAttr();
      var observer = new MutationObserver(removeAttr);
      observer.observe(document.documentElement || document.body || document, {
        attributes: true, childList: true, subtree: true
      });
    })();`},o["remove-class"]=o.rc=function(e,t){if(!e)return"";let r=t||"."+e;return`(function() {
      var cn = '${l(e)}';
      var selector = '${l(r)}';
      var removeClass = function() {
        var nodes = document.querySelectorAll(selector);
        for (var i = 0; i < nodes.length; i++) {
          nodes[i].classList.remove(cn);
        }
      };
      removeClass();
      var observer = new MutationObserver(removeClass);
      observer.observe(document.documentElement || document.body || document, {
        attributes: true, childList: true, subtree: true
      });
    })();`},o["nano-setInterval-booster"]=o["nano-sib"]=function(e,t){let r=e||"",s=parseInt(t)||.05;return`(function() {
      var needle = '${l(r)}';
      var boost = ${s};
      var origSetInterval = window.setInterval;
      window.setInterval = function(fn, ms) {
        var fnStr = typeof fn === 'function' ? fn.toString() : String(fn);
        if (needle === '' || fnStr.indexOf(needle) !== -1) {
          if (boost < 1) ms = Math.round(ms * boost);
          else ms = boost;
        }
        return origSetInterval.apply(this, [fn, ms]);
      };
    })();`},o["nano-setTimeout-booster"]=o["nano-stb"]=function(e,t){let r=e||"",s=parseInt(t)||.05;return`(function() {
      var needle = '${l(r)}';
      var boost = ${s};
      var origSetTimeout = window.setTimeout;
      window.setTimeout = function(fn, ms) {
        var fnStr = typeof fn === 'function' ? fn.toString() : String(fn);
        if (needle === '' || fnStr.indexOf(needle) !== -1) {
          if (boost < 1) ms = Math.round(ms * boost);
          else ms = boost;
        }
        return origSetTimeout.apply(this, [fn, ms]);
      };
    })();`},o.nowebrtc=function(){return`(function() {
      var noopCtor = function() { throw new DOMException('', 'NotSupportedError'); };
      if (window.RTCPeerConnection) {
        window.RTCPeerConnection = noopCtor;
      }
      if (window.webkitRTCPeerConnection) {
        window.webkitRTCPeerConnection = noopCtor;
      }
      if (window.mozRTCPeerConnection) {
        window.mozRTCPeerConnection = noopCtor;
      }
    })();`},o["json-prune"]=function(e,t){return e?`(function() {
      var propsToRemove = '${l(e||"")}';
      var requiredProps = '${l(t||"")}';
      var origParse = JSON.parse;
      function deepHas(obj, chain) {
        if (!obj || typeof obj !== 'object') return false;
        var parts = chain.split('.');
        var cur = obj;
        for (var i = 0; i < parts.length; i++) {
          if (cur == null || typeof cur !== 'object') return false;
          if (!(parts[i] in cur)) return false;
          cur = cur[parts[i]];
        }
        return true;
      }
      function deepDelete(obj, chain) {
        if (!obj || typeof obj !== 'object') return;
        var parts = chain.split('.');
        var cur = obj;
        for (var i = 0; i < parts.length - 1; i++) {
          if (cur == null || typeof cur !== 'object') return;
          if (!(parts[i] in cur)) return;
          cur = cur[parts[i]];
        }
        if (cur && typeof cur === 'object' && parts.length > 0) {
          delete cur[parts[parts.length - 1]];
        }
      }
      JSON.parse = function() {
        var r = origParse.apply(this, arguments);
        if (r instanceof Object === false) return r;
        if (requiredProps) {
          var reqs = requiredProps.split(' ');
          for (var i = 0; i < reqs.length; i++) {
            if (!deepHas(r, reqs[i])) return r;
          }
        }
        var props = propsToRemove.split(' ');
        for (var i = 0; i < props.length; i++) {
          deepDelete(r, props[i]);
        }
        return r;
      };
    })();`:""},o["addEventListener-defuser"]=o.aeld=function(e,t){let r=e||"",s=t||"";return`(function() {
      var typeNeedle = '${l(r)}';
      var patternNeedle = '${l(s)}';
      var origAdd = EventTarget.prototype.addEventListener;
      EventTarget.prototype.addEventListener = function(type, fn) {
        if (typeNeedle && type.indexOf(typeNeedle) === -1) {
          return origAdd.apply(this, arguments);
        }
        if (patternNeedle) {
          var fnStr = typeof fn === 'function' ? fn.toString() : String(fn);
          if (fnStr.indexOf(patternNeedle) !== -1) return;
        }
        return origAdd.apply(this, arguments);
      };
    })();`},o["no-setTimeout-if"]=o["prevent-setTimeout"]=o.nostif=function(e){return`(function() {
      var needle = '${l(e||"")}';
      var origSetTimeout = window.setTimeout;
      window.setTimeout = function(fn, ms) {
        if (needle) {
          var fnStr = typeof fn === 'function' ? fn.toString() : String(fn);
          if (fnStr.indexOf(needle) !== -1) return;
        }
        return origSetTimeout.apply(this, arguments);
      };
    })();`},o["no-setInterval-if"]=o["prevent-setInterval"]=o.nosiif=function(e){return`(function() {
      var needle = '${l(e||"")}';
      var origSetInterval = window.setInterval;
      window.setInterval = function(fn, ms) {
        if (needle) {
          var fnStr = typeof fn === 'function' ? fn.toString() : String(fn);
          if (fnStr.indexOf(needle) !== -1) return;
        }
        return origSetInterval.apply(this, arguments);
      };
    })();`},o["no-fetch-if"]=o["prevent-fetch"]=function(e){return`(function() {
      var needle = '${l(e||"")}';
      var origFetch = window.fetch;
      window.fetch = function(resource) {
        var url = '';
        if (typeof resource === 'string') url = resource;
        else if (resource && resource.url) url = resource.url;
        if (needle && url.indexOf(needle) !== -1) {
          return Promise.resolve(new Response('', { status: 200, statusText: 'OK' }));
        }
        return origFetch.apply(this, arguments);
      };
    })();`},o["no-xhr-if"]=o["prevent-xhr"]=function(e){return`(function() {
      var needle = '${l(e||"")}';
      var origOpen = XMLHttpRequest.prototype.open;
      XMLHttpRequest.prototype.open = function(method, url) {
        if (needle && String(url).indexOf(needle) !== -1) {
          this._blocked = true;
        }
        return origOpen.apply(this, arguments);
      };
      var origSend = XMLHttpRequest.prototype.send;
      XMLHttpRequest.prototype.send = function() {
        if (this._blocked) {
          Object.defineProperty(this, 'readyState', { value: 4 });
          Object.defineProperty(this, 'status', { value: 200 });
          Object.defineProperty(this, 'responseText', { value: '' });
          Object.defineProperty(this, 'response', { value: '' });
          this.dispatchEvent(new Event('load'));
          this.dispatchEvent(new Event('loadend'));
          return;
        }
        return origSend.apply(this, arguments);
      };
    })();`},o["window.name-defuser"]=function(){return"(function() { window.name = ''; })();"},o["disable-newtab-links"]=function(){return`(function() {
      var POPUNDER_HOSTS = [
        'trafficjunky.net', 'trafficjunky.com', 'juicyads.com', 'exoclick.com',
        'ero-advertising.com', 'plugrush.com', 'exdynsrv.com', 'popads.net',
        'popcash.net', 'onclickads.net', 'hilltopads.net', 'adcash.com'
      ];

      function hostMatches(hostname, pattern) {
        return hostname === pattern || hostname.endsWith('.' + pattern);
      }

      function isPopunderHref(href) {
        if (!href || typeof href !== 'string') return false;
        if (!/^https?:///i.test(href)) return false;
        try {
          var u = new URL(href, location.href);
          var h = String(u.hostname || '').toLowerCase();
          if (!h) return false;
          for (var i = 0; i < POPUNDER_HOSTS.length; i++) {
            if (hostMatches(h, POPUNDER_HOSTS[i])) return true;
          }
        } catch (e) {}
        return false;
      }

      document.addEventListener('click', function(e) {
        var el = e.target.closest('a[target="_blank"]');
        if (!el) return;
        var href = '';
        try { href = String(el.href || el.getAttribute('href') || ''); } catch (err) {}
        if (isPopunderHref(href)) {
          el.removeAttribute('target');
        }
      }, true);
    })();`},o.noeval=function(){return`(function() {
      window.eval = function() { return ''; };
    })();`},o["set-cookie"]=o["trusted-set-cookie"]=function(e,t){if(!e)return"";let r=t||"1";return`(function() {
      document.cookie = '${l(e)}=${l(r)}; path=/; max-age=31536000';
    })();`},o["set-local-storage-item"]=function(e,t){return e?`(function() {
      try { localStorage.setItem('${l(e)}', '${l(t||"")}'); } catch(e) {}
    })();`:""},o["set-session-storage-item"]=function(e,t){return e?`(function() {
      try { sessionStorage.setItem('${l(e)}', '${l(t||"")}'); } catch(e) {}
    })();`:""},o["yt-ad-pruner"]=Y,o["yt-skip-ad"]=o["yt-ad-pruner"],o["yt-enforce-remove"]=o["yt-ad-pruner"],o["twitch-ad-mute"]=function(){return`(function() {
      var wasMuted = false;
      var savedVol = 1;
      var adActive = false;

      function isAdPlaying() {
        // Twitch signals ads via data attributes and specific elements
        var label = document.querySelector('[data-a-target="video-ad-label"]');
        if (label) return true;
        var countdown = document.querySelector('[data-a-target="video-ad-countdown"]');
        if (countdown) return true;
        var overlay = document.querySelector('.video-player__overlay[data-a-target="video-ad-overlay"]');
        if (overlay) return true;
        // Check for "Ad" text in player status
        var status = document.querySelector('.tw-media-card-stat');
        if (status && /\\bad\\b/i.test(status.textContent)) return true;
        return false;
      }

      function hideAdUI() {
        var sels = [
          '[data-a-target="video-ad-label"]',
          '[data-a-target="video-ad-countdown"]',
          '[data-a-target="ad-countdown-text"]',
          '[data-a-target="video-ad-info-bar"]',
          '.video-player__overlay[data-a-target="video-ad-overlay"]',
          '[data-a-target="video-ad-pause-overlay"]',
        ];
        for (var i = 0; i < sels.length; i++) {
          try {
            var els = document.querySelectorAll(sels[i]);
            for (var j = 0; j < els.length; j++) {
              els[j].style.display = 'none';
            }
          } catch(e) {}
        }
      }

      function getMainPlayerVideo() {
        // Select only the main player video, not sidebar/preview thumbnails
        var player = document.querySelector('.video-player__container video, [data-a-target="video-player"] video');
        if (player) return player;
        // Fallback: pick the largest visible video element
        var videos = document.querySelectorAll('video');
        var best = null;
        var bestArea = 0;
        for (var i = 0; i < videos.length; i++) {
          var rect = videos[i].getBoundingClientRect();
          var area = rect.width * rect.height;
          if (area > bestArea) { bestArea = area; best = videos[i]; }
        }
        return best;
      }

      function muteAd() {
        try {
          var v = getMainPlayerVideo();
          if (!v) return;
          if (!adActive) {
            wasMuted = v.muted;
            savedVol = v.volume;
            adActive = true;
          }
          v.muted = true;
        } catch(e) {}
      }

      function restoreAudio() {
        if (!adActive) return;
        try {
          var v = getMainPlayerVideo();
          if (!v) return;
          v.muted = wasMuted;
          v.volume = savedVol;
          adActive = false;
        } catch(e) {}
      }

      function tick() {
        if (isAdPlaying()) {
          muteAd();
          hideAdUI();
        } else {
          restoreAudio();
        }
      }

      setInterval(tick, 300);

      // MutationObserver for faster reaction
      function startObs() {
        var target = document.querySelector('.video-player') || document.body;
        if (!target) { setTimeout(startObs, 500); return; }
        new MutationObserver(function() { tick(); }).observe(target, {
          childList: true, subtree: true, attributes: true, attributeFilter: ['class']
        });
      }
      startObs();
    })();`},o["spotify-ad-defuser"]=function(){return`(function() {
      if (window.__midoriSpotifyAdDefuserInstalled) return;
      window.__midoriSpotifyAdDefuserInstalled = true;

      var adActive = false;
      var saved = { muted: false, volume: 1 };
      var lastTickAt = 0;
      var AD_RE = /\\b(ad|advertisement|sponsored|publicidad|anuncio|patrocinado|propaganda|pubblicit[a\xE0]|annonce|werbung)\\b/i;
      var AD_SELECTORS = [
        '[data-testid*="advert" i]',
        '[data-testid*="sponsored" i]',
        '[aria-label*="Advertisement" i]',
        '[aria-label*="Sponsored" i]',
        '[aria-label*="Publicidad" i]',
        '[aria-label*="Anuncio" i]',
        'iframe[src*="doubleclick" i]',
        'iframe[src*="googlesyndication" i]',
        'iframe[src*="ads" i]',
        '[class*="ad-container" i]',
        '[class*="advertisement" i]',
        '[id*="ad-container" i]'
      ].join(',');
      var HIDE_SELECTORS = [
        '[data-testid*="advert" i]',
        '[data-testid*="sponsored" i]',
        '[aria-label*="Advertisement" i]',
        '[aria-label*="Sponsored" i]',
        '[aria-label*="Publicidad" i]',
        '[aria-label*="Anuncio" i]',
        'iframe[src*="doubleclick" i]',
        'iframe[src*="googlesyndication" i]',
        'iframe[src*="ads" i]'
      ].join(',');

      var style = document.createElement('style');
      style.textContent = HIDE_SELECTORS + '{ display: none !important; visibility: hidden !important; }';
      (document.head || document.documentElement).appendChild(style);

      function isVisible(el) {
        if (!el || !el.isConnected) return false;
        try {
          var rect = el.getBoundingClientRect();
          var cs = getComputedStyle(el);
          return rect.width > 0 && rect.height > 0 && cs.display !== 'none' && cs.visibility !== 'hidden';
        } catch(e) {
          return false;
        }
      }

      function getAudio() {
        var audio = document.querySelector('audio');
        if (audio) return audio;
        var media = document.querySelectorAll('video, audio');
        var best = null;
        for (var i = 0; i < media.length; i++) {
          if (!best || media[i].duration > best.duration) best = media[i];
        }
        return best;
      }

      function hasAdUi() {
        try {
          var nodes = document.querySelectorAll(AD_SELECTORS);
          for (var i = 0; i < nodes.length; i++) {
            if (isVisible(nodes[i])) return true;
          }
          var nowPlaying = document.querySelector('[data-testid="now-playing-widget"], footer, [role="contentinfo"]');
          if (nowPlaying && AD_RE.test(nowPlaying.textContent || '')) return true;
        } catch(e) {}
        return false;
      }

      function hideAdUi() {
        try {
          var nodes = document.querySelectorAll(HIDE_SELECTORS);
          for (var i = 0; i < nodes.length; i++) {
            nodes[i].style.setProperty('display', 'none', 'important');
            nodes[i].style.setProperty('visibility', 'hidden', 'important');
          }
          var buttons = document.querySelectorAll('button, [role="button"]');
          for (var j = 0; j < buttons.length; j++) {
            var text = [
              buttons[j].getAttribute('aria-label') || '',
              buttons[j].getAttribute('title') || '',
              buttons[j].textContent || ''
            ].join(' ');
            if (/\\b(close|dismiss|skip|cerrar|omitir|saltar)\\b/i.test(text) && AD_RE.test(text)) {
              buttons[j].click();
            }
          }
        } catch(e) {}
      }

      function muteAd() {
        try {
          var media = getAudio();
          if (!media) return;
          if (!adActive) {
            saved.muted = media.muted;
            saved.volume = media.volume;
            adActive = true;
          }
          media.muted = true;
        } catch(e) {}
      }

      function restoreAudio() {
        if (!adActive) return;
        try {
          var media = getAudio();
          if (!media) return;
          media.muted = saved.muted;
          if (Number.isFinite(saved.volume)) media.volume = saved.volume;
          adActive = false;
        } catch(e) {}
      }

      function tick() {
        var now = Date.now();
        if ((now - lastTickAt) < 250) return;
        lastTickAt = now;
        if (hasAdUi()) {
          muteAd();
          hideAdUi();
        } else {
          restoreAudio();
        }
      }

      setInterval(tick, 1200);
      function startObserver() {
        var root = document.body || document.documentElement;
        if (!root) { setTimeout(startObserver, 500); return; }
        new MutationObserver(tick).observe(root, {
          childList: true,
          subtree: true,
          attributes: true,
          attributeFilter: ['class', 'style', 'aria-label', 'data-testid']
        });
      }
      startObserver();
      setTimeout(tick, 0);
    })();`},o["canvas-fingerprint-protect"]=function(){return`(function() {
      var noiseSeed = (Math.random() * 0xFFFF | 0);
      var origToDataURL = HTMLCanvasElement.prototype.toDataURL;
      var origToBlob = HTMLCanvasElement.prototype.toBlob;

      // Fingerprinting canvases are typically small text-rendering canvases.
      // Skip noise for large canvases used for real content.
      function isFingerprintCanvas(canvas) {
        return canvas.width > 0 && canvas.height > 0 &&
               canvas.width <= 450 && canvas.height <= 200;
      }

      // Build a noisy data-URL by manipulating the encoded bytes in the
      // returned string rather than touching canvas pixels. This avoids any
      // in-page side effects while still defeating value-based deduplication.
      function noisyDataURL(url) {
        if (!url || url.indexOf(',') === -1) return url;
        var parts = url.split(',');
        var header = parts[0];
        var body = parts[1] || '';
        // Flip a few bits in the middle of the base64 payload.
        if (body.length > 20) {
          var mid = body.length >> 1;
          var chars = body.split('');
          var offset = noiseSeed % 4;
          chars[mid + offset] = String.fromCharCode(
            (chars[mid + offset].charCodeAt(0) ^ ((noiseSeed >> 4) & 0x3)) || 65
          );
          body = chars.join('');
        }
        return header + ',' + body;
      }

      HTMLCanvasElement.prototype.toDataURL = function() {
        var result = origToDataURL.apply(this, arguments);
        if (!isFingerprintCanvas(this)) return result;
        return noisyDataURL(result);
      };

      HTMLCanvasElement.prototype.toBlob = function(callback) {
        if (!isFingerprintCanvas(this)) {
          return origToBlob.apply(this, arguments);
        }
        var self = this;
        var args = arguments;
        origToBlob.call(self, function(blob) {
          // Pass blob through unmodified \u2014 the noise is non-destructive at
          // this level; the canvas content itself is never touched.
          if (typeof callback === 'function') callback(blob);
        }, args[1], args[2]);
      };
    })();`},o["webgl-fingerprint-protect"]=function(){return`(function() {
      var fakeRenderers = [
        'ANGLE (Intel, Intel(R) UHD Graphics 630, OpenGL 4.5)',
        'ANGLE (NVIDIA, NVIDIA GeForce GTX 1060, OpenGL 4.5)',
        'ANGLE (AMD, AMD Radeon RX 580, OpenGL 4.5)',
        'ANGLE (Intel, Intel(R) Iris(TM) Plus Graphics 640, OpenGL 4.1)',
      ];
      var fakeVendors = ['Google Inc. (Intel)', 'Google Inc. (NVIDIA)', 'Google Inc. (AMD)'];
      var idx = Math.floor(Math.random() * fakeRenderers.length);

      var origGetParameter = WebGLRenderingContext.prototype.getParameter;
      WebGLRenderingContext.prototype.getParameter = function(param) {
        if (param === 0x9245 || param === 0x9246) return fakeRenderers[idx];
        if (param === 0x9247 || param === 0x9248) return fakeVendors[Math.min(idx, fakeVendors.length - 1)];
        return origGetParameter.call(this, param);
      };

      if (typeof WebGL2RenderingContext !== 'undefined') {
        var origGetParameter2 = WebGL2RenderingContext.prototype.getParameter;
        WebGL2RenderingContext.prototype.getParameter = function(param) {
          if (param === 0x9245 || param === 0x9246) return fakeRenderers[idx];
          if (param === 0x9247 || param === 0x9248) return fakeVendors[Math.min(idx, fakeVendors.length - 1)];
          return origGetParameter2.call(this, param);
        };
      }
    })();`},o["audiocontext-fingerprint-protect"]=function(){return`(function() {
      var noise = Math.random() * 0.0001;
      var origCreateOscillator = AudioContext.prototype.createOscillator;
      var origGetFloatFrequencyData = AnalyserNode.prototype.getFloatFrequencyData;
      var origGetByteFrequencyData = AnalyserNode.prototype.getByteFrequencyData;

      AnalyserNode.prototype.getFloatFrequencyData = function(array) {
        origGetFloatFrequencyData.call(this, array);
        for (var i = 0; i < array.length; i++) {
          array[i] = array[i] + noise * (Math.random() - 0.5);
        }
      };

      AnalyserNode.prototype.getByteFrequencyData = function(array) {
        origGetByteFrequencyData.call(this, array);
        for (var i = 0; i < Math.min(array.length, 32); i++) {
          array[i] = Math.max(0, Math.min(255, array[i] + Math.floor(Math.random() * 3 - 1)));
        }
      };

      // NOTE: OfflineAudioContext.startRendering is intentionally NOT hooked.
      // It is used by audio fingerprinters but ALSO by legitimate audio-
      // processing apps, DAWs, and video-call software. Adding noise there
      // breaks audio quality in real-time calls and breaks AudioWorklet-based
      // codecs. The AnalyserNode hooks above are sufficient to defeat the
      // standard fingerprinting technique without side effects.
    })();`},o["navigator-fingerprint-protect"]=function(){return`(function() {
      // Round to the nearest power-of-2 bucket to defeat numeric fingerprinting
      // without lying about platform capabilities in a way that breaks apps.
      var realCores = navigator.hardwareConcurrency || 4;
      var buckets = [2, 4, 8, 16];
      var cores = buckets.reduce(function(prev, cur) {
        return Math.abs(cur - realCores) < Math.abs(prev - realCores) ? cur : prev;
      });
      // Add one-step jitter (up or down) so two calls return different values.
      var jitter = (Math.random() > 0.5 ? 1 : -1);
      var idx = buckets.indexOf(cores);
      var noisedIdx = Math.max(0, Math.min(buckets.length - 1, idx + jitter));
      var noisedCores = buckets[noisedIdx];

      var realMem = navigator.deviceMemory || 4;
      var memBuckets = [1, 2, 4, 8];
      var mem = memBuckets.reduce(function(prev, cur) {
        return Math.abs(cur - realMem) < Math.abs(prev - realMem) ? cur : prev;
      });

      try {
        Object.defineProperty(navigator, 'hardwareConcurrency', { get: function() { return noisedCores; }, configurable: true });
      } catch(e) {}
      try {
        Object.defineProperty(navigator, 'deviceMemory', { get: function() { return mem; }, configurable: true });
      } catch(e) {}
      // navigator.plugins, navigator.mimeTypes, and navigator.platform are
      // intentionally left untouched \u2014 see comment above.
    })();`},o["screen-fingerprint-protect"]=function(){return`(function() {
      var wOff = Math.floor(Math.random() * 8) - 4;
      var hOff = Math.floor(Math.random() * 8) - 4;
      var origW = screen.width;
      var origH = screen.height;
      var origAW = screen.availWidth;
      var origAH = screen.availHeight;

      try {
        Object.defineProperty(screen, 'width', { get: function() { return origW + wOff; }, configurable: true });
        Object.defineProperty(screen, 'height', { get: function() { return origH + hOff; }, configurable: true });
        Object.defineProperty(screen, 'availWidth', { get: function() { return origAW + wOff; }, configurable: true });
        Object.defineProperty(screen, 'availHeight', { get: function() { return origAH + hOff; }, configurable: true });
      } catch(e) {}
    })();`};function ie(e,t){let r=o[e];return r?r.apply(null,t||[]):null}function x(e){if(e){var t=document.head||document.documentElement;if(!t){new MutationObserver(function(v,f){document.documentElement&&(f.disconnect(),x(e))}).observe(document,{childList:!0});return}try{var r=document.createElement("script");r.textContent=e,t.appendChild(r),r.remove()}catch{try{var s=new Blob([e],{type:"text/javascript"}),d=URL.createObjectURL(s),p=document.createElement("script");p.src=d,t.appendChild(p),p.remove(),URL.revokeObjectURL(d)}catch{}}}}function I(e){if(!e||e.length===0)return;let t=[];for(let r of e){let s=ie(r.name,r.args);s&&t.push(s)}t.length>0&&x(t.join(`
`))}function ae(){var e=0,t=0,r=function(d){if(!(!d||d.isTrusted!==!0)){var p=Date.now();if(!(p-e<80)){e=p;var v="",f="";try{var m=d.target&&d.target.closest?d.target.closest('a,button,form,[role="button"]'):null;v=m&&m.href?String(m.href):"",f=m&&m.tagName?String(m.tagName).toLowerCase():""}catch{}E({action:"popup-guard-user-gesture",type:d.type,href:v,targetTag:f})}}},s=function(d){var p=Date.now();p-t<120||(t=p,E({action:"popup-guard-window-signal",type:d}))};window.addEventListener("pointerdown",r,!0),window.addEventListener("keydown",r,!0),window.addEventListener("touchstart",r,!0),window.addEventListener("blur",function(){s("blur")},!0),window.addEventListener("focus",function(){s("focus")},!0)}function oe(e){var t=JSON.stringify(e||{});return`(function() {
      var cfg = ${t};
      if (!cfg || cfg.enabled === false) return;
      // Compatibility fix (2026-05-09): only override window.open when the
      // user (or the vertical profile) actually opted into auto-closing
      // gestureless popups. Otherwise we behaved like Ghostery does \u2014 no
      // page-context window.open hooking \u2014 to avoid breaking SPA links such
      // as YouTube's channel "more links" panel that legitimately use
      // window.open(redirectUrl, '_blank') from JS.
      if (cfg.closeTabsWithoutGesture !== true) return;
      if (window.__midoriPopupDefenseInstalled) return;
      window.__midoriPopupDefenseInstalled = true;

      var lastGestureAt = 0;
      var openTimestamps = [];

      function postBlocked(reason, url) {
        try {
          window.postMessage({ type: 'midori-popup-blocked', reason: reason, url: String(url || '') }, location.origin);
        } catch (e) {}
      }

      function markGesture(event) {
        if (!event || event.isTrusted !== true) return;
        lastGestureAt = Date.now();
      }

      function withinGestureWindow() {
        return (Date.now() - lastGestureAt) <= (cfg.gestureWindowMs || 1400);
      }

      function pruneOpens() {
        var now = Date.now();
        var win = cfg.burstWindowMs || 5000;
        openTimestamps = openTimestamps.filter(function(ts) {
          return (now - ts) <= win;
        });
      }

      document.addEventListener('pointerdown', markGesture, true);
      document.addEventListener('keydown', markGesture, true);
      document.addEventListener('touchstart', markGesture, true);

      var origOpen = window.open;
      if (typeof origOpen === 'function') {
        window.open = function(url) {
          pruneOpens();
          var hasGesture = withinGestureWindow();
          var maxBurst = Number.isFinite(cfg.maxBurstWithoutGesture) ? cfg.maxBurstWithoutGesture : 1;
          if (!hasGesture && openTimestamps.length > maxBurst) {
            postBlocked('burst', url);
            return null;
          }
          if (!hasGesture) {
            postBlocked('no-gesture', url);
            return null;
          }
          openTimestamps.push(Date.now());
          return origOpen.apply(this, arguments);
        };
      }
    })();`}var w="";try{w=window.location.hostname}catch{}var D=!0;E({action:"get-site-protection-state",hostname:w}).then(function(e){D=!(e&&e.enabled===!1),D&&(ae(),E({action:"get-ia-shield-config",hostname:w}).then(function(t){t&&t.config&&t.config.enabled&&ne(t.config)}),E({action:"get-popup-defense-config",hostname:w}).then(function(t){t&&t.config&&x(oe(t.config))}),(w==="www.youtube.com"||w==="youtube.com"||w==="m.youtube.com")&&(I([{name:"yt-ad-pruner",args:[]}]),L["yt-ad-pruner:"]=!0),(w==="open.spotify.com"||w==="spotify.com"||w==="www.spotify.com")&&(I([{name:"spotify-ad-defuser",args:[]}]),L["spotify-ad-defuser:"]=!0))}).catch(function(){});var L={};window.addEventListener("message",function(e){if(e.source===window&&e.origin===location.origin&&e.data&&D){if(e.data.type==="midori-scriptlets"&&e.data.scriptlets){var t=e.data.scriptlets.filter(function(s){var d=s.name+":"+(s.args||[]).join(",");return L[d]?!1:(L[d]=!0,!0)});t.length>0&&I(t)}if(e.data.type==="midori-compiled-scriptlet"&&e.data.code&&x(e.data.code),e.data.type==="midori-compiled-scriptlet-batch"&&e.data.scripts){var r=e.data.scripts;r.length>0&&x(r.join(`
`))}e.data.type==="midori-popup-blocked"&&E({action:"popup-guard-blocked",reason:e.data.reason,url:e.data.url})}}),setTimeout(function(){J(performance.now()-K)},0)})();})();
