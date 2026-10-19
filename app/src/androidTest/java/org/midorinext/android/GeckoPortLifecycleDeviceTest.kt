package org.midorinext.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.gecko.util.EventCallback
import org.mozilla.geckoview.WebExtension

@RunWith(AndroidJUnit4::class)
class GeckoPortLifecycleDeviceTest {
    @Test
    fun lateExtensionDisconnectDoesNotNotifyOrReleaseAnAlreadyClosedPort() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val port = closedPort()
            var notifications = 0
            WebExtension.Port::class.java.getDeclaredField("delegate").apply {
                isAccessible = true
                set(port, object : WebExtension.PortDelegate {
                    override fun onDisconnect(port: WebExtension.Port) { notifications++ }
                })
            }
            val remoteDisconnect = WebExtension.Port::class.java
                .getDeclaredMethod("disconnectFromExtension", EventCallback::class.java)
                .apply { isAccessible = true }

            repeat(2) { remoteDisconnect.invoke(port, null) }

            assertEquals(0, notifications)
        }
    }

    @Test
    fun repeatedCleanupDoesNotAccessTheReleasedNativeDispatcher() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val port = closedPort()
            val cleanup = WebExtension.Port::class.java.getDeclaredMethod("disconnected")
                .apply { isAccessible = true }

            repeat(2) { cleanup.invoke(port) }
        }
    }

    private fun closedPort(): WebExtension.Port = object : WebExtension.Port() {}.also { port ->
        WebExtension.Port::class.java.getDeclaredField("disconnected").apply {
            isAccessible = true
            setBoolean(port, true)
        }
    }
}
