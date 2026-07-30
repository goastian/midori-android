#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';

const sourceRoot = path.resolve(process.argv[2] || '');
if (!sourceRoot || !fs.statSync(sourceRoot).isDirectory()) {
  throw new Error('Expected the extracted Midori Tab source directory');
}

const ANDROID_COMPATIBILITY_REVISION = 5;
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

function assertExact(relativePath, value, expectedCount = 1) {
  const file = sourcePath(relativePath);
  const content = fs.readFileSync(file, 'utf8');
  const actualCount = content.split(value).length - 1;
  if (actualCount !== expectedCount) {
    throw new Error(`${relativePath}: expected ${expectedCount} matches, found ${actualCount}`);
  }
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
  'src/services/privacyStats.js',
  `  return {
    totalBlocked,
    totalRequests,
    pageBlocked: toCount(data.pageBlocked),`,
  `  const hasEstimatedDataSavedBytes =
    data.dataSavedEstimateModel === 'conservative-8kib-per-block-v1' &&
    Object.prototype.hasOwnProperty.call(data, 'estimatedDataSavedBytes');

  return {
    totalBlocked,
    totalRequests,
    pageBlocked: toCount(data.pageBlocked),`,
);

replaceExact(
  'src/services/privacyStats.js',
  `    pageRequests: toCount(data.pageRequests),
    blockRate: totalRequests === 0 ? 0 : (totalBlocked / totalRequests) * 100,`,
  `    pageRequests: toCount(data.pageRequests),
    estimatedDataSavedBytes: hasEstimatedDataSavedBytes
      ? toCount(data.estimatedDataSavedBytes)
      : 0,
    hasEstimatedDataSavedBytes,
    blockRate: totalRequests === 0 ? 0 : (totalBlocked / totalRequests) * 100,`,
);

replaceExact(
  'src/components/PrivacyWidget.vue',
  `        <span class="pw-stat-value">{{ formattedPageBlocked }}</span>
        <span class="pw-stat-label">{{ i18n.$t('privacy.stats.page') }}</span>`,
  `        <span class="pw-stat-value">{{ fourthMetricValue }}</span>
        <span class="pw-stat-label">{{ fourthMetricLabel }}</span>`,
);

replaceExact(
  'src/components/PrivacyWidget.vue',
  `      pageBlocked: 0,
      pageRequests: 0,
      blockRate: 0,`,
  `      pageBlocked: 0,
      pageRequests: 0,
      estimatedDataSavedBytes: 0,
      hasEstimatedDataSavedBytes: false,
      blockRate: 0,`,
);

replaceExact(
  'src/components/PrivacyWidget.vue',
  `    formattedPageBlocked() {
      return this.formatCompact(this.pageBlocked);
    },

    statusLabel() {`,
  `    formattedPageBlocked() {
      return this.formatCompact(this.pageBlocked);
    },

    fourthMetricValue() {
      if (!this.hasEstimatedDataSavedBytes) return this.formattedPageBlocked;
      return this.formatEstimatedBytes(this.estimatedDataSavedBytes);
    },

    fourthMetricLabel() {
      return this.hasEstimatedDataSavedBytes
        ? this.i18n.$t('privacy.stats.estimatedSaved')
        : this.i18n.$t('privacy.stats.page');
    },

    statusLabel() {`,
);

replaceExact(
  'src/components/PrivacyWidget.vue',
  `    formatCompact(value) {
      const n = Number(value) || 0;
      if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M';
      if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K';
      return String(n);
    },

    isForeground() {`,
  `    formatCompact(value) {
      const n = Number(value) || 0;
      if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M';
      if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K';
      return String(n);
    },

    formatEstimatedBytes(value) {
      const bytes = Number(value) || 0;
      if (bytes <= 0) return '0 KB';
      const kibibytes = bytes / 1024;
      if (kibibytes < 1024) return \`≈\${Math.max(1, Math.round(kibibytes))} KB\`;
      const mebibytes = kibibytes / 1024;
      if (mebibytes < 1024) {
        return \`≈\${mebibytes < 10 ? mebibytes.toFixed(1) : Math.round(mebibytes)} MB\`;
      }
      const gibibytes = mebibytes / 1024;
      return \`≈\${gibibytes < 10 ? gibibytes.toFixed(1) : Math.round(gibibytes)} GB\`;
    },

    isForeground() {`,
);

replaceExact(
  'src/components/PrivacyWidget.vue',
  `      this.pageBlocked = nextStats.pageBlocked;
      this.pageRequests = nextStats.pageRequests;
      this.blockRate = nextStats.blockRate;`,
  `      this.pageBlocked = nextStats.pageBlocked;
      this.pageRequests = nextStats.pageRequests;
      this.estimatedDataSavedBytes = nextStats.estimatedDataSavedBytes;
      this.hasEstimatedDataSavedBytes = nextStats.hasEstimatedDataSavedBytes;
      this.blockRate = nextStats.blockRate;`,
);

const estimatedSavedTranslations = {
  en: 'Estimated saved',
  es: 'Ahorro estimado',
  de: 'Geschätzt gespart',
  fr: 'Économie estimée',
  it: 'Risparmio stimato',
  ja: '推定節約量',
  pt: 'Economia estimada',
  ru: 'Расчётная экономия',
  zh: '估算节省',
};
const privacyPageTranslations = {
  en: 'This tab',
  es: 'Esta pestaña',
  de: 'Dieser Tab',
  fr: 'Cet onglet',
  it: 'Questa scheda',
  ja: 'このタブ',
  pt: 'Esta aba',
  ru: 'Эта вкладка',
  zh: '当前标签页',
};
for (const [locale, translation] of Object.entries(estimatedSavedTranslations)) {
  const relativePath = `src/i18n/locales/${locale}.js`;
  const pageLine = fs.readFileSync(sourcePath(relativePath), 'utf8')
    .split(/\r?\n/)
    .find((line) => line.trim() === `page: '${privacyPageTranslations[locale]}',`);
  if (!pageLine) {
    throw new Error(`${relativePath}: could not find the Privacy page metric translation`);
  }
  const indentation = pageLine.match(/^\s*/)[0];
  replaceExact(
    relativePath,
    pageLine,
    `${pageLine}\n${indentation}estimatedSaved: '${translation}',`,
  );
}

assertExact('src/stores/useWidgetsStore.js', '  privacy: true,');
replaceExact(
  'src/stores/useWidgetsStore.js',
  `import { mergeWidgetSubset } from '../utils/widgetLayout.js';`,
  `import { mergeWidgetSubset } from '../utils/widgetLayout.js';
import { applyAndroidPrivacyWidgetMigration } from '../utils/androidPrivacyWidgetMigration.js';`,
);
const androidPrivacyMigration = `const ANDROID_PRIVACY_WIDGET_MIGRATION_REVISION = 1;

export function applyAndroidPrivacyWidgetMigration(store) {
  const currentRevision = Number(store?.androidPrivacyMigrationRevision) || 0;
  if (currentRevision >= ANDROID_PRIVACY_WIDGET_MIGRATION_REVISION) return false;

  store.enabled.privacy = true;
  store.androidPrivacyMigrationRevision = ANDROID_PRIVACY_WIDGET_MIGRATION_REVISION;
  return true;
}
`;
const androidPrivacyMigrationPath = sourcePath('src/utils/androidPrivacyWidgetMigration.js');
if (fs.existsSync(androidPrivacyMigrationPath)) {
  throw new Error('src/utils/androidPrivacyWidgetMigration.js already exists upstream; review the compatibility patch');
}
fs.writeFileSync(androidPrivacyMigrationPath, androidPrivacyMigration);
replaceExact(
  'src/stores/useWidgetsStore.js',
  `    order: [...DEFAULT_ORDER],
    installedMarketplaceWidgets: {},`,
  `    order: [...DEFAULT_ORDER],
    androidPrivacyMigrationRevision: 0,
    installedMarketplaceWidgets: {},`,
);
replaceExact(
  'src/stores/useWidgetsStore.js',
  `    enable: true,
    storage: localStorage,
    paths: ['enabled', 'order'],
    afterRestore(ctx) {`,
  `    storage: localStorage,
    pick: ['enabled', 'order', 'androidPrivacyMigrationRevision'],
    afterHydrate(ctx) {`,
);
replaceExact(
  'src/stores/useWidgetsStore.js',
  `      // Ensure order array contains all widget keys
      if (Array.isArray(store.order)) {`,
  `      // Earlier Android bundles persisted the Privacy widget as disabled.
      // Store the migration revision beside the preference so an older live
      // tab cannot make the one-shot migration look complete before its state
      // has actually been persisted. Future user choices remain untouched.
      applyAndroidPrivacyWidgetMigration(store);

      // Ensure order array contains all widget keys
      if (Array.isArray(store.order)) {`,
);
replaceExact(
  'src/stores/useWidgetsStore.js',
  `      store.installedMarketplaceWidgets = {};
    },`,
  `      store.installedMarketplaceWidgets = {};
      store.$persist();
    },`,
);

const androidPrivacyTest = `import test from 'node:test';
import assert from 'node:assert/strict';
import { createApp } from 'vue';
import { createPinia } from 'pinia';
import persistedState from 'pinia-plugin-persistedstate';
import { normalizePrivacyStats } from '../src/services/privacyStats.js';
import { applyAndroidPrivacyWidgetMigration } from '../src/utils/androidPrivacyWidgetMigration.js';

test('normalizes the Android Midori Privacy savings estimate', () => {
  const result = normalizePrivacyStats({
    totalBlocked: 2,
    totalRequests: 10,
    pageBlocked: 0,
    pageRequests: 0,
    estimatedDataSavedBytes: 16_384,
    dataSavedEstimateModel: 'conservative-8kib-per-block-v1',
  });
  assert.equal(result.estimatedDataSavedBytes, 16_384);
  assert.equal(result.hasEstimatedDataSavedBytes, true);
  assert.equal(result.blockRate, 20);
});

test('keeps the previous page metric available during a partial update', () => {
  const result = normalizePrivacyStats({ totalBlocked: 3, pageBlocked: 2 });
  assert.equal(result.estimatedDataSavedBytes, 0);
  assert.equal(result.hasEstimatedDataSavedBytes, false);
  assert.equal(result.pageBlocked, 2);
});

test('enables Privacy exactly once for profiles created by older Android bundles', () => {
  const store = {
    enabled: { privacy: false },
    androidPrivacyMigrationRevision: 0,
  };

  assert.equal(applyAndroidPrivacyWidgetMigration(store), true);
  assert.equal(store.enabled.privacy, true);
  assert.equal(store.androidPrivacyMigrationRevision, 1);

  store.enabled.privacy = false;
  assert.equal(applyAndroidPrivacyWidgetMigration(store), false);
  assert.equal(store.enabled.privacy, false);
});

test('hydrates and persists the Android Privacy migration with the installed Pinia plugin', async t => {
  const values = new Map([
    ['widgetsStore', JSON.stringify({ enabled: { privacy: false }, order: ['privacy'] })],
  ]);
  globalThis.localStorage = {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key),
    clear: () => values.clear(),
    key: index => [...values.keys()][index] ?? null,
    get length() { return values.size; },
  };
  t.after(() => { delete globalThis.localStorage; });

  const { default: useWidgetsStore } = await import('../src/stores/useWidgetsStore.js');
  const createPersistedPinia = () => {
    const pinia = createPinia();
    pinia.use(persistedState);
    createApp({}).use(pinia);
    return pinia;
  };

  const store = useWidgetsStore(createPersistedPinia());
  assert.equal(store.enabled.privacy, true);
  assert.equal(store.androidPrivacyMigrationRevision, 1);
  assert.equal(JSON.parse(values.get('widgetsStore')).enabled.privacy, true);

  store.enabled.privacy = false;
  store.$persist();
  const reloadedStore = useWidgetsStore(createPersistedPinia());
  assert.equal(reloadedStore.enabled.privacy, false);
  assert.equal(reloadedStore.androidPrivacyMigrationRevision, 1);
});
`;
const androidPrivacyTestPath = sourcePath('tests/privacy-stats-android.test.mjs');
if (fs.existsSync(androidPrivacyTestPath)) {
  throw new Error('tests/privacy-stats-android.test.mjs already exists upstream; review the compatibility patch');
}
fs.writeFileSync(androidPrivacyTestPath, androidPrivacyTest);

console.log('Applied the Midori Tab Firefox Android compatibility layer.');
