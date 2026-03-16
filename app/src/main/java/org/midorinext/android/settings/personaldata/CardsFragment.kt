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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject
import org.midorinext.android.R

private val MidoriGreen = Color(0xFF4CAF50)

data class SavedCard(
    val cardholderName: String,
    val cardNumber: String,
    val expiryDate: String,
)

class CardsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            MaterialTheme {
                CardsScreen()
            }
        }
    }

    @Composable
    private fun CardsScreen() {
        val context = requireContext()
        val cards = remember { mutableStateListOf<SavedCard>() }
        var showAddDialog by remember { mutableStateOf(false) }
        var editIndex by remember { mutableStateOf(-1) }
        var deleteIndex by remember { mutableStateOf(-1) }

        // Load cards
        remember {
            cards.addAll(loadCards(context))
            true
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (cards.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_credit_card),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.saved_cards_empty),
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
                    itemsIndexed(cards) { index, card ->
                        CardItem(
                            card = card,
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
                    contentDescription = stringResource(R.string.saved_cards_add),
                    modifier = Modifier.size(24.dp),
                )
            }

            if (showAddDialog || editIndex >= 0) {
                val editing = if (editIndex >= 0) cards[editIndex] else null
                CardEditDialog(
                    card = editing,
                    onDismiss = {
                        showAddDialog = false
                        editIndex = -1
                    },
                    onSave = { card ->
                        if (editIndex >= 0) {
                            cards[editIndex] = card
                        } else {
                            cards.add(card)
                        }
                        saveCards(context, cards)
                        showAddDialog = false
                        editIndex = -1
                        Toast.makeText(context, R.string.saved_cards_saved, Toast.LENGTH_SHORT).show()
                    },
                )
            }

            if (deleteIndex >= 0) {
                AlertDialog(
                    onDismissRequest = { deleteIndex = -1 },
                    title = { Text(stringResource(R.string.passwords_delete)) },
                    text = { Text(stringResource(R.string.saved_cards_delete_confirm)) },
                    confirmButton = {
                        TextButton(onClick = {
                            cards.removeAt(deleteIndex)
                            saveCards(context, cards)
                            deleteIndex = -1
                            Toast.makeText(context, R.string.saved_cards_deleted, Toast.LENGTH_SHORT).show()
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
    private fun CardItem(
        card: SavedCard,
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
                        painter = painterResource(R.drawable.ic_credit_card),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MidoriGreen,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = card.cardholderName.ifEmpty { "Card" },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = maskCardNumber(card.cardNumber),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Text(
                    text = "Expires ${card.expiryDate}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
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
    private fun CardEditDialog(
        card: SavedCard?,
        onDismiss: () -> Unit,
        onSave: (SavedCard) -> Unit,
    ) {
        var name by remember { mutableStateOf(card?.cardholderName ?: "") }
        var number by remember { mutableStateOf(card?.cardNumber ?: "") }
        var expiry by remember { mutableStateOf(card?.expiryDate ?: "") }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(if (card != null) stringResource(R.string.passwords_edit) else stringResource(R.string.saved_cards_add))
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.saved_cards_cardholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it.filter { c -> c.isDigit() }.take(19) },
                        label = { Text(stringResource(R.string.saved_cards_card_number)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = expiry,
                        onValueChange = { expiry = it.take(5) },
                        label = { Text(stringResource(R.string.saved_cards_expiry)) },
                        placeholder = { Text("MM/YY") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (number.isNotBlank()) {
                        onSave(SavedCard(name, number, expiry))
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

    private fun maskCardNumber(number: String): String {
        if (number.length < 4) return number
        return "•••• •••• •••• " + number.takeLast(4)
    }

    companion object {
        fun loadCards(context: Context): List<SavedCard> {
            val prefs = context.getSharedPreferences("midori_cards", Context.MODE_PRIVATE)
            val json = prefs.getString("cards_json", "[]") ?: "[]"
            val arr = JSONArray(json)
            val list = mutableListOf<SavedCard>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SavedCard(
                        cardholderName = obj.optString("name", ""),
                        cardNumber = obj.optString("number", ""),
                        expiryDate = obj.optString("expiry", ""),
                    ),
                )
            }
            return list
        }

        fun saveCards(context: Context, cards: List<SavedCard>) {
            val arr = JSONArray()
            cards.forEach { card ->
                val obj = JSONObject()
                obj.put("name", card.cardholderName)
                obj.put("number", card.cardNumber)
                obj.put("expiry", card.expiryDate)
                arr.put(obj)
            }
            val prefs = context.getSharedPreferences("midori_cards", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("cards_json", arr.toString())
                .putInt("card_count", cards.size)
                .apply()
        }
    }
}
