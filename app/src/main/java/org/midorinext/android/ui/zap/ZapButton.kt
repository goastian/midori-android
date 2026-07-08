package org.midorinext.android.ui.zap

import android.content.SharedPreferences
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import org.midorinext.android.R
import org.midorinext.android.ui.MidoriApplicationViewModel
import org.midorinext.android.ui.browser.ToolbarAction
import org.midorinext.android.ui.theme.LocalMidoriTheme
import kotlinx.coroutines.delay

@Composable
fun ZapButton(
    appViewModel: MidoriApplicationViewModel,
    fromScreen: String = "Toolbar",
    afterZap: ((success: Boolean) -> Unit)? = null
) {
    // TODO move from sharedprefs to internal datastore
    val context = LocalContext.current

    val prefkey = stringResource(id = R.string.pref_key_zap_highlight)
    val prefs: SharedPreferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var shouldHighlightZapPref by remember { mutableStateOf(prefs.getBoolean(prefkey, true)) }

    val hasHistory by appViewModel.hasHistory.collectAsState()

    val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        key?.takeIf { it == prefkey }?.let {
            shouldHighlightZapPref = p.getBoolean(it, true)
        }
    }
    DisposableEffect(prefs) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val zapDoneString = stringResource(id = R.string.cleardata_done)
    val zap = { appViewModel.zap(from = fromScreen) { success ->
        if (success) appViewModel.showSnackbar(zapDoneString)
        afterZap?.invoke(success)
    } }
    if (shouldHighlightZapPref && hasHistory) {
        AnimatedZapButton(zap = {
            with(prefs.edit()) {
                putBoolean(prefkey, false)
                apply()
            }
            zap()
        })
    } else {
        StaticZapButton(zap = zap)
    }
}


@Composable
fun StaticZapButton(
    zap: () -> Unit
) {
    ToolbarAction(onClick = { zap() }) {
        Image(
            painter = painterResource(id = LocalMidoriTheme.current.icons.zap),
            contentDescription = "zap",
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun AnimatedZapButton(
    zap: () -> Unit
) {
    // Hacky way to employ AnimatedDrawable to repeat without reverse
    //  maybe there is something better than hiding it

    var atEnd by remember { mutableStateOf(false) }
    var staticOverlayVisible by remember { mutableStateOf(true) }

    val midoriTheme = LocalMidoriTheme.current
    val animatedImage = AnimatedImageVector.animatedVectorResource(id = midoriTheme.icons.zapAnimated)
    val animatedPainter = rememberAnimatedVectorPainter(animatedImage, atEnd)
    val staticPainter = painterResource(id = midoriTheme.icons.zap)

    var runCount = remember { 0 }

    suspend fun runAnimation() {
        delay(1000)
        while (true) {
            if (atEnd) {
                staticOverlayVisible = true
                delay(50)
                atEnd = false
                runCount = (runCount + 1) % 4
                if (runCount == 0) {
                    delay(4000)
                } else {
                    delay(animatedImage.totalDuration.toLong() + 50)
                }
            } else {
                staticOverlayVisible = false
                delay(50)
                atEnd = true
                delay(animatedImage.totalDuration.toLong() + 50)
            }
        }
    }

    LaunchedEffect(animatedImage) {
        runAnimation()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(44.dp)
            .fillMaxHeight()
            .clickable { zap() }
    ) {
        if (staticOverlayVisible) {
            Image(
                painter = staticPainter,
                contentDescription = "zap",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        } else {
            Image(
                painter = animatedPainter,
                contentDescription = "zap",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
