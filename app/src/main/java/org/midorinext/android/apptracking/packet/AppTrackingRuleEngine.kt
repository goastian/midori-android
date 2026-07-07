package org.midorinext.android.apptracking.packet

class AppTrackingRuleEngine {
    private val trackerSignatures = listOf(
        TrackerSignature("doubleclick.net", "Google", listOf("Advertising ID", "Ad Attribution")),
        TrackerSignature("google-analytics.com", "Google", listOf("Analytics", "App Behavior")),
        TrackerSignature("googletagmanager.com", "Google", listOf("Analytics", "Telemetry")),
        TrackerSignature("app-measurement.com", "Google", listOf("Analytics", "Telemetry")),
        TrackerSignature("firebaseinstallations.googleapis.com", "Google", listOf("Unique Identifier", "Install Tracking")),
        TrackerSignature("crashlytics.com", "Google", listOf("Crash Reporting", "Device Info")),
        TrackerSignature("graph.facebook.com", "Meta", listOf("Advertising ID", "Attribution")),
        TrackerSignature("connect.facebook.net", "Meta", listOf("Attribution", "Device Info")),
        TrackerSignature("an.facebook.com", "Meta", listOf("Advertising ID", "Attribution")),
        TrackerSignature("appsflyer.com", "AppsFlyer", listOf("Install Attribution", "Unique Identifier")),
        TrackerSignature("adjust.com", "Adjust", listOf("Install Attribution", "Marketing")),
        TrackerSignature("branch.io", "Branch", listOf("Deep Link Attribution", "Campaign Tracking")),
        TrackerSignature("mixpanel.com", "Mixpanel", listOf("Analytics", "User Events")),
        TrackerSignature("segment.io", "Twilio Segment", listOf("Analytics", "User Events")),
        TrackerSignature("amplitude.com", "Amplitude", listOf("Analytics", "Behavioral Metrics"))
    )

    fun shouldBlock(domain: String): Boolean = classify(domain) != null

    fun classify(domain: String): TrackerMatch? {
        val normalized = domain.lowercase().trimEnd('.')
        val signature = trackerSignatures.firstOrNull {
            normalized == it.domain || normalized.endsWith(".${it.domain}")
        } ?: return null
        return TrackerMatch(
            company = signature.company,
            categories = signature.categories,
            matchedPattern = signature.domain
        )
    }
}

private data class TrackerSignature(
    val domain: String,
    val company: String,
    val categories: List<String>
)

data class TrackerMatch(
    val company: String,
    val categories: List<String>,
    val matchedPattern: String
)


