
const nativePort = browser.runtime.connectNative("midori_browser");


const storageCache = {
    disabledHost: []
}

const initStorageCache = chrome.storage.local.get().then((items) => {
    Object.assign(storageCache, items);
});

// Never called on android
// Not sure what it is supposed to do ...
chrome.runtime.onConnect.addListener(async () => {
    try {
        await initStorageCache;
    } catch (e) {
        console.error(e);
    }
});

const ruleset_ids = [
    
    "filter_unblocking_search_ads_and_self_promotion",
    
    "easy_privacy_optimized",
    
    "adblock_warning_removal",
    
    "fanboy_annoyance",
    
];

function tabInfoToStatus(host, enabled) {
    return {
        action: "tabStatus",
        protectionEnabled: enabled,
        tabUrl: host,
    };
}

// Activate or deactivate the extension whenever the active tab changes
chrome.tabs.onActivated.addListener(async (details) => {
    try {
        const tab = await chrome.tabs.get(details.tabId);
        const host = urlToHostname(tab.url);
        const hostDisabledIndex = storageCache.disabledHost.indexOf(host);

        if (hostDisabledIndex > -1) {
            await disableRuleSets();
        } else {
            await enableRuleSets();
        }
    } catch (err) {
        // Ignored
    }
});


nativePort.onMessage.addListener(function (msg) {
    // Only asked when the app starts, for the first tab
    // as it may be cached and not go through 'onBeforeNavigate' event
    // Also, the url is given by the app as the 'selected tab' is not set yet on the extension side
    if (msg.action == 'askTabStatusForUrl') {
        const host = urlToHostname(msg.selectedTabUrl);
        const enabled = !storageCache.disabledHost.includes(host);
        // Only run disable if the url should be disabled (as it is enabled by default)
        if (!enabled) {
            disableRuleSets().then(() => {
                // Not sure this is needed, as the webpage is originally cached with already
                // the right setting, but keeping it to be sure
                chrome.tabs.reload(tab.id);
            });
        }
    } else {
        onMessageReceived(msg, nativePort);
    }
});


// Activate or deactivate the extension whenever the popup toggle is clicked,
// This will update the block list in chrome storage and reload the page if needed
function onMessageReceived(msg, port) {
    getCurrentTab().then(tab => {
        const host = urlToHostname(tab.url);
        const hostDisabledIndex = storageCache.disabledHost.indexOf(host);

        if (!host) {
            // remove notify for android (as it is done within updateIcon)
            
            updateIcon(true);
        } else if (msg.action === 'toggleProtection') {
            if (hostDisabledIndex > -1) {
                storageCache.disabledHost.splice(hostDisabledIndex, 1)
                enableRuleSets().then(() => {
                    // remove notify for android (as it is done within updateIcon from enable)
                    
                    chrome.tabs.reload(tab.id);
                });
            } else {
                storageCache.disabledHost.push(host)
                disableRuleSets().then(() => {
                    // remove notify for android (as it is done within updateIcon from disable)
                    
                    chrome.tabs.reload(tab.id);
                });
            }
        } else if (msg.action === 'askTabStatus') {
            const enabled = !storageCache.disabledHost.includes(host);
            updateIcon(enabled)
            notifyToggled(tabInfoToStatus(host, enabled), port)
        } else {
          console.error('Unknown action', msg)
        }
    })
}

function notifyToggled(response, port) {
  storageCache.disabledHost = removeDuplicates(storageCache.disabledHost)
  chrome.storage.local.set(storageCache);
  port.postMessage(response);
}

// Before navigating on the main frame check either the current host is protected or not
chrome.webNavigation.onBeforeNavigate.addListener(async (details) => {
    try {
        // For firefox and android, use details.frameId !== 0. 
        // Could be used also for chrome based browser I guess.
        
        if (details.frameId !== 0) {
        
            return
        }

        const host = urlToHostname(details.url);
        const hostDisabledIndex = storageCache.disabledHost.indexOf(host);

        if (hostDisabledIndex > -1) {
            await disableRuleSets();
        } else {
            await enableRuleSets();
        }
    } catch (err) {
        // Ignore
    }
});


async function disableRuleSets() {
    await updateIcon(false);
    await chrome.declarativeNetRequest.updateEnabledRulesets({
        disableRulesetIds: ruleset_ids,
        enableRulesetIds: [],
    });
}

async function enableRuleSets() {
    await updateIcon(true);
    await chrome.declarativeNetRequest.updateEnabledRulesets({
        disableRulesetIds: [],
        enableRulesetIds: ruleset_ids,
    });
}

async function getCurrentTab() {
    const queryOptions = {active: true, lastFocusedWindow: true};
    const [tab] = await chrome.tabs.query(queryOptions);
    return tab;
}

function urlToHostname(rawUrl) {
    if (!rawUrl) {
        return;
    }

    const url = new URL(rawUrl);
    return url.host;
}


function removeDuplicates(arr) {
    return arr.filter((item, index) => arr.indexOf(item) === index);
}

async function updateIcon(enabled) {
    
    notifyToggled({
        action: "tabStatus",
        protectionEnabled: enabled
    }, nativePort);
    
}

// @formatter:off

// @formatter:on
