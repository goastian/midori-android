package org.mozilla.geckoview;

import org.mozilla.gecko.util.EventCallback;

public final class WebExtension {
    public static class Port {
        public boolean disconnected;
        public boolean reenterFromDelegate;
        public int shutdowns;
        public int notifications;

        public void disconnect() {
            if (disconnected) return;
            disconnected();
        }

        public void remoteDisconnect() {
            disconnectFromExtension(null);
        }

        private void disconnectFromExtension(EventCallback callback) {
            notifications++;
            if (reenterFromDelegate) disconnect();
            disconnected();
        }

        private void disconnected() {
            if (++shutdowns > 1) throw new NullPointerException("Native dispatcher already released");
            disconnected = true;
        }
    }
}
