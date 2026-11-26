package org.midorinext.android.storage.autofill;

import mozilla.components.browser.engine.gecko.GeckoEngineSession;
import mozilla.components.concept.engine.EngineSession;
import org.mozilla.geckoview.GeckoSession;
import java.lang.reflect.Proxy;

final class GeckoCreditCardBridge {
    private GeckoCreditCardBridge() {
    }

    static void install(EngineSession engineSession) {
        if (engineSession instanceof GeckoEngineSession) {
            GeckoEngineSession engine = (GeckoEngineSession) engineSession;
            GeckoSession session = engine.getGeckoSession$browser_engine_gecko();
            GeckoSession.PromptDelegate delegate = session.getPromptDelegate();
            if (delegate != null && !(Proxy.isProxyClass(delegate.getClass())
                    && Proxy.getInvocationHandler(delegate) instanceof GeckoCreditCardPromptDelegate)) {
                session.setPromptDelegate(new GeckoCreditCardPromptDelegate(engine, delegate).getPromptDelegate());
            }
        }
    }
}
