package org.midorinext.android.preferences.app

import org.midorinext.android.newtab.HomeAdsBookmark
import org.midorinext.android.newtab.defaultHomeShortcuts

internal fun AppPreferences.withHomeAdsLegacyDefaults(): AppPreferences {
    val defaults = defaultHomeShortcuts().associateBy { it.id }
    val shortcuts = homeShortcutsList.map { saved ->
        val original = defaults[saved.id]
        if (saved.adsBookmarkKey.isEmpty() && !saved.isFolder && original != null &&
            original.adsBookmarkKey !in hiddenHomeAdsKeysList && original.title == saved.title && original.url == saved.url) {
            saved.toBuilder().setAdsBookmarkKey(original.adsBookmarkKey).setAdsDefault(true).build()
        } else saved
    }
    return toBuilder().clearHomeShortcuts().addAllHomeShortcuts(shortcuts).build()
}

internal fun AppPreferences.withHomeAdsCatalog(catalog: List<HomeAdsBookmark>): AppPreferences {
    val legacyDefaults = defaultHomeShortcuts().associateBy { it.id }
    val entries = catalog.associateBy { it.key }
    val hidden = hiddenHomeAdsKeysList.toMutableSet()
    if (!homeAdsCatalogInitialized && homeShortcutsInitialized) {
        legacyDefaults.values.filter { original ->
            homeShortcutsList.none { saved -> saved.adsBookmarkKey == original.adsBookmarkKey ||
                (saved.id == original.id && saved.title == original.title && saved.url == original.url) }
        }.forEach { hidden.add(it.adsBookmarkKey) }
    }
    val shortcuts = homeShortcutsList.mapNotNull { saved ->
        val legacy = legacyDefaults[saved.id]?.takeIf {
            saved.adsBookmarkKey.isEmpty() && !saved.isFolder && it.title == saved.title && it.url == saved.url
        }
        val key = saved.adsBookmarkKey.ifEmpty { legacy?.adsBookmarkKey.orEmpty() }
        if (key.isEmpty()) return@mapNotNull saved
        val entry = entries[key]
        val default = saved.adsDefault || legacy != null
        if (default && (entry == null || key in hidden || (saved.parentId.isEmpty() && !entry.isHomeDefault))) return@mapNotNull null
        if (entry == null) return@mapNotNull saved
        saved.toBuilder().setTitle(entry.title).setUrl(entry.url).setAdsBookmarkKey(key).setAdsDefault(default).build()
    }.toMutableList()
    val present = shortcuts.map { it.adsBookmarkKey }.toSet()
    val personalUrls = shortcuts.filter { it.adsBookmarkKey.isEmpty() }.map { it.url }.toSet()
    catalog.filter { it.isHomeDefault && it.key !in hidden && it.key !in present && it.url !in personalUrls }.forEach { entry ->
        shortcuts.add(HomeShortcut.newBuilder().setId("ads-default:${entry.key}")
            .setTitle(entry.title).setUrl(entry.url).setAdsBookmarkKey(entry.key).setAdsDefault(true).build())
    }
    return toBuilder().clearHomeShortcuts().addAllHomeShortcuts(shortcuts)
        .clearHiddenHomeAdsKeys().addAllHiddenHomeAdsKeys(hidden).setHomeAdsCatalogInitialized(true).build()
}
