/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

// Midori exposes Reader View on every regular web page. Mozilla's default content script
// deliberately excludes home pages and several hosts before Readability even gets a chance to
// parse them. We keep Mozilla's cache/native-message protocol, but let the reader renderer decide
// how to simplify every HTTP(S) document.
const supportedProtocols = ["http:", "https:"];

function isReaderable() {
  return supportedProtocols.includes(location.protocol);
}

function connectNativePort() {
  let port = browser.runtime.connectNative("mozacReaderview");
  port.onMessage.addListener(message => {
    switch (message.action) {
      case "cachePage": {
        let serializedDoc = new XMLSerializer().serializeToString(document);
        browser.runtime.sendMessage({
          action: "addSerializedDoc",
          doc: serializedDoc,
          id: message.id,
        });
        break;
      }
      case "checkReaderState":
        port.postMessage({
          type: "checkReaderState",
          baseUrl: browser.runtime.getURL("/"),
          readerable: isReaderable(),
        });
        break;
      default:
        console.error(`Received unsupported action ${message.action}`);
    }
  });

  return port;
}

let port = connectNativePort();

window.addEventListener("pageshow", _event => {
  port = port != null ? port : connectNativePort();
});

window.addEventListener("pagehide", _event => {
  port.disconnect();
  port = null;
});
