package org.midorinext.android.ui.preferences

import android.app.KeyguardManager
import android.os.Build
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import mozilla.components.concept.storage.Address
import mozilla.components.concept.storage.CreditCard
import mozilla.components.concept.storage.Login
import org.midorinext.android.R
import org.midorinext.android.ext.activity
import org.midorinext.android.ui.widgets.ScreenHeader
import java.time.YearMonth

@Composable
fun SavedPasswordsScreen(viewModel: SecureDataViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    SecureUnlockGate(
        title = stringResource(R.string.settings_saved_passwords),
        onUnlocked = viewModel::loadPasswords,
    ) {
        SecureScreenScaffold(title = stringResource(R.string.settings_saved_passwords)) {
            SecureStatusHeader(
                title = stringResource(R.string.passwords_vault_title),
                description = stringResource(R.string.passwords_vault_description),
                count = state.logins.size,
            )
            StateMessage(state)
            if (!state.loading && state.logins.isEmpty()) {
                SecureEmptyState(
                    title = stringResource(R.string.passwords_empty),
                    description = stringResource(R.string.passwords_empty_description),
                )
            } else {
                state.logins.forEach { login ->
                    LoginCard(login = login, onDelete = { viewModel.deleteLogin(login.guid) })
                }
            }
        }
    }
}

@Composable
fun SavedAutofillScreen(viewModel: SecureDataViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var showCardDialog by remember { mutableStateOf(false) }
    var showAddressDialog by remember { mutableStateOf(false) }

    SecureUnlockGate(
        title = stringResource(R.string.settings_autofill_title),
        onUnlocked = viewModel::loadAutofill,
    ) {
        SecureScreenScaffold(title = stringResource(R.string.settings_autofill_title)) {
            SecureStatusHeader(
                title = stringResource(R.string.autofill_vault_title),
                description = stringResource(R.string.autofill_vault_description),
                count = state.cards.size + state.addresses.size,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = { showCardDialog = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_autofill_add_card))
                }
                OutlinedButton(onClick = { showAddressDialog = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_autofill_add_address))
                }
            }
            StateMessage(state)
            SectionTitle(stringResource(R.string.settings_autofill_cards))
            if (!state.loading && state.cards.isEmpty()) {
                SecureEmptyState(
                    title = stringResource(R.string.saved_cards_empty),
                    description = stringResource(R.string.saved_cards_empty_description),
                )
            }
            state.cards.forEach { card ->
                CreditCardRow(card = card, onDelete = { viewModel.deleteCard(card.guid) })
            }
            SectionTitle(stringResource(R.string.settings_autofill_addresses))
            if (!state.loading && state.addresses.isEmpty()) {
                SecureEmptyState(
                    title = stringResource(R.string.saved_addresses_empty),
                    description = stringResource(R.string.saved_addresses_empty_description),
                )
            }
            state.addresses.forEach { address ->
                AddressRow(address = address, onDelete = { viewModel.deleteAddress(address.guid) })
            }
        }
    }

    if (showCardDialog) {
        AddCardDialog(
            onDismiss = { showCardDialog = false },
            onSave = { name, number, month, year ->
                viewModel.addCard(name, number, month, year)
                showCardDialog = false
            },
        )
    }
    if (showAddressDialog) {
        AddAddressDialog(
            onDismiss = { showAddressDialog = false },
            onSave = {
                viewModel.addAddress(it)
                showAddressDialog = false
            },
        )
    }
}

@Composable
private fun SecureUnlockGate(
    title: String,
    onUnlocked: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.activity
    var unlocked by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val unlockSubtitle = stringResource(R.string.secure_unlock_subtitle)
    val noScreenLock = stringResource(R.string.secure_unlock_no_screen_lock)

    val authenticate = remember(activity, title, unlockSubtitle, noScreenLock) {
        {
            val keyguardManager = ContextCompat.getSystemService(context, KeyguardManager::class.java)
            if (activity == null || keyguardManager?.isDeviceSecure != true) {
                error = noScreenLock
            } else {
                val executor = ContextCompat.getMainExecutor(context)
                val prompt = BiometricPrompt(
                    activity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            unlocked = true
                            error = null
                            onUnlocked()
                        }

                        override fun onAuthenticationError(code: Int, message: CharSequence) {
                            error = message.toString()
                        }

                        override fun onAuthenticationFailed() {
                            error = context.getString(R.string.secure_unlock_failed)
                        }
                    },
                )
                val builder = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(unlockSubtitle)
                    .setConfirmationRequired(false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setAllowedAuthenticators(
                        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    builder.setDeviceCredentialAllowed(true)
                }
                prompt.authenticate(builder.build())
            }
        }
    }

    LaunchedEffect(Unit) { authenticate() }

    if (unlocked) {
        content()
    } else {
        SecureScreenScaffold(title = title) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.icons_lock),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.secure_unlock_description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(onClick = authenticate) {
                        Text(stringResource(R.string.secure_unlock_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginCard(login: Login, onDelete: () -> Unit) {
    var showPassword by remember(login.guid) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    SecureItemCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(login.origin, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(login.username.ifBlank { stringResource(R.string.passwords_no_username) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (showPassword) login.password else "••••••••••••",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            painter = painterResource(if (showPassword) R.drawable.icons_eye_off else R.drawable.icons_eye),
                            contentDescription = stringResource(
                                if (showPassword) R.string.passwords_hide else R.string.passwords_show,
                            ),
                        )
                    }
                }
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(painterResource(R.drawable.icons_trash), stringResource(R.string.passwords_delete))
            }
        }
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.passwords_delete_confirm),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }
}

@Composable
private fun CreditCardRow(card: CreditCard, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    SecureItemCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(card.billingName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(card.obfuscatedCardNumber, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.saved_cards_expires, card.expiryMonth, card.expiryYear),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(painterResource(R.drawable.icons_trash), stringResource(R.string.saved_cards_delete))
            }
        }
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.saved_cards_delete_confirm),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }
}

@Composable
private fun AddressRow(address: Address, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    SecureItemCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(address.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(address.streetAddress, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    listOf(address.addressLevel2, address.addressLevel1, address.postalCode, address.country)
                        .filter(String::isNotBlank).joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(painterResource(R.drawable.icons_trash), stringResource(R.string.saved_addresses_delete))
            }
        }
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.saved_addresses_delete_confirm),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }
}

@Composable
private fun AddCardDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Int, Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val now = remember { YearMonth.now() }
    val invalidCard = stringResource(R.string.saved_cards_invalid_number)
    val invalidExpiry = stringResource(R.string.saved_cards_invalid_expiry)
    val requiredFields = stringResource(R.string.secure_required_fields)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.saved_cards_add)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.saved_cards_cardholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = number,
                    onValueChange = { value -> number = value.filter(Char::isDigit).take(19) },
                    label = { Text(stringResource(R.string.saved_cards_card_number)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = month,
                        onValueChange = { month = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(R.string.saved_cards_month)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.saved_cards_year)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(
                    stringResource(R.string.saved_cards_encryption_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val expiryMonth = month.toIntOrNull()
                val expiryYear = year.toIntOrNull()
                error = when {
                    name.isBlank() || number.isBlank() || month.isBlank() || year.isBlank() -> requiredFields
                    !isValidCardNumber(number) -> invalidCard
                    expiryMonth !in 1..12 || expiryYear == null ||
                        YearMonth.of(expiryYear, expiryMonth!!).isBefore(now) -> invalidExpiry
                    else -> null
                }
                if (error == null) onSave(name, number, expiryMonth!!, expiryYear!!)
            }) { Text(stringResource(R.string.passwords_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.passwords_cancel)) } },
    )
}

@Composable
private fun AddAddressDialog(onDismiss: () -> Unit, onSave: (AddressForm) -> Unit) {
    var name by remember { mutableStateOf("") }
    var street by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var postalCode by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val requiredFields = stringResource(R.string.secure_required_fields)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.saved_addresses_add)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AddressField(name, { name = it }, R.string.saved_addresses_name)
                AddressField(street, { street = it }, R.string.saved_addresses_street)
                AddressField(city, { city = it }, R.string.saved_addresses_city)
                AddressField(region, { region = it }, R.string.saved_addresses_state)
                AddressField(postalCode, { postalCode = it }, R.string.saved_addresses_zip)
                AddressField(country, { country = it }, R.string.saved_addresses_country)
                AddressField(phone, { phone = it }, R.string.saved_addresses_phone, KeyboardType.Phone)
                AddressField(email, { email = it }, R.string.saved_addresses_email, KeyboardType.Email)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank() || street.isBlank() || city.isBlank() || country.isBlank()) {
                    error = requiredFields
                } else {
                    onSave(AddressForm(name, street, city, region, postalCode, country, phone, email))
                }
            }) { Text(stringResource(R.string.passwords_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.passwords_cancel)) } },
    )
}

@Composable
private fun AddressField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SecureScreenScaffold(title: String, content: @Composable () -> Unit) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(title = title, scrollableState = scrollState)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
private fun SecureStatusHeader(title: String, description: String, count: Int) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.secure_items_count, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun StateMessage(state: SecureDataUiState) {
    if (state.loading) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
    state.error?.let {
        Text(
            stringResource(R.string.secure_storage_error),
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun SecureEmptyState(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SecureItemCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) { Box(Modifier.padding(16.dp)) { content() } }
}

@Composable
private fun SectionTitle(title: String) {
    Column(Modifier.padding(top = 10.dp)) {
        Text(
            title.uppercase(),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun ConfirmDeleteDialog(title: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(stringResource(R.string.secure_delete_irreversible)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.passwords_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.passwords_cancel)) } },
    )
}
