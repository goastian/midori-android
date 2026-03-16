/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.settings.personaldata

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject
import org.midorinext.android.R

private val MidoriGreen = Color(0xFF4CAF50)

data class SavedContact(
    val name: String,
    val email: String,
    val phone: String,
    val company: String,
)

class ContactInfoFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            MaterialTheme {
                ContactsScreen()
            }
        }
    }

    @Composable
    private fun ContactsScreen() {
        val context = requireContext()
        val contacts = remember { mutableStateListOf<SavedContact>() }
        var showAddDialog by remember { mutableStateOf(false) }
        var editIndex by remember { mutableStateOf(-1) }
        var deleteIndex by remember { mutableStateOf(-1) }

        remember {
            contacts.addAll(loadContacts(context))
            true
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (contacts.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_contact_phone),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.contact_info_empty),
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
                    itemsIndexed(contacts) { index, contact ->
                        ContactItem(
                            contact = contact,
                            onEdit = { editIndex = index },
                            onDelete = { deleteIndex = index },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }

            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = MidoriGreen,
                contentColor = Color.White,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add_circle),
                    contentDescription = stringResource(R.string.contact_info_add),
                    modifier = Modifier.size(24.dp),
                )
            }

            if (showAddDialog || editIndex >= 0) {
                val editing = if (editIndex >= 0) contacts[editIndex] else null
                ContactEditDialog(
                    contact = editing,
                    onDismiss = {
                        showAddDialog = false
                        editIndex = -1
                    },
                    onSave = { c ->
                        if (editIndex >= 0) {
                            contacts[editIndex] = c
                        } else {
                            contacts.add(c)
                        }
                        saveContacts(context, contacts)
                        showAddDialog = false
                        editIndex = -1
                        Toast.makeText(context, R.string.contact_info_saved, Toast.LENGTH_SHORT).show()
                    },
                )
            }

            if (deleteIndex >= 0) {
                AlertDialog(
                    onDismissRequest = { deleteIndex = -1 },
                    title = { Text(stringResource(R.string.passwords_delete)) },
                    text = { Text(stringResource(R.string.contact_info_delete_confirm)) },
                    confirmButton = {
                        TextButton(onClick = {
                            contacts.removeAt(deleteIndex)
                            saveContacts(context, contacts)
                            deleteIndex = -1
                            Toast.makeText(context, R.string.contact_info_deleted, Toast.LENGTH_SHORT).show()
                        }) {
                            Text(stringResource(R.string.passwords_delete), color = Color.Red)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteIndex = -1 }) {
                            Text(stringResource(R.string.passwords_cancel))
                        }
                    },
                )
            }
        }
    }

    @Composable
    private fun ContactItem(
        contact: SavedContact,
        onEdit: () -> Unit,
        onDelete: () -> Unit,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_contact_phone),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MidoriGreen,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = contact.name.ifEmpty { contact.email },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                if (contact.email.isNotBlank()) {
                    Text(text = contact.email, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
                if (contact.phone.isNotBlank()) {
                    Text(text = contact.phone, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
                if (contact.company.isNotBlank()) {
                    Text(text = contact.company, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onEdit) {
                        Text(stringResource(R.string.passwords_edit), color = MidoriGreen, fontSize = 13.sp)
                    }
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.passwords_delete), color = Color.Red.copy(alpha = 0.7f), fontSize = 13.sp)
                    }
                }
            }
        }
    }

    @Composable
    private fun ContactEditDialog(
        contact: SavedContact?,
        onDismiss: () -> Unit,
        onSave: (SavedContact) -> Unit,
    ) {
        var name by remember { mutableStateOf(contact?.name ?: "") }
        var email by remember { mutableStateOf(contact?.email ?: "") }
        var phone by remember { mutableStateOf(contact?.phone ?: "") }
        var company by remember { mutableStateOf(contact?.company ?: "") }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(if (contact != null) stringResource(R.string.passwords_edit) else stringResource(R.string.contact_info_add))
            },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.contact_info_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text(stringResource(R.string.contact_info_email)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(stringResource(R.string.contact_info_phone)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = company, onValueChange = { company = it }, label = { Text(stringResource(R.string.contact_info_company)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank() || email.isNotBlank()) {
                        onSave(SavedContact(name, email, phone, company))
                    }
                }) {
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

    companion object {
        fun loadContacts(context: Context): List<SavedContact> {
            val prefs = context.getSharedPreferences("midori_contacts", Context.MODE_PRIVATE)
            val json = prefs.getString("contacts_json", "[]") ?: "[]"
            val arr = JSONArray(json)
            val list = mutableListOf<SavedContact>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(SavedContact(
                    name = o.optString("name", ""),
                    email = o.optString("email", ""),
                    phone = o.optString("phone", ""),
                    company = o.optString("company", ""),
                ))
            }
            return list
        }

        fun saveContacts(context: Context, contacts: List<SavedContact>) {
            val arr = JSONArray()
            contacts.forEach { c ->
                val o = JSONObject()
                o.put("name", c.name)
                o.put("email", c.email)
                o.put("phone", c.phone)
                o.put("company", c.company)
                arr.put(o)
            }
            val prefs = context.getSharedPreferences("midori_contacts", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("contacts_json", arr.toString())
                .putInt("contact_count", contacts.size)
                .apply()
        }
    }
}
