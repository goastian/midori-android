"use strict";

globalThis.MidoriLitePublicImages = (() => {
  const filter = { urls: ["https://images.unsplash.com/photo-*"], types: ["image"] };
  const parameters = new Set(["auto", "crop", "cs", "dpr", "fit", "fm", "h", "ixid", "ixlib", "q", "w"]);
  const formats = new Set(["avif", "jpeg", "jpg", "webp"]);
  const quality = 65;

  function lighterPhoto(address) {
    const url = new URL(address);
    if (url.origin !== "https://images.unsplash.com" || url.username || url.password ||
        !/^\/photo-\d{13}-[0-9a-f]{12}$/.test(url.pathname)) return;

    const seen = new Set();
    for (const key of url.searchParams.keys()) {
      if (!parameters.has(key) || seen.has(key)) return;
      seen.add(key);
    }

    const original = url.searchParams.get("q");
    const format = url.searchParams.get("fm");
    if (!/^\d{1,3}$/.test(original ?? "") || Number(original) <= quality || Number(original) > 100 ||
        (format !== null && !formats.has(format))) return;

    url.searchParams.set("q", String(quality));
    return url.href;
  }

  function install(enabled) {
    const attempts = new Map();

    browser.webRequest.onBeforeRequest.addListener(details => {
      if (!enabled() || details.tabId < 0 || details.method !== "GET" ||
          attempts.has(details.requestId) || attempts.size >= 128) return;
      const optimized = lighterPhoto(details.url);
      if (!optimized) return;
      attempts.set(details.requestId, { original: details.url, optimized });
      return { redirectUrl: optimized };
    }, filter, ["blocking"]);

    browser.webRequest.onHeadersReceived.addListener(details => {
      const attempt = attempts.get(details.requestId);
      if (attempt?.optimized !== details.url || details.statusCode === 304) return;
      const type = details.responseHeaders.find(header => header.name.toLowerCase() === "content-type")?.value;
      if (details.statusCode === 200 && /^image\/(avif|jpeg|webp)(?:;|$)/i.test(type ?? "")) return;
      attempt.optimized = null;
      return { redirectUrl: attempt.original };
    }, filter, ["blocking", "responseHeaders"]);

    const forget = details => attempts.delete(details.requestId);
    browser.webRequest.onCompleted.addListener(forget, filter);
    browser.webRequest.onErrorOccurred.addListener(forget, filter);
  }

  return { install };
})();
