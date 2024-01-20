/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.home.sessioncontrol.viewholders.onboarding

import android.view.View
import androidx.navigation.Navigation
import androidx.recyclerview.widget.RecyclerView
import org.midorinext.android.R
import org.midorinext.android.components.accounts.MidoriFxAEntryPoint
import org.midorinext.android.databinding.OnboardingManualSigninBinding
import org.midorinext.android.home.HomeFragmentDirections

class OnboardingManualSignInViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private var binding: OnboardingManualSigninBinding = OnboardingManualSigninBinding.bind(view)

    init {
        binding.fxaSignInButton.setOnClickListener {
            val directions = HomeFragmentDirections.actionGlobalTurnOnSync(
                entrypoint = MidoriFxAEntryPoint.OnboardingManualSignIn,
            )
            Navigation.findNavController(view).navigate(directions)
        }
    }

    companion object {
        const val LAYOUT_ID = R.layout.onboarding_manual_signin
    }
}
