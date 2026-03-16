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

data class SavedAddress(
    val name: String,
    val street: String,
    val city: String,
    val state: String,
    val zip: String,
    val country: String,
    val phone: String,
    val email: String,
)

class AddressesFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            MaterialTheme {
                AddressesScreen()
            }
        }
    }

    @Composable
    private fun AddressesScreen() {
        val context = requireContext()
        val addresses = remember { mutableStateListOf<SavedAddress>() }
        var showAddDialog by remember { mutableStateOf(false) }
        var editIndex by remember { mutableStateOf(-1) }
        var deleteIndex by remember { mutableStateOf(-1) }

        remember {
            addresses.addAll(loadAddresses(context))
            true
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (addresses.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_location),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.saved_addresses_empty),
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
                    itemsIndexed(addresses) { index, address ->
                        AddressItem(
                            address = address,
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
                    contentDescription = stringResource(R.string.saved_addresses_add),
                    modifier = Modifier.size(24.dp),
                )
            }

            if (showAddDialog || editIndex >= 0) {
                val editing = if (editIndex >= 0) addresses[editIndex] else null
                AddressEditDialog(
                    address = editing,
                    onDismiss = {
                        showAddDialog = false
                        editIndex = -1
                    },
                    onSave = { addr ->
                        if (editIndex >= 0) {
                            addresses[editIndex] = addr
                        } else {
                            addresses.add(addr)
                        }
                        saveAddresses(context, addresses)
                        showAddDialog = false
                        editIndex = -1
                        Toast.makeText(context, R.string.saved_addresses_saved, Toast.LENGTH_SHORT).show()
                    },
                )
            }

            if (deleteIndex >= 0) {
                AlertDialog(
                    onDismissRequest = { deleteIndex = -1 },
                    title = { Text(stringResource(R.string.passwords_delete)) },
                    text = { Text(stringResource(R.string.saved_addresses_delete_confirm)) },
                    confirmButton = {
                        TextButton(onClick = {
                            addresses.removeAt(deleteIndex)
                            saveAddresses(context, addresses)
                            deleteIndex = -1
                            Toast.makeText(context, R.string.saved_addresses_deleted, Toast.LENGTH_SHORT).show()
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
    private fun AddressItem(
        address: SavedAddress,
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
                        painter = painterResource(R.drawable.ic_location),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MidoriGreen,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = address.name.ifEmpty { address.street },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                if (address.street.isNotBlank()) {
                    Text(
                        text = address.street,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
                val cityLine = listOf(address.city, address.state, address.zip)
                    .filter { it.isNotBlank() }.joinToString(", ")
                if (cityLine.isNotBlank()) {
                    Text(
                        text = cityLine,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
                if (address.country.isNotBlank()) {
                    Text(
                        text = address.country,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
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
    private fun AddressEditDialog(
        address: SavedAddress?,
        onDismiss: () -> Unit,
        onSave: (SavedAddress) -> Unit,
    ) {
        var name by remember { mutableStateOf(address?.name ?: "") }
        var street by remember { mutableStateOf(address?.street ?: "") }
        var city by remember { mutableStateOf(address?.city ?: "") }
        var state by remember { mutableStateOf(address?.state ?: "") }
        var zip by remember { mutableStateOf(address?.zip ?: "") }
        var country by remember { mutableStateOf(address?.country ?: "") }
        var phone by remember { mutableStateOf(address?.phone ?: "") }
        var email by remember { mutableStateOf(address?.email ?: "") }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(if (address != null) stringResource(R.string.passwords_edit) else stringResource(R.string.saved_addresses_add))
            },
            text = {
                LazyColumn {
                    item {
                        Column {
                            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.saved_addresses_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = street, onValueChange = { street = it }, label = { Text(stringResource(R.string.saved_addresses_street)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text(stringResource(R.string.saved_addresses_city)) }, singleLine = true, modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedTextField(value = state, onValueChange = { state = it }, label = { Text(stringResource(R.string.saved_addresses_state)) }, singleLine = true, modifier = Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(value = zip, onValueChange = { zip = it }, label = { Text(stringResource(R.string.saved_addresses_zip)) }, singleLine = true, modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedTextField(value = country, onValueChange = { country = it }, label = { Text(stringResource(R.string.saved_addresses_country)) }, singleLine = true, modifier = Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(stringResource(R.string.saved_addresses_phone)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text(stringResource(R.string.saved_addresses_email)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank() || street.isNotBlank()) {
                        onSave(SavedAddress(name, street, city, state, zip, country, phone, email))
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
        fun loadAddresses(context: Context): List<SavedAddress> {
            val prefs = context.getSharedPreferences("midori_addresses", Context.MODE_PRIVATE)
            val json = prefs.getString("addresses_json", "[]") ?: "[]"
            val arr = JSONArray(json)
            val list = mutableListOf<SavedAddress>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(SavedAddress(
                    name = o.optString("name", ""),
                    street = o.optString("street", ""),
                    city = o.optString("city", ""),
                    state = o.optString("state", ""),
                    zip = o.optString("zip", ""),
                    country = o.optString("country", ""),
                    phone = o.optString("phone", ""),
                    email = o.optString("email", ""),
                ))
            }
            return list
        }

        fun saveAddresses(context: Context, addresses: List<SavedAddress>) {
            val arr = JSONArray()
            addresses.forEach { a ->
                val o = JSONObject()
                o.put("name", a.name)
                o.put("street", a.street)
                o.put("city", a.city)
                o.put("state", a.state)
                o.put("zip", a.zip)
                o.put("country", a.country)
                o.put("phone", a.phone)
                o.put("email", a.email)
                arr.put(o)
            }
            val prefs = context.getSharedPreferences("midori_addresses", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("addresses_json", arr.toString())
                .putInt("address_count", addresses.size)
                .apply()
        }
    }
}
