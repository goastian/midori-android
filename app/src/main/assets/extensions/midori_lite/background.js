"use strict";

const filter = { urls: ["http://*/*"], types: ["main_frame", "sub_frame", "image", "font", "stylesheet", "script"] };
const challenged = new Set();
const excludedSaveDataHeaders = new Set(["save-data", "range", "authorization"]);
let server = null;
let dataSaving = false;
let requestSiteSavings = false;
let relayAvailable = false;
let nativePort = null;
let imageRequestId = 0;
const imageRequests = new Map();

function isPublicHost(host) {
  const name = host.toLowerCase().replace(/\.$/, "");
  if (!name.includes(".") || name.includes(":") || /\.(localhost|local|internal|onion|lan|home|arpa)$/.test(name)) {
    return false;
  }
  const ip = /^(\d+)\.(\d+)\.(\d+)\.(\d+)$/.exec(name);
  if (!ip) return true;
  const first = Number(ip[1]);
  const second = Number(ip[2]);
  return first > 0 && first < 224 && ![10, 127].includes(first) &&
    !(first === 100 && second >= 64 && second <= 127) &&
    !(first === 169 && second === 254) &&
    !(first === 172 && second >= 16 && second <= 31) &&
    !(first === 192 && second === 168) &&
    !(first === 198 && (second === 18 || second === 19));
}

browser.proxy.onRequest.addListener(details => {
  if (!server || details.incognito !== false || details.tabId < 0 || details.method !== "GET") return;
  const target = new URL(details.url);
  if (!isPublicHost(target.hostname) || target.hostname === server.host || target.username || target.password) return;

  const proxy = { type: server.scheme, host: server.host, port: server.port, failoverTimeout: 3 };
  if (server.scheme === "https") proxy.proxyAuthorizationHeader = `Bearer ${server.token}`;
  return [proxy, { type: "direct" }];
}, filter);

browser.webRequest.onAuthRequired.addListener(details => {
  const challenger = details.challenger;
  if (!server || details.incognito !== false || details.tabId < 0 || !details.isProxy ||
      details.scheme !== "basic" || challenger.host !== server.host || challenger.port !== server.port ||
      details.proxyInfo?.type !== server.scheme) return;
  if (challenged.has(details.requestId)) return { cancel: true };
  challenged.add(details.requestId);
  return { authCredentials: { username: server.device, password: server.token } };
}, filter, ["blocking"]);

const forgetChallenge = details => challenged.delete(details.requestId);
browser.webRequest.onCompleted.addListener(forgetChallenge, filter);
browser.webRequest.onErrorOccurred.addListener(forgetChallenge, filter);
browser.webRequest.onBeforeRedirect.addListener(forgetChallenge, filter);

browser.webRequest.onBeforeSendHeaders.addListener(details => {
  if (!requestSiteSavings || details.tabId < 0 || (details.method !== "GET" && details.method !== "HEAD")) return;
  const target = new URL(details.url);
  if (!isPublicHost(target.hostname) || target.username || target.password) return;
  const headers = details.requestHeaders;
  if (!headers || headers.some(({ name }) => excludedSaveDataHeaders.has(name.toLowerCase()))) return;
  return { requestHeaders: [...headers, { name: "Save-Data", value: "on" }] };
}, {
  urls: ["https://*/*"],
  types: ["main_frame", "sub_frame", "image", "font", "stylesheet", "script", "media"],
}, ["blocking", "requestHeaders"]);

MidoriLitePublicImages.install(() => requestSiteSavings);

function finishImageRequest(id, image) {
  const request = imageRequests.get(id);
  if (!request) return;
  imageRequests.delete(id);
  clearTimeout(request.timeout);
  request.resolve(image);
}

function requestImage(url) {
  if (!nativePort || imageRequests.size >= 4) return undefined;
  const id = ++imageRequestId;
  return new Promise(resolve => {
    const timeout = setTimeout(() => finishImageRequest(id), 2750);
    imageRequests.set(id, { resolve, timeout });
    try {
      nativePort.postMessage({ type: "liteImage", requestId: id, url });
    } catch (_) {
      finishImageRequest(id);
    }
  });
}

MidoriLiteImageRelay.install(() => dataSaving && relayAvailable, requestImage);

function connect() {
  const port = browser.runtime.connectNative("midoriLite");
  nativePort = port;
  port.onMessage.addListener(message => {
    if (message.type === "liteImageResult") {
      finishImageRequest(message.requestId, message.data);
      return;
    }
    dataSaving = message.dataSaving === true;
    requestSiteSavings = message.requestSiteSavings === true;
    relayAvailable = message.imageRelay === true;
    server = message.enabled ? message : null;
  });
  port.onDisconnect.addListener(() => {
    dataSaving = false;
    requestSiteSavings = false;
    relayAvailable = false;
    nativePort = null;
    for (const id of imageRequests.keys()) finishImageRequest(id);
    server = null;
    challenged.clear();
    setTimeout(connect, 1000);
  });
}

connect();
