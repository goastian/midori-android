package org.midorinext.android.storage.autofill

import mozilla.components.browser.engine.gecko.GeckoEngineSession
import mozilla.components.browser.engine.gecko.ext.toCreditCardEntry
import mozilla.components.concept.engine.prompt.PromptRequest
import org.midorinext.android.mozac.Gecko161Features
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

internal class GeckoCreditCardPromptDelegate(
    private val delegate: PromptDelegate,
    private val onRequest: (PromptRequest) -> Unit,
    private val onDismissed: (PromptRequest) -> Unit,
) : InvocationHandler {
    constructor(engineSession: GeckoEngineSession, delegate: PromptDelegate) : this(
        delegate,
        { request -> engineSession.notifyObservers { onPromptRequest(request) } },
        { request -> engineSession.notifyObservers { onPromptDismissed(request) } },
    )

    val promptDelegate: PromptDelegate = Proxy.newProxyInstance(
        PromptDelegate::class.java.classLoader,
        arrayOf(PromptDelegate::class.java),
        this,
    ) as PromptDelegate

    override fun invoke(proxy: Any, method: Method, arguments: Array<out Any?>?): Any? {
        if (method.name == "onCreditCardSelect") {
            @Suppress("UNCHECKED_CAST")
            return onCreditCardSelect(arguments!![1] as PromptDelegate.AutocompleteRequest<Autocomplete.CreditCardSelectOption>)
        }
        return try {
            method.invoke(delegate, *(arguments ?: emptyArray()))
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }

    private fun onCreditCardSelect(
        request: PromptDelegate.AutocompleteRequest<Autocomplete.CreditCardSelectOption>,
    ): GeckoResult<PromptDelegate.PromptResponse> {
        val result = GeckoResult<PromptDelegate.PromptResponse>()
        val prompt = PromptRequest.SelectCreditCard(
            creditCards = request.options.map { it.value.toCreditCardEntry() },
            onConfirm = CreditCardConfirmation { card, code ->
                if (!request.isComplete) {
                    val value = Gecko161Features.securityCode(
                        Autocomplete.CreditCard.Builder()
                            .guid(card.guid).name(card.name).number(card.number)
                            .expirationMonth(card.expiryMonth).expirationYear(card.expiryYear),
                        code.takeIf { isValidSecurityCode(card.number, it) },
                    ).build()
                    result.complete(request.confirm(Autocomplete.CreditCardSelectOption(value)))
                }
            },
            onDismiss = { if (!request.isComplete) result.complete(request.dismiss()) },
        )
        request.delegate = object : PromptDelegate.PromptInstanceDelegate {
            override fun onPromptDismiss(promptRequest: PromptDelegate.BasePrompt) {
                onDismissed(prompt)
            }
        }
        onRequest(prompt)
        return result
    }
}
