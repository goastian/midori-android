(() => {
  "use strict";

  const tabs = new Map();
  let policy = null;
  let nativePort = null;
  const reportId = crypto.randomUUID();
  let totalBlocked = 0;
  let totalTimer = null;
  let nextRequestId = 0;
  let decisionReceived = false;
  const requests = new Map();
  let ready;
  let markReady;
  function resetReady() { ready = new Promise(resolve => { markReady = resolve; }); }
  resetReady();

  function reportTotal() {
    try { nativePort?.postMessage({ type: "blocked_total", reportId, count: totalBlocked }); }
    catch {}
  }

  function recordTotal(details) {
    if (details.incognito !== false) return;
    totalBlocked++;
    if (totalTimer !== null) return;
    totalTimer = setTimeout(() => {
      totalTimer = null;
      reportTotal();
    }, 75);
  }

  function deadline(promise, milliseconds, fallback) {
    let timer;
    return Promise.race([promise, new Promise(resolve => { timer = setTimeout(() => resolve(fallback), milliseconds); })])
      .finally(() => clearTimeout(timer));
  }

  async function requestDecision(message) {
    const port = nativePort;
    if (!port) return null;
    const requestId = ++nextRequestId;
    const response = new Promise(resolve => { requests.set(requestId, resolve); });
    try {
      port.postMessage({ type: "network_request", requestId, ...message });
      const result = await deadline(response, decisionReceived ? 1500 : 4000, null);
      if (typeof result?.cancel === "boolean") decisionReceived = true;
      return result;
    } catch { return null; }
    finally { requests.delete(requestId); }
  }

  function policyKey(value) {
    return JSON.stringify([value?.level, value?.revision, Object.entries(value?.siteLevels || {}).sort()]);
  }

  function stateFor(tabId, siteUrl) {
    if (!tabs.has(tabId)) tabs.set(tabId, { siteUrl, count: 0, cache: new Map(), timer: null, frames: new Map() });
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
    if (details.type === "sub_frame") state.frames.delete(details.frameId);
    if (!policy) await deadline(ready, 2000, null);
    if (levelFor(siteUrl) === "OFF") return {};
    const requestPolicy = policy;

    const originUrl = details.documentUrl || details.originUrl || siteUrl;
    const key = `${details.type}\n${details.url}\n${originUrl}`;
    let cancel = state.cache.get(key);
    if (cancel === undefined) {
      try {
        const response = await requestDecision({ url: details.url, siteUrl, originUrl, resourceType: details.type });
        if (policy !== requestPolicy || tabs.get(details.tabId) !== state) return {};
        if (typeof response?.cancel !== "boolean") return {};
        cancel = response.cancel;
        if (state.cache.size >= 512) state.cache.clear();
        state.cache.set(key, cancel);
      } catch { return {}; }
    }
    if (cancel && tabs.get(details.tabId) === state) {
      state.count++;
      recordTotal(details);
      notifyCount(details.tabId, state);
    }
    return cancel ? { cancel: true } : {};
  }

  function connect() {
    try {
      const port = browser.runtime.connectNative("midori_network");
      nativePort = port;
      port.onMessage.addListener(message => {
        if (nativePort !== port) return;
        if (message?.type === "network_response") {
          requests.get(message.requestId)?.(message);
          return;
        }
        if (message?.type !== "network_policy") return;
        if (policyKey(policy) !== policyKey(message)) {
          policy = message;
          for (const state of tabs.values()) state.cache.clear();
        }
        markReady();
        port.postMessage({ type: "network_policy_applied", revision: message.revision });
      });
      port.onDisconnect.addListener(() => {
        if (nativePort !== port) return;
        nativePort = null;
        decisionReceived = false;
        for (const resolve of requests.values()) resolve(null);
        requests.clear();
        policy = null;
        markReady();
        resetReady();
        setTimeout(connect, 1000);
      });
      port.postMessage({ type: "ready" });
      reportTotal();
    } catch { setTimeout(connect, 1000); }
  }
  connect();

  function updatePlacements(message, sender) {
    const tabId = sender.tab.id;
    const state = stateFor(tabId, sender.tab.url);
    const frameId = sender.frameId;
    if (!state.frames.has(frameId)) state.frames.set(frameId, { css: "", task: Promise.resolve(), url: sender.url });
    const frame = state.frames.get(frameId);
    if (frame.url !== sender.url) { frame.url = sender.url; frame.css = ""; }
    frame.task = frame.task.catch(() => {}).then(async () => {
      if (tabs.get(tabId) !== state) return;
      if (state.frames.get(frameId) !== frame || frame.url !== sender.url) return;
      if (message.siteUrl?.split("#")[0] !== (sender.url || sender.tab.url)?.split("#")[0]) return;
      const code = message.enabled === true && Array.isArray(message.selectors) && message.selectors.length
        ? message.selectors.map(selector => `${selector} { display: none !important; }`).join("\n") : "";
      if (code === frame.css) return;
      if (frame.css) await browser.tabs.removeCSS(tabId, { code: frame.css, cssOrigin: "user", frameId });
      frame.css = "";
      if (code) {
        await browser.tabs.insertCSS(tabId, { code, cssOrigin: "user", frameId, runAt: "document_start" });
        frame.css = code;
      }
    });
    return frame.task;
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
    if (!sender.tab) return undefined;
    if (message?.type === "ad_placement_policy") return updatePlacements(message, sender);
    if (sender.frameId !== 0) return undefined;
    if (message?.type !== "get_blocked_count") return undefined;
    const state = tabs.get(sender.tab.id);
    return Promise.resolve({ type: "blocked_count", count: state?.count || 0, siteUrl: state?.siteUrl || sender.tab.url });
  });
})();
