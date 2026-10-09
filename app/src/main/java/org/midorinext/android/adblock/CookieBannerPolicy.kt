package org.midorinext.android.adblock

import org.json.JSONArray
import org.json.JSONObject

internal object CookieBannerPolicy {
    fun message(config: AdBlockConfiguration, url: String): JSONObject = JSONObject()
        .put("type", "cookie_banner_policy")
        .put("enabled", config.rejectsCookieBannersFor(url))
        .put("rejectSelectors", JSONArray(rejectSelectors))
        .put("bannerSelectors", JSONArray(bannerSelectors))
        .put("shadowHosts", JSONArray(shadowHosts))
        .put("rejectText", rejectText)

    private val rejectSelectors = listOf(
        "#onetrust-reject-all-handler",
        "#CybotCookiebotDialogBodyButtonDecline",
        "#didomi-notice-disagree-button",
        "[data-testid='uc-deny-all-button']",
        "[data-testid='uc-reject-all-button']",
        "#truste-consent-required",
        ".osano-cm-denyAll",
        "#iubenda-cs-banner .iubenda-cs-reject-btn",
        "#tarteaucitronAllDenied2",
        "#tarteaucitronAllDenied",
        "#cmplz-cookiebanner-container .cmplz-deny",
        "#cookie-notice #cn-refuse-cookie",
        "#cookie-law-info-bar .cli_action_button[data-cli_action='reject']",
        "#moove_gdpr_cookie_info_bar .moove-gdpr-infobar-reject-btn",
    )

    private val bannerSelectors = listOf(
        "#onetrust-banner-sdk", "#onetrust-pc-sdk",
        "#CybotCookiebotDialog", "#didomi-host", "#usercentrics-root", "#usercentrics-cmp-ui",
        "#truste-consent-track", "#qc-cmp2-ui", ".osano-cm-dialog", "#iubenda-cs-banner",
        "#tarteaucitronRoot", "#cmplz-cookiebanner-container", ".cmplz-cookiebanner",
        "#cookie-notice", "#cookie-law-info-bar", "#moove_gdpr_cookie_info_bar",
        "#cookie-banner", "#cookie-consent", "#cookie-consent-banner", "#cookies-banner",
        ".cookie-banner", ".cookie-consent", ".cookie-consent-banner", "[data-cookie-banner]",
        "[role='dialog'][aria-label*='cookie' i]",
    )

    private val shadowHosts = listOf("#usercentrics-root", "#usercentrics-cmp-ui")

    private const val rejectText = "^(?:reject(?: all)?(?: cookies| optional cookies)?|" +
        "decline(?: all)?(?: cookies| optional cookies)?|deny all|" +
        "(?:accept |allow |use )?only (?:necessary|essential) cookies|" +
        "rechazar(?: todas| todo)?(?: las cookies| cookies)?|" +
        "solo (?:las )?(?:cookies )?necesarias|continuar sin aceptar|" +
        "(?:recusar|rejeitar)(?: todos| todas| tudo)?(?: os cookies| cookies)?|" +
        "apenas (?:cookies )?necessarios|tout refuser|refuser(?: tout| les cookies)?|" +
        "uniquement les cookies necessaires|alle ablehnen|nur notwendige cookies|" +
        "rifiuta(?: tutti)?(?: i cookie)?|solo cookie necessari|" +
        "отклонить(?: все)?(?: файлы cookie)?|только необходимые(?: cookie)?|" +
        "全部拒绝|拒绝所有(?: Cookie| cookie)?|仅(?:必要|必需)(?: Cookie| cookie)?|" +
        "すべて拒否|全て拒否|必須(?:の)?Cookieのみ|" +
        "refuzo(?: te gjitha)?|vetem cookie te nevojshme|" +
        "सभी अस्वीकार करें|केवल आवश्यक कुकीज़)$"
}
