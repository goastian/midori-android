"use strict";

let reported = false;

function reportImagePolicy() {
  const policies = Array.from(document.head?.querySelectorAll("meta[http-equiv]") ?? [])
    .filter(meta => meta.httpEquiv.toLowerCase() === "content-security-policy")
    .map(meta => meta.content);
  browser.runtime.sendMessage({ type: "liteImagePolicy", policies }).catch(() => {});
  reported = true;
}

if (document.body || document.readyState !== "loading") {
  reportImagePolicy();
} else {
  document.addEventListener("DOMContentLoaded", () => {
    if (!reported) reportImagePolicy();
  }, { once: true });
}

new MutationObserver(records => {
  if (!reported) {
    if (document.body) reportImagePolicy();
    return;
  }
  if (records.some(record => {
    const isMeta = node => node.nodeName === "META";
    return isMeta(record.target) || [...record.addedNodes, ...record.removedNodes].some(isMeta);
  })) reportImagePolicy();
}).observe(document, { subtree: true, childList: true, attributes: true, attributeFilter: ["http-equiv", "content"] });
