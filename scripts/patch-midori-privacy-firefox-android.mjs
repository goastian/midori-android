#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';

const extensionArgument = process.argv[2];
const extensionRoot = extensionArgument ? path.resolve(extensionArgument) : '';
if (!extensionRoot || !fs.existsSync(extensionRoot) || !fs.statSync(extensionRoot).isDirectory()) {
  throw new Error('Expected the extracted Midori Privacy Firefox extension directory');
}

const ANDROID_COMPATIBILITY_REVISION = 5;
const MIDORI_PRIVACY_EXTENSION_ID = 'midori-protection@astian.org';

function extensionPath(relativePath) {
  return path.join(extensionRoot, relativePath);
}

function replaceExact(relativePath, before, after, expectedCount = 1) {
  const file = extensionPath(relativePath);
  const original = fs.readFileSync(file, 'utf8');
  const actualCount = original.split(before).length - 1;
  if (actualCount !== expectedCount) {
    throw new Error(`${relativePath}: expected ${expectedCount} matches, found ${actualCount}`);
  }
  fs.writeFileSync(file, original.split(before).join(after));
}

const manifestPath = extensionPath('manifest.json');
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
const sourceVersion = String(manifest.version || '');
if (!/^\d+\.\d+\.\d+$/.test(sourceVersion)) {
  throw new Error(`Expected a three-part upstream version, found ${sourceVersion}`);
}
if (manifest.manifest_version !== 2) {
  throw new Error(`Expected the Firefox MV2 release, found manifest version ${manifest.manifest_version}`);
}
if (manifest.background?.page !== 'background.html') {
  throw new Error('Expected the Midori Privacy Firefox background page');
}

const androidVersion = `${sourceVersion}.${ANDROID_COMPATIBILITY_REVISION}`;
manifest.version = androidVersion;
manifest.browser_specific_settings ??= {};
manifest.browser_specific_settings.gecko ??= {};
manifest.browser_specific_settings.gecko.id = MIDORI_PRIVACY_EXTENSION_ID;
fs.writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`);

// uBlock treats any suffix after the three-part version as a development
// build. Our numeric fourth component is only an Android compatibility
// revision, so it must retain the stable release behavior.
replaceExact(
  'js/vapi-common.js',
  `    if ( /^\\d+\\.\\d+\\.\\d+\\D/.test(version) ) {
        soup.add('devbuild');
    }`,
  `    const isAndroidCompatibilityBuild =
        /^\\d+\\.\\d+\\.\\d+\\.\\d+$/.test(version);
    if (
        isAndroidCompatibilityBuild === false &&
        /^\\d+\\.\\d+\\.\\d+\\D/.test(version)
    ) {
        soup.add('devbuild');
    }`,
);

// The imported artifact is always a verified stable release. Pin its
// bootstrap to the packaged stable registry instead of assets.dev.json.
replaceExact(
  'js/background.js',
  `    assetsJsonPath: vAPI.webextFlavor.soup.has('devbuild')
        ? '/assets/assets.dev.json'
        : '/assets/assets.json',`,
  `    assetsJsonPath: 'assets/assets.json',`,
);

// A failed bootstrap used to persist an empty registry which was then treated
// as valid forever. Only reuse a cache containing the canonical assets entry
// and at least one stock filter source. A cache containing only My filters is
// the fingerprint of the broken Android bootstrap, not a usable registry.
replaceExact(
  'js/assets.js',
  `let assetSourceRegistryPromise;
let assetSourceRegistry = Object.create(null);

function getAssetSourceRegistry() {`,
  `let assetSourceRegistryPromise;
let assetSourceRegistry = Object.create(null);

function assetSourceRegistryIsValid(registry) {
    if (
        registry instanceof Object === false ||
        Array.isArray(registry) ||
        registry['assets.json'] instanceof Object === false
    ) {
        return false;
    }

    return Object.entries(registry).some(([ assetKey, entry ]) =>
        assetKey !== 'user-filters' &&
        entry instanceof Object &&
        entry.content === 'filters' &&
        entry.submitter !== 'user'
    );
}

function getAssetSourceRegistry() {`,
);

replaceExact(
  'js/assets.js',
  `            if ( bin instanceof Object ) {
                if ( bin.assetSourceRegistry instanceof Object ) {
                    assetSourceRegistry = bin.assetSourceRegistry;
                    ubolog('Loaded assetSourceRegistry');
                    return assetSourceRegistry;
                }
            }`,
  `            const cachedRegistry = bin instanceof Object
                ? bin.assetSourceRegistry
                : undefined;
            if ( assetSourceRegistryIsValid(cachedRegistry) ) {
                assetSourceRegistry = cachedRegistry;
                ubolog('Loaded assetSourceRegistry');
                return assetSourceRegistry;
            }
            // Preserve user-submitted sources while rebuilding built-ins.
            if (
                cachedRegistry instanceof Object &&
                Array.isArray(cachedRegistry) === false
            ) {
                assetSourceRegistry = cachedRegistry;
            }`,
);

// Refuse syntactically valid but incomplete bootstrap JSON. Otherwise `{}`
// would replace the registry and recreate the same no-filter state.
replaceExact(
  'js/assets.js',
  `    let newDict;
    try {
        newDict = JSON.parse(json);
        newDict['assets.json'].defaultListset =
            Array.from(Object.entries(newDict))
                .filter(a => a[1].content === 'filters' && a[1].off === undefined)
                .map(a => a[0]);
    } catch {
    }
    if ( newDict instanceof Object === false ) { return; }`,
  `    let newDict;
    try {
        newDict = JSON.parse(json);
    } catch {
    }
    if ( assetSourceRegistryIsValid(newDict) === false ) { return; }

    newDict['assets.json'].defaultListset =
        Array.from(Object.entries(newDict))
            .filter(a => a[1].content === 'filters' && a[1].off === undefined)
            .map(a => a[0]);`,
);

// Repair only the fingerprint left by the broken Android bootstrap: the user
// list is the sole selected and sole available list. A legitimate custom
// selection still has the full available-list registry and is left untouched.
replaceExact(
  'js/storage.js',
  `µb.loadSelectedFilterLists = async function() {
    const bin = await vAPI.storage.get('selectedFilterLists');
    if ( bin instanceof Object && Array.isArray(bin.selectedFilterLists) ) {
        this.selectedFilterLists = bin.selectedFilterLists;
        return;
    }

    // https://github.com/gorhill/uBlock/issues/747
    //   Select default filter lists if first-time launch.
    const lists = await io.metadata();
    this.saveSelectedFilterLists(this.autoSelectRegionalFilterLists(lists));
};`,
  `µb.loadSelectedFilterLists = async function() {
    const bin = await vAPI.storage.get([
        'selectedFilterLists',
        'availableFilterLists',
        'midoriAndroidFilterListsMigration',
    ]);
    if ( bin instanceof Object && Array.isArray(bin.selectedFilterLists) ) {
        this.selectedFilterLists = bin.selectedFilterLists;

        const availableFilterLists = bin.availableFilterLists;
        const availableListKeys = availableFilterLists instanceof Object
            ? Object.keys(availableFilterLists)
            : [];
        const hasBrokenAndroidBootstrapState =
            bin.midoriAndroidFilterListsMigration !== 1 &&
            this.selectedFilterLists.length === 1 &&
            this.selectedFilterLists[0] === this.userFiltersPath &&
            availableListKeys.every(key => key === this.userFiltersPath);
        if ( hasBrokenAndroidBootstrapState ) {
            const lists = await io.metadata();
            const repairedListKeys = this.autoSelectRegionalFilterLists(lists);
            if ( repairedListKeys.length > 1 ) {
                this.selfieManager.destroy();
                await Promise.all([
                    this.saveSelectedFilterLists(repairedListKeys),
                    vAPI.storage.set({ midoriAndroidFilterListsMigration: 1 }),
                ]);
            }
        }
        return;
    }

    // https://github.com/gorhill/uBlock/issues/747
    //   Select default filter lists if first-time launch.
    const lists = await io.metadata();
    this.saveSelectedFilterLists(this.autoSelectRegionalFilterLists(lists));
};`,
);

const startScript = fs.readFileSync(extensionPath('js/start.js'), 'utf8');
if (!startScript.includes("import './midori-stats.js';")) {
  throw new Error('Midori Privacy no longer loads the statistics bridge from js/start.js');
}

replaceExact(
  'js/midori-stats.js',
  "const STATS_ACTION = 'get-stats-summary';",
  `const STATS_ACTION = 'get-stats-summary';
const ALLOWED_SENDER_IDS = new Set([
    'midoritabs@astian.org',
]);
const ESTIMATED_BYTES_PER_BLOCK = 8 * 1024;`,
);

replaceExact(
  'js/midori-stats.js',
  `    const page = pageStatsFromSender(sender);

    return {`,
  `    const page = pageStatsFromSender(sender);
    const estimatedDataSavedBytes = Math.min(
        Number.MAX_SAFE_INTEGER,
        blocked * ESTIMATED_BYTES_PER_BLOCK
    );

    return {`,
);

replaceExact(
  'js/midori-stats.js',
  `        totalBlocked: blocked,
        totalRequests: blocked + allowed,`,
  `        totalBlocked: blocked,
        totalRequests: blocked + allowed,
        estimatedDataSavedBytes,
        dataSavedEstimateModel: 'conservative-8kib-per-block-v1',`,
);

replaceExact(
  'js/midori-stats.js',
  `const onExternalMessage = (request, sender, sendResponse) => {
    if ( request instanceof Object === false ) { return; }
    if ( request.action !== STATS_ACTION ) { return; }`,
  `const onExternalMessage = (request, sender, sendResponse) => {
    if ( request instanceof Object === false ) { return; }
    if ( sender instanceof Object === false ||
         ALLOWED_SENDER_IDS.has(sender.id) === false ) { return; }
    if ( request.action !== STATS_ACTION ) { return; }`,
);

console.log(`Patched Midori Privacy ${sourceVersion} for Android as ${androidVersion}`);
