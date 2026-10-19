(() => {
  "use strict";

  const port = browser.runtime.connectNative("midori_content_blocking");
  const documentId = Array.from(crypto.getRandomValues(new Uint32Array(4)), value => value.toString(16)).join("-");
  const style = document.createElement("style");
  let enabled = false;
  let portId = null;
  let selectors = [], styles = [], queryTimer = null, tokenKey = "";
  const procedural = globalThis.MidoriCosmetics?.create(value => { styles = value; render(); });
  const observer = new MutationObserver(() => { attachStyle(); scheduleQuery(); });

  function render() {
    const code = selectors.map(selector => `${selector} { display: none !important; }`)
      .concat(styles.map(item => `${item.selector} { ${item.declarations} }`)).join("\n");
    if (style.textContent !== code) style.textContent = code;
    browser.runtime.sendMessage({ type: "ad_placement_policy", enabled, selectors, styles, documentId, siteUrl: location.href }).catch(() => {});
    attachStyle();
  }

  function scheduleQuery() {
    if (!enabled || queryTimer !== null) return;
    queryTimer = setTimeout(() => {
      queryTimer = null;
      const classes = new Set(), ids = new Set();
      for (const element of document.querySelectorAll("[class], [id]")) {
        if (classes.size < 2048) for (const value of element.classList) if (value.length <= 256) classes.add(value);
        if (ids.size < 2048 && element.id && element.id.length <= 256) ids.add(element.id);
        if (classes.size >= 2048 && ids.size >= 2048) break;
      }
      const query = { type: "cosmetic_query", classes: [...classes].slice(0, 2048).sort(), ids: [...ids].sort() };
      const key = JSON.stringify(query);
      if (key !== tokenKey) { tokenKey = key; port.postMessage({ ...query, portId }); }
    }, 200);
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
    enabled = message.enabled === true;
    observer.disconnect();
    procedural?.update(enabled && Array.isArray(message.procedural) ? message.procedural.filter(value => typeof value === "string").sort() : []);
    render();
    if (enabled) {
      observer.observe(document, { childList: true, subtree: true, attributes: true, attributeFilter: ["class", "id"] });
      attachStyle();
      scheduleQuery();
    } else {
      clearTimeout(queryTimer); queryTimer = null; tokenKey = "";
      style.remove();
    }
  });
  port.onDisconnect.addListener(() => {
    enabled = false;
    observer.disconnect();
    clearTimeout(queryTimer);
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
