package org.midorinext.android.storage.history

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import mozilla.components.concept.storage.VisitType

@Entity(tableName = "pages")
data class Page(
    @PrimaryKey(autoGenerate = false)
    val uri: String,

    var title: String? = null,
    var previewImageUrl: String? = null
)

@Entity(
    tableName = "visits",
    primaryKeys = ["pageUri", "timestampUsec", "type"],
    foreignKeys = [ForeignKey(
        entity = Page::class,
        parentColumns = arrayOf("uri"),
        childColumns = arrayOf("pageUri"),
        onDelete = ForeignKey.CASCADE
    )]
)
data class Visit(
    val pageUri: String,
    val time: Long,
    val type: VisitType,
    val timestampUsec: Long = time * 1000,
    val count: Int = 1,
)

data class VisitKey(
    val pageUri: String,
    val time: Long
)

@Entity(tableName = "sync_history_removals")
data class HistoryRemoval(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String?,
    val startTime: Long,
    val endTime: Long,
)
