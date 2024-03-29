/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.detektrules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider
import org.midorinext.android.detektrules.perf.MozillaBannedPropertyAccess
import org.midorinext.android.detektrules.perf.MozillaRunBlockingCheck
import org.midorinext.android.detektrules.perf.MozillaStrictModeSuppression
import org.midorinext.android.detektrules.perf.MozillaUseLazyMonitored

class CustomRulesetProvider : RuleSetProvider {
    override val ruleSetId: String = "mozilla-detekt-rules"

    override fun instance(config: Config): RuleSet = RuleSet(
        ruleSetId,
        listOf(
            MozillaBannedPropertyAccess(config),
            MozillaStrictModeSuppression(config),
            MozillaCorrectUnitTestRunner(config),
            MozillaRunBlockingCheck(config),
            MozillaUseLazyMonitored(config),
        )
    )
}
