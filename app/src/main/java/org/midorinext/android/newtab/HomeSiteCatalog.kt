package org.midorinext.android.newtab

import androidx.annotation.StringRes
import org.midorinext.android.R

data class HomeSuggestedSite(val title: String, val url: String)
data class HomeSiteCategory(val id: String, @param:StringRes val title: Int, val sites: List<HomeSuggestedSite>)

private fun sites(vararg entries: Pair<String, String>) = entries.map { HomeSuggestedSite(it.first, it.second) }

val homeSiteCategories = listOf(
    HomeSiteCategory("gaming", R.string.home_category_gaming, sites(
        "Steam" to "https://store.steampowered.com/", "IGN" to "https://www.ign.com/",
        "Xbox" to "https://www.xbox.com/", "Nintendo" to "https://www.nintendo.com/")),
    HomeSiteCategory("finance", R.string.home_category_finance, sites(
        "PayPal" to "https://www.paypal.com/", "Yahoo Finance" to "https://finance.yahoo.com/",
        "TradingView" to "https://www.tradingview.com/")),
    HomeSiteCategory("social", R.string.home_category_social, sites(
        "Reddit" to "https://www.reddit.com/", "Instagram" to "https://www.instagram.com/",
        "Facebook" to "https://www.facebook.com/", "Mastodon" to "https://joinmastodon.org/")),
    HomeSiteCategory("travel", R.string.home_category_travel, sites(
        "Kayak" to "https://www.kayak.com/", "Tripadvisor" to "https://www.tripadvisor.com/",
        "Booking.com" to "https://www.booking.com/", "Airbnb" to "https://www.airbnb.com/")),
    HomeSiteCategory("education", R.string.home_category_education, sites(
        "Wikipedia" to "https://www.wikipedia.org/", "Khan Academy" to "https://www.khanacademy.org/",
        "Duolingo" to "https://www.duolingo.com/", "Internet Archive" to "https://archive.org/")),
    HomeSiteCategory("news", R.string.home_category_news, sites(
        "BBC" to "https://www.bbc.com/news", "Reuters" to "https://www.reuters.com/",
        "The Guardian" to "https://www.theguardian.com/")),
    HomeSiteCategory("sport", R.string.home_category_sport, sites(
        "ESPN" to "https://www.espn.com/", "Eurosport" to "https://www.eurosport.com/",
        "FIFA" to "https://www.fifa.com/", "NBA" to "https://www.nba.com/")),
    HomeSiteCategory("shopping", R.string.home_category_shopping, sites(
        "Amazon" to "https://www.amazon.com/", "eBay" to "https://www.ebay.com/",
        "AliExpress" to "https://www.aliexpress.com/", "Etsy" to "https://www.etsy.com/")),
    HomeSiteCategory("government", R.string.home_category_government, sites(
        "UN.org" to "https://www.un.org/", "UNICEF" to "https://www.unicef.org/",
        "Europa.eu" to "https://european-union.europa.eu/")),
)

val homeSuggestedSites = sites(
    "Wikipedia" to "https://www.wikipedia.org/", "YouTube" to "https://www.youtube.com/",
    "Reddit" to "https://www.reddit.com/", "GitHub" to "https://github.com/",
)
