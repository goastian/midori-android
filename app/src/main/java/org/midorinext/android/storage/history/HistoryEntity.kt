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
    primaryKeys = ["pageUri", "time"],
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
)

data class VisitKey(
    val pageUri: String,
    val time: Long
)