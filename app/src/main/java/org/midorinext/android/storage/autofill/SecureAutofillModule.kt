package org.midorinext.android.storage.autofill

import android.content.Context
import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mozilla.components.browser.engine.gecko.ext.toAutocompleteAddress
import mozilla.components.browser.engine.gecko.ext.toCreditCardEntry
import mozilla.components.browser.engine.gecko.ext.toLoginEntry
import mozilla.components.concept.storage.CreditCardsAddressesStorageDelegate
import mozilla.components.concept.storage.LoginsStorage
import mozilla.components.lib.dataprotect.SecureAbove22Preferences
import mozilla.components.service.sync.autofill.AutofillCreditCardsAddressesStorage
import mozilla.components.service.sync.autofill.GeckoCreditCardsAddressesStorageDelegate
import mozilla.components.service.sync.logins.SyncableLoginsStorage
import org.midorinext.android.preferences.app.AppPreferences
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult
import javax.inject.Inject
import javax.inject.Singleton

/** Fast, process-local preference snapshot used by Gecko's synchronous delegates. */
@Singleton
class AutofillPreferenceState @Inject constructor() {
    @Volatile var savePasswordsEnabled: Boolean = false
        private set
    @Volatile var passwordAutofillEnabled: Boolean = false
        private set
    @Volatile var addressAutofillEnabled: Boolean = false
        private set
    @Volatile var cardAutofillEnabled: Boolean = false
        private set

    fun update(preferences: AppPreferences) {
        savePasswordsEnabled = preferences.savePasswordsEnabled
        passwordAutofillEnabled = preferences.passwordAutofillEnabled
        addressAutofillEnabled = preferences.autofillAddressesEnabled
        cardAutofillEnabled = preferences.autofillCardsEnabled
    }
}

@Module
@InstallIn(SingletonComponent::class)
object SecureAutofillModule {
    @Provides
    @Singleton
    fun provideSecurePreferences(
        @ApplicationContext context: Context,
    ): SecureAbove22Preferences = SecureAbove22Preferences(context, "midori_secure_storage_keys")

    @Provides
    @Singleton
    fun provideLoginsStorage(
        @ApplicationContext context: Context,
        securePreferences: SecureAbove22Preferences,
    ): SyncableLoginsStorage = SyncableLoginsStorage(context, lazy { securePreferences })

    @Provides
    @Singleton
    fun provideAutofillStorage(
        @ApplicationContext context: Context,
        securePreferences: SecureAbove22Preferences,
    ): AutofillCreditCardsAddressesStorage =
        AutofillCreditCardsAddressesStorage(context, lazy { securePreferences })

    @Provides
    @Singleton
    fun provideAutocompleteStorageDelegate(
        loginsStorage: SyncableLoginsStorage,
        autofillStorage: AutofillCreditCardsAddressesStorage,
        preferenceState: AutofillPreferenceState,
    ): Autocomplete.StorageDelegate {
        val autofillDelegate = GeckoCreditCardsAddressesStorageDelegate(
            storage = lazy { autofillStorage },
            isCreditCardAutofillEnabled = { preferenceState.cardAutofillEnabled },
            isAddressAutofillEnabled = { preferenceState.addressAutofillEnabled },
        )
        return SafeAutocompleteStorageDelegate(
            creditCardsAddressesDelegate = autofillDelegate,
            loginsStorage = loginsStorage,
            preferenceState = preferenceState,
        )
    }
}

/**
 * Gecko's stock autocomplete bridge launches storage work in global coroutines. An exception while
 * opening encrypted storage can therefore terminate the app as soon as Gecko detects a form. This
 * bridge keeps every storage boundary supervised and always completes Gecko's pending result.
 */
internal class SafeAutocompleteStorageDelegate(
    private val creditCardsAddressesDelegate: CreditCardsAddressesStorageDelegate,
    private val loginsStorage: LoginsStorage,
    private val preferenceState: AutofillPreferenceState,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : Autocomplete.StorageDelegate {
    override fun onAddressFetch(): GeckoResult<Array<Autocomplete.Address>> = geckoResult(emptyArray()) {
        if (!preferenceState.addressAutofillEnabled) {
            emptyArray()
        } else {
            creditCardsAddressesDelegate.onAddressesFetch()
                .map { it.toAutocompleteAddress() }
                .toTypedArray()
        }
    }

    override fun onCreditCardFetch(): GeckoResult<Array<Autocomplete.CreditCard>> =
        geckoResult(emptyArray()) {
            if (!preferenceState.cardAutofillEnabled) {
                emptyArray()
            } else {
                val key = creditCardsAddressesDelegate.getOrGenerateKey()
                creditCardsAddressesDelegate.onCreditCardsFetch().mapNotNull { card ->
                    creditCardsAddressesDelegate.decrypt(key, card.encryptedCardNumber)?.let { number ->
                        Autocomplete.CreditCard.Builder()
                            .guid(card.guid)
                            .name(card.billingName)
                            .number(number.number)
                            .expirationMonth(card.expiryMonth.toString())
                            .expirationYear(card.expiryYear.toString())
                            .build()
                    }
                }.toTypedArray()
            }
        }

    override fun onCreditCardSave(creditCard: Autocomplete.CreditCard) {
        if (!preferenceState.cardAutofillEnabled) return
        launchSafely("saving a credit card") {
            creditCardsAddressesDelegate.onCreditCardSave(creditCard.toCreditCardEntry())
        }
    }

    override fun onLoginFetch(domain: String): GeckoResult<Array<Autocomplete.LoginEntry>> =
        geckoResult(emptyArray()) {
            if (!preferenceState.passwordAutofillEnabled) {
                emptyArray()
            } else {
                loginsStorage.getByBaseDomain(domain)
                    .map { it.toLoginEntry() }
                    .toTypedArray()
            }
        }

    override fun onLoginSave(login: Autocomplete.LoginEntry) {
        if (!preferenceState.savePasswordsEnabled) return
        launchSafely("saving a login") {
            loginsStorage.addOrUpdate(login.toLoginEntry())
        }
    }

    private fun <T> geckoResult(fallback: T, operation: suspend () -> T): GeckoResult<T> {
        val result = GeckoResult<T>()
        scope.launch {
            val value = try {
                operation()
            } catch (error: Throwable) {
                Log.e(TAG, "Autocomplete storage operation failed", error)
                fallback
            }
            result.complete(value)
        }
        return result
    }

    private fun launchSafely(operationName: String, operation: suspend () -> Unit) {
        scope.launch {
            try {
                operation()
            } catch (error: Throwable) {
                Log.e(TAG, "Failed while $operationName", error)
            }
        }
    }

    private companion object {
        const val TAG = "MidoriAutofill"
    }
}
