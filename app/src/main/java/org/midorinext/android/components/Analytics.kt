/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.components

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import mozilla.components.lib.crash.CrashReporter
import mozilla.components.lib.crash.sentry.SentryService
import mozilla.components.lib.crash.service.CrashReporterService
import org.mozilla.geckoview.BuildConfig.MOZ_APP_BUILDID
import org.mozilla.geckoview.BuildConfig.MOZ_APP_VERSION
import org.midorinext.android.BrowserApplication
import org.midorinext.android.BuildConfig
import org.midorinext.android.R

/**
 * Component group for all functionality related to analytics e.g. crash reporting.
 * Only Sentry is used for crash reporting — no Google/Firebase/Socorro services.
 */
class Analytics(
    private val context: Context,
) {
    val crashReporter: CrashReporter by lazy {
        val services: MutableList<CrashReporterService> = mutableListOf()

        if (isSentryEnabled()) {
            services.add(
                SentryService(
                    context,
                    BuildConfig.SENTRY_TOKEN,
                    mapOf("geckoview" to "$MOZ_APP_VERSION-$MOZ_APP_BUILDID"),
                    sendEventForNativeCrashes = true,
                ),
            )
        }

        // CrashReporter requires at least one service; when no Sentry token is
        // configured (e.g. debug builds) we still need a valid instance, so we
        // add a minimal no-op service that simply logs crashes locally.
        if (services.isEmpty()) {
            services.add(object : CrashReporterService {
                override val id: String = "noop"
                override val name: String = "NoOp"
                override fun createCrashReportUrl(identifier: String): String? = null
                override fun report(crash: mozilla.components.lib.crash.Crash.UncaughtExceptionCrash): String? = ""
                override fun report(crash: mozilla.components.lib.crash.Crash.NativeCodeCrash): String? = ""
                override fun report(
                    throwable: Throwable,
                    breadcrumbs: ArrayList<mozilla.components.concept.base.crash.Breadcrumb>,
                ): String? = ""
            })
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }

        CrashReporter(
            context = context,
            services = services,
            telemetryServices = emptyList(),
            shouldPrompt = CrashReporter.Prompt.ALWAYS,
            promptConfiguration = CrashReporter.PromptConfiguration(
                appName = context.getString(R.string.app_name),
                organizationName = "Astian",
            ),
            nonFatalCrashIntent = PendingIntent
                .getBroadcast(context, 0, Intent(BrowserApplication.NON_FATAL_CRASH_BROADCAST), flags),
            enabled = isSentryEnabled(),
        )
    }
}

fun isSentryEnabled() = !BuildConfig.SENTRY_TOKEN.isNullOrEmpty()
