/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.onboarding

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import kotlinx.coroutines.launch
import org.midorinext.android.BrowserActivity
import org.midorinext.android.R
import org.midorinext.android.ext.getPreferenceKey
import org.midorinext.android.settings.CustomizeSettingsFragment
import org.midorinext.android.widget.SearchWidgetProvider

private const val ONBOARDING_PAGE_COUNT = 5

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        setContent {
            OnboardingPager(
                onFinish = {
                    markOnboardingCompleted()
                    navigateToBrowser()
                },
                onSetDefault = { requestDefaultBrowser() },
                onAddWidget = { requestSearchWidget() },
                onToolbarPositionSelected = { position -> saveToolbarPosition(position) },
                onThemeSelected = { theme -> saveTheme(theme) },
            )
        }
    }

    private fun markOnboardingCompleted() {
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putBoolean(getString(R.string.pref_key_onboarding_completed), true)
            .apply()
    }

    private fun saveToolbarPosition(position: String) {
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putString(getPreferenceKey(R.string.pref_key_toolbar_position), position)
            .apply()
    }

    private fun saveTheme(theme: String) {
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putString(getString(R.string.pref_key_theme), theme)
            .apply()
        CustomizeSettingsFragment.applyTheme(theme)
    }

    private fun requestDefaultBrowser() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivity(intent)
        }
    }

    private fun requestSearchWidget() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val widgetProvider = ComponentName(this, SearchWidgetProvider::class.java)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                appWidgetManager.requestPinAppWidget(widgetProvider, null, null)
            }
        }
    }

    private fun navigateToBrowser() {
        val intent = Intent(this, BrowserActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

// --- Midori Green Theme Colors ---
private val BackgroundColor = androidx.compose.ui.graphics.Color(0xFF024B30)
private val CardColor = androidx.compose.ui.graphics.Color(0xFF036641)
private val ButtonColor = androidx.compose.ui.graphics.Color(0xFF06E290)
private val ButtonTextColor = androidx.compose.ui.graphics.Color(0xFF024B30)
private val TextPrimary = androidx.compose.ui.graphics.Color.White
private val TextSecondary = androidx.compose.ui.graphics.Color(0xCCFFFFFF)
private val LinkColor = androidx.compose.ui.graphics.Color(0xFF2EFAAE)
private val AccentGreen = androidx.compose.ui.graphics.Color(0xFF06E290)
private val SelectedBorder = androidx.compose.ui.graphics.Color(0xFF06E290)
private val UnselectedBorder = androidx.compose.ui.graphics.Color(0xFF04A469)

@Composable
fun OnboardingPager(
    onFinish: () -> Unit,
    onSetDefault: () -> Unit,
    onAddWidget: () -> Unit,
    onToolbarPositionSelected: (String) -> Unit,
    onThemeSelected: (String) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGE_COUNT })
    val scope = rememberCoroutineScope()

    fun nextPage() {
        scope.launch {
            if (pagerState.currentPage < ONBOARDING_PAGE_COUNT - 1) {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            } else {
                onFinish()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .systemBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false,
            ) { page ->
                when (page) {
                    0 -> WelcomePage(onContinue = { nextPage() })
                    1 -> ThemePage(
                        onContinue = { nextPage() },
                        onThemeSelected = onThemeSelected,
                    )
                    2 -> ToolbarPositionPage(
                        onContinue = { nextPage() },
                        onPositionSelected = onToolbarPositionSelected,
                    )
                    3 -> SearchWidgetPage(
                        onAddWidget = {
                            onAddWidget()
                            nextPage()
                        },
                        onSkip = { nextPage() },
                    )
                    4 -> DefaultBrowserPage(
                        onSetDefault = {
                            onSetDefault()
                            nextPage()
                        },
                        onSkip = { nextPage() },
                    )
                }
            }

            // Page indicator dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(ONBOARDING_PAGE_COUNT) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) {
                                    TextPrimary
                                } else {
                                    TextPrimary.copy(alpha = 0.4f)
                                },
                            ),
                    )
                }
            }
        }
    }
}

// ==================== Page 1: Welcome ====================
@Composable
fun WelcomePage(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(0.6f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardColor)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = "Midori Logo",
                modifier = Modifier.size(140.dp),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.welcome_title),
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.welcome_subtitle),
                color = TextSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
            )

            Spacer(modifier = Modifier.height(20.dp))

            LinkText(
                fullText = "By continuing, you agree to the Midori Terms of Use.",
                linkText = "Midori Terms of Use",
                linkUrl = "https://astian.org/midori-browser/terms",
                textColor = TextSecondary,
                linkColor = LinkColor,
            )

            Spacer(modifier = Modifier.height(10.dp))

            LinkText(
                fullText = "Midori cares about your privacy. Learn more in our Privacy Notice.",
                linkText = "Privacy Notice",
                linkUrl = "https://astian.org/midori-browser/privacy",
                textColor = TextSecondary,
                linkColor = LinkColor,
            )

            Spacer(modifier = Modifier.height(10.dp))

            LinkText(
                fullText = "To help improve the browser, Midori sends diagnostic data to Astian. Manage settings.",
                linkText = "Manage settings",
                linkUrl = "https://astian.org/midori-browser/privacy",
                textColor = TextSecondary,
                linkColor = LinkColor,
            )

            Spacer(modifier = Modifier.height(28.dp))

            OnboardingButton(
                text = stringResource(R.string.welcome_continue),
                onClick = onContinue,
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

// ==================== Page 2: Theme ====================
@Composable
fun ThemePage(
    onContinue: () -> Unit,
    onThemeSelected: (String) -> Unit,
) {
    val selectedTheme = remember { mutableStateOf("system") }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        onThemeSelected("system")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(0.6f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardColor)
                .padding(vertical = 32.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.onboarding_theme_title),
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.onboarding_theme_description),
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Theme options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ThemeOption(
                    label = stringResource(R.string.preferences_theme_system),
                    emoji = "\uD83D\uDCF1",
                    isSelected = selectedTheme.value == "system",
                    onClick = {
                        selectedTheme.value = "system"
                        onThemeSelected("system")
                    },
                )
                ThemeOption(
                    label = stringResource(R.string.preferences_theme_light),
                    emoji = "☀\uFE0F",
                    isSelected = selectedTheme.value == "light",
                    onClick = {
                        selectedTheme.value = "light"
                        onThemeSelected("light")
                    },
                )
                ThemeOption(
                    label = stringResource(R.string.preferences_theme_dark),
                    emoji = "\uD83C\uDF19",
                    isSelected = selectedTheme.value == "dark",
                    onClick = {
                        selectedTheme.value = "dark"
                        onThemeSelected("dark")
                    },
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            OnboardingButton(
                text = stringResource(R.string.welcome_continue),
                onClick = onContinue,
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun ThemeOption(
    label: String,
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) SelectedBorder else UnselectedBorder

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, borderColor, RoundedCornerShape(16.dp))
                .background(
                    if (isSelected) {
                        SelectedBorder.copy(alpha = 0.15f)
                    } else {
                        androidx.compose.ui.graphics.Color.Transparent
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = emoji,
                fontSize = 32.sp,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = label,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Radio indicator
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(2.dp, if (isSelected) SelectedBorder else UnselectedBorder, CircleShape)
                .background(
                    if (isSelected) SelectedBorder else androidx.compose.ui.graphics.Color.Transparent,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(ButtonTextColor),
                )
            }
        }
    }
}

// ==================== Page 3: Toolbar Position ====================
@Composable
fun ToolbarPositionPage(
    onContinue: () -> Unit,
    onPositionSelected: (String) -> Unit,
) {
    val selectedPosition = remember { mutableStateOf("top") }

    // Save default position immediately so it persists even if user doesn't tap
    androidx.compose.runtime.LaunchedEffect(Unit) {
        onPositionSelected("top")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(0.6f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardColor)
                .padding(vertical = 32.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.onboarding_toolbar_title),
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Phone mockups for Top / Bottom selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // Top option
                ToolbarOption(
                    label = stringResource(R.string.onboarding_toolbar_top),
                    isSelected = selectedPosition.value == "top",
                    isTop = true,
                    onClick = {
                        selectedPosition.value = "top"
                        onPositionSelected("top")
                    },
                )

                // Bottom option
                ToolbarOption(
                    label = stringResource(R.string.onboarding_toolbar_bottom),
                    isSelected = selectedPosition.value == "bottom",
                    isTop = false,
                    onClick = {
                        selectedPosition.value = "bottom"
                        onPositionSelected("bottom")
                    },
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            OnboardingButton(
                text = stringResource(R.string.welcome_continue),
                onClick = onContinue,
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun ToolbarOption(
    label: String,
    isSelected: Boolean,
    isTop: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) SelectedBorder else UnselectedBorder

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        // Phone mockup
        Box(
            modifier = Modifier
                .width(110.dp)
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                .background(androidx.compose.ui.graphics.Color(0xFF024B30)),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (isTop) {
                    // Address bar at top
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(androidx.compose.ui.graphics.Color(0xFF04A469)),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                    // Address bar at bottom
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(androidx.compose.ui.graphics.Color(0xFF04A469)),
                    )
                }
            }

            // Midori logo centered
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.Center),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = label,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Radio indicator
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(2.dp, if (isSelected) SelectedBorder else UnselectedBorder, CircleShape)
                .background(
                    if (isSelected) SelectedBorder else androidx.compose.ui.graphics.Color.Transparent,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(ButtonTextColor),
                )
            }
        }
    }
}

// ==================== Page 3: Search Widget ====================
@Composable
fun SearchWidgetPage(
    onAddWidget: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(0.6f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardColor)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.onboarding_widget_title),
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Phone mockup with search widget preview
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(androidx.compose.ui.graphics.Color(0xFF024B30)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Search bar mockup
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(androidx.compose.ui.graphics.Color(0xFF3A3A5C))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(id = R.mipmap.ic_launcher),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Image(
                            painter = painterResource(id = R.drawable.ic_search),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            colorFilter = ColorFilter.tint(TextSecondary),
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // App icons mockup
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        androidx.compose.ui.graphics.Color(0xFF04A469),
                                    ),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.onboarding_widget_description),
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )

            Spacer(modifier = Modifier.height(28.dp))

            OnboardingButton(
                text = stringResource(R.string.onboarding_widget_add),
                onClick = onAddWidget,
            )

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onSkip) {
                Text(
                    text = stringResource(R.string.onboarding_widget_skip),
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textDecoration = TextDecoration.Underline,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

// ==================== Page 4: Default Browser ====================
@Composable
fun DefaultBrowserPage(
    onSetDefault: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(0.4f))

        // Illustration area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Phone with Midori logo
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(androidx.compose.ui.graphics.Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.2f))

        // Card with text and buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardColor)
                .padding(32.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = stringResource(R.string.onboarding_default_title),
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.onboarding_default_description),
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )

            Spacer(modifier = Modifier.height(28.dp))

            OnboardingButton(
                text = stringResource(R.string.onboarding_default_set),
                onClick = onSetDefault,
            )

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = onSkip,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_default_skip),
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textDecoration = TextDecoration.Underline,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

// ==================== Shared Components ====================
@Composable
fun OnboardingButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ButtonColor,
            contentColor = ButtonTextColor,
        ),
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun LinkText(
    fullText: String,
    linkText: String,
    linkUrl: String,
    textColor: androidx.compose.ui.graphics.Color,
    linkColor: androidx.compose.ui.graphics.Color,
) {
    val startIndex = fullText.indexOf(linkText)
    if (startIndex == -1) {
        Text(text = fullText, color = textColor, fontSize = 13.sp, lineHeight = 18.sp)
        return
    }

    val uriHandler = LocalUriHandler.current
    val annotatedString = buildAnnotatedString {
        withStyle(SpanStyle(color = textColor, fontSize = 13.sp)) {
            append(fullText.substring(0, startIndex))
        }
        val linkStyles = TextLinkStyles(
            style = SpanStyle(
                color = linkColor,
                fontSize = 13.sp,
                textDecoration = TextDecoration.Underline,
            ),
        )
        pushLink(LinkAnnotation.Clickable(tag = "link", styles = linkStyles) {
            uriHandler.openUri(linkUrl)
        })
        append(linkText)
        pop()
        withStyle(SpanStyle(color = textColor, fontSize = 13.sp)) {
            append(fullText.substring(startIndex + linkText.length))
        }
    }

    BasicText(
        text = annotatedString,
        style = TextStyle(lineHeight = 18.sp),
    )
}
