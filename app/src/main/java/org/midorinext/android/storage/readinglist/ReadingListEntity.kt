package org.midorinext.android.storage.readinglist

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reading_list_item",
    indices = [Index(value = ["url"], unique = true)]
)
data class ReadingListItem(
    @PrimaryKey(autoGenerate = false)
    val id: String,
    val url: String,
    val title: String,
    val addedAt: Long,
    val updatedAt: Long,
    val read: Boolean = false,
    val offlinePath: String? = null
)
