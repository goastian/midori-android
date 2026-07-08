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

    val systemTheme = isSystemInDarkTheme()
    val darkTheme by remember(appearance, systemTheme) { derivedStateOf {
        when (appearance) {
            Appearance.LIGHT -> false
            Appearance.DARK -> true
            Appearance.SYSTEM_SETTINGS -> systemTheme
            else -> false
        }
    } }

    if (appearance != null && appearance != Appearance.UNRECOGNIZED
        && toolbarPosition != ToolbarPosition.UNRECOGNIZED
    ) {
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
                            y = if (toolbarPosition == ToolbarPosition.BOTTOM) (-56).dp else 0.dp
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
    } else {
        // TODO splash screen ?
    }
}


