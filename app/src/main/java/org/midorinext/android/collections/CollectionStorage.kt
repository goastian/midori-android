/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.collections

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TabCollection(
    val id: String,
    val title: String,
    val tabs: List<CollectionTab>,
    val createdAt: Long = System.currentTimeMillis(),
)

data class CollectionTab(
    val title: String,
    val url: String,
)

class CollectionStorage(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCollections(): List<TabCollection> {
        val json = prefs.getString(KEY_COLLECTIONS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val tabsArray = obj.getJSONArray("tabs")
                val tabs = (0 until tabsArray.length()).map { j ->
                    val tab = tabsArray.getJSONObject(j)
                    CollectionTab(
                        title = tab.getString("title"),
                        url = tab.getString("url"),
                    )
                }
                TabCollection(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    tabs = tabs,
                    createdAt = obj.optLong("createdAt", 0L),
                )
            }.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCollection(collection: TabCollection) {
        val collections = getCollections().toMutableList()
        val existingIndex = collections.indexOfFirst { it.id == collection.id }
        if (existingIndex >= 0) {
            collections[existingIndex] = collection
        } else {
            collections.add(0, collection)
        }
        persist(collections)
    }

    fun deleteCollection(collectionId: String) {
        val collections = getCollections().filter { it.id != collectionId }
        persist(collections)
    }

    fun removeTabFromCollection(collectionId: String, tabUrl: String) {
        val collections = getCollections().toMutableList()
        val index = collections.indexOfFirst { it.id == collectionId }
        if (index >= 0) {
            val collection = collections[index]
            val updatedTabs = collection.tabs.filter { it.url != tabUrl }
            if (updatedTabs.isEmpty()) {
                collections.removeAt(index)
            } else {
                collections[index] = collection.copy(tabs = updatedTabs)
            }
            persist(collections)
        }
    }

    fun addTabsToCollection(collectionId: String, tabs: List<CollectionTab>) {
        val collections = getCollections().toMutableList()
        val index = collections.indexOfFirst { it.id == collectionId }
        if (index >= 0) {
            val collection = collections[index]
            val existingUrls = collection.tabs.map { it.url }.toSet()
            val newTabs = tabs.filter { it.url !in existingUrls }
            collections[index] = collection.copy(tabs = collection.tabs + newTabs)
            persist(collections)
        }
    }

    private fun persist(collections: List<TabCollection>) {
        val array = JSONArray()
        for (collection in collections) {
            val obj = JSONObject()
            obj.put("id", collection.id)
            obj.put("title", collection.title)
            obj.put("createdAt", collection.createdAt)
            val tabsArray = JSONArray()
            for (tab in collection.tabs) {
                val tabObj = JSONObject()
                tabObj.put("title", tab.title)
                tabObj.put("url", tab.url)
                tabsArray.put(tabObj)
            }
            obj.put("tabs", tabsArray)
            array.put(obj)
        }
        prefs.edit().putString(KEY_COLLECTIONS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "midori_collections"
        private const val KEY_COLLECTIONS = "collections_data"
    }
}
