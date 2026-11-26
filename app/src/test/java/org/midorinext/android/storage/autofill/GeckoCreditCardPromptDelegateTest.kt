package org.midorinext.android.storage.autofill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

class GeckoCreditCardPromptDelegateTest {
    @Test
    fun allOtherGeckoPromptsReachTheOriginalDelegate() {
        val called = mutableListOf<String>()
        val original = original { called += it }
        val bridge = GeckoCreditCardPromptDelegate(original, {}, {}).promptDelegate
        val methods = PromptDelegate::class.java.declaredMethods.filter { it.name != "onCreditCardSelect" }

        methods.forEach { it.invoke(bridge, *arrayOfNulls<Any>(it.parameterCount)) }

        assertEquals(methods.map { it.name }.sorted(), called.sorted())
    }

    @Test
    fun originalFailuresKeepTheirExceptionType() {
        val failure = IllegalStateException("Original failure")
        val bridge = GeckoCreditCardPromptDelegate(original { throw failure }, {}, {}).promptDelegate
        val method = PromptDelegate::class.java.declaredMethods.first { it.name == "onAlertPrompt" }

        val error = assertThrows(InvocationTargetException::class.java) { method.invoke(bridge, null, null) }

        assertSame(failure, error.cause)
    }

    private fun original(onCall: (String) -> Unit): PromptDelegate = Proxy.newProxyInstance(
        PromptDelegate::class.java.classLoader,
        arrayOf(PromptDelegate::class.java),
    ) { _, method, _ -> onCall(method.name); null } as PromptDelegate
}
