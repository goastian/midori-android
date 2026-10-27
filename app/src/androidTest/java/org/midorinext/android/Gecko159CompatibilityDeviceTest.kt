package org.midorinext.android

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import mozilla.components.concept.engine.EngineSession
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Gecko159CompatibilityDeviceTest {
    @Test
    fun privateEngineSessionSupportsThe159WebPlatformWithoutChangingBrowserTabs() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as MidoriApplication
        val done = CountDownLatch(1)
        val report = AtomicReference<JSONObject>()
        lateinit var session: EngineSession
        instrumentation.runOnMainSync {
            session = app.engine.get().createSession(private = true)
            session.register(object : EngineSession.Observer {
                override fun onTitleChange(title: String) {
                    if (title.startsWith(PREFIX)) {
                        report.set(JSONObject(title.removePrefix(PREFIX)))
                        done.countDown()
                    }
                }
            })
            session.loadData(FIXTURE)
        }
        try {
            assertTrue("Gecko did not execute the compatibility fixture", done.await(30, TimeUnit.SECONDS))
            val result = report.get()
            Log.i("Gecko159Compatibility", result.toString())
            assertTrue(result.toString(), result.optString("error").isEmpty())
            assertTrue("Scroll timelines: $result", result.getBoolean("scrollTimeline"))
            assertTrue("View timelines: $result", result.getBoolean("viewTimeline"))
            assertTrue("Scoped custom elements: $result", result.getBoolean("scopedRegistry"))
            assertTrue("Sanitizer: $result", result.getBoolean("sanitizer"))
        } finally {
            instrumentation.runOnMainSync { session.close() }
        }
    }

    private companion object {
        const val PREFIX = "midori159:"
        val FIXTURE = """
            <!doctype html><meta charset="utf-8"><body><script>
            const result = {};
            try {
                result.scrollTimeline = CSS.supports('animation-timeline', 'scroll()') &&
                    typeof ScrollTimeline === 'function';
                result.viewTimeline = typeof ViewTimeline === 'function';
                const registry = new CustomElementRegistry();
                class ScopedElement extends HTMLElement {}
                registry.define('midori-scoped', ScopedElement);
                const shadow = document.createElement('div').attachShadow({
                    mode: 'open', customElementRegistry: registry
                });
                shadow.innerHTML = '<midori-scoped></midori-scoped>';
                result.scopedRegistry = shadow.firstElementChild instanceof ScopedElement &&
                    customElements.get('midori-scoped') === undefined;
                let constructed = 0;
                customElements.define('midori-removed', class extends HTMLElement {
                    constructor() { super(); constructed++; }
                });
                const sanitized = document.createElement('div');
                sanitized.setHTML('<midori-removed>text</midori-removed>');
                result.sanitizer = constructed === 0 &&
                    sanitized.querySelector('midori-removed') === null;
                const gl = document.createElement('canvas').getContext('webgl2');
                result.webgl = gl ? gl.getSupportedExtensions().filter(name => [
                    'EXT_clip_control', 'EXT_texture_mirror_clamp_to_edge', 'EXT_texture_norm16'
                ].includes(name)) : [];
            } catch (error) { result.error = String(error); }
            document.title = 'midori159:' + JSON.stringify(result);
            </script>
        """.trimIndent()
    }
}
