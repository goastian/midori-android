/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.settings.personaldata

import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.concept.storage.Login
import mozilla.components.concept.storage.LoginEntry
import org.midorinext.android.R
import org.midorinext.android.ext.requireComponents

private val MidoriGreen = Color(0xFF4CAF50)
private val MidoriGreenDark = Color(0xFF388E3C)

class PasswordsFragment : Fragment() {

    private var isAuthenticated = false
    private lateinit var authLauncher: ActivityResultLauncher<android.content.Intent>
    private val authCallback = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                isAuthenticated = true
                authCallback.value = true
            } else {
                Toast.makeText(
                    requireContext(),
                    R.string.passwords_auth_failed,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            MaterialTheme {
                PasswordsScreen(
                    authState = authCallback.value,
                    onRequestAuth = { requestAuthentication() },
                )
            }
        }
    }

    private fun requestAuthentication() {
        val keyguardManager = requireContext().getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguardManager.isKeyguardSecure) {
            @Suppress("DEPRECATION")
            val intent = keyguardManager.createConfirmDeviceCredentialIntent(
                getString(R.string.passwords_auth_required),
                getString(R.string.passwords_auth_message),
            )
            if (intent != null) {
                authLauncher.launch(intent)
            } else {
                isAuthenticated = true
                authCallback.value = true
            }
        } else {
            isAuthenticated = true
            authCallback.value = true
        }
    }

    @Composable
    private fun PasswordsScreen(
        authState: Boolean,
        onRequestAuth: () -> Unit,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val logins = remember { mutableStateListOf<Login>() }
        var showAddDialog by remember { mutableStateOf(false) }
        var editingLogin by remember { mutableStateOf<Login?>(null) }
        var deleteLogin by remember { mutableStateOf<Login?>(null) }

        LaunchedEffect(authState) {
            if (authState) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val storage = requireComponents.core.loginsStorage
                        val list = storage.list()
                        withContext(Dispatchers.Main) {
                            logins.clear()
                            logins.addAll(list)
                        }
                    } catch (_: Exception) { }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (!authState) {
                // Auth required screen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_password_generate),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MidoriGreen,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.passwords_auth_required),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.passwords_auth_message),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = onRequestAuth,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MidoriGreen,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = "Verify Identity",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            } else {
                // Passwords list
                if (logins.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_key),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.passwords_empty),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(logins, key = { it.guid }) { login ->
                            PasswordCard(
                                login = login,
                                onEdit = { editingLogin = login },
                                onDelete = { deleteLogin = login },
                                onCopyUsername = {
                                    copyToClipboard(context, "username", login.username)
                                },
                                onCopyPassword = {
                                    copyToClipboard(context, "password", login.password)
                                },
                            )
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }

                // FAB
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = MidoriGreen,
                    contentColor = Color.White,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add_circle),
                        contentDescription = stringResource(R.string.passwords_add),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Add/Edit dialog
            if (showAddDialog || editingLogin != null) {
                PasswordEditDialog(
                    login = editingLogin,
                    onDismiss = {
                        showAddDialog = false
                        editingLogin = null
                    },
                    onSave = { site, username, password ->
                        scope.launch(Dispatchers.IO) {
                            try {
                                val storage = requireComponents.core.loginsStorage
                                if (editingLogin != null) {
                                    val entry = LoginEntry(
                                        origin = editingLogin!!.origin,
                                        formActionOrigin = editingLogin!!.formActionOrigin,
                                        httpRealm = editingLogin!!.httpRealm,
                                        username = username,
                                        password = password,
                                    )
                                    storage.update(editingLogin!!.guid, entry)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, R.string.passwords_updated, Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    val entry = LoginEntry(
                                        origin = site,
                                        formActionOrigin = site,
                                        httpRealm = null,
                                        username = username,
                                        password = password,
                                    )
                                    storage.add(entry)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, R.string.passwords_saved, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                val list = storage.list()
                                withContext(Dispatchers.Main) {
                                    logins.clear()
                                    logins.addAll(list)
                                    showAddDialog = false
                                    editingLogin = null
                                }
                            } catch (_: Exception) { }
                        }
                    },
                )
            }

            // Delete confirmation
            if (deleteLogin != null) {
                AlertDialog(
                    onDismissRequest = { deleteLogin = null },
                    title = { Text(stringResource(R.string.passwords_delete)) },
                    text = { Text(stringResource(R.string.passwords_delete_confirm)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val toDelete = deleteLogin!!
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        requireComponents.core.loginsStorage.delete(toDelete.guid)
                                        val list = requireComponents.core.loginsStorage.list()
                                        withContext(Dispatchers.Main) {
                                            logins.clear()
                                            logins.addAll(list)
                                            deleteLogin = null
                                            Toast.makeText(context, R.string.passwords_deleted, Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (_: Exception) { }
                                }
                            },
                        ) {
                            Text(stringResource(R.string.passwords_delete), color = Color.Red)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteLogin = null }) {
                            Text(stringResource(R.string.passwords_cancel))
                        }
                    },
                )
            }
        }
    }

    @Composable
    private fun PasswordCard(
        login: Login,
        onEdit: () -> Unit,
        onDelete: () -> Unit,
        onCopyUsername: () -> Unit,
        onCopyPassword: () -> Unit,
    ) {
        var showPassword by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Site
                Text(
                    text = login.origin.removePrefix("https://").removePrefix("http://"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Username row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.passwords_username),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                        Text(
                            text = login.username,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onCopyUsername, modifier = Modifier.size(36.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_share),
                            contentDescription = stringResource(R.string.passwords_copy_username),
                            modifier = Modifier.size(18.dp),
                            tint = MidoriGreen,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Password row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.passwords_password),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                        Text(
                            text = if (showPassword) login.password else "••••••••",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(
                        onClick = { showPassword = !showPassword },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = if (showPassword) {
                                stringResource(R.string.passwords_hide)
                            } else {
                                stringResource(R.string.passwords_show)
                            },
                            modifier = Modifier.size(18.dp),
                            tint = MidoriGreen,
                        )
                    }
                    IconButton(onClick = onCopyPassword, modifier = Modifier.size(36.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_share),
                            contentDescription = stringResource(R.string.passwords_copy_password),
                            modifier = Modifier.size(18.dp),
                            tint = MidoriGreen,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onEdit) {
                        Text(
                            text = stringResource(R.string.passwords_edit),
                            color = MidoriGreen,
                            fontSize = 13.sp,
                        )
                    }
                    TextButton(onClick = onDelete) {
                        Text(
                            text = stringResource(R.string.passwords_delete),
                            color = Color.Red.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun PasswordEditDialog(
        login: Login?,
        onDismiss: () -> Unit,
        onSave: (site: String, username: String, password: String) -> Unit,
    ) {
        var site by remember { mutableStateOf(login?.origin ?: "https://") }
        var username by remember { mutableStateOf(login?.username ?: "") }
        var password by remember { mutableStateOf(login?.password ?: "") }
        var showPassword by remember { mutableStateOf(false) }
        var showGenerator by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    if (login != null) stringResource(R.string.passwords_edit)
                    else stringResource(R.string.passwords_add),
                )
            },
            text = {
                Column {
                    if (login == null) {
                        OutlinedTextField(
                            value = site,
                            onValueChange = { site = it },
                            label = { Text(stringResource(R.string.passwords_site)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.passwords_username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.passwords_password)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_search),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Generate password button
                    TextButton(
                        onClick = { showGenerator = !showGenerator },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_password_generate),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MidoriGreen,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.passwords_generate),
                            color = MidoriGreen,
                            fontSize = 13.sp,
                        )
                    }

                    AnimatedVisibility(
                        visible = showGenerator,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        PasswordGeneratorInline(
                            onUse = { generated ->
                                password = generated
                                showGenerator = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (site.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                            onSave(site, username, password)
                        }
                    },
                ) {
                    Text(stringResource(R.string.passwords_save), color = MidoriGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.passwords_cancel))
                }
            },
        )
    }

    private fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, R.string.passwords_copied, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun PasswordGeneratorInline(
    onUse: (String) -> Unit,
) {
    var generated by remember { mutableStateOf(generatePassword()) }
    val strength = calculateStrength(generated)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MidoriGreen.copy(alpha = 0.08f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = generated,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MidoriGreenDark,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Strength indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val strengthColor = when (strength) {
                    "Strong" -> Color(0xFF4CAF50)
                    "Medium" -> Color(0xFFFFA726)
                    else -> Color(0xFFEF5350)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(strengthColor.copy(alpha = 0.3f)),
                ) {
                    val fraction = when (strength) {
                        "Strong" -> 1f
                        "Medium" -> 0.66f
                        else -> 0.33f
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(strengthColor),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strength,
                    fontSize = 12.sp,
                    color = strengthColor,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { generated = generatePassword() }) {
                    Text("Refresh", color = MidoriGreen, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onUse(generated) },
                    colors = ButtonDefaults.buttonColors(containerColor = MidoriGreen),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Use", fontSize = 13.sp)
                }
            }
        }
    }
}

private fun generatePassword(length: Int = 16): String {
    val upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    val lower = "abcdefghijklmnopqrstuvwxyz"
    val digits = "0123456789"
    val special = "!@#\$%^&*()-_=+[]{}|;:,.<>?"
    val all = upper + lower + digits + special

    val password = StringBuilder()
    password.append(upper.random())
    password.append(lower.random())
    password.append(digits.random())
    password.append(special.random())
    repeat(length - 4) {
        password.append(all.random())
    }
    return password.toList().shuffled().joinToString("")
}

private fun calculateStrength(password: String): String {
    var score = 0
    if (password.length >= 12) score++
    if (password.length >= 16) score++
    if (password.any { it.isUpperCase() }) score++
    if (password.any { it.isLowerCase() }) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    return when {
        score >= 5 -> "Strong"
        score >= 3 -> "Medium"
        else -> "Weak"
    }
}
