package org.midorinext.android.storage.autofill

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import mozilla.components.concept.storage.CreditCardsAddressesStorageDelegate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.preferences.app.AppPreferencesSerializer
import java.lang.reflect.Proxy

@RunWith(AndroidJUnit4::class)
class SafeAutocompleteStorageDelegateDeviceTest {
    @Test
    fun disabledAutofillCompletesFetchWithoutInitializingStorage() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val delegate = delegate(
            LoginAutofillRepository(lazy { error("Disabled autofill must not open storage") }),
            AutofillPreferenceState(),
            scope,
        )

        val result = delegate.onLoginFetch("example.org")
        scope.coroutineContext[kotlinx.coroutines.Job]!!.children.toList().joinAll()

        assertEquals(0, result.poll(0)!!.size)
    }

    @Test
    fun failedFetchCompletesWithEmptyCredentials() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val state = AutofillPreferenceState().apply {
            update(AppPreferencesSerializer.defaultValue.toBuilder().setPasswordAutofillEnabled(true).build())
        }
        val delegate = delegate(LoginAutofillRepository(lazy { error("Encrypted storage unavailable") }), state, scope)

        val result = delegate.onLoginFetch("example.org")
        scope.coroutineContext[kotlinx.coroutines.Job]!!.children.toList().joinAll()

        assertEquals(0, result.poll(0)!!.size)
    }

    @Test
    fun cancelledScopeStillCompletesGeckoFetch() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scope.cancel()
        val delegate = delegate(LoginAutofillRepository(lazy { error("Cancelled scope must not open storage") }), AutofillPreferenceState(), scope)

        assertEquals(0, delegate.onLoginFetch("example.org").poll(0)!!.size)
    }

    private fun delegate(repository: LoginAutofillRepository, state: AutofillPreferenceState, scope: CoroutineScope) =
        SafeAutocompleteStorageDelegate(
            creditCardsAddressesDelegate = Proxy.newProxyInstance(
                CreditCardsAddressesStorageDelegate::class.java.classLoader,
                arrayOf(CreditCardsAddressesStorageDelegate::class.java),
            ) { _, _, _ -> error("Login fetch must not access cards or addresses") } as CreditCardsAddressesStorageDelegate,
            loginRepository = repository,
            preferenceState = state,
            scope = scope,
        )
}
