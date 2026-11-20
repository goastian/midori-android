"use strict";

globalThis.MidoriLiteImageRelay = (() => {
  const pages = ["https://www.youtube.com/*", "https://m.youtube.com/*", "https://www.google.com/*", "https://developers.google.com/*", "https://unsplash.com/*", "https://www.unsplash.com/*", "https://www.bing.com/*"];
  const routes = {
    "i.ytimg.com": /^\/vi\/[A-Za-z0-9_-]{11}\/(?:default|mqdefault|hqdefault|sddefault|maxresdefault|hq720(?:_[1-3])?|oar2)\.jpg$|^\/vi_webp\/[A-Za-z0-9_-]{11}\/(?:default|mqdefault|hqdefault|sddefault|maxresdefault|hq720(?:_[1-3])?|oar2)\.webp$/,
    "www.gstatic.com": /^\/webp\/gallery\/[1-5](?:\.sm)?\.(?:jpg|png)$|^\/images\/branding\/googlelogo\/[1-4]x\/googlelogo_(?:color|light_color|white)_[0-9]{2,4}x[0-9]{2,4}dp\.png$/,
    "images.unsplash.com": /^\/photo-[0-9]{13}-[0-9a-f]{12}$/,
    "www.bing.com": /^\/th$/,
    "th.bing.com": /^\/th(?:\/id\/O(?:DF|IF|IP)\.[A-Za-z0-9_-]{20,64})?$/,
  };
  const bingWallpaper = /^OHR\.[A-Za-z0-9]{1,80}_(?:ROW|[A-Z]{2}-[A-Z]{2})[0-9]{6,14}_(?:[0-9]{2,4}x[0-9]{2,4}|tmb)\.(?:jpg|webp)$/;
  const bingThumbHosts = new Set([1, 2, 3, 4].flatMap(index => [`ts${index}.mm.bing.net`, `tse${index}.mm.bing.net`]));
  for (const host of bingThumbHosts) routes[host] = /^\/th$/;
  const pageFilter = { urls: pages, types: ["main_frame"], incognito: false };
  const unwantedHeaders = new Set(["origin", "authorization", "proxy-authorization", "range"]);
  const documents = new Map();

  function bingMobileVariant(asset) {
    const query = asset.searchParams;
    const id = asset.pathname === "/th" ? query.get("id") ?? "" : asset.pathname.slice("/th/id/".length);
    if (!/^O(?:DF|IF|IP)\.[A-Za-z0-9_-]{20,64}$/.test(id) || query.get("pid") !== "1.7") return false;
    for (const size of ["w", "h"]) {
      const value = query.get(size) ?? "";
      if (!/^\d{1,4}$/.test(value) || Number(value) < 64 || Number(value) > 2048) return false;
    }
    const seen = new Set();
    for (const [name, value] of query) {
      if (seen.has(name)) return false;
      seen.add(name);
      switch (name) {
        case "w": case "h": case "pid": break;
        case "id": if (asset.pathname !== "/th") return false; break;
        case "c": case "rs": case "p": case "r": case "o": case "rm":
          if (!/^\d{1,2}$/.test(value)) return false;
          break;
        case "dpr":
          if (!/^\d(?:\.\d{1,2})?$/.test(value) || Number(value) < 0.5 || Number(value) > 4) return false;
          break;
        default: return false;
      }
    }
    return true;
  }

  function publicVariant(asset) {
    if (asset.hostname === "www.bing.com") {
      return asset.searchParams.size === 1 && bingWallpaper.test(asset.searchParams.get("id") ?? "");
    }
    if (bingThumbHosts.has(asset.hostname)) {
      const query = asset.searchParams;
      return query.size === 2 && query.get("pid") === "15.1" &&
        /^OIP\.[A-Za-z0-9_-]{20,64}$/.test(query.get("id") ?? "");
    }
    if (asset.hostname === "th.bing.com") return bingMobileVariant(asset);
    if (!asset.search) return true;
    if (asset.hostname !== "i.ytimg.com") return false;
    const query = asset.searchParams;
    return query.size === 2 &&
      /^-oaymw[A-Za-z0-9_-]{2,114}={0,2}$/.test(query.get("sqp") ?? "") &&
      /^AOn4CL[A-Za-z0-9_-]{28}$/.test(query.get("rs") ?? "");
  }

  function allowsData(policy) {
    const directives = new Map();
    for (const directive of policy.split(";")) {
      const [name, ...sources] = directive.trim().toLowerCase().split(/\s+/);
      if (!directives.has(name)) directives.set(name, sources);
    }
    const sources = directives.get("img-src") ?? directives.get("default-src");
    return !sources || sources.includes("data:");
  }

  function install(enabled, fetchImage) {
    browser.webRequest.onBeforeRequest.addListener(details => documents.delete(details.tabId), {
      urls: ["<all_urls>"], types: ["main_frame"], incognito: false,
    });
    browser.tabs.onRemoved.addListener(tabId => documents.delete(tabId));

    browser.webRequest.onHeadersReceived.addListener(details => {
      if (details.tabId < 0 || details.statusCode !== 200 || !details.responseHeaders) return;
      const policies = details.responseHeaders
        .filter(({ name }) => name.toLowerCase() === "content-security-policy")
        .flatMap(({ value }) => value.split(","));
      if (!policies.every(allowsData)) return;
      let reportMeta;
      const meta = new Promise(resolve => { reportMeta = resolve; });
      documents.set(details.tabId, { origin: new URL(details.url).origin, meta, reportMeta, metaAllowed: undefined });
    }, pageFilter, ["responseHeaders"]);

    browser.runtime.onMessage.addListener((message, sender) => {
      if (message?.type !== "liteImagePolicy" || !sender.tab || sender.tab.incognito !== false || sender.frameId !== 0) return;
      const doc = documents.get(sender.tab.id);
      if (doc && new URL(sender.url).origin === doc.origin) {
        doc.metaAllowed = doc.metaAllowed !== false && message.policies.every(allowsData);
        doc.reportMeta();
      }
    });

    browser.webRequest.onBeforeSendHeaders.addListener(async details => {
      if (!enabled() || details.incognito !== false || details.tabId < 0 || details.frameId !== 0 ||
          details.method !== "GET" || !details.documentUrl) return;
      const doc = documents.get(details.tabId);
      if (!doc || new URL(details.documentUrl).origin !== doc.origin) return;

      const asset = new URL(details.url);
      if (asset.username || asset.password || asset.port || asset.hash || !publicVariant(asset) ||
          !routes[asset.hostname]?.test(asset.pathname)) return;
      const headers = details.requestHeaders;
      const publicBingAsset = doc.origin === "https://www.bing.com" &&
        (asset.hostname === "www.bing.com" || asset.hostname === "th.bing.com");
      if (!headers || headers.some(({ name }) => {
        const lower = name.toLowerCase();
        return unwantedHeaders.has(lower) || (lower === "cookie" && !publicBingAsset);
      }) ||
          !headers.some(({ name, value }) => name.toLowerCase() === "sec-fetch-mode" && value === "no-cors")) return;

      if (doc.metaAllowed === undefined) {
        let timeout;
        await Promise.race([doc.meta, new Promise(resolve => { timeout = setTimeout(resolve, 500); })]);
        clearTimeout(timeout);
      }
      if (!doc.metaAllowed || !enabled() || documents.get(details.tabId) !== doc) return;
      const image = await fetchImage(asset.href);
      if (image && enabled() && documents.get(details.tabId) === doc && doc.metaAllowed) return { redirectUrl: image };
    }, {
      urls: Object.keys(routes).map(host => `https://${host}/*`), types: ["image"], incognito: false,
    }, ["blocking", "requestHeaders"]);
  }

  return { install };
})();
