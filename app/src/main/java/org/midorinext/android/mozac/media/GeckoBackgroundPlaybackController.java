package org.midorinext.android.mozac.media;

import mozilla.components.browser.engine.gecko.GeckoEngineSession;
import mozilla.components.concept.engine.EngineSession;

/** Bridges Android Components' Gecko session to GeckoView's documented activity API. */
final class GeckoBackgroundPlaybackController {
    private GeckoBackgroundPlaybackController() {
    }

    static void keepActive(EngineSession engineSession) {
        if (engineSession instanceof GeckoEngineSession) {
            final org.mozilla.geckoview.GeckoSession geckoSession =
                ((GeckoEngineSession) engineSession).getGeckoSession$browser_engine_gecko();
            geckoSession.setActive(true);
            geckoSession.setFocused(true);
        }
    }
}
