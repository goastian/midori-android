package org.mozilla.reference.browser.storage

import android.os.Parcel
import android.os.Parcelable
import org.midorinext.android.legacy.bookmarks.SerializableBitmap
import java.io.Serializable

class BookmarkItemV2(
    val type: BookmarkType,
    var title: String,

        // bookmarks only
    var url: String? = null,
    var icon: SerializableBitmap? = null,

        // folder only
    var children: ArrayList<BookmarkItemV2> = arrayListOf(),

    var order: Int = -1,

    @Transient var parent: BookmarkItemV2? = null
) : Serializable, Parcelable {
    enum class BookmarkType {
        BOOKMARK, FOLDER
    }

    constructor(parcel: Parcel) : this(
            if (parcel.readInt() == 0) BookmarkType.BOOKMARK else BookmarkType.FOLDER,
            parcel.readString() ?: "",
            parcel.readString()
    ) {
        // parcel.readParcelable(icon)
        val a: Array<BookmarkItemV2> = arrayOf()
        parcel.readTypedArray<BookmarkItemV2>(a, CREATOR)
        if (a.isNotEmpty()) {
            // children = arrayListOf()
            a.forEach {
                it.parent = this
                children.add(it)
                reloadChildren(it)
            }
        }
    }

    private fun reloadChildren(forBookmark: BookmarkItemV2) {
        // if (forBookmark.children.isNotEmpty()) {
            forBookmark.children.forEach {
                it.parent = forBookmark
                children.add(it)
                reloadChildren(it)
            }
        // }
    }

    fun addChild(item: BookmarkItemV2) {
        if (type == BookmarkType.FOLDER) {
            // if (children == null) children = arrayListOf(item)
            // else children!!.add(item)
            children.add(item)
        }
    }

    fun removeChild(item: BookmarkItemV2) {
        if (type == BookmarkType.FOLDER){
            children.remove(item)
        }
    }

    fun isParentOf(item: BookmarkItemV2): Boolean {
        if (item.parent === this) return true
        else if (item.parent != null) return this.isParentOf(item.parent!!)
        return false
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(type.ordinal)
        parcel.writeString(title)
        parcel.writeString(url)
        // parcel.writeParcelable(icon)
        parcel.writeInt(order)
        val a: Array<BookmarkItemV2> = arrayOf()
        parcel.writeTypedArray(children.toArray(a), 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<BookmarkItemV2> {
        private const val serialVersionUID: Long = 6457512457286548937

        override fun createFromParcel(parcel: Parcel): BookmarkItemV2 {
            return BookmarkItemV2(parcel)
        }

        override fun newArray(size: Int): Array<BookmarkItemV2?> {
            return arrayOfNulls(size)
        }
    }
}