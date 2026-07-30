// Midori Tab — Background Service Worker
// Replaces the original Omni background.js with a differential cache approach.
// No jQuery, no global resets on every tab event.

'use strict';

const OMNI_CONTENT_SCRIPT_FILES = ['omni-content.js'];
const OMNI_QUERY_TTL = 5_000;
const OMNI_QUERY_CACHE_MAX = 50;
const OMNI_MAX_RESULTS = 100;
const OMNI_LOCALE_STORAGE_KEY = 'midori-locale';
const OMNI_STATIC_ACTIONS_FILE = 'omni-static-actions.json';
const omniQueryCache = new Map();
const IS_ANDROID_RUNTIME = /android/i.test(globalThis.navigator?.userAgent || '');
const ANDROID_UNSUPPORTED_ACTIONS = new Set([
  'create-bookmark',
  'close-window',
  'incognito',
  'history',
  'downloads',
  'extensions',
  'settings',
  'manage-data',
]);

const OMNI_SHARED_CONFIG_FILE = 'omni-ui.shared.json';

const OMNI_SHARED_FALLBACK = {
  copyByLocale: {
    en: {
      label: 'Command Menu',
      placeholder: 'Type a command or search…',
      resultsLabel: 'Search results',
      results: 'results',
      navigate: 'navigate',
      select: 'select',
      openInNewTab: 'new tab',
      dismiss: 'dismiss',
      noResults: 'No results found',
    },
  },
  densityPresets: {
    cozy: {
      dialogMaxHeight: 560,
      listMaxHeight: 420,
      searchRowHeight: 52,
      searchRowPaddingX: 16,
      itemPaddingX: 16,
      itemPaddingY: 9,
      footerPaddingX: 16,
      footerPaddingY: 8,
    },
    compact: {
      dialogMaxHeight: 470,
      listMaxHeight: 340,
      searchRowHeight: 44,
      searchRowPaddingX: 12,
      itemPaddingX: 12,
      itemPaddingY: 7,
      footerPaddingX: 12,
      footerPaddingY: 6,
    },
  },
};

let omniSharedConfigPromise = null;

function callChrome(method, ...args) {
  return new Promise((resolve, reject) => {
    try {
      const callback = (result) => {
        const lastError = chrome.runtime?.lastError;
        if (lastError) {
          reject(new Error(lastError.message));
          return;
        }
        resolve(result);
      };

      const maybePromise = method(...args, callback);
      if (maybePromise && typeof maybePromise.then === 'function') {
        maybePromise.then(resolve, reject);
      }
    } catch (error) {
      reject(error);
    }
  });
}

function safeCallChrome(method, ...args) {
  return callChrome(method, ...args).catch(() => undefined);
}

function normalizeLocale(code) {
  if (!code) return '';
  return String(code).trim().toLowerCase().split('-')[0];
}

async function getOmniSharedConfig() {
  if (!omniSharedConfigPromise) {
    omniSharedConfigPromise = (async () => {
      try {
        const url = chrome.runtime.getURL(OMNI_SHARED_CONFIG_FILE);
        const response = await fetch(url, { cache: 'no-cache' });
        if (!response.ok) throw new Error(`Unable to fetch ${OMNI_SHARED_CONFIG_FILE}`);
        const payload = await response.json();
        if (!payload || typeof payload !== 'object') {
          return OMNI_SHARED_FALLBACK;
        }

        return {
          copyByLocale: payload.copyByLocale || OMNI_SHARED_FALLBACK.copyByLocale,
          densityPresets: payload.densityPresets || OMNI_SHARED_FALLBACK.densityPresets,
        };
      } catch (_e) {
        return OMNI_SHARED_FALLBACK;
      }
    })();
  }

  return omniSharedConfigPromise;
}

async function getStoredLocale() {
  if (!chrome.storage?.local?.get) return '';
  const data = await safeCallChrome(
    chrome.storage.local.get.bind(chrome.storage.local),
    [OMNI_LOCALE_STORAGE_KEY]
  );

  return normalizeLocale(data?.[OMNI_LOCALE_STORAGE_KEY]);
}

function pruneOmniQueryCache(now = Date.now()) {
  for (const [key, entry] of omniQueryCache) {
    if (!entry || now - entry.ts >= OMNI_QUERY_TTL) {
      omniQueryCache.delete(key);
    }
  }

  while (omniQueryCache.size > OMNI_QUERY_CACHE_MAX) {
    const oldestKey = omniQueryCache.keys().next().value;
    if (oldestKey === undefined) break;
    omniQueryCache.delete(oldestKey);
  }
}

function clearOmniQueryCache(dependency = null) {
  if (!dependency) {
    omniQueryCache.clear();
    return;
  }

  for (const [key, entry] of omniQueryCache) {
    if (entry?.deps?.includes(dependency)) {
      omniQueryCache.delete(key);
    }
  }
}

function isRestrictedOmniUrl(url) {
  return (
    !url ||
    url.startsWith('chrome://') ||
    url.startsWith('about:') ||
    url.startsWith('chrome-extension://') ||
    url.includes('chrome.google.com')
  );
}

function isFirefoxNewTabOverrideUrl(url) {
  return url === 'about:newtab' || url === 'about:home';
}

async function ensureOmniContentScript(tabId) {
  const ready = await safeCallChrome(
    chrome.tabs.sendMessage.bind(chrome.tabs),
    tabId,
    { request: 'omni-handshake' }
  );

  if (ready?.ok) {
    return true;
  }

  if (chrome.scripting?.executeScript) {
    await callChrome(
      chrome.scripting.executeScript.bind(chrome.scripting),
      {
        target: { tabId },
        files: OMNI_CONTENT_SCRIPT_FILES,
      }
    );
    return true;
  }

  if (chrome.tabs?.executeScript) {
    await callChrome(
      chrome.tabs.executeScript.bind(chrome.tabs),
      tabId,
      {
        file: OMNI_CONTENT_SCRIPT_FILES[0],
        runAt: 'document_idle',
      }
    );
    return true;
  }

  return false;
}

// ─── Differential Tabs Cache ───────────────────────────────────────────────
const tabsCache = new Map(); // tabId → normalised tab object
let tabsCacheReady = false;
let tabsPrunePromise = null;
const TABS_CACHE_MAX = 500;

function normaliseTab(tab) {
  return {
    id: tab.id,
    index: tab.index,
    windowId: tab.windowId,
    title: tab.title || '',
    url: tab.url || '',
    favIconUrl: tab.favIconUrl || '',
    mutedInfo: tab.mutedInfo || { muted: false },
    pinned: !!tab.pinned,
    type: 'tab',
    action: 'switch-tab',
    desc: 'Open tab',
    emoji: false,
    keycheck: false,
  };
}

async function ensureTabsCache() {
  if (tabsCacheReady) return;
  const tabs = await callChrome(chrome.tabs.query.bind(chrome.tabs), {});
  for (const tab of tabs || []) {
    if (tab?.id) {
      tabsCache.delete(tab.id);
      tabsCache.set(tab.id, normaliseTab(tab));
    }
  }
  tabsCacheReady = true;
  await pruneTabsCache();
}

function rememberTab(tab) {
  if (!tab?.id) return;
  tabsCache.delete(tab.id);
  tabsCache.set(tab.id, normaliseTab(tab));
  void pruneTabsCache();
}

async function pruneTabsCache() {
  if (tabsCache.size <= TABS_CACHE_MAX) return;
  if (tabsPrunePromise) return tabsPrunePromise;

  tabsPrunePromise = (async () => {
    const currentWindowTabs = await safeCallChrome(chrome.tabs.query.bind(chrome.tabs), { currentWindow: true });
    const keepIds = new Set((currentWindowTabs || []).map((tab) => tab.id).filter(Boolean));
    const recentEntries = Array.from(tabsCache.entries()).reverse();
    const nextCache = new Map();

    for (const [tabId, tab] of recentEntries) {
      if (nextCache.size >= TABS_CACHE_MAX) break;
      if (keepIds.has(tabId)) {
        nextCache.set(tabId, tab);
      }
    }

    for (const [tabId, tab] of recentEntries) {
      if (nextCache.size >= TABS_CACHE_MAX) break;
      if (!nextCache.has(tabId)) {
        nextCache.set(tabId, tab);
      }
    }

    tabsCache.clear();
    for (const [tabId, tab] of Array.from(nextCache.entries()).reverse()) {
      tabsCache.set(tabId, tab);
    }
  })();

  try {
    await tabsPrunePromise;
  } finally {
    tabsPrunePromise = null;
  }
}

chrome.tabs.onCreated.addListener((tab) => {
  rememberTab(tab);
  clearOmniQueryCache('tabs');
});

chrome.tabs.onRemoved.addListener((tabId) => {
  tabsCache.delete(tabId);
  clearOmniQueryCache('tabs');
  clearOmniQueryCache('active-tab');
});

chrome.tabs.onReplaced.addListener((addedTabId, removedTabId) => {
  tabsCache.delete(removedTabId);
  clearOmniQueryCache('tabs');
});

chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  if (tabsCache.has(tabId)) {
    const existing = tabsCache.get(tabId);
    tabsCache.delete(tabId);
    tabsCache.set(tabId, {
      ...existing,
      title: tab.title ?? existing.title,
      url: tab.url ?? existing.url,
      favIconUrl: tab.favIconUrl ?? existing.favIconUrl,
      mutedInfo: tab.mutedInfo ?? existing.mutedInfo,
      pinned: tab.pinned !== undefined ? tab.pinned : existing.pinned,
    });
  } else {
    // Tab appeared without onCreated (e.g. restored session)
    rememberTab(tab);
  }
  clearOmniQueryCache('tabs');
  if (tab.active) {
    clearOmniQueryCache('active-tab');
  }
});

chrome.tabs.onActivated.addListener(() => {
  clearOmniQueryCache('active-tab');
});

chrome.windows?.onFocusChanged?.addListener(() => {
  clearOmniQueryCache('active-tab');
});

// ─── Bookmarks Cache ────────────────────────────────────────────────────────
let bookmarksCache = null;
let bookmarksInvalidateTimer = null;

function invalidateBookmarks() {
  clearTimeout(bookmarksInvalidateTimer);
  bookmarksInvalidateTimer = setTimeout(() => {
    bookmarksCache = null;
    clearOmniQueryCache('bookmarks');
  }, 1000);
}

[chrome.bookmarks?.onCreated, chrome.bookmarks?.onRemoved, chrome.bookmarks?.onChanged, chrome.bookmarks?.onMoved]
  .filter(Boolean)
  .forEach((event) => event.addListener(invalidateBookmarks));

async function getBookmarks() {
  if (!chrome.bookmarks?.getRecent) return [];
  if (bookmarksCache) return bookmarksCache;
  const recent = await callChrome(chrome.bookmarks.getRecent.bind(chrome.bookmarks), 100);
  bookmarksCache = (recent || [])
    .filter((b) => Boolean(b.url))
    .map((b) => ({
      id: b.id,
      title: b.title || b.url,
      desc: 'Bookmark',
      url: b.url,
      type: 'bookmark',
      action: 'bookmark',
      emoji: true,
      emojiChar: '⭐️',
      keycheck: false,
    }));
  return bookmarksCache;
}

// ─── Static Actions ─────────────────────────────────────────────────────────
let staticActions = null;
let staticActionsPromise = null;
let platformOs = null;

async function getPlatformOs() {
  if (!platformOs) {
    const info = await callChrome(chrome.runtime.getPlatformInfo.bind(chrome.runtime));
    platformOs = info?.os || 'linux';
  }
  return platformOs;
}

function resolveDensityVariant(message) {
  const requested = message?.densityVariant;
  if (requested === 'compact' || requested === 'cozy') {
    return requested;
  }

  const viewport = message?.viewport;
  const width = Number(viewport?.width) || 0;
  const height = Number(viewport?.height) || 0;

  if ((width > 0 && width < 1024) || (height > 0 && height < 760)) {
    return 'compact';
  }

  return 'cozy';
}

function buildLocalizedCopy(locale, copyByLocale) {
  const copy = copyByLocale[locale] || copyByLocale.en || OMNI_SHARED_FALLBACK.copyByLocale.en;
  return {
    dialogLabel: copy.label,
    placeholder: copy.placeholder,
    noResults: copy.noResults,
    resultsLabel: copy.resultsLabel,
    resultsWord: copy.results,
    hintNavigateLabel: copy.navigate,
    hintSelectLabel: copy.select,
    hintOpenInNewTabLabel: copy.openInNewTab,
    hintDismissLabel: copy.dismiss,
  };
}

async function getOmniUiConfig(message = {}) {
  const os = await getPlatformOs();
  const openInNewTab = os === 'mac' ? '⌘+↵' : 'Ctrl+↵';
  const sharedConfig = await getOmniSharedConfig();
  const requestedLocale = normalizeLocale(message.locale);
  const storedLocale = await getStoredLocale();
  const uiLocale = normalizeLocale(chrome.i18n?.getUILanguage?.() || '');
  const locale = requestedLocale || storedLocale || uiLocale || 'en';
  const densityVariant = resolveDensityVariant(message);
  const densityPresets = sharedConfig.densityPresets || OMNI_SHARED_FALLBACK.densityPresets;
  const density = densityPresets[densityVariant] || densityPresets.cozy || OMNI_SHARED_FALLBACK.densityPresets.cozy;

  return {
    maxResults: OMNI_MAX_RESULTS,
    locale,
    densityVariant,
    copy: buildLocalizedCopy(locale, sharedConfig.copyByLocale || OMNI_SHARED_FALLBACK.copyByLocale),
    hints: {
      navigate: '↑↓',
      select: '↵',
      openInNewTab,
      dismiss: 'Esc',
    },
    density,
  };
}

async function loadStaticActionTemplates() {
  const response = await fetch(chrome.runtime.getURL(OMNI_STATIC_ACTIONS_FILE), { cache: 'no-cache' });
  if (!response.ok) throw new Error(`Unable to fetch ${OMNI_STATIC_ACTIONS_FILE}`);
  const payload = await response.json();
  return Array.isArray(payload) ? payload : [];
}

function resolveActionKeys(keys, isMac) {
  const mod = isMac ? '⌘' : 'Ctrl';
  const alt = isMac ? '⌥' : 'Alt';
  const shift = '⇧';

  const selectedKeys = Array.isArray(keys) ? keys : keys?.[isMac ? 'mac' : 'default'];
  if (!Array.isArray(selectedKeys)) return undefined;
  return selectedKeys.map((key) => {
    if (key === '$mod') return mod;
    if (key === '$alt') return alt;
    if (key === '$shift') return shift;
    return key;
  });
}

async function buildStaticActions(isMac) {
  const templates = await loadStaticActionTemplates();
  return templates
    .filter((template) => !IS_ANDROID_RUNTIME || !ANDROID_UNSUPPORTED_ACTIONS.has(template.action))
    .map((template) => {
      const action = { ...template };
      const keys = resolveActionKeys(template.keys, isMac);
      if (keys) {
        action.keys = keys;
      } else {
        delete action.keys;
      }
      return action;
    });
}

async function ensureStaticActions() {
  if (staticActions) return;
  if (!staticActionsPromise) {
    staticActionsPromise = (async () => {
      const os = await getPlatformOs();
      staticActions = await buildStaticActions(os === 'mac');
    })().finally(() => {
      staticActionsPromise = null;
    });
  }
  await staticActionsPromise;
}

function isValidOmniUrl(str) {
  return /^(https?:\/\/)?((([a-z\d]([a-z\d-]*[a-z\d])*)\.)+[a-z]{2,}|((\d{1,3}\.){3}\d{1,3}))(\:\d+)?(\/[-a-z\d%_.~+]*)*(\?[;&a-z\d%_.~+=-]*)?(\#[-a-z\d_]*)?$/i.test(str);
}

function addHttp(url) {
  return /^(?:f|ht)tps?\:\/\//.test(url) ? url : 'https://' + url;
}

function expandOmniShorthand(query) {
  if (query === '/t') return '/tabs ';
  if (query === '/b') return '/bookmarks ';
  if (query === '/h') return '/history ';
  if (query === '/r') return '/remove ';
  if (query === '/a') return '/actions ';
  return query;
}

function matchOmniItem(item, lower) {
  return (
    (item.title || '').toLowerCase().includes(lower) ||
    (item.url || '').toLowerCase().includes(lower) ||
    (item.desc || '').toLowerCase().includes(lower)
  );
}

// ─── Dynamic actions based on current tab ──────────────────────────────────
async function getDynamicActions() {
  const tabs = await callChrome(chrome.tabs.query.bind(chrome.tabs), { active: true, currentWindow: true });
  const [tab] = tabs || [];
  if (!tab) return [];
  const isMac = (await getPlatformOs()) === 'mac';
  const alt = isMac ? '⌥' : 'Alt';

  const muteAction = tab.mutedInfo?.muted
    ? { title: 'Unmute tab', desc: 'Unmute the current tab', type: 'action', action: 'unmute', emoji: true, emojiChar: '🔈', keycheck: true, keys: [alt, '⇧', 'M'] }
    : { title: 'Mute tab', desc: 'Mute the current tab', type: 'action', action: 'mute', emoji: true, emojiChar: '🔇', keycheck: true, keys: [alt, '⇧', 'M'] };

  const pinAction = tab.pinned
    ? { title: 'Unpin tab', desc: 'Unpin the current tab', type: 'action', action: 'unpin', emoji: true, emojiChar: '📌', keycheck: true, keys: [alt, '⇧', 'P'] }
    : { title: 'Pin tab', desc: 'Pin the current tab', type: 'action', action: 'pin', emoji: true, emojiChar: '📌', keycheck: true, keys: [alt, '⇧', 'P'] };

  return [muteAction, pinAction];
}

async function collectOmniData(options = {}) {
  const {
    includeTabs = true,
    includeBookmarks = true,
    includeActions = true,
    includeDynamicActions = true,
  } = options;

  if (includeTabs) {
    await ensureTabsCache();
  }
  if (includeActions) {
    await ensureStaticActions();
  }

  const [bookmarks, dynamicActions] = await Promise.all([
    includeBookmarks ? getBookmarks() : Promise.resolve([]),
    includeActions && includeDynamicActions ? getDynamicActions() : Promise.resolve([]),
  ]);

  return {
    tabs: includeTabs ? Array.from(tabsCache.values()) : [],
    bookmarks,
    actions: includeActions ? [...dynamicActions, ...staticActions] : [],
  };
}

function getOmniQueryPlan(lower) {
  if (lower.startsWith('/history')) {
    return {
      deps: ['history'],
      includeTabs: false,
      includeBookmarks: false,
      includeActions: false,
      includeDynamicActions: false,
    };
  }

  if (lower.startsWith('/bookmarks')) {
    return {
      deps: ['bookmarks'],
      includeTabs: false,
      includeBookmarks: true,
      includeActions: false,
      includeDynamicActions: false,
    };
  }

  if (lower.startsWith('/tabs')) {
    return {
      deps: ['tabs'],
      includeTabs: true,
      includeBookmarks: false,
      includeActions: false,
      includeDynamicActions: false,
    };
  }

  if (lower.startsWith('/actions') || lower === '') {
    return {
      deps: ['active-tab', 'actions'],
      includeTabs: false,
      includeBookmarks: false,
      includeActions: true,
      includeDynamicActions: true,
    };
  }

  if (lower.startsWith('/remove')) {
    return {
      deps: ['tabs', 'bookmarks'],
      includeTabs: true,
      includeBookmarks: true,
      includeActions: false,
      includeDynamicActions: false,
    };
  }

  return {
    deps: ['tabs', 'bookmarks', 'active-tab', 'actions'],
    includeTabs: true,
    includeBookmarks: true,
    includeActions: true,
    includeDynamicActions: true,
  };
}

async function queryOmni(rawQuery) {
  const expanded = expandOmniShorthand(rawQuery || '');
  const lower = expanded.toLowerCase().trim();
  const now = Date.now();
  pruneOmniQueryCache(now);
  const cached = omniQueryCache.get(lower);

  if (cached && now - cached.ts < OMNI_QUERY_TTL) {
    omniQueryCache.delete(lower);
    omniQueryCache.set(lower, cached);
    return cached.results;
  }

  const queryPlan = getOmniQueryPlan(lower);
  const data = await collectOmniData(queryPlan);
  const { tabs, bookmarks, actions } = data;
  let results = [];

  if (lower === '') {
    results = [...actions];
  } else if (lower.startsWith('/history')) {
    const historyQuery = lower.replace('/history', '').trim();
    const historyResults = chrome.history?.search
      ? await callChrome(chrome.history.search.bind(chrome.history), {
          text: historyQuery,
          maxResults: 50,
          startTime: 0,
        })
      : [];
    results = (historyResults || []).map((entry) => ({
      id: entry.id,
      title: entry.title || entry.url,
      desc: entry.url,
      url: entry.url,
      type: 'history',
      action: 'history',
      emoji: true,
      emojiChar: '🏛',
      keycheck: false,
    }));
  } else if (lower.startsWith('/bookmarks')) {
    const bookmarkQuery = lower.replace('/bookmarks', '').trim();
    if (bookmarkQuery) {
      const bookmarkResults = chrome.bookmarks?.search
        ? await callChrome(chrome.bookmarks.search.bind(chrome.bookmarks), { query: bookmarkQuery })
        : [];
      results = (bookmarkResults || [])
        .filter((bookmark) => Boolean(bookmark.url))
        .map((bookmark) => ({
          id: bookmark.id,
          title: bookmark.title || bookmark.url,
          desc: bookmark.url,
          url: bookmark.url,
          type: 'bookmark',
          action: 'bookmark',
          emoji: true,
          emojiChar: '⭐️',
          keycheck: false,
        }));
    } else {
      results = bookmarks;
    }
  } else if (lower.startsWith('/tabs')) {
    const tabQuery = lower.replace('/tabs', '').trim();
    results = tabQuery ? tabs.filter((tab) => matchOmniItem(tab, tabQuery)) : tabs;
  } else if (lower.startsWith('/actions')) {
    const actionQuery = lower.replace('/actions', '').trim();
    results = actionQuery ? actions.filter((action) => matchOmniItem(action, actionQuery)) : actions;
  } else if (lower.startsWith('/remove')) {
    const removeQuery = lower.replace('/remove', '').trim();
    const removables = [...tabs, ...bookmarks];
    results = removeQuery ? removables.filter((item) => matchOmniItem(item, removeQuery)) : removables;
  } else {
    const matched = [...tabs, ...bookmarks, ...actions].filter((item) => matchOmniItem(item, lower));

    if (isValidOmniUrl(lower)) {
      results = [
        { type: 'special', action: 'goto', title: rawQuery, desc: addHttp(rawQuery), emoji: true, emojiChar: '🔗', keycheck: false },
        ...matched,
      ];
    } else {
      results = [
        { type: 'special', action: 'search', title: `"${rawQuery}"`, desc: 'Search the web', emoji: true, emojiChar: '🔍', keycheck: false },
        ...matched,
      ];
    }
  }

  const boundedResults = results.slice(0, OMNI_MAX_RESULTS);
  omniQueryCache.set(lower, { results: boundedResults, ts: Date.now(), deps: queryPlan.deps });
  pruneOmniQueryCache();
  return boundedResults;
}

// ─── Action Handlers ────────────────────────────────────────────────────────
async function getCurrentTab() {
  const tabs = await callChrome(chrome.tabs.query.bind(chrome.tabs), { active: true, currentWindow: true });
  const [tab] = tabs || [];
  return tab;
}

function isDestructiveOmniItem(item, removeMode = false) {
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

  return Boolean(removeMode || destructiveActions.has(item?.action));
}

async function switchTab(tab) {
  await safeCallChrome(chrome.tabs.highlight.bind(chrome.tabs), { tabs: tab.index, windowId: tab.windowId });
  if (chrome.windows?.update) {
    await safeCallChrome(chrome.windows.update.bind(chrome.windows), tab.windowId, { focused: true });
  }
}

async function openUrlFromCurrentTab(url, newTab = false) {
  if (!url) return;

  if (newTab) {
    chrome.tabs.create({ url });
    return;
  }

  const currentTab = await getCurrentTab();
  if (currentTab?.id) {
    chrome.tabs.update(currentTab.id, { url });
    return;
  }

  chrome.tabs.create({ url });
}

async function executeAction(message) {
  switch (message.request) {
    case 'switch-tab':
      await switchTab(message.tab);
      break;
    case 'go-back': {
      const t = await getCurrentTab();
      chrome.tabs.goBack(t.id);
      break;
    }
    case 'go-forward': {
      const t = await getCurrentTab();
      chrome.tabs.goForward(t.id);
      break;
    }
    case 'duplicate-tab': {
      const t = await getCurrentTab();
      chrome.tabs.duplicate(t.id);
      break;
    }
    case 'create-bookmark': {
      const t = await getCurrentTab();
      if (t && chrome.bookmarks?.create) {
        chrome.bookmarks.create({ title: t.title, url: t.url });
      }
      break;
    }
    case 'mute': {
      const t = await getCurrentTab();
      chrome.tabs.update(t.id, { muted: true });
      break;
    }
    case 'unmute': {
      const t = await getCurrentTab();
      chrome.tabs.update(t.id, { muted: false });
      break;
    }
    case 'reload':
      chrome.tabs.reload();
      break;
    case 'pin': {
      const t = await getCurrentTab();
      chrome.tabs.update(t.id, { pinned: true });
      break;
    }
    case 'unpin': {
      const t = await getCurrentTab();
      chrome.tabs.update(t.id, { pinned: false });
      break;
    }
    case 'new-tab':
      chrome.tabs.create({});
      break;
    case 'incognito':
      chrome.windows?.create?.({ incognito: true });
      break;
    case 'close-tab': {
      const t = await getCurrentTab();
      chrome.tabs.remove(t.id);
      break;
    }
    case 'close-window': {
      const t = await getCurrentTab();
      if (t?.windowId !== undefined) {
        chrome.windows?.remove?.(t.windowId);
      }
      break;
    }
    case 'remove': {
      if (message.type === 'bookmark' && chrome.bookmarks?.remove) {
        chrome.bookmarks.remove(message.action.id);
        invalidateBookmarks();
      } else if (message.type === 'tab') {
        chrome.tabs.remove(message.action.id);
      }
      break;
    }
    case 'remove-all':
      chrome.browsingData.remove(
        { since: new Date().getTime() },
        { appcache: true, cache: true, cacheStorage: true, cookies: true, downloads: true, fileSystems: true, formData: true, history: true, indexedDB: true, localStorage: true, passwords: true, serviceWorkers: true, webSQL: true }
      );
      break;
    case 'remove-history':
      chrome.browsingData.removeHistory({ since: 0 });
      break;
    case 'remove-cookies':
      chrome.browsingData.removeCookies({ since: 0 });
      break;
    case 'remove-cache':
      chrome.browsingData.removeCache({ since: 0 });
      break;
    case 'remove-local-storage':
      chrome.browsingData.removeLocalStorage({ since: 0 });
      break;
    case 'remove-passwords':
      chrome.browsingData.removePasswords({ since: 0 });
      break;
    case 'history':
    case 'downloads':
    case 'extensions':
    case 'settings':
      chrome.tabs.create({ url: `chrome://${message.request}/` });
      break;
    case 'manage-data':
      chrome.tabs.create({ url: 'chrome://settings/clearBrowserData' });
      break;
    case 'search':
      if (message.query) {
        if (chrome.search?.query) {
          chrome.search.query({ text: message.query });
        } else {
          const searchUrl = new URL('https://astiango.com/');
          searchUrl.searchParams.set('client', 'midoritab');
          searchUrl.searchParams.set('omnibar', '1');
          searchUrl.searchParams.set('q', message.query);
          searchUrl.searchParams.set('qbc', '1');
          await openUrlFromCurrentTab(searchUrl.toString());
        }
      }
      break;
  }
}

async function executeOmniItem(message) {
  const item = message.item || {};
  const query = message.query || '';
  const removeMode = Boolean(message.removeMode);
  const newTab = Boolean(message.newTab);
  const confirmed = Boolean(message.confirmed);

  if (isDestructiveOmniItem(item, removeMode) && !confirmed) {
    return { handled: false, requiresConfirmation: true };
  }

  if (removeMode) {
    await executeAction({ request: 'remove', type: item.type, action: item });
    return { handled: true };
  }

  switch (item.action) {
    case 'switch-tab':
      await executeAction({ request: 'switch-tab', tab: item });
      return { handled: true };
    case 'bookmark':
    case 'history':
    case 'url':
      if (item.url) {
        await openUrlFromCurrentTab(item.url, newTab);
      }
      return { handled: true };
    case 'goto':
      await openUrlFromCurrentTab(addHttp(query), newTab);
      return { handled: true };
    case 'search':
      await executeAction({ request: 'search', query });
      return { handled: true };
    case 'new-tab':
      await executeAction({ request: 'new-tab' });
      return { handled: true };
    case 'email':
      await openUrlFromCurrentTab('mailto:', newTab);
      return { handled: true };
    case 'print':
    case 'fullscreen':
    case 'scroll-top':
    case 'scroll-bottom':
      return { handled: false, localAction: item.action };
    default:
      if (item.url) {
        await openUrlFromCurrentTab(item.url, newTab);
        return { handled: true };
      }
      if (item.action) {
        await executeAction({ request: item.action, tab: item, query });
        return { handled: true };
      }
      return { handled: false };
  }
}

// ─── Message Handler ─────────────────────────────────────────────────────────
async function handleMessage(message) {
  switch (message.request) {
    case 'get-data': {
      return collectOmniData();
    }

    case 'query-omni': {
      const results = await queryOmni(message.query ?? '');
      return { results };
    }

    case 'open-omni': {
      const tab = await getCurrentTab();
      await openOmniInTab(tab);
      return { ok: true };
    }

    case 'get-omni-config': {
      return getOmniUiConfig(message);
    }

    case 'execute-omni-item': {
      return executeOmniItem(message);
    }

    case 'search-history': {
      const results = chrome.history?.search
        ? await callChrome(chrome.history.search.bind(chrome.history), {
            text: message.query ?? '',
            maxResults: 50,
            startTime: 0,
          })
        : [];
      return {
        history: (results || []).map((h) => ({
          id: h.id,
          title: h.title || h.url,
          desc: h.url,
          url: h.url,
          type: 'history',
          action: 'history',
          emoji: true,
          emojiChar: '🏛',
          keycheck: false,
        })),
      };
    }

    case 'search-bookmarks': {
      const results = chrome.bookmarks?.search
        ? await callChrome(chrome.bookmarks.search.bind(chrome.bookmarks), { query: message.query })
        : [];
      return {
        bookmarks: (results || [])
          .filter((b) => Boolean(b.url))
          .map((b) => ({
            id: b.id,
            title: b.title || b.url,
            desc: b.url,
            url: b.url,
            type: 'bookmark',
            action: 'bookmark',
            emoji: true,
            emojiChar: '⭐️',
            keycheck: false,
          })),
      };
    }

    case 'switch-tab':
    case 'go-back':
    case 'go-forward':
    case 'duplicate-tab':
    case 'create-bookmark':
    case 'mute':
    case 'unmute':
    case 'reload':
    case 'pin':
    case 'unpin':
    case 'new-tab':
    case 'incognito':
    case 'close-tab':
    case 'close-window':
    case 'remove':
    case 'remove-all':
    case 'remove-history':
    case 'remove-cookies':
    case 'remove-cache':
    case 'remove-local-storage':
    case 'remove-passwords':
    case 'history':
    case 'downloads':
    case 'extensions':
    case 'settings':
    case 'manage-data':
    case 'search':
      await executeAction(message);
      return { ok: true };

    default:
      return null;
  }
}

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  handleMessage(message)
    .then((result) => sendResponse(result ?? {}))
    .catch((err) => {
      console.error('[Midori Omni background] Error handling message:', message.request, err);
      sendResponse({ error: String(err) });
    });
  return true; // keep port open for async response
});

globalThis.__midoriOmniBackground = {
  collectOmniData,
  ensureStaticActions,
  ensureTabsCache,
  pruneTabsCache,
  getDebugState() {
    return {
      tabsCacheReady,
      tabsCacheSize: tabsCache.size,
      bookmarksCacheReady: Array.isArray(bookmarksCache),
      staticActionsReady: Array.isArray(staticActions),
      omniQueryCacheSize: omniQueryCache.size,
    };
  },
  resetDebugState() {
    tabsCache.clear();
    tabsCacheReady = false;
    tabsPrunePromise = null;
    bookmarksCache = null;
    staticActions = null;
    staticActionsPromise = null;
    omniQueryCache.clear();
  },
  executeOmniItem,
};

// ─── Keyboard shortcut & toolbar click ──────────────────────────────────────
async function openOmniInTab(tab) {
  if (!tab) return;
  const url = tab.url || '';

  try {
    await callChrome(chrome.tabs.sendMessage.bind(chrome.tabs), tab.id, { request: 'open-omni' });
    return;
  } catch (_) {
    // Firefox reports extension new-tab overrides as about:newtab; those pages
    // receive runtime messages, not tabs.sendMessage content-script messages.
  }

  if (isFirefoxNewTabOverrideUrl(url)) {
    await safeCallChrome(chrome.runtime.sendMessage.bind(chrome.runtime), { request: 'open-omni-page' });
    return;
  }

  // Can't send to privileged system pages
  if (isRestrictedOmniUrl(url)) {
    // Fallback: open a new tab (the new tab IS the omni launcher)
    chrome.tabs.create({});
    return;
  }

  try {
    await ensureOmniContentScript(tab.id);
    await callChrome(chrome.tabs.sendMessage.bind(chrome.tabs), tab.id, { request: 'open-omni' });
  } catch (_) {
    chrome.tabs.create({});
  }
}

globalThis.__midoriOmniBackground.openOmniInTab = openOmniInTab;

chrome.commands?.onCommand?.addListener(async (command) => {
  if (command === 'open-omni') {
    const tab = await getCurrentTab();
    await openOmniInTab(tab);
  }
});

const toolbarActionApi = chrome.action || chrome.browserAction;

if (toolbarActionApi?.onClicked) {
  toolbarActionApi.onClicked.addListener(async (tab) => {
    await openOmniInTab(tab);
  });
}

// ─── Install lifecycle ──────────────────────────────────────────────────────
chrome.runtime.onInstalled.addListener(async (details) => {
  if (details.reason === 'install' || details.reason === 'update') {
    omniQueryCache.clear();
  }
});
