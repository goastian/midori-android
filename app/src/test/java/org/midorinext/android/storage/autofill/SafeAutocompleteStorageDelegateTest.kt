package org.midorinext.android.storage.autofill

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import mozilla.components.browser.engine.gecko.ext.toLoginEntry
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
            loginRepository = LoginAutofillRepository(lazy { error("Password storage must stay lazy") }),
            autofillStorage = dagger.Lazy { error("Autofill storage must stay lazy") },
            preferenceState = AutofillPreferenceState(),
        )

        delegate.onLoginSave(login())
        delegate.onCreditCardSave(Autocomplete.CreditCard.Builder().build())
    }

    @Test
    fun savingEnabledPasswordsInitializesStorageOnceAndPreservesFields() = runBlocking {
        val state = passwordPreferences(enabled = true)
        val saved = mutableListOf<LoginEntry>()
        var initialized = 0
        val storage = lazy {
            initialized++
            loginStorage(saved)
        }
        val repository = LoginAutofillRepository(storage)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val delegate = delegate(repository, state, scope)
        assertEquals(0, initialized)

        repository.authorizeSave(login().toLoginEntry())
        delegate.onLoginSave(login())
        scope.coroutineContext[kotlinx.coroutines.Job]!!.children.toList().joinAll()
        repository.authorizeSave(login().toLoginEntry())
        delegate.onLoginSave(login())
        scope.coroutineContext[kotlinx.coroutines.Job]!!.children.toList().joinAll()

        assertEquals(1, initialized)
        assertEquals(2, saved.size)
        assertEquals("https://example.org", saved.first().origin)
        assertEquals("user", saved.first().username)
        assertEquals("password", saved.first().password)
    }

    @Test
    fun disablingPasswordSavingStopsAccessToAnExistingStorage() = runBlocking {
        val state = passwordPreferences(enabled = true)
        val saved = mutableListOf<LoginEntry>()
        val repository = LoginAutofillRepository(lazy { loginStorage(saved) })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val delegate = delegate(repository, state, scope)
        repository.authorizeSave(login().toLoginEntry())
        delegate.onLoginSave(login())
        scope.coroutineContext[kotlinx.coroutines.Job]!!.children.toList().joinAll()

        state.update(AppPreferencesSerializer.defaultValue.toBuilder().setSavePasswordsEnabled(false).build())
        repository.authorizeSave(login().toLoginEntry())
        delegate.onLoginSave(login())

        assertEquals(1, saved.size)
    }

    @Test
    fun failureDuringLazyStorageInitializationIsStillSupervised() = runBlocking {
        var attempts = 0
        val repository = LoginAutofillRepository(lazy {
                attempts++
                error("Encrypted storage is unavailable")
            })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val delegate = delegate(
            repository,
            passwordPreferences(enabled = true),
            scope,
        )

        repository.authorizeSave(login().toLoginEntry())
        delegate.onLoginSave(login())
        scope.coroutineContext[kotlinx.coroutines.Job]!!.children.toList().joinAll()
        repository.authorizeSave(login().toLoginEntry())
        delegate.onLoginSave(login())
        scope.coroutineContext[kotlinx.coroutines.Job]!!.children.toList().joinAll()

        assertEquals(2, attempts)
    }

    @Test
    fun unconfirmedOrRepeatedSaveCallbacksCannotWritePasswords() = runBlocking {
        val saved = mutableListOf<LoginEntry>()
        val repository = LoginAutofillRepository(lazy { loginStorage(saved) })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val delegate = delegate(repository, passwordPreferences(enabled = true), scope)

        delegate.onLoginSave(login())
        repository.authorizeSave(login().toLoginEntry())
        delegate.onLoginSave(login())
        delegate.onLoginSave(login())
        scope.coroutineContext[kotlinx.coroutines.Job]!!.children.toList().joinAll()

        assertEquals(1, saved.size)
    }

    private fun delegate(repository: LoginAutofillRepository, state: AutofillPreferenceState, scope: CoroutineScope) =
        SafeAutocompleteStorageDelegate(
            creditCardsAddressesDelegate = Proxy.newProxyInstance(
                CreditCardsAddressesStorageDelegate::class.java.classLoader,
                arrayOf(CreditCardsAddressesStorageDelegate::class.java),
            ) { _, _, _ -> error("Password saving must not access credit cards or addresses") }
                as CreditCardsAddressesStorageDelegate,
            loginRepository = repository,
            preferenceState = state,
            scope = scope,
        )

    private fun loginStorage(saved: MutableList<LoginEntry>) = Proxy.newProxyInstance(
        LoginsStorage::class.java.classLoader,
        arrayOf(LoginsStorage::class.java),
    ) { _, method, arguments ->
        check(method.name == "add")
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
