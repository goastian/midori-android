package org.midorinext.android.ui.preferences

import androidx.compose.runtime.Composable
import org.midorinext.android.R
import org.midorinext.android.preferences.app.TabsViewOption
import org.midorinext.android.ui.preferences.widgets.PreferenceRadioButtonSelector
import org.midorinext.android.ui.preferences.widgets.PreferenceSelectionPopup
import org.midorinext.android.ui.preferences.widgets.RadioButtonOption


val tabsViewOptions = mapOf(
    TabsViewOption.GRID to R.string.available_tabs_view_grid,
    TabsViewOption.LIST to R.string.available_tabs_view_list
)

@Composable
fun TabsViewPreference(
    value: TabsViewOption,
    onValueChange: (TabsViewOption) -> Unit
) {
    PreferenceSelectionPopup(
        label = R.string.tabs_view_label,
        description = tabsViewOptions[value],
        popupContent = { TabsViewPreferenceSelector(
            value = value,
            onValueChange = onValueChange
        )}
    )
}

@Composable
fun TabsViewPreferenceSelector(
    value: TabsViewOption,
    onValueChange: (TabsViewOption) -> Unit
) {
    PreferenceRadioButtonSelector(
        options = tabsViewOptions.map {
            RadioButtonOption(it.key, it.value)
        },
        value = value,
        onValueChange = onValueChange
    )
}