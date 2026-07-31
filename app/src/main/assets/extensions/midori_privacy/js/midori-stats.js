/*******************************************************************************

    Midori Privacy - lightweight cross-extension statistics bridge

    This exposes only counters which uBlock Origin already keeps in memory.
    It deliberately avoids storage reads, tab queries and filter-list scans so
    the New Tab widget can request a snapshot without creating background work.

*/

import µb from './background.js';

/******************************************************************************/

const STATS_ACTION = 'get-stats-summary';
const ALLOWED_SENDER_IDS = new Set([
    'midoritabs@astian.org',
]);
const ESTIMATED_BYTES_PER_BLOCK = 8 * 1024;

const toCount = value => {
    return Number.isFinite(value) && value > 0 ? Math.trunc(value) : 0;
};

const pageStatsFromSender = sender => {
    const tabId = sender && sender.tab && sender.tab.id;
    if ( Number.isInteger(tabId) === false ) { return; }

    const pageStore = µb.pageStoreFromTabId(tabId);
    if ( pageStore === null ) { return; }

    const { allowed, blocked } = pageStore.counts;
    return {
        blocked: toCount(blocked.any),
        requests: toCount(blocked.any) + toCount(allowed.any),
        categories: {
            scripts: toCount(blocked.script),
            frames: toCount(blocked.frame),
            xhr: toCount(blocked.xhr),
            images: toCount(blocked.image),
            media: toCount(blocked.media),
            fonts: toCount(blocked.font),
            other: toCount(blocked.other),
        },
    };
};

const getStatsSummary = sender => {
    const blocked = toCount(µb.requestStats.blockedCount);
    const allowed = toCount(µb.requestStats.allowedCount);
    const page = pageStatsFromSender(sender);
    const estimatedDataSavedBytes = Math.min(
        Number.MAX_SAFE_INTEGER,
        blocked * ESTIMATED_BYTES_PER_BLOCK
    );

    return {
        schemaVersion: 2,
        source: 'midori-privacy-ublock',
        state: µb.readyToFilter === true ? 'ready' : 'loading',
        enabled: true,
        totalBlocked: blocked,
        totalRequests: blocked + allowed,
        estimatedDataSavedBytes,
        dataSavedEstimateModel: 'conservative-8kib-per-block-v1',
        pageBlocked: page && page.blocked || 0,
        pageRequests: page && page.requests || 0,
        categories: page && page.categories || {},
    };
};

const onExternalMessage = (request, sender, sendResponse) => {
    if ( request instanceof Object === false ) { return; }
    if ( sender instanceof Object === false ||
         ALLOWED_SENDER_IDS.has(sender.id) === false ) { return; }
    if ( request.action !== STATS_ACTION ) { return; }

    sendResponse(getStatsSummary(sender));
};

if ( browser.runtime.onMessageExternal instanceof Object ) {
    browser.runtime.onMessageExternal.addListener(onExternalMessage);
}

