/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.home.sessioncontrol.viewholders.onboarding

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.midorinext.android.R
import org.midorinext.android.databinding.OnboardingPrivacyNoticeBinding
import org.midorinext.android.home.sessioncontrol.OnboardingInteractor

class OnboardingPrivacyNoticeViewHolder(
    view: View,
    private val interactor: OnboardingInteractor
) : RecyclerView.ViewHolder(view) {

    init {
        val binding = OnboardingPrivacyNoticeBinding.bind(view)

        binding.readButton.setOnClickListener {
            interactor.onReadPrivacyNoticeClicked()
        }
    }

    companion object {
        const val LAYOUT_ID = R.layout.onboarding_privacy_notice
    }
}
