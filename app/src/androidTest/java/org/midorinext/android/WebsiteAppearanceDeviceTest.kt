package org.midorinext.android

import android.net.Uri
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Test
import org.midorinext.android.preferences.app.Appearance
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView

class WebsiteAppearanceDeviceTest {
    @Test
    fun openPagesFollowAppAppearanceAndOptionalForcedColors(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as MidoriApplication
        val preferences = app.appPreferencesRepository.get()
        val original = preferences.flow.first()
        val document = """
            <!doctype html><title>Loading appearance</title>
            <style>body { background: white; color: black }</style>
            <script>
              const dark = matchMedia("(prefers-color-scheme: dark)");
              const forced = matchMedia("(forced-colors: active)");
              function report() { document.title = dark.matches + ":" + forced.matches; }
              dark.addEventListener("change", report);
              forced.addEventListener("change", report);
              report();
            </script>
            <p>Appearance test</p>
        """.trimIndent()
        val url = "data:text/html,${Uri.encode(document)}"

        val sessions = mutableListOf<GeckoSession>()
        val regularTitle = MutableStateFlow("")
        val privateTitle = MutableStateFlow("")
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            preferences.updateForceDarkWebsiteColors(false)
            preferences.updateAppearance(Appearance.DARK)
            scenario.onActivity { activity ->
                app.initializeBrowser("website_appearance_test", restoreSession = false)
                val layout = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                activity.setContentView(layout)
                for ((isPrivate, title) in listOf(false to regularTitle, true to privateTitle)) {
                    val session = GeckoSession(GeckoSessionSettings.Builder().usePrivateMode(isPrivate).build())
                    sessions += session
                    session.contentDelegate = object : GeckoSession.ContentDelegate {
                        override fun onTitleChange(session: GeckoSession, value: String?) {
                            title.value = value.orEmpty()
                        }
                    }
                    session.open(app.geckoRuntime.get())
                    val view = GeckoView(activity)
                    view.setSession(session)
                    layout.addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
                    session.loadUri(url)
                }
            }

            suspend fun awaitTitles(expected: String) {
                withTimeoutOrNull(30_000) {
                    combine(regularTitle, privateTitle) { regular, private -> regular to private }
                        .first { (regular, private) -> regular == expected && private == expected }
                }
                assertEquals(expected to expected, regularTitle.value to privateTitle.value)
            }

            awaitTitles("true:false")
            preferences.updateForceDarkWebsiteColors(true)
            awaitTitles("true:true")
            preferences.updateAppearance(Appearance.LIGHT)
            awaitTitles("false:false")
            preferences.updateAppearance(Appearance.DARK)
            awaitTitles("true:true")
            preferences.updateForceDarkWebsiteColors(false)
            awaitTitles("true:false")
        } finally {
            scenario.close()
            instrumentation.runOnMainSync { sessions.forEach(GeckoSession::close) }
            preferences.updateForceDarkWebsiteColors(original.forceDarkWebsiteColors)
            preferences.updateAppearance(original.appearance)
        }
    }
}
