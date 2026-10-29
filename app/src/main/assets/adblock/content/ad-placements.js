(() => {
  "use strict";

  const port = browser.runtime.connectNative("midori_content_blocking");
  const documentId = Array.from(crypto.getRandomValues(new Uint32Array(4)), value => value.toString(16)).join("-");
  const style = document.createElement("style");
  let enabled = false;
  let portId = null;
  let selectors = [], styles = [], queryTimer = null, tokenKey = "";
  const procedural = globalThis.MidoriCosmetics?.create(value => { styles = value; render(); });
  const roots = new Set();
  const classes = new Set(), ids = new Set();
  let scanning = null, pruning = null, needsPrune = false;
  const observer = new MutationObserver(records => {
    attachStyle();
    needsPrune = true;
    for (const record of records) {
      if (record.type === "attributes") queueRoot(record.target);
      else for (const node of record.addedNodes) if (node.nodeType === 1) queueRoot(node);
    }
    scheduleQuery();
  });

  function queueRoot(root) {
    if (root === style || roots.has(document)) return;
    if (roots.size >= 64) { roots.clear(); roots.add(document); }
    else roots.add(root);
  }

  function* pruneTokens() {
    for (const value of classes) {
      if (!document.getElementsByClassName(value).length) classes.delete(value);
      yield;
    }
    for (const value of ids) {
      if (!document.getElementById(value)) ids.delete(value);
      yield;
    }
  }

  function* elements(root) {
    if (root.nodeType === 1) yield root;
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT);
    let node;
    while ((node = walker.nextNode())) yield node;
  }

  function render() {
    const code = selectors.map(selector => `${selector} { display: none !important; }`)
      .concat(styles.map(item => `${item.selector} { ${item.declarations} }`)).join("\n");
    if (style.textContent !== code) style.textContent = code;
    browser.runtime.sendMessage({ type: "ad_placement_policy", enabled, selectors, styles, documentId, siteUrl: location.href }).catch(() => {});
    attachStyle();
  }

  function scheduleQuery(delay = 200) {
    if (!enabled || queryTimer !== null) return;
    queryTimer = setTimeout(() => {
      queryTimer = null;
      const deadline = performance.now() + 6;
      let visited = 0;
      if (needsPrune && !pruning) { needsPrune = false; pruning = pruneTokens(); }
      while (pruning || scanning || roots.size) {
        if (pruning) {
          if (pruning.next().done) pruning = null;
          if (++visited >= 256 || performance.now() >= deadline) break;
          continue;
        }
        if (!scanning) {
          const root = roots.values().next().value;
          roots.delete(root);
          if (root !== document && !root.isConnected) continue;
          scanning = elements(root);
        }
        const next = scanning.next();
        if (next.done) { scanning = null; continue; }
        const element = next.value;
        if (classes.size < 2048) for (const value of element.classList) {
          if (classes.size >= 2048) break;
          if (value.length <= 256) classes.add(value);
        }
        if (ids.size < 2048 && element.id && element.id.length <= 256) ids.add(element.id);
        if (classes.size >= 2048 && ids.size >= 2048) { roots.clear(); scanning = null; break; }
        if (++visited >= 256 || performance.now() >= deadline) break;
      }
      const query = { type: "cosmetic_query", classes: [...classes].slice(0, 2048).sort(), ids: [...ids].sort() };
      const key = JSON.stringify(query);
      if (portId && key !== tokenKey) { tokenKey = key; port.postMessage({ ...query, portId }); }
      if (pruning || needsPrune || scanning || roots.size) scheduleQuery(16);
    }, delay);
  }

  function attachStyle() {
    if (!enabled || style.isConnected) return;
    (document.head || document.documentElement)?.append(style);
  }

  function reportCount(message) {
    if (message?.type !== "blocked_count") return;
    try {
      const site = new URL(message.siteUrl);
      const current = new URL(location.href);
      site.hash = "";
      current.hash = "";
      if (site.href === current.href) port.postMessage({ type: "blocked_count", count: message.count, siteUrl: current.href, portId });
    } catch {}
  }

  port.onMessage.addListener(message => {
    if (message?.type === "content_port") {
      portId = message.portId;
      port.postMessage({ type: "ready", portId });
      return;
    }
    if (message?.type !== "ad_placement_policy") return;
    selectors = Array.isArray(message.selectors) ? message.selectors.filter(selector =>
      typeof selector === "string" && !/[{}\r\n]/.test(selector) && CSS.supports(`selector(${selector})`)
    ).sort() : [];
    const wasEnabled = enabled;
    enabled = message.enabled === true;
    observer.disconnect();
    procedural?.update(enabled && Array.isArray(message.procedural) ? message.procedural.filter(value => typeof value === "string").sort() : []);
    render();
    if (enabled) {
      observer.observe(document, { childList: true, subtree: true, attributes: true, attributeFilter: ["class", "id"] });
      attachStyle();
      if (!wasEnabled) queueRoot(document);
      scheduleQuery();
    } else {
      clearTimeout(queryTimer); queryTimer = null; tokenKey = "";
      roots.clear(); scanning = null; pruning = null; needsPrune = false; classes.clear(); ids.clear();
      style.remove();
    }
  });
  port.onDisconnect.addListener(() => {
    enabled = false;
    observer.disconnect();
    clearTimeout(queryTimer);
    roots.clear(); scanning = null; pruning = null; needsPrune = false; classes.clear(); ids.clear();
    procedural?.disconnect();
    style.remove();
    browser.runtime.sendMessage({ type: "ad_placement_policy", enabled: false, documentId, siteUrl: location.href }).catch(() => {});
  });
  if (window === window.top) {
    browser.runtime.onMessage.addListener(reportCount);
    browser.runtime.sendMessage({ type: "get_blocked_count" }).then(reportCount).catch(() => {});
  }
  port.postMessage({ type: "ready" });
})();
