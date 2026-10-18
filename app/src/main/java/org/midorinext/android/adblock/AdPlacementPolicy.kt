package org.midorinext.android.adblock

import org.json.JSONArray
import org.json.JSONObject

internal object AdPlacementPolicy {
    private val selectors = listOf(
        "ins.adsbygoogle", "[id^='google_ads_iframe_']", "[id^='google_ads_frame']",
        "[id^='div-gpt-ad']", "[data-ad-slot][data-ad-client^='ca-pub-']",
    )

    fun message(enabled: Boolean, selection: CosmeticSelection = CosmeticSelection()): JSONObject {
        val active = enabled && selection.enabled
        val exceptions = selection.rules.filter { it.exception }.mapTo(HashSet()) { it.selector }
        val hidden = ((if (selection.genericEnabled) selectors else emptyList()) + selection.rules
            .filter { !it.exception && (selection.genericEnabled || it.included.isNotEmpty()) }
            .map { it.selector }).distinct() - exceptions
        return JSONObject()
            .put("type", "ad_placement_policy")
            .put("enabled", active)
            .put("selectors", JSONArray(if (active) hidden else emptyList()))
    }
}
