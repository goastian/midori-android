#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';

const sourceRoot = path.resolve(process.argv[2] || '');
if (!sourceRoot || !fs.statSync(sourceRoot).isDirectory()) {
  throw new Error('Expected the extracted Midori Tab source directory');
}

const ANDROID_COMPATIBILITY_REVISION = 2;
const packageJson = JSON.parse(fs.readFileSync(sourcePath('package.json'), 'utf8'));
const sourceVersion = String(packageJson.version || '');
if (!/^\d+\.\d+\.\d+$/.test(sourceVersion)) {
  throw new Error(`Expected a three-part upstream version, found ${sourceVersion}`);
}
const androidVersion = `${sourceVersion}.${ANDROID_COMPATIBILITY_REVISION}`;

function sourcePath(relativePath) {
  return path.join(sourceRoot, relativePath);
}

function replaceExact(relativePath, before, after, expectedCount = 1) {
  const file = sourcePath(relativePath);
  const original = fs.readFileSync(file, 'utf8');
  const actualCount = original.split(before).length - 1;
  if (actualCount !== expectedCount) {
    throw new Error(`${relativePath}: expected ${expectedCount} matches, found ${actualCount}`);
  }
  fs.writeFileSync(file, original.split(before).join(after));
}

const firefoxManifestPath = sourcePath('manifest/firefox.json');
const firefoxManifest = JSON.parse(fs.readFileSync(firefoxManifestPath, 'utf8'));
firefoxManifest.permissions = [
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
firefoxManifest.version = androidVersion;
firefoxManifest.chrome_url_overrides = null;
firefoxManifest.commands = null;
fs.writeFileSync(firefoxManifestPath, `${JSON.stringify(firefoxManifest, null, 2)}\n`);

replaceExact(
  'build-manifest.js',
  '    version: options.version || APP_VERSION,',
  '    version: options.version || targetManifest.version || APP_VERSION,',
);

replaceExact(
  'public/background.js',
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
  'public/background.js',
  `chrome.windows.onFocusChanged.addListener(() => {
  clearOmniQueryCache('active-tab');
});`,
  `chrome.windows?.onFocusChanged?.addListener(() => {
  clearOmniQueryCache('active-tab');
});`,
);

replaceExact(
  'public/background.js',
  `chrome.bookmarks.onCreated.addListener(invalidateBookmarks);
chrome.bookmarks.onRemoved.addListener(invalidateBookmarks);
chrome.bookmarks.onChanged.addListener(invalidateBookmarks);
chrome.bookmarks.onMoved.addListener(invalidateBookmarks);`,
  `[chrome.bookmarks?.onCreated, chrome.bookmarks?.onRemoved, chrome.bookmarks?.onChanged, chrome.bookmarks?.onMoved]
  .filter(Boolean)
  .forEach((event) => event.addListener(invalidateBookmarks));`,
);

replaceExact(
  'public/background.js',
  `async function getBookmarks() {
  if (bookmarksCache) return bookmarksCache;`,
  `async function getBookmarks() {
  if (!chrome.bookmarks?.getRecent) return [];
  if (bookmarksCache) return bookmarksCache;`,
);

replaceExact(
  'public/background.js',
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
  'public/background.js',
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
  'public/background.js',
  `      const bookmarkResults = await callChrome(chrome.bookmarks.search.bind(chrome.bookmarks), { query: bookmarkQuery });`,
  `      const bookmarkResults = chrome.bookmarks?.search
        ? await callChrome(chrome.bookmarks.search.bind(chrome.bookmarks), { query: bookmarkQuery })
        : [];`,
);

replaceExact(
  'public/background.js',
  `      const results = await callChrome(chrome.bookmarks.search.bind(chrome.bookmarks), { query: message.query });`,
  `      const results = chrome.bookmarks?.search
        ? await callChrome(chrome.bookmarks.search.bind(chrome.bookmarks), { query: message.query })
        : [];`,
);

replaceExact(
  'public/background.js',
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
  'public/background.js',
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
  'public/background.js',
  `    case 'incognito':
      chrome.windows.create({ incognito: true });
      break;`,
  `    case 'incognito':
      chrome.windows?.create?.({ incognito: true });
      break;`,
);

replaceExact(
  'public/background.js',
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
  'public/background.js',
  `      if (message.type === 'bookmark') {
        chrome.bookmarks.remove(message.action.id);
        invalidateBookmarks();`,
  `      if (message.type === 'bookmark' && chrome.bookmarks?.remove) {
        chrome.bookmarks.remove(message.action.id);
        invalidateBookmarks();`,
);

replaceExact(
  'public/background.js',
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
  'public/background.js',
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
  'public/background.js',
  'chrome.commands.onCommand.addListener(async (command) => {',
  'chrome.commands?.onCommand?.addListener(async (command) => {',
);

replaceExact(
  'public/background.js',
  "  return /^(?:f|ht)tps?\\:\\/\\//.test(url) ? url : 'http://' + url;",
  "  return /^(?:f|ht)tps?\\:\\/\\//.test(url) ? url : 'https://' + url;",
);

replaceExact(
  'src/utils/browserInfo.js',
  `  const platform = String(navigator.platform || '').toLowerCase();
  const isWindows = /win/i.test(platform) || /windows/i.test(ua);`,
  `  const platform = String(navigator.platform || '').toLowerCase();
  const isAndroid = /android/i.test(ua);
  const isWindows = /win/i.test(platform) || /windows/i.test(ua);`,
);

replaceExact(
  'src/utils/browserInfo.js',
  `  const isLinux = /linux/i.test(platform) || /x11/i.test(platform);`,
  `  const isLinux = !isAndroid && (/linux/i.test(platform) || /x11/i.test(platform));`,
);

replaceExact(
  'src/utils/browserInfo.js',
  `  const platformName = isWindows ? 'windows' : isMac ? 'macos' : isLinux ? 'linux' : 'unknown';`,
  `  const platformName = isAndroid ? 'android' : isWindows ? 'windows' : isMac ? 'macos' : isLinux ? 'linux' : 'unknown';`,
);

replaceExact(
  'src/utils/browserInfo.js',
  `    isMidori,
    isFirefox,`,
  `    isMidori,
    isAndroid,
    supportsDesktopUpdates: isMidori && !isAndroid,
    isFirefox,`,
);

replaceExact('src/App.vue', '.isMidori', '.supportsDesktopUpdates', 5);
replaceExact(
  'src/services/MidoriUpdateService.js',
  'browserInfo?.isMidori',
  'browserInfo?.supportsDesktopUpdates',
  3,
);

replaceExact(
  'src/stores/useWidgetsStore.js',
  '  privacy: true,',
  '  privacy: false,',
);

console.log('Applied the Midori Tab Firefox Android compatibility layer.');
