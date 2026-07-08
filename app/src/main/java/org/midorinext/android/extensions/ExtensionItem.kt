package org.midorinext.android.extensions

/**
 * Lightweight UI model that represents an installed browser extension.
 *
 * This is mapped from [org.midorinext.android.extensions.store.InstalledExtensionProto]
 * so that the UI layer never depends on the protobuf classes directly.
 */
data class ExtensionItem(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val iconUrl: String,
    val enabled: Boolean
)

