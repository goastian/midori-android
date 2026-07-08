package org.midorinext.android.storage.bookmarks

import androidx.room.Entity
import androidx.room.PrimaryKey
import mozilla.components.concept.storage.BookmarkNodeType


@Entity(tableName = "bookmark_node")
data class BookmarkNode(
    @PrimaryKey(autoGenerate = false)
    val guid: String,
    val type: BookmarkNodeType,
    var parentGuid: String?,
    var position: Int?,
    var title: String?,
    var url: String?,
    val dateAdded: Long
)