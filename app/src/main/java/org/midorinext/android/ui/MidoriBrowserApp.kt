package org.midorinext.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import org.midorinext.android.ui.nav.MidoriNavHost
import org.midorinext.android.ui.theme.MidoriBrowserTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.midorinext.android.preferences.app.Appearance
import org.midorinext.android.preferences.app.ToolbarPosition
import org.midorinext.android.ui.zap.ZapFeature

@Composable
fun MidoriBrowserApp(
    applicationViewModel: MidoriApplicationViewModel = hiltViewModel()
) {
    val navController = rememberNavController()

    val isPrivate by applicationViewModel.isPrivate.collectAsState()
    val appearance by applicationViewModel.appearance.collectAsState()
    val toolbarPosition by applicationViewModel.toolbarPosition.collectAsState()

    // Protobuf returns UNRECOGNIZED for enum values written by a newer or older app
    // version. Never hand that sentinel to Compose as a remember key: its generated
    // getNumber() implementation throws by design.
    val resolvedAppearance = when (appearance) {
        Appearance.LIGHT,
        Appearance.DARK,
        Appearance.SYSTEM_SETTINGS -> appearance
        else -> Appearance.SYSTEM_SETTINGS
    }
    val resolvedToolbarPosition = when (toolbarPosition) {
        ToolbarPosition.BOTTOM -> ToolbarPosition.BOTTOM
        else -> ToolbarPosition.TOP
    }

    val systemTheme = isSystemInDarkTheme()
    val darkTheme by remember(resolvedAppearance, systemTheme) { derivedStateOf {
        when (resolvedAppearance) {
            Appearance.LIGHT -> false
            Appearance.DARK -> true
            Appearance.SYSTEM_SETTINGS -> systemTheme
            else -> false
        }
    } }

    MidoriBrowserTheme(
        darkTheme = darkTheme,
        privacy = isPrivate
    ) {
        Scaffold(
            modifier = Modifier.imePadding(),
            snackbarHost = {
                SnackbarHost(
                    hostState = applicationViewModel.snackbarHostState,
                    modifier = Modifier.offset(
                        y = if (resolvedToolbarPosition == ToolbarPosition.BOTTOM) (-56).dp else 0.dp
                    )
                )
            },
        ) { scaffoldPadding ->
            MidoriNavHost(
                navController = navController,
                appViewModel = applicationViewModel,
                modifier = Modifier.padding(scaffoldPadding)
            )
            ZapFeature(state = applicationViewModel.zapState)
        }
    }
}
