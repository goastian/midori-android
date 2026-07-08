package org.midorinext.android.preferences.app

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import mozilla.components.concept.engine.Engine
import java.io.InputStream
import java.io.OutputStream


object AppPreferencesSerializer : Serializer<AppPreferences> {
    override val defaultValue: AppPreferences = AppPreferences.getDefaultInstance().toBuilder()
        .setToolbarPosition(ToolbarPosition.TOP)
        .setHideToolbarOnScroll(true)
        .setTabsView(TabsViewOption.GRID)
        .setOpenLinksInApp(true)
        .setClearDataOnQuit(false)
        .setClearDataHistory(true)
        .setClearDataTabs(true)
        .setClearDataBrowsingdata(Engine.BrowsingData.ALL)
        .setPrivacyGlobalPrivacyControl(true)
        .setPrivacyFingerprintingProtection(true)
        .setPrivacyCookiePartitioning(true)
        .setPrivacyStrictTrackingProtection(true)
        .setTrackingProtectionLevel(TrackingProtectionLevel.STANDARD)
        .setDohProvider(DoHProvider.DOH_DEFAULT)
        .setHttpsOnlyLevel(HttpsOnlyLevel.OFF)
        .build()

    override suspend fun readFrom(input: InputStream): AppPreferences {
        try {
            return AppPreferences.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read protobuf.", exception)
        }
    }

    override suspend fun writeTo(t: AppPreferences, output: OutputStream) = t.writeTo(output)
}