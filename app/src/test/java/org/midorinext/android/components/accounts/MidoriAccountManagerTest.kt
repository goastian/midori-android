/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.components.accounts

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import mozilla.components.concept.sync.OAuthAccount
import mozilla.components.concept.sync.Profile
import mozilla.components.service.fxa.manager.FxaAccountManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.midorinext.android.ext.components

class MidoriAccountManagerTest {

    private lateinit var midoriFxaManager: MidoriAccountManager
    private lateinit var accountManagerComponent: FxaAccountManager
    private lateinit var context: Context
    private lateinit var account: OAuthAccount
    private lateinit var profile: Profile

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        account = mockk(relaxed = true)
        profile = mockk(relaxed = true)
        accountManagerComponent = mockk(relaxed = true)
    }

    @Test
    fun `GIVEN an account does not exist WHEN accountProfileEmail is called THEN it returns null`() {
        every { accountManagerComponent.authenticatedAccount() } returns null
        every { accountManagerComponent.accountProfile() } returns null
        every { context.components.backgroundServices.accountManager } returns accountManagerComponent
        midoriFxaManager = MidoriAccountManager(context)

        val result = midoriFxaManager.accountProfileEmail

        assertEquals(null, result)
    }

    @Test
    fun `GIVEN an account exists but needs to be re-authenticated WHEN accountProfileEmail is called THEN it returns null`() {
        every { accountManagerComponent.authenticatedAccount() } returns account
        every { accountManagerComponent.accountProfile() } returns profile
        every { accountManagerComponent.accountProfile()?.email } returns "contact@astian.org"
        every { accountManagerComponent.accountNeedsReauth() } returns true
        every { context.components.backgroundServices.accountManager } returns accountManagerComponent
        midoriFxaManager = MidoriAccountManager(context)

        val result = midoriFxaManager.accountProfileEmail

        assertEquals(null, result)
    }

    @Test
    fun `GIVEN an account exists and doesn't need to be re-authenticated WHEN accountProfileEmail is called THEN it returns the associated email address`() {
        every { accountManagerComponent.authenticatedAccount() } returns account
        every { accountManagerComponent.accountProfile() } returns profile
        val accountEmail = "contact@astian.org"
        every { accountManagerComponent.accountProfile()?.email } returns accountEmail
        every { accountManagerComponent.accountNeedsReauth() } returns false
        every { context.components.backgroundServices.accountManager } returns accountManagerComponent
        midoriFxaManager = MidoriAccountManager(context)

        val result = midoriFxaManager.accountProfileEmail

        assertEquals(accountEmail, result)
    }

    @Test
    fun `GIVEN no account exists WHEN accountState is called THEN it returns AccountState#NO_ACCOUNT`() {
        every { context.components.backgroundServices.accountManager } returns accountManagerComponent
        every { accountManagerComponent.authenticatedAccount() } returns null
        midoriFxaManager = MidoriAccountManager(context)

        assertSame(AccountState.NO_ACCOUNT, midoriFxaManager.accountState)

        // No account but signed in should not be possible. Test protecting against such a regression.
        every { accountManagerComponent.accountNeedsReauth() } returns false
        assertSame(AccountState.NO_ACCOUNT, midoriFxaManager.accountState)

        // No account and signed out still means no account. Test protecting against such a regression.
        every { accountManagerComponent.accountNeedsReauth() } returns true
        assertSame(AccountState.NO_ACCOUNT, midoriFxaManager.accountState)
    }

    @Test
    fun `GIVEN an account exists but needs to be re-authenticated WHEN accountState is called THEN it returns AccountState#NEEDS_REAUTHENTICATION`() {
        every { context.components.backgroundServices.accountManager } returns accountManagerComponent
        every { accountManagerComponent.authenticatedAccount() } returns mockk()
        every { accountManagerComponent.accountNeedsReauth() } returns true
        midoriFxaManager = MidoriAccountManager(context)

        val result = midoriFxaManager.accountState

        assertSame(AccountState.NEEDS_REAUTHENTICATION, result)
    }

    @Test
    fun `GIVEN an account exists and doesn't need to be re-authenticated WHEN accountState is called THEN it returns AccountState#AUTHENTICATED`() {
        every { context.components.backgroundServices.accountManager } returns accountManagerComponent
        every { accountManagerComponent.authenticatedAccount() } returns mockk()
        every { accountManagerComponent.accountNeedsReauth() } returns false
        midoriFxaManager = MidoriAccountManager(context)

        val result = midoriFxaManager.accountState

        assertSame(AccountState.AUTHENTICATED, result)
    }
}
