package org.midorinext.android.preferences.app

import org.junit.Assert.assertFalse
import org.junit.Test

class AppPreferencesSerializerTest {
    @Test
    fun newTabHomeIsShownByDefault() {
        assertFalse(AppPreferencesSerializer.defaultValue.openBlankNewTab)
    }
}
