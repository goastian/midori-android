package org.midorinext.android.ui.browser.menu

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val dismiss = {
        scope.launch {
            sheetState.hide()
            if (!sheetState.isVisible) onDismissRequest()
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content(dismiss)
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
    onAccountClick: () -> Unit,
    onSetDefault: () -> Unit,
    onDismissDefault: () -> Unit,
) {
    if (showDefaultPrompt) {
        DefaultBrowserBanner(onSetDefault, onDismissDefault)
    }
    BrowserMenuGroup {
        BrowserMenuRow(
            text = stringResource(R.string.menu_midori_vpn),
            icon = R.drawable.icons_internet,
            enabled = vpnEnabled,
            onClick = onVpnClick,
            modifier = Modifier.testTag("browser_menu_vpn"),
        )
    }
    BrowserMenuGroup {
        BrowserMenuRow(
            text = stringResource(R.string.extensions_title),
            icon = R.drawable.icons_menu_extensions,
            onClick = { onNavigate(NavDestination.Extensions) },
            trailing = if (extensionCount > 0) {
                { Text(extensionCount.toString(), style = MaterialTheme.typography.bodyMedium) }
            } else null,
            modifier = Modifier.testTag("browser_menu_extensions"),
        )
    }
    BrowserMenuLibrary(onNavigate)
    BrowserMenuGroup {
        BrowserMenuRow(
            text = stringResource(R.string.menu_account),
            icon = R.drawable.icons_account,
            onClick = onAccountClick,
        )
        BrowserMenuRow(
            text = stringResource(R.string.menu_customize_homepage),
            icon = R.drawable.icons_home,
            onClick = { onNavigate(NavDestination.HomepageSettings) },
            modifier = Modifier.testTag("browser_menu_customize_homepage"),
        )
        BrowserMenuRow(
            text = stringResource(R.string.settings),
            icon = R.drawable.icons_settings,
            onClick = { onNavigate(NavDestination.Preferences) },
            modifier = Modifier.testTag("browser_menu_settings"),
        )
    }
}

@Composable
internal fun BrowserMenuGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        content = content,
    )
}

@Composable
internal fun BrowserMenuRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    trailing: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor.copy(alpha = if (enabled) 1f else .38f)) {
            if (icon != null) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = contentColor.copy(alpha = if (enabled) .7f else .38f),
                )
            }
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            trailing?.invoke()
        }
    }
}

@Composable
private fun BrowserMenuLibrary(onNavigate: (NavDestination) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(MaterialTheme.shapes.medium).testTag("browser_menu_library"),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BrowserMenuLibraryItem(R.string.history, R.drawable.icons_menu_history, "browser_menu_history") {
            onNavigate(NavDestination.History)
        }
        BrowserMenuLibraryItem(R.string.bookmarks, R.drawable.icons_menu_bookmarks, "browser_menu_bookmarks") {
            onNavigate(NavDestination.Bookmarks)
        }
        BrowserMenuLibraryItem(R.string.browser_downloads, R.drawable.icons_download, "browser_menu_downloads") {
            onNavigate(NavDestination.Downloads)
        }
        BrowserMenuLibraryItem(R.string.settings_passwords_title, R.drawable.icons_password, "browser_menu_passwords") {
            onNavigate(NavDestination.SavedPasswords)
        }
    }
}

@Composable
private fun RowScope.BrowserMenuLibraryItem(label: Int, icon: Int, tag: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.weight(1f).fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 2.dp, vertical = 12.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DefaultBrowserBanner(onClick: () -> Unit, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
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
