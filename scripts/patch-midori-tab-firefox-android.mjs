#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';

const sourceRoot = path.resolve(process.argv[2] || '');
if (!sourceRoot || !fs.statSync(sourceRoot).isDirectory()) {
  throw new Error('Expected the extracted Midori Tab source directory');
}

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
  'identity',
  'tabs',
  'activeTab',
  'bookmarks',
  'history',
  'browsingData',
  'search',
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
fs.writeFileSync(firefoxManifestPath, `${JSON.stringify(firefoxManifest, null, 2)}\n`);

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
