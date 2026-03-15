/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.onboarding

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.preference.PreferenceManager
import org.midorinext.android.BrowserActivity
import org.midorinext.android.R

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        setContent {
            WelcomeScreen(
                onContinue = {
                    markOnboardingCompleted()
                    navigateToBrowser()
                },
            )
        }
    }

    private fun markOnboardingCompleted() {
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putBoolean(getString(R.string.pref_key_onboarding_completed), true)
            .apply()
    }

    private fun navigateToBrowser() {
        val intent = Intent(this, BrowserActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    val backgroundColor = androidx.compose.ui.graphics.Color(0xFF1B1033)
    val cardColor = androidx.compose.ui.graphics.Color(0xFF2D2248)
    val buttonColor = androidx.compose.ui.graphics.Color(0xFFCEB4F0)
    val buttonTextColor = androidx.compose.ui.graphics.Color(0xFF2D1566)
    val textPrimary = androidx.compose.ui.graphics.Color.White
    val textSecondary = androidx.compose.ui.graphics.Color(0xFFCCCBCF)
    val linkColor = androidx.compose.ui.graphics.Color(0xFFBA68C8)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .systemBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(0.8f))

            // Card with logo and text
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardColor)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Logo
                Image(
                    painter = painterResource(id = R.drawable.ic_midori_welcome_logo),
                    contentDescription = "Midori Logo",
                    modifier = Modifier.size(160.dp),
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Title
                Text(
                    text = stringResource(R.string.welcome_title),
                    color = textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Subtitle
                Text(
                    text = stringResource(R.string.welcome_subtitle),
                    color = textSecondary,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Terms link
                LinkText(
                    fullText = "By continuing, you agree to the Midori Terms of Use.",
                    linkText = "Midori Terms of Use",
                    linkUrl = "https://astian.org/midori-browser/terms",
                    textColor = textSecondary,
                    linkColor = linkColor,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Privacy notice
                LinkText(
                    fullText = "Midori cares about your privacy. Learn more in our Privacy Notice.",
                    linkText = "Privacy Notice",
                    linkUrl = "https://astian.org/midori-browser/privacy",
                    textColor = textSecondary,
                    linkColor = linkColor,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Data notice
                LinkText(
                    fullText = "To help improve the browser, Midori sends diagnostic and interaction data to Astian. Manage settings.",
                    linkText = "Manage settings",
                    linkUrl = "https://astian.org/midori-browser/privacy",
                    textColor = textSecondary,
                    linkColor = linkColor,
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Continue button
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = buttonTextColor,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.welcome_continue),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Page indicator dots
            Row(
                modifier = Modifier.padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(5) { index ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == 0) {
                                    androidx.compose.ui.graphics.Color.White
                                } else {
                                    androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f)
                                },
                            ),
                    )
                }
            }
        }
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
