let retryDelay = 250;
function connect() {
  const port = browser.runtime.connectNative("midoriSyncStorage");
  port.onMessage.addListener(async request => {
    retryDelay = 250;
    try {
      const data = await browser.midoriSyncStorage.request(JSON.stringify(request));
      port.postMessage({ id: request.id, data: JSON.parse(data) });
    } catch {
      port.postMessage({ id: request.id, error: "native_storage_failed" });
    }
  });
  port.onDisconnect.addListener(() => {
    setTimeout(connect, retryDelay);
    retryDelay = Math.min(retryDelay * 2, 30000);
  });
  port.postMessage({ ready: true });
}
connect();
