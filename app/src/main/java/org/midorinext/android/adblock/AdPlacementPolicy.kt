package org.midorinext.android.adblock

import org.json.JSONArray
import org.json.JSONObject

internal object AdPlacementPolicy {
    private val selectors = listOf(
        "ins.adsbygoogle", "[id^='google_ads_iframe_']", "[id^='google_ads_frame']",
        "[id^='div-gpt-ad']", "[data-ad-slot][data-ad-client^='ca-pub-']",
    )

    fun message(enabled: Boolean): JSONObject = JSONObject()
        .put("type", "ad_placement_policy")
        .put("enabled", enabled)
        .put("selectors", JSONArray(selectors))
}
