(() => {
  "use strict";

  const port = browser.runtime.connectNative("midori_content_blocking");
  const style = document.createElement("style");
  let enabled = false;
  const observer = new MutationObserver(attachStyle);

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
      if (site.href === current.href) port.postMessage({ type: "blocked_count", count: message.count, siteUrl: current.href });
    } catch {}
  }

  port.onMessage.addListener(message => {
    if (message?.type !== "ad_placement_policy") return;
    browser.runtime.sendMessage({ ...message, siteUrl: location.href }).catch(() => {});
    enabled = message.enabled === true;
    observer.disconnect();
    if (enabled) {
      style.textContent = `${message.selectors.join(",")} { display: none !important; }`;
      observer.observe(document, { childList: true, subtree: true });
      attachStyle();
    } else {
      style.remove();
    }
  });
  port.onDisconnect.addListener(() => {
    enabled = false;
    observer.disconnect();
    style.remove();
    browser.runtime.sendMessage({ type: "ad_placement_policy", enabled: false, siteUrl: location.href }).catch(() => {});
  });
  browser.runtime.onMessage.addListener(reportCount);
  browser.runtime.sendMessage({ type: "get_blocked_count" }).then(reportCount).catch(() => {});
  port.postMessage({ type: "ready" });
})();
