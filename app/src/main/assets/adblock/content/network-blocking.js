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
  let policyApplied = false;
  let policyTask = Promise.resolve();
  const requests = new Map();
  const responsePolicies = new Map();
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
    return JSON.stringify([value?.level, value?.revision, Object.entries(value?.siteLevels || {}).sort(), value?.scriptlets]);
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
    responsePolicies.delete(details.requestId);
    if (details.tabId < 0) return {};
    const main = details.type === "main_frame";
    if (main) {
      await deadline(ready, 4000, null);
      clearTimeout(tabs.get(details.tabId)?.timer);
      tabs.delete(details.tabId);
      stateFor(details.tabId, details.url);
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
    const method = details.method || "GET";
    const key = `${details.type}\n${method}\n${details.url}\n${originUrl}`;
    let decision = state.cache.get(key);
    if (decision === undefined) {
      try {
        const response = await requestDecision({ url: details.url, siteUrl, originUrl, resourceType: details.type, method });
        if (policy !== requestPolicy || tabs.get(details.tabId) !== state) return {};
        if (typeof response?.cancel !== "boolean") return {};
        decision = { cancel: response.cancel, blocked: response.blocked === true };
        if (typeof response.csp === "string" && response.csp.length <= 65536 && !/[\r\n]/.test(response.csp)) decision.csp = response.csp;
        if (typeof response.redirectUrl === "string" && response.redirectUrl !== details.url &&
            /^(?:https?:|data:)/.test(response.redirectUrl)) decision.redirectUrl = response.redirectUrl;
        if (state.cache.size >= 512) state.cache.clear();
        state.cache.set(key, decision);
      } catch { return {}; }
    }
    if (decision.csp && details.requestId && !decision.redirectUrl) {
      if (responsePolicies.size >= 2048) responsePolicies.clear();
      responsePolicies.set(details.requestId, { csp: decision.csp, policy: requestPolicy, tabId: details.tabId, siteUrl });
    }
    if (main && (decision.cancel || decision.blocked)) return {};
    if ((decision.cancel || decision.blocked) && tabs.get(details.tabId) === state) {
      state.count++;
      recordTotal(details);
      notifyCount(details.tabId, state);
    }
    if (decision.cancel) return { cancel: true };
    return decision.redirectUrl ? { redirectUrl: decision.redirectUrl } : {};
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
        if (typeof message.scriptlets === "string") {
          try { message = { ...message, scriptlets: JSON.parse(message.scriptlets) }; }
          catch (error) { port.postMessage({ type: "scriptlet_policy_failed", error: String(error) }); return; }
        }
        if (policyKey(policy) !== policyKey(message)) {
          if (policyApplied) resetReady();
          policyApplied = false;
          policy = message;
          for (const state of tabs.values()) state.cache.clear();
        }
        const applying = policy;
        policyTask = policyTask.catch(() => {}).then(async () => {
          try {
            if (message.scriptlets) await MidoriScriptlets.update(message.scriptlets);
            if (policy === applying) { policyApplied = true; markReady(); }
            port.postMessage({ type: "network_policy_applied", revision: message.revision, policyId: message.policyId });
          } catch (error) {
            port.postMessage({ type: "scriptlet_policy_failed", error: String(error) });
          }
        });
      });
      port.onDisconnect.addListener(() => {
        if (nativePort !== port) return;
        nativePort = null;
        decisionReceived = false;
        for (const resolve of requests.values()) resolve(null);
        requests.clear();
        policy = null;
        policyApplied = false;
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
    if (!state.frames.has(frameId) || state.frames.get(frameId).documentId !== message.documentId) {
      state.frames.set(frameId, { css: "", task: Promise.resolve(), url: sender.url, documentId: message.documentId });
    }
    const frame = state.frames.get(frameId);
    if (frame.url !== sender.url) { frame.url = sender.url; frame.css = ""; }
    frame.task = frame.task.catch(() => {}).then(async () => {
      if (tabs.get(tabId) !== state) return;
      if (state.frames.get(frameId) !== frame || frame.url !== sender.url) return;
      if (message.siteUrl?.split("#")[0] !== (sender.url || sender.tab.url)?.split("#")[0]) return;
      const selectors = Array.isArray(message.selectors) ? message.selectors : [];
      const styles = Array.isArray(message.styles) ? message.styles.filter(item =>
        typeof item?.selector === "string" && !/[{}\r\n]/.test(item.selector) &&
        typeof item.declarations === "string" && !/[{}\r\n@]|url\s*\(/i.test(item.declarations)) : [];
      const code = message.enabled === true ? selectors.map(selector => `${selector} { display: none !important; }`)
        .concat(styles.map(item => `${item.selector} { ${item.declarations} }`)).join("\n") : "";
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

  browser.webRequest.onBeforeRequest.addListener(intercept, { urls: ["http://*/*", "https://*/*", "ws://*/*", "wss://*/*"] }, ["blocking"]);
  browser.webRequest.onHeadersReceived?.addListener(details => {
    const response = responsePolicies.get(details.requestId);
    responsePolicies.delete(details.requestId);
    if (!response || policy !== response.policy || levelFor(response.siteUrl) === "OFF") return {};
    return { responseHeaders: [...(details.responseHeaders || []), { name: "Content-Security-Policy", value: response.csp }] };
  }, { urls: ["http://*/*", "https://*/*"], types: ["main_frame", "sub_frame"] }, ["blocking", "responseHeaders"]);
  const forgetResponse = details => { responsePolicies.delete(details.requestId); };
  browser.webRequest.onCompleted?.addListener(forgetResponse, { urls: ["<all_urls>"] });
  browser.webRequest.onErrorOccurred?.addListener(forgetResponse, { urls: ["<all_urls>"] });
  browser.tabs.onRemoved.addListener(tabId => {
    clearTimeout(tabs.get(tabId)?.timer);
    tabs.delete(tabId);
    for (const [id, response] of responsePolicies) if (response.tabId === tabId) responsePolicies.delete(id);
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
