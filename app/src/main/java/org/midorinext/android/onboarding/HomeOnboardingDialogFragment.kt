/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.onboarding

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import com.google.accompanist.insets.ProvideWindowInsets
import mozilla.components.lib.state.ext.observeAsComposableState
import org.midorinext.android.R
import org.midorinext.android.components.accounts.MidoriFxAEntryPoint
import org.midorinext.android.components.components
import org.midorinext.android.ext.nav
import org.midorinext.android.ext.settings
import org.midorinext.android.onboarding.view.Onboarding
import org.midorinext.android.theme.MidoriTheme

/**
 * Dialog displayed once when one or multiples of these sections are shown in the home screen
 * recentTabs, recentBookmarks or historyMetadata.
 */
class HomeOnboardingDialogFragment : DialogFragment() {
    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.HomeOnboardingDialogStyle)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    override fun onDestroy() {
        super.onDestroy()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

        setContent {
            ProvideWindowInsets {
                MidoriTheme {
                    val account =
                        components.backgroundServices.syncStore.observeAsComposableState { state -> state.account }

                    Onboarding(
                        isSyncSignIn = account.value != null,
                        onDismiss = ::onDismiss,
                        onSignInButtonClick = {
                            findNavController().nav(
                                R.id.homeOnboardingDialogFragment,
                                HomeOnboardingDialogFragmentDirections.actionGlobalTurnOnSync(
                                    entrypoint = MidoriFxAEntryPoint.HomeOnboardingDialog,
                                ),
                            )
                            onDismiss()
                        },
                    )
                }
            }
        }
    }

    private fun onDismiss() {
        context?.settings()?.showHomeOnboardingDialog = false
        dismiss()
    }
}
