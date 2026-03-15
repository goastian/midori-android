/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android

import android.content.Context
import android.os.Bundle
import mozilla.components.browser.engine.gecko.GeckoEngine
import mozilla.components.browser.engine.gecko.fetch.GeckoViewFetchClient
import mozilla.components.concept.engine.DefaultSettings
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.fetch.Client
import mozilla.components.feature.webcompat.WebCompatFeature
import mozilla.components.lib.crash.handler.CrashHandlerService
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

object EngineProvider {
    private var runtime: GeckoRuntime? = null

    @Synchronized
    fun getOrCreateRuntime(context: Context): GeckoRuntime {
        if (runtime == null) {
            val builder = GeckoRuntimeSettings.Builder()

            builder.crashHandler(CrashHandlerService::class.java)

            // About config & extensions
            builder.aboutConfigEnabled(true)
            builder.extensionsWebAPIEnabled(true)

            // --- Performance: disable console output in production ---
            builder.consoleOutput(false)

            // --- Performance: disable GL MSAA (saves GPU memory) ---
            builder.glMsaaLevel(0)

            // --- Performance: prefer color scheme to avoid extra repaints ---
            builder.preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM)

            // --- Performance: Gecko about:config prefs via extras Bundle ---
            val extras = Bundle().apply {
                // ===== NETWORK: faster connection setup =====
                // Increase speculative parallel connections
                putInt("network.http.speculative-parallel-limit", 20)
                // More persistent connections per server
                putInt("network.http.max-persistent-connections-per-server", 10)
                // More total persistent connections
                putInt("network.http.max-persistent-connections-per-proxy", 32)
                // Enable HTTP pipelining for faster loading
                putBoolean("network.http.pipelining", true)
                putInt("network.http.pipelining.maxrequests", 8)
                // DNS prefetching
                putBoolean("network.dns.disablePrefetch", false)
                putBoolean("network.dns.disablePrefetchFromHTTPS", false)
                // Predictor (speculative connections on hover/link)
                putBoolean("network.predictor.enabled", true)
                putBoolean("network.predictor.enable-hover-on-ssl", true)
                putInt("network.predictor.max-resources-per-entry", 250)
                putInt("network.predictor.max-uri-length", 1000)
                // Faster initial connection
                putBoolean("network.tcp.tcp_fastopen_enable", true)

                // ===== RENDERING: faster paint & layout =====
                // Enable hardware accelerated layers
                putBoolean("layers.acceleration.force-enabled", true)
                // GPU compositing
                putBoolean("gfx.webrender.all", true)
                // Reduce reflow timer for faster layout
                putInt("content.notify.interval", 100000)
                putBoolean("content.notify.ontimer", true)
                // Faster image decoding
                putBoolean("image.mem.decode_bytes_at_a_time", true)
                putInt("image.cache.size", 10485760)
                // Reduce initial paint delay (default is 250ms in Gecko)
                putInt("nglayout.initialpaint.delay", 0)
                putInt("nglayout.initialpaint.delay_in_oopif", 0)

                // ===== MEMORY: optimized cache usage =====
                // Use more memory for faster browsing
                putInt("browser.cache.memory.capacity", 65536)
                putBoolean("browser.cache.memory.enable", true)
                putBoolean("browser.cache.disk.enable", true)
                // Session history optimization
                putInt("browser.sessionhistory.max_total_viewers", 4)

                // ===== JAVASCRIPT: JIT performance =====
                // Enable baseline and Ion JIT compilers
                putBoolean("javascript.options.baselinejit", true)
                putBoolean("javascript.options.ion", true)
                // Warp (optimizing JIT) enabled
                putBoolean("javascript.options.warp", true)
                // Reduce JIT compilation threshold for faster warmup
                putInt("javascript.options.baselinejit.threshold", 10)
                putInt("javascript.options.ion.threshold", 100)

                // ===== UI: smoother scrolling =====
                putBoolean("general.smoothScroll", true)
                putInt("general.smoothScroll.mouseWheel.durationMaxMS", 200)
                putInt("general.smoothScroll.mouseWheel.durationMinMS", 100)

                // ===== MEDIA: reduce background overhead =====
                putBoolean("media.suspend-background-video.enabled", true)

                // ===== TELEMETRY: disable for faster startup =====
                putBoolean("toolkit.telemetry.enabled", false)
                putBoolean("toolkit.telemetry.unified", false)
                putBoolean("datareporting.healthreport.uploadEnabled", false)
                putBoolean("datareporting.policy.dataSubmissionEnabled", false)
            }
            builder.extras(extras)

            runtime = GeckoRuntime.create(context.applicationContext, builder.build())
        }

        return runtime!!
    }

    fun createEngine(
        context: Context,
        defaultSettings: DefaultSettings,
    ): Engine {
        val runtime = getOrCreateRuntime(context)

        return GeckoEngine(context, defaultSettings, runtime).also {
            WebCompatFeature.install(it)
        }
    }

    fun createClient(context: Context): Client {
        val runtime = getOrCreateRuntime(context)
        return GeckoViewFetchClient(context, runtime)
    }
}
