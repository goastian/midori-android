#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';

const extensionRoot = path.resolve(process.argv[2] || '');
if (!extensionRoot || !fs.statSync(extensionRoot).isDirectory()) {
  throw new Error('Expected the extracted Midori Tab Firefox extension directory');
}

const ANDROID_COMPATIBILITY_REVISION = 6;

function extensionPath(relativePath) {
  return path.join(extensionRoot, relativePath);
}

function replaceExact(relativePath, before, after) {
  const file = extensionPath(relativePath);
  const original = fs.readFileSync(file, 'utf8');
  const matches = original.split(before).length - 1;
  if (matches !== 1) {
    throw new Error(`${relativePath}: expected 1 match, found ${matches}`);
  }
  fs.writeFileSync(file, original.replace(before, after));
}

const manifestPath = extensionPath('manifest.json');
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
const sourceVersion = String(manifest.version || '');
if (!/^\d+\.\d+\.\d+$/.test(sourceVersion) || manifest.manifest_version !== 2) {
  throw new Error(`Expected a three-part Firefox MV2 release, found ${sourceVersion}`);
}

manifest.version = `${sourceVersion}.${ANDROID_COMPATIBILITY_REVISION}`;
manifest.permissions = [
  'storage',
  'tabs',
  'activeTab',
  'browsingData',
  'https://api.rss2json.com/*',
  'https://api.unsplash.com/*',
  'https://api.github.com/*',
  'https://api.open-meteo.com/*',
  'https://geocoding-api.open-meteo.com/*',
  'https://ipwho.is/*',
  'https://nominatim.openstreetmap.org/*',
  'https://duckduckgo.com/*',
  'https://astiango.com/*',
  'https://marketplace.astian.org/*',
  'https://open.er-api.com/*',
  'https://ads.astian.org/*',
];
delete manifest.chrome_url_overrides;
delete manifest.commands;
fs.writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`);

replaceExact(
  'background.js',
  `const OMNI_STATIC_ACTIONS_FILE = 'omni-static-actions.json';
const omniQueryCache = new Map();`,
  `const OMNI_STATIC_ACTIONS_FILE = 'omni-static-actions.json';
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
]);`,
);

replaceExact(
  'background.js',
  `chrome.windows.onFocusChanged.addListener(() => {
  clearOmniQueryCache('active-tab');
});`,
  `chrome.windows?.onFocusChanged?.addListener(() => {
  clearOmniQueryCache('active-tab');
});`,
);

replaceExact(
  'background.js',
  `chrome.bookmarks.onCreated.addListener(invalidateBookmarks);
chrome.bookmarks.onRemoved.addListener(invalidateBookmarks);
chrome.bookmarks.onChanged.addListener(invalidateBookmarks);
chrome.bookmarks.onMoved.addListener(invalidateBookmarks);`,
  `[chrome.bookmarks?.onCreated, chrome.bookmarks?.onRemoved, chrome.bookmarks?.onChanged, chrome.bookmarks?.onMoved]
  .filter(Boolean)
  .forEach((event) => event.addListener(invalidateBookmarks));`,
);

replaceExact(
  'background.js',
  `async function getBookmarks() {
  if (bookmarksCache) return bookmarksCache;`,
  `async function getBookmarks() {
  if (!chrome.bookmarks?.getRecent) return [];
  if (bookmarksCache) return bookmarksCache;`,
);

replaceExact(
  'background.js',
  `async function buildStaticActions(isMac) {
  const templates = await loadStaticActionTemplates();
  return templates.map((template) => {
    const action = { ...template };
    const keys = resolveActionKeys(template.keys, isMac);
    if (keys) {
      action.keys = keys;
    } else {
      delete action.keys;
    }
    return action;
  });
}`,
  `async function buildStaticActions(isMac) {
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
}`,
);

replaceExact(
  'background.js',
  `    const historyResults = await callChrome(chrome.history.search.bind(chrome.history), {
      text: historyQuery,
      maxResults: 50,
      startTime: 0,
    });`,
  `    const historyResults = chrome.history?.search
      ? await callChrome(chrome.history.search.bind(chrome.history), {
          text: historyQuery,
          maxResults: 50,
          startTime: 0,
        })
      : [];`,
);

replaceExact(
  'background.js',
  '      const bookmarkResults = await callChrome(chrome.bookmarks.search.bind(chrome.bookmarks), { query: bookmarkQuery });',
  `      const bookmarkResults = chrome.bookmarks?.search
        ? await callChrome(chrome.bookmarks.search.bind(chrome.bookmarks), { query: bookmarkQuery })
        : [];`,
);

replaceExact(
  'background.js',
  '      const results = await callChrome(chrome.bookmarks.search.bind(chrome.bookmarks), { query: message.query });',
  `      const results = chrome.bookmarks?.search
        ? await callChrome(chrome.bookmarks.search.bind(chrome.bookmarks), { query: message.query })
        : [];`,
);

replaceExact(
  'background.js',
  `async function switchTab(tab) {
  await safeCallChrome(chrome.tabs.highlight.bind(chrome.tabs), { tabs: tab.index, windowId: tab.windowId });
  await safeCallChrome(chrome.windows.update.bind(chrome.windows), tab.windowId, { focused: true });
}`,
  `async function switchTab(tab) {
  await safeCallChrome(chrome.tabs.highlight.bind(chrome.tabs), { tabs: tab.index, windowId: tab.windowId });
  if (chrome.windows?.update) {
    await safeCallChrome(chrome.windows.update.bind(chrome.windows), tab.windowId, { focused: true });
  }
}`,
);

replaceExact(
  'background.js',
  `    case 'create-bookmark': {
      const t = await getCurrentTab();
      chrome.bookmarks.create({ title: t.title, url: t.url });
      break;
    }`,
  `    case 'create-bookmark': {
      const t = await getCurrentTab();
      if (t && chrome.bookmarks?.create) {
        chrome.bookmarks.create({ title: t.title, url: t.url });
      }
      break;
    }`,
);

replaceExact(
  'background.js',
  `    case 'incognito':
      chrome.windows.create({ incognito: true });
      break;`,
  `    case 'incognito':
      chrome.windows?.create?.({ incognito: true });
      break;`,
);

replaceExact(
  'background.js',
  `    case 'close-window': {
      const t = await getCurrentTab();
      chrome.windows.remove(t.windowId);
      break;
    }`,
  `    case 'close-window': {
      const t = await getCurrentTab();
      if (t?.windowId !== undefined) {
        chrome.windows?.remove?.(t.windowId);
      }
      break;
    }`,
);

replaceExact(
  'background.js',
  `      if (message.type === 'bookmark') {
        chrome.bookmarks.remove(message.action.id);
        invalidateBookmarks();`,
  `      if (message.type === 'bookmark' && chrome.bookmarks?.remove) {
        chrome.bookmarks.remove(message.action.id);
        invalidateBookmarks();`,
);

replaceExact(
  'background.js',
  `    case 'search':
      if (message.query) {
        chrome.search.query({ text: message.query });
      }
      break;`,
  `    case 'search':
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
      break;`,
);

replaceExact(
  'background.js',
  `    case 'search-history': {
      const results = await callChrome(chrome.history.search.bind(chrome.history), {
        text: message.query ?? '',
        maxResults: 50,
        startTime: 0,
      });`,
  `    case 'search-history': {
      const results = chrome.history?.search
        ? await callChrome(chrome.history.search.bind(chrome.history), {
            text: message.query ?? '',
            maxResults: 50,
            startTime: 0,
          })
        : [];`,
);

replaceExact(
  'background.js',
  'chrome.commands.onCommand.addListener(async (command) => {',
  'chrome.commands?.onCommand?.addListener(async (command) => {',
);

replaceExact(
  'background.js',
  "  return /^(?:f|ht)tps?\\:\\/\\//.test(url) ? url : 'http://' + url;",
  "  return /^(?:f|ht)tps?\\:\\/\\//.test(url) ? url : 'https://' + url;",
);

console.log(
  `Patched Midori Tab ${sourceVersion} for Android as ${manifest.version}`,
);
