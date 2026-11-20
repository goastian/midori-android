package org.midorinext.android.ui.browser.menu

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.midorinext.android.R
import org.midorinext.android.ui.nav.NavDestination
import org.midorinext.android.ui.theme.MidoriDialogSystemBars

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserMenuSheet(
    onDismissRequest: () -> Unit,
    navigation: @Composable ((dismiss: () -> Unit) -> Unit)? = null,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var dismissing by remember { mutableStateOf(false) }
    val dismiss = {
        if (!dismissing) {
            dismissing = true
            scope.launch {
                try {
                    sheetState.hide()
                    if (!sheetState.isVisible) onDismissRequest()
                } finally {
                    dismissing = false
                }
            }
        }
        Unit
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("browser_menu_sheet"),
    ) {
        MidoriDialogSystemBars()
        Column(Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .8f)
            .testTag("browser_menu_content")) {
            navigation?.invoke(dismiss)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 8.dp, end = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content(dismiss)
            }
        }
    }
}

@Composable
internal fun BrowserMenuMainContent(
    showDefaultPrompt: Boolean,
    vpnEnabled: Boolean,
    extensionCount: Int,
    onNavigate: (NavDestination) -> Unit,
    onVpnClick: () -> Unit,
    onSetDefault: () -> Unit,
    onDismissDefault: () -> Unit,
    liteMode: @Composable () -> Unit = {},
    showPageActions: Boolean = false,
    pageActions: @Composable () -> Unit = {},
    moreOptions: @Composable () -> Unit = {},
    account: @Composable () -> Unit = {},
) {
    BrowserMenuGroup {
        pageActions()
        BrowserMenuRow(
            text = stringResource(R.string.adblock_settings_title),
            icon = R.drawable.icons_lock,
            onClick = { onNavigate(NavDestination.AdBlockSettings) },
            modifier = Modifier.testTag("browser_menu_protection"),
        )
        BrowserMenuRow(
            text = stringResource(R.string.menu_midori_vpn),
            icon = R.drawable.icons_internet,
            enabled = vpnEnabled,
            onClick = onVpnClick,
            modifier = Modifier.testTag("browser_menu_vpn"),
        )
        liteMode()
        BrowserMenuRow(
            text = stringResource(R.string.extensions_title),
            icon = R.drawable.icons_menu_extensions,
            onClick = { onNavigate(NavDestination.Extensions) },
            trailing = if (extensionCount > 0) {
                { Text(extensionCount.toString(), style = MaterialTheme.typography.bodyMedium) }
            } else null,
            modifier = Modifier.testTag("browser_menu_extensions"),
        )
        moreOptions()
    }
    BrowserMenuGroup { BrowserMenuLibrary(onNavigate) }
    BrowserMenuGroup {
        account()
        if (!showPageActions) {
            BrowserMenuRow(
                text = stringResource(R.string.menu_customize_homepage),
                icon = R.drawable.icons_home,
                onClick = { onNavigate(NavDestination.HomepageSettings) },
                modifier = Modifier.testTag("browser_menu_customize_homepage"),
            )
        }
        BrowserMenuRow(
            text = stringResource(R.string.settings),
            icon = R.drawable.icons_settings,
            onClick = { onNavigate(NavDestination.Preferences) },
            modifier = Modifier.testTag("browser_menu_settings"),
        )
    }
    if (showDefaultPrompt) {
        DefaultBrowserBanner(onSetDefault, onDismissDefault)
    }
}

@Composable
internal fun BrowserMenuGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        content()
    }
}

@Composable
internal fun BrowserMenuRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    trailing: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    checked: Boolean? = null,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    val interaction = if (checked == null) {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    } else {
        Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = { onClick() })
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .then(interaction)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor.copy(alpha = if (enabled) 1f else .38f)) {
            if (icon != null) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .38f),
                )
            }
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            trailing?.invoke()
        }
    }
}

@Composable
private fun BrowserMenuLibrary(onNavigate: (NavDestination) -> Unit) {
    BrowserMenuActionGrid { range ->
        BrowserMenuLibraryItems(onNavigate, range)
    }
}

@Composable
internal fun BrowserMenuActionGrid(content: @Composable RowScope.(range: IntRange) -> Unit) {
    val largeText = LocalDensity.current.fontScale >= 1.3f || LocalConfiguration.current.screenWidthDp < 360
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val ranges = if (largeText) listOf(0..1, 2..3) else listOf(0..3)
        ranges.forEach { range ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) { content(range) }
        }
    }
}

@Composable
private fun RowScope.BrowserMenuLibraryItems(onNavigate: (NavDestination) -> Unit, range: IntRange = 0..3) {
    val labels = listOf(R.string.history, R.string.bookmarks, R.string.browser_downloads, R.string.settings_passwords_title)
    val icons = listOf(R.drawable.icons_menu_history, R.drawable.icons_menu_bookmarks, R.drawable.icons_download, R.drawable.icons_password)
    val tags = listOf("browser_menu_history", "browser_menu_bookmarks", "browser_menu_downloads", "browser_menu_passwords")
    val destinations = listOf(NavDestination.History, NavDestination.Bookmarks, NavDestination.Downloads, NavDestination.SavedPasswords)
    range.forEach { index ->
        BrowserMenuAction(stringResource(labels[index]), icons[index], tags[index]) { onNavigate(destinations[index]) }
    }
}

@Composable
internal fun RowScope.BrowserMenuAction(
    label: String,
    @DrawableRes icon: Int,
    tag: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.weight(1f).fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 4.dp, vertical = 12.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .38f))
        Text(label, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f))
    }
}

@Composable
private fun DefaultBrowserBanner(onClick: () -> Unit, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 24.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.onboarding_default_title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.menu_default_browser_description), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(48.dp))
        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.Top)) {
            Icon(painterResource(R.drawable.icons_close), contentDescription = stringResource(R.string.menu_dismiss_default_prompt))
        }
    }
}
