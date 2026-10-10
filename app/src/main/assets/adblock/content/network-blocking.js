(() => {
  "use strict";

  const tabs = new Map();
  let policy = null;
  let ready;
  let markReady;
  function resetReady() { ready = new Promise(resolve => { markReady = resolve; }); }
  resetReady();

  function deadline(promise, milliseconds, fallback) {
    let timer;
    return Promise.race([promise, new Promise(resolve => { timer = setTimeout(() => resolve(fallback), milliseconds); })])
      .finally(() => clearTimeout(timer));
  }

  function stateFor(tabId, siteUrl) {
    if (!tabs.has(tabId)) tabs.set(tabId, { siteUrl, count: 0, cache: new Map(), timer: null, css: "", cssTask: Promise.resolve() });
    return tabs.get(tabId);
  }

  function notifyCount(tabId, state) {
    if (state.timer !== null) return;
    state.timer = setTimeout(() => {
      state.timer = null;
      if (tabs.get(tabId) !== state) return;
      browser.tabs.sendMessage(tabId, { type: "blocked_count", count: state.count, siteUrl: state.siteUrl }, { frameId: 0 })
        .catch(() => {});
    }, 75);
  }

  function levelFor(siteUrl) {
    try {
      const url = new URL(siteUrl);
      if (url.protocol !== "http:" && url.protocol !== "https:") return "OFF";
      return policy?.siteLevels[url.hostname] || policy?.level || "OFF";
    } catch { return "OFF"; }
  }

  function isWebPage(siteUrl) {
    try {
      const protocol = new URL(siteUrl).protocol;
      return protocol === "http:" || protocol === "https:";
    } catch { return false; }
  }

  async function intercept(details) {
    if (details.tabId < 0) return {};
    if (details.type === "main_frame") {
      clearTimeout(tabs.get(details.tabId)?.timer);
      tabs.delete(details.tabId);
      stateFor(details.tabId, details.url);
      return {};
    }

    const ancestor = details.frameAncestors?.find(frame => frame.frameId === 0)?.url;
    let siteUrl = ancestor || tabs.get(details.tabId)?.siteUrl;
    if (!siteUrl) {
      try { siteUrl = (await browser.tabs.get(details.tabId)).url; }
      catch { return {}; }
    }
    if (!isWebPage(siteUrl)) return {};
    const state = stateFor(details.tabId, siteUrl);
    if (!policy) await deadline(ready, 2000, null);
    if (levelFor(siteUrl) === "OFF") return {};
    const requestPolicy = policy;

    const originUrl = details.documentUrl || details.originUrl || siteUrl;
    const key = `${details.type}\n${details.url}\n${originUrl}`;
    let cancel = state.cache.get(key);
    if (cancel === undefined) {
      try {
        const response = await deadline(browser.runtime.sendNativeMessage("midori_network", {
          type: "network_request", url: details.url, siteUrl, originUrl, resourceType: details.type,
        }), 1500, null);
        if (policy !== requestPolicy || tabs.get(details.tabId) !== state) return {};
        if (typeof response?.cancel !== "boolean") return {};
        cancel = response.cancel;
        if (state.cache.size >= 512) state.cache.clear();
        state.cache.set(key, cancel);
      } catch { return {}; }
    }
    if (cancel && tabs.get(details.tabId) === state) {
      state.count++;
      notifyCount(details.tabId, state);
    }
    return cancel ? { cancel: true } : {};
  }

  function connect() {
    try {
      const port = browser.runtime.connectNative("midori_network");
      port.onMessage.addListener(message => {
        if (message?.type !== "network_policy") return;
        policy = message;
        for (const state of tabs.values()) state.cache.clear();
        markReady();
      });
      port.onDisconnect.addListener(() => {
        policy = null;
        markReady();
        resetReady();
        setTimeout(connect, 1000);
      });
      port.postMessage({ type: "ready" });
    } catch { setTimeout(connect, 1000); }
  }
  connect();

  function updatePlacements(message, sender) {
    const tabId = sender.tab.id;
    const state = stateFor(tabId, sender.tab.url);
    state.cssTask = state.cssTask.catch(() => {}).then(async () => {
      if (tabs.get(tabId) !== state) return;
      if (message.siteUrl?.split("#")[0] !== state.siteUrl?.split("#")[0]) return;
      const code = message.enabled === true && Array.isArray(message.selectors) && message.selectors.length
        ? `${message.selectors.join(",")} { display: none !important; }` : "";
      if (code === state.css) return;
      if (state.css) await browser.tabs.removeCSS(tabId, { code: state.css, cssOrigin: "user", frameId: 0 });
      state.css = "";
      if (code) {
        await browser.tabs.insertCSS(tabId, { code, cssOrigin: "user", frameId: 0, runAt: "document_start" });
        state.css = code;
      }
    });
    return state.cssTask;
  }

  browser.webRequest.onBeforeRequest.addListener(intercept, { urls: ["http://*/*", "https://*/*"] }, ["blocking"]);
  browser.tabs.onRemoved.addListener(tabId => {
    clearTimeout(tabs.get(tabId)?.timer);
    tabs.delete(tabId);
  });
  browser.tabs.onUpdated.addListener((tabId, change) => {
    if (change.url && tabs.has(tabId)) {
      tabs.get(tabId).siteUrl = change.url;
      tabs.get(tabId).cache.clear();
    }
  });
  browser.runtime.onMessage.addListener((message, sender) => {
    if (sender.frameId !== 0 || !sender.tab) return undefined;
    if (message?.type === "ad_placement_policy") return updatePlacements(message, sender);
    if (message?.type !== "get_blocked_count") return undefined;
    const state = tabs.get(sender.tab.id);
    return Promise.resolve({ type: "blocked_count", count: state?.count || 0, siteUrl: state?.siteUrl || sender.tab.url });
  });
})();
