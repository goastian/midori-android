package org.midorinext.android.ui.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.concept.storage.Address
import mozilla.components.concept.storage.CreditCard
import mozilla.components.concept.storage.CreditCardNumber
import mozilla.components.concept.storage.Login
import mozilla.components.concept.storage.NewCreditCardFields
import mozilla.components.concept.storage.UpdatableAddressFields
import mozilla.components.service.sync.autofill.AutofillCreditCardsAddressesStorage
import mozilla.components.service.sync.logins.SyncableLoginsStorage
import javax.inject.Inject

data class SecureDataUiState(
    val loading: Boolean = false,
    val logins: List<Login> = emptyList(),
    val cards: List<CreditCard> = emptyList(),
    val addresses: List<Address> = emptyList(),
    val error: String? = null,
)

data class AddressForm(
    val name: String,
    val street: String,
    val city: String,
    val region: String,
    val postalCode: String,
    val country: String,
    val phone: String,
    val email: String,
)

@HiltViewModel
class SecureDataViewModel @Inject constructor(
    private val loginsStorage: SyncableLoginsStorage,
    private val autofillStorage: AutofillCreditCardsAddressesStorage,
) : ViewModel() {
    private val _state = MutableStateFlow(SecureDataUiState())
    val state: StateFlow<SecureDataUiState> = _state.asStateFlow()

    fun loadPasswords() = launchOperation {
        val logins = loginsStorage.list().sortedBy { it.origin.lowercase() }
        _state.update { it.copy(logins = logins) }
    }

    fun loadAutofill() = launchOperation {
        val cards = autofillStorage.getAllCreditCards()
        val addresses = autofillStorage.getAllAddresses()
        _state.update { it.copy(cards = cards, addresses = addresses) }
    }

    fun deleteLogin(guid: String) = launchOperation(reload = ::loadPasswords) {
        loginsStorage.delete(guid)
    }

    fun deleteCard(guid: String) = launchOperation(reload = ::loadAutofill) {
        autofillStorage.deleteCreditCard(guid)
    }

    fun deleteAddress(guid: String) = launchOperation(reload = ::loadAutofill) {
        autofillStorage.deleteAddress(guid)
    }

    fun addCard(
        name: String,
        number: String,
        expiryMonth: Int,
        expiryYear: Int,
    ) = launchOperation(reload = ::loadAutofill) {
        val digits = number.filter(Char::isDigit)
        autofillStorage.addCreditCard(
            NewCreditCardFields(
                billingName = name.trim(),
                plaintextCardNumber = CreditCardNumber.Plaintext(digits),
                cardNumberLast4 = digits.takeLast(4),
                expiryMonth = expiryMonth.toLong(),
                expiryYear = expiryYear.toLong(),
                cardType = cardType(digits),
            ),
        )
    }

    fun addAddress(form: AddressForm) = launchOperation(reload = ::loadAutofill) {
        autofillStorage.addAddress(
            UpdatableAddressFields(
                name = form.name.trim(),
                organization = "",
                streetAddress = form.street.trim(),
                addressLevel3 = "",
                addressLevel2 = form.city.trim(),
                addressLevel1 = form.region.trim(),
                postalCode = form.postalCode.trim(),
                country = form.country.trim(),
                tel = form.phone.trim(),
                email = form.email.trim(),
            ),
        )
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun launchOperation(
        reload: (() -> Unit)? = null,
        operation: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { withContext(Dispatchers.IO) { operation() } }
                .onSuccess {
                    _state.update { it.copy(loading = false) }
                    reload?.invoke()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(loading = false, error = error.message ?: "Unable to update secure data")
                    }
                }
        }
    }
}

internal fun isValidCardNumber(number: String): Boolean {
    val digits = number.filter(Char::isDigit)
    if (digits.length !in 12..19) return false
    return digits.reversed().mapIndexed { index, char ->
        var value = char.digitToInt()
        if (index % 2 == 1) {
            value *= 2
            if (value > 9) value -= 9
        }
        value
    }.sum() % 10 == 0
}

private fun cardType(number: String): String = when {
    number.startsWith("4") -> "visa"
    number.startsWith("34") || number.startsWith("37") -> "amex"
    number.take(2).toIntOrNull() in 51..55 -> "mastercard"
    else -> "unknown"
}
