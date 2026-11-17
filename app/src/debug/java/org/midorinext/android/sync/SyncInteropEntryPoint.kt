package org.midorinext.android.sync

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import mozilla.components.service.sync.autofill.AutofillCreditCardsAddressesStorage
import org.midorinext.android.storage.autofill.LoginAutofillRepository
import org.midorinext.android.storage.bookmarks.BookmarksRepository
import org.midorinext.android.storage.history.HistoryDatabase
import org.midorinext.android.storage.history.HistoryRepository

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface SyncInteropEntryPoint {
    fun coordinator(): SyncCoordinator
    fun stores(): SyncNativeStores
    fun vault(): SyncVault
    fun bookmarks(): BookmarksRepository
    fun history(): HistoryRepository
    fun historyDatabase(): HistoryDatabase
    fun logins(): LoginAutofillRepository
    fun autofill(): AutofillCreditCardsAddressesStorage
    fun forms(): SyncFormHistory
}

