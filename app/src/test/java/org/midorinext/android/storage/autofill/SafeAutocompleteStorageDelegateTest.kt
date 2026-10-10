package org.midorinext.android.storage.autofill

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import mozilla.components.concept.storage.CreditCardsAddressesStorageDelegate
import mozilla.components.concept.storage.Login
import mozilla.components.concept.storage.LoginEntry
import mozilla.components.concept.storage.LoginsStorage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.midorinext.android.preferences.app.AppPreferencesSerializer
import org.mozilla.geckoview.Autocomplete
import java.lang.reflect.Proxy

class SafeAutocompleteStorageDelegateTest {
    @Test
    fun creatingGeckoDelegateAndDisabledSavesDoNotInitializeStorage() {
        val delegate = SecureAutofillModule.provideAutocompleteStorageDelegate(
            loginsStorage = dagger.Lazy { error("Password storage must stay lazy") },
            autofillStorage = dagger.Lazy { error("Autofill storage must stay lazy") },
            preferenceState = AutofillPreferenceState(),
        )

        delegate.onLoginSave(login())
        delegate.onCreditCardSave(Autocomplete.CreditCard.Builder().build())
    }

    @Test
    fun savingEnabledPasswordsInitializesStorageOnceAndPreservesFields() {
        val state = passwordPreferences(enabled = true)
        val saved = mutableListOf<LoginEntry>()
        var initialized = 0
        val storage = lazy {
            initialized++
            loginStorage(saved)
        }
        val delegate = delegate(storage, state)
        assertEquals(0, initialized)

        delegate.onLoginSave(login())
        delegate.onLoginSave(login())

        assertEquals(1, initialized)
        assertEquals(2, saved.size)
        assertEquals("https://example.org", saved.first().origin)
        assertEquals("user", saved.first().username)
        assertEquals("password", saved.first().password)
    }

    @Test
    fun disablingPasswordSavingStopsAccessToAnExistingStorage() {
        val state = passwordPreferences(enabled = true)
        val saved = mutableListOf<LoginEntry>()
        val delegate = delegate(lazy { loginStorage(saved) }, state)
        delegate.onLoginSave(login())

        state.update(AppPreferencesSerializer.defaultValue.toBuilder().setSavePasswordsEnabled(false).build())
        delegate.onLoginSave(login())

        assertEquals(1, saved.size)
    }

    @Test
    fun failureDuringLazyStorageInitializationIsStillSupervised() {
        var attempts = 0
        val delegate = delegate(
            lazy {
                attempts++
                error("Encrypted storage is unavailable")
            },
            passwordPreferences(enabled = true),
        )

        delegate.onLoginSave(login())
        delegate.onLoginSave(login())

        assertEquals(2, attempts)
    }

    private fun delegate(storage: Lazy<LoginsStorage>, state: AutofillPreferenceState) =
        SafeAutocompleteStorageDelegate(
            creditCardsAddressesDelegate = Proxy.newProxyInstance(
                CreditCardsAddressesStorageDelegate::class.java.classLoader,
                arrayOf(CreditCardsAddressesStorageDelegate::class.java),
            ) { _, _, _ -> error("Password saving must not access credit cards or addresses") }
                as CreditCardsAddressesStorageDelegate,
            loginsStorage = storage,
            preferenceState = state,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )

    private fun loginStorage(saved: MutableList<LoginEntry>) = Proxy.newProxyInstance(
        LoginsStorage::class.java.classLoader,
        arrayOf(LoginsStorage::class.java),
    ) { _, method, arguments ->
        check(method.name == "addOrUpdate")
        val entry = arguments!![0] as LoginEntry
        saved += entry
        Login(guid = "saved-login", username = entry.username, password = entry.password, origin = entry.origin)
    } as LoginsStorage

    private fun passwordPreferences(enabled: Boolean) = AutofillPreferenceState().apply {
        update(AppPreferencesSerializer.defaultValue.toBuilder().setSavePasswordsEnabled(enabled).build())
    }

    private fun login() = Autocomplete.LoginEntry.Builder()
        .origin("https://example.org")
        .formActionOrigin("https://example.org/login")
        .username("user")
        .password("password")
        .build()
}
