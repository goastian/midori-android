package org.midorinext.android.contentBlocker.cacheDb

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.midorinext.android.contentBlocker.ContentBlockerState

enum class BlockListType {
    BLACKLIST, REDIRECT
}

@Entity(
    tableName = "domains",
    primaryKeys = ["hash", "type"]
)
data class Domain(
    val hash: String,
    val type: BlockListType,
    val blocked: Boolean,
    val time: Long
)

@Entity(
    tableName = "urls",
    primaryKeys = ["hash", "type"]
)
data class Url(
    val hash: String,
    val type: BlockListType,
    val blocked: Boolean,
    val time: Long
)

@Entity(tableName = "tab_ids")
data class TabId(
    @PrimaryKey(autoGenerate = false)
    val tabId: String,
    val blocked: Boolean,
    val reason: ContentBlockerState.BlockReason,
    val url: String
)