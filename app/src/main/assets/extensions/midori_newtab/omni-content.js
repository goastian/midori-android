// Midori Tab — Content Script (trigger)
// Lightweight script injected into all pages. Loads the full launcher UI on demand.
'use strict';

(function () {
  if (window.__midoriOmniLoaded) return;
  window.__midoriOmniLoaded = true;

  const OMNI_SHARED_CONFIG_FILE = 'omni-ui.shared.json';
  const OMNI_FALLBACK_COPY = {
    label: 'Command Menu',
    placeholder: 'Type a command or search…',
    resultsLabel: 'Search results',
    results: 'results',
    navigate: 'navigate',
    select: 'select',
    openInNewTab: 'new tab',
    dismiss: 'dismiss',
    noResults: 'No results found',
  };
  const OMNI_FALLBACK_DENSITY = {
    dialogMaxHeight: 560,
    listMaxHeight: 420,
    searchRowHeight: 52,
    searchRowPaddingX: 16,
    itemPaddingX: 16,
    itemPaddingY: 9,
    footerPaddingX: 16,
    footerPaddingY: 8,
  };

  let sharedOmniDefaultsPromise = null;

  let overlay = null;
  let isOpen = false;
  let omniConfig = {
    maxResults: 100,
    copy: {
      dialogLabel: 'Midori Omni',
      placeholder: 'Type a command or search…',
      noResults: 'No results',
      resultsLabel: 'Search results',
      resultsWord: 'results',
      hintNavigateLabel: 'navigate',
      hintSelectLabel: 'select',
      hintOpenInNewTabLabel: 'open in new tab',
      hintDismissLabel: 'dismiss',
    },
    hints: {
      navigate: '↑↓',
      select: '↵',
      openInNewTab: 'Ctrl+↵',
      dismiss: 'Esc',
    },
    density: {
      dialogMaxHeight: 560,
      listMaxHeight: 420,
      searchRowHeight: 52,
      searchRowPaddingX: 16,
      itemPaddingX: 16,
      itemPaddingY: 9,
      footerPaddingX: 16,
      footerPaddingY: 8,
    },
  };

  function normalizeLocale(code) {
    if (!code) return '';
    return String(code).trim().toLowerCase().split('-')[0];
  }

  function resolveDensityVariant() {
    return window.innerHeight < 760 || window.innerWidth < 1024 ? 'compact' : 'cozy';
  }

  function extractSharedDefaults(payload) {
    const locale = normalizeLocale(navigator.language) || 'en';
    const copyByLocale = payload?.copyByLocale || {};
    const densityPresets = payload?.densityPresets || {};
    const densityVariant = resolveDensityVariant();

    return {
      copy: copyByLocale[locale] || copyByLocale.en || OMNI_FALLBACK_COPY,
      density: densityPresets[densityVariant] || densityPresets.cozy || OMNI_FALLBACK_DENSITY,
    };
  }

  async function loadSharedOmniDefaults() {
    if (!sharedOmniDefaultsPromise) {
      sharedOmniDefaultsPromise = (async () => {
        try {
          const url = chrome.runtime.getURL(OMNI_SHARED_CONFIG_FILE);
          const response = await fetch(url, { cache: 'no-cache' });
          if (!response.ok) throw new Error('Unable to fetch shared Omni config');
          const payload = await response.json();
          return extractSharedDefaults(payload);
        } catch (_e) {
          return {
            copy: OMNI_FALLBACK_COPY,
            density: OMNI_FALLBACK_DENSITY,
          };
        }
      })();
    }

    const defaults = await sharedOmniDefaultsPromise;
    if (defaults?.copy) {
      omniConfig.copy = {
        ...omniConfig.copy,
        dialogLabel: defaults.copy.label || omniConfig.copy.dialogLabel,
        placeholder: defaults.copy.placeholder || omniConfig.copy.placeholder,
        noResults: defaults.copy.noResults || omniConfig.copy.noResults,
        resultsLabel: defaults.copy.resultsLabel || omniConfig.copy.resultsLabel,
        resultsWord: defaults.copy.results || omniConfig.copy.resultsWord,
        hintNavigateLabel: defaults.copy.navigate || omniConfig.copy.hintNavigateLabel,
        hintSelectLabel: defaults.copy.select || omniConfig.copy.hintSelectLabel,
        hintOpenInNewTabLabel: defaults.copy.openInNewTab || omniConfig.copy.hintOpenInNewTabLabel,
        hintDismissLabel: defaults.copy.dismiss || omniConfig.copy.hintDismissLabel,
      };
    }

    if (defaults?.density) {
      omniConfig.density = {
        ...omniConfig.density,
        ...defaults.density,
      };
    }
  }

  // ── Escape listener for pages with aggressive Esc capture ──────────────
  document.addEventListener('keyup', (e) => {
    if (e.key === 'Escape' && isOpen) {
      closeOverlay();
    }
  }, { capture: true });

  // ── Message listener ────────────────────────────────────────────────────
  chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
    if (message.request === 'omni-handshake') {
      sendResponse({ ok: true });
      return;
    }

    if (message.request === 'open-omni') {
      if (isOpen) {
        closeOverlay();
      } else {
        void openOverlay();
      }
    }

    return undefined;
  });

  // ── Build overlay ────────────────────────────────────────────────────────
  async function openOverlay() {
    await loadSharedOmniDefaults();

    if (!overlay) {
      overlay = buildOverlay();
      document.body.appendChild(overlay);
    }

    applyOmniCopy();
    applyOmniDensity();
    updateHintsText();

    loadOmniConfig();
    isOpen = true;
    overlay.style.display = '';
    overlay.classList.remove('omni-cs-closing');
    requestAnimationFrame(() => {
      const input = overlay.querySelector('.omni-cs-input');
      input && input.focus();
    });
    executeSearch('');
  }

  function closeOverlay() {
    if (!overlay) return;
    isOpen = false;
    overlay.classList.add('omni-cs-closing');
    setTimeout(() => {
      if (overlay) overlay.style.display = 'none';
    }, 200);
  }

  // ── Build DOM ────────────────────────────────────────────────────────────
  function buildOverlay() {
    const host = document.createElement('div');
    host.id = 'midori-omni-cs';
    host.innerHTML = `
      <div class="omni-cs-backdrop" id="omni-cs-backdrop"></div>
      <div class="omni-cs-dialog" role="dialog" aria-modal="true" aria-label="Midori Omni">
        <div class="omni-cs-search-row">
          <span class="omni-cs-search-icon" aria-hidden="true">🔍</span>
          <input
            class="omni-cs-input"
            type="text"
            placeholder="Type a command or search…"
            autocomplete="off"
            spellcheck="false"
          />
        </div>
        <ul class="omni-cs-list" id="omni-cs-list" role="listbox"></ul>
        <div class="omni-cs-footer" aria-hidden="true">
          <span class="omni-cs-count" id="omni-cs-count">0 results</span>
          <span class="omni-cs-hint">↑↓ navigate · ↵ select · Esc dismiss</span>
        </div>
      </div>
    `;

    // Inject styles
    const style = document.createElement('style');
    style.textContent = getStyles();
    host.prepend(style);

    // Events
    host.querySelector('#omni-cs-backdrop').addEventListener('click', closeOverlay);
    const input = host.querySelector('.omni-cs-input');
    input.addEventListener('input', onInput);
    input.addEventListener('keydown', onKeydown);
    host.querySelector('.omni-cs-dialog').addEventListener('click', (e) => e.stopPropagation());

    return host;
  }

  function updateHintsText() {
    if (!overlay) return;
    const hintEl = overlay.querySelector('.omni-cs-hint');
    if (!hintEl) return;

    const copy = omniConfig.copy || {};
    const hints = omniConfig.hints || {};
    const navigate = hints.navigate || '↑↓';
    const select = hints.select || '↵';
    const openInNewTab = hints.openInNewTab || 'Ctrl+↵';
    const dismiss = hints.dismiss || 'Esc';
    const navigateLabel = copy.hintNavigateLabel || 'navigate';
    const selectLabel = copy.hintSelectLabel || 'select';
    const openInNewTabLabel = copy.hintOpenInNewTabLabel || 'open in new tab';
    const dismissLabel = copy.hintDismissLabel || 'dismiss';

    hintEl.textContent = `${navigate} ${navigateLabel} · ${select} ${selectLabel} · ${openInNewTab} ${openInNewTabLabel} · ${dismiss} ${dismissLabel}`;
  }

  function applyOmniCopy() {
    if (!overlay) return;
    const copy = omniConfig.copy || {};

    const dialog = overlay.querySelector('.omni-cs-dialog');
    if (dialog) {
      dialog.setAttribute('aria-label', copy.dialogLabel || 'Midori Omni');
    }

    const input = overlay.querySelector('.omni-cs-input');
    if (input) {
      input.setAttribute('placeholder', copy.placeholder || 'Type a command or search…');
    }
  }

  function applyOmniDensity() {
    if (!overlay) return;
    const density = omniConfig.density || {};

    if (Number.isFinite(density.dialogMaxHeight)) {
      overlay.style.setProperty('--omni-dialog-max-height', `${density.dialogMaxHeight}px`);
    }
    if (Number.isFinite(density.listMaxHeight)) {
      overlay.style.setProperty('--omni-list-max-height', `${density.listMaxHeight}px`);
    }
    if (Number.isFinite(density.searchRowHeight)) {
      overlay.style.setProperty('--omni-search-row-height', `${density.searchRowHeight}px`);
    }
    if (Number.isFinite(density.searchRowPaddingX)) {
      overlay.style.setProperty('--omni-search-row-padding-x', `${density.searchRowPaddingX}px`);
    }
    if (Number.isFinite(density.itemPaddingX)) {
      overlay.style.setProperty('--omni-item-padding-x', `${density.itemPaddingX}px`);
    }
    if (Number.isFinite(density.itemPaddingY)) {
      overlay.style.setProperty('--omni-item-padding-y', `${density.itemPaddingY}px`);
    }
    if (Number.isFinite(density.footerPaddingX)) {
      overlay.style.setProperty('--omni-footer-padding-x', `${density.footerPaddingX}px`);
    }
    if (Number.isFinite(density.footerPaddingY)) {
      overlay.style.setProperty('--omni-footer-padding-y', `${density.footerPaddingY}px`);
    }
  }

  function loadOmniConfig() {
    chrome.runtime.sendMessage({
      request: 'get-omni-config',
      locale: navigator.language,
      viewport: {
        width: window.innerWidth,
        height: window.innerHeight,
      },
      densityVariant: resolveDensityVariant(),
    }, (config) => {
      if (chrome.runtime.lastError || !config) return;

      if (Number.isFinite(config.maxResults) && config.maxResults > 0) {
        omniConfig.maxResults = config.maxResults;
      }
      if (config.copy && typeof config.copy === 'object') {
        omniConfig.copy = { ...omniConfig.copy, ...config.copy };
      }
      if (config.hints && typeof config.hints === 'object') {
        omniConfig.hints = { ...omniConfig.hints, ...config.hints };
      }
      if (config.density && typeof config.density === 'object') {
        omniConfig.density = { ...omniConfig.density, ...config.density };
      }

      applyOmniCopy();
      applyOmniDensity();
      updateHintsText();
    });
  }

  // ── State ─────────────────────────────────────────────────────────────────
  let visibleItems = [];
  let selectedIdx = 0;
  let debounceTimer = null;

  function onInput(e) {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => executeSearch(e.target.value), 250);
  }

  function onKeydown(e) {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      moveSelection(1);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      moveSelection(-1);
    } else if (e.key === 'Escape') {
      closeOverlay();
    } else if (e.key === 'Enter') {
      e.preventDefault();
      const item = visibleItems[selectedIdx];
      if (item) handleAction(item, e.ctrlKey || e.metaKey);
    }
  }

  function moveSelection(delta) {
    const next = selectedIdx + delta;
    if (next >= 0 && next < visibleItems.length) {
      selectedIdx = next;
      highlightSelected();
      const el = overlay.querySelector(`[data-idx="${selectedIdx}"]`);
      el && el.scrollIntoView({ block: 'nearest' });
    }
  }

  function highlightSelected() {
    overlay.querySelectorAll('.omni-cs-item').forEach((el, i) => {
      el.classList.toggle('omni-cs-item--selected', i === selectedIdx);
      el.setAttribute('aria-selected', String(i === selectedIdx));
    });
  }

  // ── Search ────────────────────────────────────────────────────────────────
  function executeSearch(rawQuery) {
    chrome.runtime.sendMessage({ request: 'query-omni', query: rawQuery }, (response) => {
      if (chrome.runtime.lastError) {
        renderResults([]);
        return;
      }
      renderResults(response?.results ?? []);
    });
  }

  // ── Render ─────────────────────────────────────────────────────────────────
  function renderResults(items) {
    visibleItems = items;
    visibleItems = visibleItems.slice(0, omniConfig.maxResults);
    selectedIdx = 0;

    const list = overlay.querySelector('#omni-cs-list');
    const count = overlay.querySelector('#omni-cs-count');
    if (!list) return;

    list.innerHTML = '';
    if (!visibleItems.length) {
      const empty = document.createElement('li');
      empty.className = 'omni-cs-empty';
      empty.setAttribute('role', 'status');
      empty.setAttribute('aria-live', 'polite');
      empty.textContent = omniConfig.copy?.noResults || 'No results';
      list.appendChild(empty);
    }

    visibleItems.forEach((item, idx) => {
      const li = document.createElement('li');
      li.className = 'omni-cs-item' + (idx === 0 ? ' omni-cs-item--selected' : '');
      li.setAttribute('role', 'option');
      li.setAttribute('aria-selected', String(idx === 0));
      li.setAttribute('data-idx', idx);

      const icon = item.emoji
        ? `<span class="omni-cs-icon" aria-hidden="true">${item.emojiChar || '📄'}</span>`
        : `<img class="omni-cs-icon omni-cs-icon--img" src="${item.favIconUrl || ''}" alt="" aria-hidden="true" onerror="this.src='${chrome.runtime.getURL('globe.svg')}'" />`;

      const keys = item.keycheck && item.keys
        ? `<div class="omni-cs-keys" aria-hidden="true">${item.keys.map((k) => `<kbd class="omni-cs-key">${k}</kbd>`).join('')}</div>`
        : '';

      li.innerHTML = `${icon}<div class="omni-cs-body"><div class="omni-cs-title">${escapeHtml(item.title || '')}</div><div class="omni-cs-desc">${escapeHtml(item.desc || item.url || '')}</div></div>${keys}`;
      li.addEventListener('click', () => handleAction(item, false));
      li.addEventListener('pointerenter', () => { selectedIdx = idx; highlightSelected(); });
      list.appendChild(li);
    });

    if (count) {
      const resultsWord = omniConfig.copy?.resultsWord || 'results';
      count.textContent = `${visibleItems.length} ${resultsWord}`;
    }
  }

  function escapeHtml(str) {
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  // ── Action handler ─────────────────────────────────────────────────────────
  function handleAction(item, newTab) {
    const input = overlay ? overlay.querySelector('.omni-cs-input') : null;
    const query = input ? input.value : '';
    const isRemoveMode = query.toLowerCase().startsWith('/remove');
    const confirmed = confirmDestructiveAction(item, isRemoveMode);
    if (confirmed === false) return;

    closeOverlay();

    chrome.runtime.sendMessage(
      {
        request: 'execute-omni-item',
        item,
        query,
        newTab,
        removeMode: isRemoveMode,
        confirmed,
      },
      (response) => {
        if (chrome.runtime.lastError) return;
        if (response?.requiresConfirmation) return;

        switch (response?.localAction) {
          case 'print':
            window.print();
            break;
          case 'fullscreen':
            document.documentElement.requestFullscreen?.();
            break;
          case 'scroll-top':
            window.scrollTo(0, 0);
            break;
          case 'scroll-bottom':
            window.scrollTo(0, document.body.scrollHeight);
            break;
          default:
            break;
        }
      }
    );
  }

  function confirmDestructiveAction(item, removeMode) {
    const destructiveActions = new Set([
      'close-tab',
      'close-window',
      'remove-all',
      'remove-history',
      'remove-cookies',
      'remove-cache',
      'remove-local-storage',
      'remove-passwords',
    ]);

    if (!removeMode && !destructiveActions.has(item?.action)) {
      return undefined;
    }

    const label = item?.title || item?.desc || item?.action || 'this action';
    return window.confirm(`Confirm ${label}?`);
  }

  // ── Styles ─────────────────────────────────────────────────────────────────
  function getStyles() {
    return `
      #midori-omni-cs { all: initial; }
      #midori-omni-cs *, #midori-omni-cs *::before, #midori-omni-cs *::after { box-sizing: border-box; }

      @media (prefers-color-scheme: dark) {
        #midori-omni-cs {
          --omni-bg: #1e2128; --omni-bg2: #292d36; --omni-border: #35373e;
          --omni-text: #f1f1f1; --omni-text2: #c5c6ca; --omni-text3: #a5a5ae;
          --omni-select: #17191e; --omni-accent: #6068d2; --omni-kbd: #383e4a;
          --omni-placeholder: #63687b;
        }
      }
      @media (prefers-color-scheme: light) {
        #midori-omni-cs {
          --omni-bg: #fafcff; --omni-bg2: #f4f6fb; --omni-border: #e8eaf2;
          --omni-text: #2b2d41; --omni-text2: #555770; --omni-text3: #929db2;
          --omni-select: #eff3f9; --omni-accent: #6068d2; --omni-kbd: #dadeea;
          --omni-placeholder: #bac2d1;
        }
      }

      .omni-cs-backdrop {
        position: fixed; inset: 0; background: rgba(0,0,0,.55);
        z-index: 2147483645;
        transition: opacity .15s ease;
      }
      .omni-cs-closing .omni-cs-backdrop { opacity: 0; }

      .omni-cs-dialog {
        position: fixed; inset: 0; margin: auto;
        width: min(700px, 96vw); height: fit-content;
        max-height: var(--omni-dialog-max-height, 560px);
        background: var(--omni-bg);
        border: 1px solid var(--omni-border);
        border-radius: 10px;
        z-index: 2147483646;
        overflow: hidden;
        display: flex; flex-direction: column;
        box-shadow: 0 24px 64px rgba(0,0,0,.35);
        font-family: system-ui, -apple-system, sans-serif;
        transition: transform .18s cubic-bezier(.05,.03,.35,1), opacity .18s ease;
      }
      .omni-cs-closing .omni-cs-dialog { transform: scale(.93); opacity: 0; }

      .omni-cs-search-row {
        display: flex; align-items: center;
        padding: 0 var(--omni-search-row-padding-x, 16px);
        border-bottom: 1px solid var(--omni-border);
        flex-shrink: 0;
      }
      .omni-cs-search-icon { font-size: 18px; margin-right: 8px; }
      .omni-cs-input {
        all: unset; flex: 1;
        height: var(--omni-search-row-height, 52px); font-size: 18px; font-weight: 400;
        color: var(--omni-text);
        caret-color: var(--omni-accent);
      }
      .omni-cs-input::placeholder { color: var(--omni-placeholder); }

      .omni-cs-list {
        list-style: none; margin: 0; padding: .25rem 0;
        overflow-y: auto; flex: 1;
        max-height: var(--omni-list-max-height, 420px);
      }
      .omni-cs-list::-webkit-scrollbar { width: 6px; }
      .omni-cs-list::-webkit-scrollbar-thumb { background: rgba(127,127,127,.4); border-radius: 3px; }

      .omni-cs-item {
        display: flex; align-items: center; gap: 12px;
        padding: var(--omni-item-padding-y, 9px) var(--omni-item-padding-x, 16px); cursor: pointer;
        transition: background .1s;
      }
      .omni-cs-item:hover, .omni-cs-item--selected {
        background: var(--omni-select);
      }

      .omni-cs-icon {
        width: 24px; height: 24px; font-size: 19px;
        display: flex; align-items: center; justify-content: center;
        flex-shrink: 0; border-radius: 4px;
      }
      .omni-cs-icon--img { object-fit: contain; }

      .omni-cs-body { flex: 1; min-width: 0; }
      .omni-cs-title {
        font-size: 14px; font-weight: 500;
        color: var(--omni-text); white-space: nowrap;
        overflow: hidden; text-overflow: ellipsis;
      }
      .omni-cs-desc {
        font-size: 12px; color: var(--omni-text3);
        white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
      }

      .omni-cs-keys { display: flex; gap: .2rem; flex-shrink: 0; }
      .omni-cs-key {
        display: inline-flex; align-items: center; justify-content: center;
        font-size: 11px; min-width: 20px; height: 20px; padding: 0 4px;
        background: var(--omni-kbd); border-radius: 4px;
        color: var(--omni-text); font-family: inherit;
      }

      .omni-cs-footer {
        display: flex; align-items: center; justify-content: space-between;
        padding: var(--omni-footer-padding-y, 8px) var(--omni-footer-padding-x, 16px);
        border-top: 1px solid var(--omni-border);
        flex-shrink: 0;
      }
      .omni-cs-count { font-size: 12px; color: var(--omni-text3); }
      .omni-cs-hint { font-size: 11px; color: var(--omni-text3); }
      .omni-cs-empty { padding: 12px 16px; list-style: none; color: var(--omni-text3); text-align: center; font-size: 13px; }

      @media (prefers-reduced-motion: reduce) {
        .omni-cs-dialog, .omni-cs-backdrop { transition: none !important; }
      }
    `;
  }
})();
