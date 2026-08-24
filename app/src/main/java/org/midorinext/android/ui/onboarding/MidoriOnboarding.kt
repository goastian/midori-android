// Hallmark · pre-emit critique: P5 H5 E4 S5 R4 V5
// Hallmark · genre: playful · macrostructure: Narrative Workflow · theme: Midori custom
// Theme axes: light / geometric-sans / chromatic-green · enrichment: Tier A Compose texture
package org.midorinext.android.ui.onboarding

import android.app.Activity
import android.app.role.RoleManager
import android.appwidget.AppWidgetManager
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import org.midorinext.android.R
import org.midorinext.android.ext.openDefaultAppsSystemSettings
import org.midorinext.android.preferences.app.ToolbarPosition
import org.midorinext.android.ui.theme.OnboardingBorder
import org.midorinext.android.ui.theme.OnboardingGreen
import org.midorinext.android.ui.theme.OnboardingGreenDeep
import org.midorinext.android.ui.theme.OnboardingInk
import org.midorinext.android.ui.theme.OnboardingLeafLine
import org.midorinext.android.ui.theme.OnboardingMintMist
import org.midorinext.android.ui.theme.OnboardingMutedInk
import org.midorinext.android.ui.theme.OnboardingPaper
import org.midorinext.android.ui.theme.OnboardingPaperRaised
import org.midorinext.android.widget.WidgetProvider

private const val OnboardingPageCount = 4

@Composable
fun MidoriOnboarding(
    onToolbarPositionSelected: (ToolbarPosition) -> Unit,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf(0) }
    var toolbarAtBottom by rememberSaveable { mutableStateOf(false) }
    val reduceMotion = !ValueAnimator.areAnimatorsEnabled()

    MidoriOnboardingSystemBars()

    val defaultBrowserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { }

    val advance = { page = (page + 1).coerceAtMost(OnboardingPageCount - 1) }
    val handlePrimaryAction = {
        when (page) {
            0 -> advance()
            1 -> {
                requestDefaultBrowser(context, defaultBrowserLauncher)
                advance()
            }
            2 -> {
                requestPinnedWidget(context)
                advance()
            }
            else -> onComplete()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingPaper)
            .midoriTexture()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        OnboardingProgress(page = page)

        AnimatedContent(
            targetState = page,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                } else {
                    (slideInHorizontally(
                        animationSpec = tween(280),
                        initialOffsetX = { fullWidth -> fullWidth / 7 }
                    ) + fadeIn(animationSpec = tween(220))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = tween(190),
                            targetOffsetX = { fullWidth -> -fullWidth / 9 }
                        ) + fadeOut(animationSpec = tween(150)))
                }
            },
            label = "midori onboarding page",
        ) { currentPage ->
            when (currentPage) {
                0 -> WelcomePage()
                1 -> DefaultBrowserPage()
                2 -> WidgetPage()
                else -> ToolbarPage(
                    toolbarAtBottom = toolbarAtBottom,
                    onToolbarPositionSelected = { selectedBottom ->
                        toolbarAtBottom = selectedBottom
                        onToolbarPositionSelected(
                            if (selectedBottom) ToolbarPosition.BOTTOM else ToolbarPosition.TOP
                        )
                    }
                )
            }
        }

        OnboardingActions(
            page = page,
            onPrimary = handlePrimaryAction,
            onSkip = advance,
        )
    }
}

@Composable
private fun MidoriOnboardingSystemBars() {
    val view = LocalView.current
    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        activity.window.statusBarColor = OnboardingPaper.toArgb()
        activity.window.navigationBarColor = OnboardingPaper.toArgb()
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }
}

@Composable
private fun OnboardingProgress(page: Int) {
    val progressDescription = stringResource(R.string.onboarding_progress, page + 1, OnboardingPageCount)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = progressDescription },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(OnboardingPageCount) { index ->
            val active = index == page
            Surface(
                modifier = Modifier
                    .weight(if (active) 1.65f else 1f)
                    .height(6.dp),
                shape = CircleShape,
                color = if (active) OnboardingGreen else OnboardingBorder,
                content = {},
            )
        }
    }
}

@Composable
private fun WelcomePage() {
    PageColumn(verticalArrangement = Arrangement.SpaceEvenly) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(132.dp)
                .shadow(18.dp, CircleShape, ambientColor = OnboardingMintMist, spotColor = OnboardingMintMist),
            shape = CircleShape,
            color = OnboardingPaperRaised,
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.padding(22.dp),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = OnboardingInk,
            )
            Text(
                text = stringResource(R.string.welcome_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = OnboardingMutedInk,
            )
        }

        Text(
            text = stringResource(R.string.onboarding_welcome_legal),
            style = MaterialTheme.typography.bodySmall,
            color = OnboardingMutedInk,
        )
    }
}

@Composable
private fun DefaultBrowserPage() {
    PageColumn(verticalArrangement = Arrangement.SpaceBetween) {
        PageTitle(
            title = stringResource(R.string.onboarding_default_title),
            description = stringResource(R.string.onboarding_default_description),
        )
        PrivacyLinkPreview()
        Text(
            text = stringResource(R.string.onboarding_default_hint),
            style = MaterialTheme.typography.titleMedium,
            color = OnboardingMutedInk,
        )
    }
}

@Composable
private fun WidgetPage() {
    PageColumn(verticalArrangement = Arrangement.SpaceBetween) {
        PageTitle(
            title = stringResource(R.string.onboarding_widget_title),
            description = stringResource(R.string.onboarding_widget_description),
        )
        WidgetPreview()
        Text(
            text = stringResource(R.string.onboarding_widget_hint),
            style = MaterialTheme.typography.titleMedium,
            color = OnboardingMutedInk,
        )
    }
}

@Composable
private fun ToolbarPage(
    toolbarAtBottom: Boolean,
    onToolbarPositionSelected: (Boolean) -> Unit,
) {
    PageColumn(verticalArrangement = Arrangement.SpaceBetween) {
        PageTitle(
            title = stringResource(R.string.onboarding_toolbar_title),
            description = stringResource(R.string.onboarding_toolbar_description),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ToolbarPlacementPreview(
                label = stringResource(R.string.onboarding_toolbar_top),
                toolbarAtBottom = false,
                selected = !toolbarAtBottom,
                onSelected = { onToolbarPositionSelected(false) },
                modifier = Modifier.weight(1f),
            )
            ToolbarPlacementPreview(
                label = stringResource(R.string.onboarding_toolbar_bottom),
                toolbarAtBottom = true,
                selected = toolbarAtBottom,
                onSelected = { onToolbarPositionSelected(true) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PageColumn(
    verticalArrangement: Arrangement.Vertical,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 34.dp, bottom = 24.dp),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

@Composable
private fun PageTitle(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            color = OnboardingInk,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = OnboardingMutedInk,
        )
    }
}

@Composable
private fun PrivacyLinkPreview() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.18f),
        shape = RoundedCornerShape(28.dp),
        color = OnboardingPaperRaised,
        border = androidx.compose.foundation.BorderStroke(1.dp, OnboardingBorder),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "MIDORI",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = OnboardingGreenDeep,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { line ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(if (line == 2) .66f else 1f)
                            .height(12.dp),
                        shape = CircleShape,
                        color = if (line == 0) OnboardingMintMist else OnboardingBorder,
                        content = {},
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = OnboardingGreen,
            ) {
                Text(
                    text = stringResource(R.string.onboarding_private_by_default),
                    modifier = Modifier.padding(vertical = 15.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = OnboardingInk,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun WidgetPreview() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = OnboardingInk,
        border = androidx.compose.foundation.BorderStroke(1.dp, OnboardingGreenDeep),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = OnboardingGreen,
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.padding(7.dp),
                )
            }
            Text(
                text = stringResource(R.string.search_widget_text),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = OnboardingPaper,
            )
            Text(
                text = "⌕",
                style = MaterialTheme.typography.headlineSmall,
                color = OnboardingPaper,
            )
        }
    }
}

@Composable
private fun ToolbarPlacementPreview(
    label: String,
    toolbarAtBottom: Boolean,
    selected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedText = stringResource(R.string.onboarding_toolbar_selected)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) OnboardingGreen else OnboardingBorder,
                shape = RoundedCornerShape(24.dp),
            )
            .clickable(role = Role.RadioButton, onClick = onSelected)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(.62f)
                .clip(RoundedCornerShape(18.dp))
                .background(OnboardingInk)
                .padding(9.dp),
        ) {
            Surface(
                modifier = Modifier
                    .align(if (toolbarAtBottom) Alignment.BottomCenter else Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(28.dp),
                shape = RoundedCornerShape(12.dp),
                color = OnboardingPaper,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Surface(Modifier.size(7.dp), CircleShape, OnboardingGreen, content = {})
                    Surface(Modifier.weight(1f).height(7.dp), CircleShape, OnboardingBorder, content = {})
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = OnboardingInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = if (selected) OnboardingGreen else Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(2.dp, if (selected) OnboardingGreen else OnboardingBorder),
        ) {
            if (selected) {
                Text(
                    text = "✓",
                    modifier = Modifier.padding(top = 2.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = OnboardingInk,
                )
            }
        }
        if (selected) {
            Text(
                text = selectedText,
                style = MaterialTheme.typography.labelSmall,
                color = OnboardingGreenDeep,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun OnboardingActions(
    page: Int,
    onPrimary: () -> Unit,
    onSkip: () -> Unit,
) {
    val primaryAction = when (page) {
        0 -> stringResource(R.string.onboarding_welcome_continue)
        1 -> stringResource(R.string.onboarding_default_set)
        2 -> stringResource(R.string.onboarding_widget_add)
        else -> stringResource(R.string.onboarding_finish)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (page == 1 || page == 2) {
            Button(
                onClick = onSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnboardingGreenDeep),
                border = androidx.compose.foundation.BorderStroke(1.dp, OnboardingBorder),
            ) {
                Text(
                    text = if (page == 1) stringResource(R.string.onboarding_default_skip) else stringResource(R.string.onboarding_widget_skip),
                    maxLines = 1,
                )
            }
        }
        Button(
            onClick = onPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .semantics { contentDescription = primaryAction },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = OnboardingGreen,
                contentColor = OnboardingInk,
            ),
        ) {
            Text(
                text = primaryAction,
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun requestDefaultBrowser(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>,
) {
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
            ) {
                launcher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
            }
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> context.openDefaultAppsSystemSettings()
    }
}

private fun requestPinnedWidget(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val appWidgetManager = AppWidgetManager.getInstance(context)
    if (appWidgetManager.isRequestPinAppWidgetSupported) {
        appWidgetManager.requestPinAppWidget(ComponentName(context, WidgetProvider::class.java), null, null)
    }
}

private fun Modifier.midoriTexture(): Modifier = drawBehind {
    drawMidoriTexture()
}

private fun DrawScope.drawMidoriTexture() {
    val width = size.width
    val height = size.height
    drawCircle(
        color = OnboardingMintMist,
        radius = width * .64f,
        center = Offset(width * 1.05f, height * .09f),
    )
    drawCircle(
        color = OnboardingLeafLine,
        radius = width * .48f,
        center = Offset(width * -.14f, height * .86f),
    )
    repeat(4) { index ->
        drawCircle(
            color = OnboardingLeafLine,
            radius = width * (.13f + index * .045f),
            center = Offset(width * 1.08f, height * .11f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
        )
    }
}
