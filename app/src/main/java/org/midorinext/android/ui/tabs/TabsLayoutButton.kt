package org.midorinext.android.ui.tabs

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import org.midorinext.android.R
import org.midorinext.android.preferences.app.TabsViewOption
import org.midorinext.android.preferences.app.nextTabsViewOption
import org.midorinext.android.ui.preferences.tabsViewOptions

@Composable
internal fun TabsLayoutButton(
    tabsViewOption: TabsViewOption,
    onCycleTabsView: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val nextView = tabsViewOption.nextTabsViewOption()
    val currentLabel = stringResource(tabsViewOptions[tabsViewOption] ?: R.string.available_tabs_view_grid)
    val nextLabel = stringResource(tabsViewOptions.getValue(nextView))
    val actionLabel = stringResource(R.string.tab_tray_switch_view, nextLabel)
    val nextIcon = when (nextView) {
        TabsViewOption.CAROUSEL -> R.drawable.icons_view_carousel
        TabsViewOption.LIST -> R.drawable.icons_list
        else -> R.drawable.icons_grid
    }

    IconButton(
        onClick = onCycleTabsView,
        enabled = enabled,
        modifier = modifier.testTag("tabs_layout_toggle").semantics {
            contentDescription = actionLabel
            stateDescription = currentLabel
        },
    ) {
        Icon(painterResource(nextIcon), contentDescription = null)
    }
}
