// Hallmark · pre-emit critique: P5 H5 E4 S5 R4 V5
// Hallmark · genre: playful · macrostructure: Narrative Workflow · theme: Midori custom
// Theme axes: light / geometric-sans / chromatic-green · enrichment: Tier A Compose texture
package org.midorinext.android.ui.onboarding

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import org.midorinext.android.MidoriApplication
import org.midorinext.android.R
import org.midorinext.android.adblock.BlockingLevel
import org.midorinext.android.ext.defaultBrowserStatus
import org.midorinext.android.preferences.app.SearchEnginePreference
import org.midorinext.android.preferences.app.ToolbarPosition
import org.midorinext.android.ui.preferences.searchEngineName
import org.midorinext.android.ui.theme.OnboardingBorder
import org.midorinext.android.ui.theme.OnboardingGreen
import org.midorinext.android.ui.theme.OnboardingGreenDeep
import org.midorinext.android.ui.theme.OnboardingInk
import org.midorinext.android.ui.theme.OnboardingLeafLine
import org.midorinext.android.ui.theme.OnboardingMintMist
import org.midorinext.android.ui.theme.OnboardingMutedInk
import org.midorinext.android.ui.theme.OnboardingPaper
import org.midorinext.android.ui.theme.OnboardingPaperRaised
import org.midorinext.android.ui.animation.reduceMotionRequested
import org.midorinext.android.widget.WidgetProvider
import org.midorinext.android.ui.widgets.SearchEngineIcon
import org.midorinext.android.usecases.SearchEngineOption
import org.midorinext.android.usecases.SearchEngines

private enum class ActionOutcome { IDLE, REQUESTED, SUCCEEDED, NOT_COMPLETED, UNAVAILABLE }

@Composable
fun MidoriOnboarding(
    onToolbarPositionSelected: (ToolbarPosition) -> Unit,
    blockingLevel: BlockingLevel,
    onBlockingLevelSelected: (BlockingLevel) -> Unit,
    searchEngine: SearchEnginePreference,
    onSearchEngineSelected: (SearchEnginePreference) -> Unit,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    val widgetManager = remember(context) { AppWidgetManager.getInstance(context) }
    val widgetProvider = remember(context) { ComponentName(context, WidgetProvider::class.java) }
    val steps = rememberSaveable {
        arrayListOf(0).apply {
            if (!context.defaultBrowserStatus().isDefault) add(1)
            if (widgetManager.isRequestPinAppWidgetSupported && widgetManager.getAppWidgetIds(widgetProvider).isEmpty()) add(2)
            addAll(listOf(3, 4, 5))
        }
    }
    var stepIndex by rememberSaveable { mutableStateOf(0) }
    val page = steps[stepIndex]
    var defaultOutcome by rememberSaveable { mutableStateOf(ActionOutcome.IDLE) }
    var widgetOutcome by rememberSaveable { mutableStateOf(ActionOutcome.IDLE) }
    var legalUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var toolbarAtBottom by rememberSaveable { mutableStateOf(false) }
    val reduceMotion = reduceMotionRequested()
    val layoutDirection = LocalLayoutDirection.current

    MidoriOnboardingSystemBars()

    val defaultBrowserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        defaultOutcome = if (context.defaultBrowserStatus().isDefault) ActionOutcome.SUCCEEDED else ActionOutcome.NOT_COMPLETED
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (context.defaultBrowserStatus().isDefault) defaultOutcome = ActionOutcome.SUCCEEDED
                if (widgetManager.getAppWidgetIds(widgetProvider).isNotEmpty()) widgetOutcome = ActionOutcome.SUCCEEDED
                else if (!widgetManager.isRequestPinAppWidgetSupported) widgetOutcome = ActionOutcome.UNAVAILABLE
                else if (widgetOutcome == ActionOutcome.REQUESTED) widgetOutcome = ActionOutcome.NOT_COMPLETED
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(page, widgetOutcome, lifecycle) {
        if (page == 2 && widgetOutcome in listOf(ActionOutcome.REQUESTED, ActionOutcome.NOT_COMPLETED)) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (widgetOutcome != ActionOutcome.SUCCEEDED) {
                    if (widgetManager.getAppWidgetIds(widgetProvider).isNotEmpty()) widgetOutcome = ActionOutcome.SUCCEEDED
                    delay(500)
                }
            }
        }
    }

    val advance = { stepIndex = (stepIndex + 1).coerceAtMost(steps.lastIndex) }
    val handlePrimaryAction = {
        when (page) {
            0 -> advance()
            1 -> {
                if (defaultOutcome == ActionOutcome.SUCCEEDED) advance()
                else {
                    defaultOutcome = try {
                        val status = context.defaultBrowserStatus()
                        if (status.isDefault) ActionOutcome.SUCCEEDED
                        else {
                            defaultBrowserLauncher.launch(status.requestIntent())
                            ActionOutcome.REQUESTED
                        }
                    } catch (_: android.content.ActivityNotFoundException) {
                        ActionOutcome.UNAVAILABLE
                    } catch (_: SecurityException) {
                        ActionOutcome.UNAVAILABLE
                    }
                }
            }
            2 -> {
                if (widgetOutcome == ActionOutcome.SUCCEEDED) advance()
                else {
                    widgetOutcome = try {
                        if (widgetManager.getAppWidgetIds(widgetProvider).isNotEmpty()) ActionOutcome.SUCCEEDED
                        else if (widgetManager.isRequestPinAppWidgetSupported && widgetManager.requestPinAppWidget(widgetProvider, null, null)) ActionOutcome.REQUESTED
                        else ActionOutcome.UNAVAILABLE
                    } catch (_: IllegalStateException) {
                        ActionOutcome.UNAVAILABLE
                    } catch (_: SecurityException) {
                        ActionOutcome.UNAVAILABLE
                    }
                }
            }
            3, 4 -> advance()
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
        OnboardingProgress(page = stepIndex, pageCount = steps.size)

        AnimatedContent(
            targetState = page,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                } else {
                    val towardsEnd = if (layoutDirection == LayoutDirection.Ltr) 1 else -1
                    (slideInHorizontally(
                        animationSpec = tween(280),
                        initialOffsetX = { fullWidth -> towardsEnd * fullWidth / 7 }
                    ) + fadeIn(animationSpec = tween(220))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = tween(190),
                            targetOffsetX = { fullWidth -> -towardsEnd * fullWidth / 9 }
                        ) + fadeOut(animationSpec = tween(150)))
                }
            },
            label = "midori onboarding page",
        ) { currentPage ->
            when (currentPage) {
                0 -> WelcomePage(onOpenLegal = { legalUrl = it })
                1 -> DefaultBrowserPage()
                2 -> WidgetPage()
                3 -> ToolbarPage(
                    toolbarAtBottom = toolbarAtBottom,
                    onToolbarPositionSelected = { selectedBottom ->
                        toolbarAtBottom = selectedBottom
                        onToolbarPositionSelected(
                            if (selectedBottom) ToolbarPosition.BOTTOM else ToolbarPosition.TOP
                        )
                    }
                )
                4 -> SearchEnginePage(searchEngine, onSearchEngineSelected)
                else -> AdBlockPage(blockingLevel, onBlockingLevelSelected)
            }
        }

        OnboardingActions(
            page = page,
            outcome = if (page == 1) defaultOutcome else if (page == 2) widgetOutcome else ActionOutcome.IDLE,
            onPrimary = handlePrimaryAction,
            onSkip = advance,
        )
    }
    legalUrl?.let { url -> OnboardingLegalPage(url, onClose = { legalUrl = null }) }
}

@Composable
private fun SearchEnginePage(
    selectedEngine: SearchEnginePreference,
    onSelected: (SearchEnginePreference) -> Unit,
) {
    PageColumn(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        PageTitle(
            title = stringResource(R.string.onboarding_search_title),
            description = stringResource(R.string.onboarding_search_description),
        )
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchEngines.builtInEngines.forEach { engine ->
                val selected = selectedEngine == engine
                Surface(
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSelected(engine) },
                    ),
                    shape = RoundedCornerShape(18.dp),
                    color = OnboardingPaperRaised,
                    border = androidx.compose.foundation.BorderStroke(
                        if (selected) 2.dp else 1.dp,
                        if (selected) OnboardingGreen else OnboardingBorder,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = OnboardingGreenDeep,
                                unselectedColor = OnboardingMutedInk,
                            ),
                        )
                        SearchEngineIcon(
                            option = SearchEngineOption(SearchEngines.id(engine), engine.name, engine),
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            text = searchEngineName(engine),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            color = OnboardingInk,
                        )
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.onboarding_search_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = OnboardingMutedInk,
        )
    }
}

@Composable
private fun AdBlockPage(level: BlockingLevel, onSelected: (BlockingLevel) -> Unit) {
    PageColumn(verticalArrangement = Arrangement.SpaceBetween) {
        PageTitle(
            title = stringResource(R.string.onboarding_adblock_title),
            description = stringResource(R.string.onboarding_adblock_description),
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BlockingLevel.entries.forEach { option ->
                val label = when (option) {
                    BlockingLevel.OFF -> R.string.adblock_level_off
                    BlockingLevel.TRACKERS -> R.string.adblock_level_trackers
                    BlockingLevel.TRACKERS_AND_ADS -> R.string.adblock_level_trackers_ads
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = level == option,
                        role = Role.RadioButton,
                        onClick = { onSelected(option) },
                    ),
                    shape = RoundedCornerShape(18.dp),
                    color = OnboardingPaperRaised,
                    border = androidx.compose.foundation.BorderStroke(
                        if (level == option) 2.dp else 1.dp,
                        if (level == option) OnboardingGreen else OnboardingBorder,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = level == option,
                            onClick = { onSelected(option) },
                        )
                        Text(stringResource(label), style = MaterialTheme.typography.titleMedium, color = OnboardingInk)
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.onboarding_adblock_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = OnboardingMutedInk,
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
private fun OnboardingProgress(page: Int, pageCount: Int) {
    val progressDescription = stringResource(R.string.onboarding_progress, page + 1, pageCount)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = progressDescription },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(pageCount) { index ->
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
private fun WelcomePage(onOpenLegal: (String) -> Unit) {
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

        Column {
            Text(stringResource(R.string.onboarding_welcome_legal), style = MaterialTheme.typography.bodySmall, color = OnboardingMutedInk)
            TextButton(onClick = { onOpenLegal("https://astian.org/terms-conditions/") }, modifier = Modifier.testTag("onboarding_terms")) {
                Text(stringResource(R.string.onboarding_terms_link))
            }
            val privacyUrl = stringResource(R.string.privacy_policy_url)
            TextButton(onClick = { onOpenLegal(privacyUrl) }, modifier = Modifier.testTag("onboarding_privacy")) {
                Text(stringResource(R.string.privacy_policy_label))
            }
        }
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
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelected)
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
    outcome: ActionOutcome,
    onPrimary: () -> Unit,
    onSkip: () -> Unit,
) {
    val primaryAction = if (outcome == ActionOutcome.SUCCEEDED) stringResource(R.string.welcome_continue) else when (page) {
        0 -> stringResource(R.string.onboarding_welcome_continue)
        1 -> stringResource(R.string.onboarding_default_set)
        2 -> stringResource(R.string.onboarding_widget_add)
        3 -> stringResource(R.string.onboarding_welcome_continue)
        4 -> stringResource(R.string.welcome_continue)
        else -> stringResource(R.string.onboarding_finish)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (outcome != ActionOutcome.IDLE) {
            Text(stringResource(when (outcome) {
                ActionOutcome.SUCCEEDED -> if (page == 1) R.string.onboarding_default_done else R.string.onboarding_widget_done
                ActionOutcome.NOT_COMPLETED -> R.string.onboarding_action_not_completed
                ActionOutcome.UNAVAILABLE -> R.string.onboarding_action_unavailable
                else -> R.string.onboarding_action_requested
            }), modifier = Modifier.testTag("onboarding_action_result"), color = OnboardingMutedInk, style = MaterialTheme.typography.bodyMedium)
        }
        if (page == 1 || page == 2) {
            Button(
                onClick = onSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(Modifier.heightIn(min = 48.dp)).testTag("onboarding_skip"),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnboardingGreenDeep),
                border = androidx.compose.foundation.BorderStroke(1.dp, OnboardingBorder),
            ) {
                Text(
                    text = if (page == 1) stringResource(R.string.onboarding_default_skip) else stringResource(R.string.onboarding_widget_skip),
                )
            }
        }
        Button(
            onClick = onPrimary,
            enabled = outcome != ActionOutcome.REQUESTED && outcome != ActionOutcome.UNAVAILABLE,
            modifier = Modifier
                .fillMaxWidth()
                .then(Modifier.heightIn(min = 56.dp)).testTag("onboarding_primary"),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = OnboardingGreen,
                contentColor = OnboardingInk,
            ),
        ) {
            Text(
                text = primaryAction,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun OnboardingLegalPage(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val engine = remember(context) { (context.applicationContext as MidoriApplication).engine.get() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("onboarding_legal")) {
            Column {
                TextButton(onClick = onClose) { Text(stringResource(R.string.nav_back)) }
                Text(url, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                val session = remember(engine, url) { engine.createSession(private = true) }
                DisposableEffect(session) { onDispose { session.close() } }
                org.midorinext.android.ui.browser.mozaccompose.EngineView(engine, Modifier.weight(1f)) { view ->
                    DisposableEffect(view, session) {
                        view.render(session)
                        session.loadUrl(url)
                        onDispose { view.release() }
                    }
                }
            }
        }
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
