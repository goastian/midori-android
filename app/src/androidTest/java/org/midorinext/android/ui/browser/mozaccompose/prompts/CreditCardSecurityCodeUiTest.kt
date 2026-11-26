package org.midorinext.android.ui.browser.mozaccompose.prompts

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import mozilla.components.concept.storage.CreditCardEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.midorinext.android.R
import org.midorinext.android.ui.browser.mozaccompose.prompts.dialog.CreditCardPicker
import org.midorinext.android.ui.theme.MidoriBrowserTheme
import androidx.compose.ui.unit.dp

class CreditCardSecurityCodeUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val card = CreditCardEntry("test", "Test card", "4111111111111111", "12", "2030", "visa")

    @Test
    fun selectionRequiresAValidCodeAndPreservesLeadingZeros() {
        val selected = mutableListOf<String>()
        compose.setContent {
            MidoriBrowserTheme { CreditCardPicker(listOf(card), true, 0.dp, { _, code -> selected += code }, {}) }
        }
        compose.onNodeWithTag("autofill_option_0").performClick()
        compose.onNodeWithTag("card_security_code_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("card_security_code").performTextInput("001")
        compose.onNodeWithTag("card_security_code_confirm").performClick()
        compose.runOnIdle { assertEquals(listOf("001"), selected) }
    }

    @Test
    fun securityCodeIsNotRestoredWithSavedInstanceState() {
        val selected = mutableListOf<String>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MidoriBrowserTheme { CreditCardPicker(listOf(card), true, 0.dp, { _, code -> selected += code }, {}) }
        }
        compose.onNodeWithTag("autofill_option_0").performClick()
        compose.onNodeWithTag("card_security_code").performTextInput("001")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("autofill_option_0").assertIsDisplayed().performClick()
        compose.onNodeWithTag("card_security_code_confirm").assertIsNotEnabled()
        compose.runOnIdle { assertTrue(selected.isEmpty()) }
    }

    @Test
    fun cancelDoesNotSubmitTheEnteredCode() {
        var dismissed = 0
        val selected = mutableListOf<String>()
        compose.setContent {
            MidoriBrowserTheme { CreditCardPicker(listOf(card), true, 0.dp, { _, code -> selected += code }, { dismissed++ }) }
        }
        compose.onNodeWithTag("autofill_option_0").performClick()
        compose.onNodeWithTag("card_security_code").performTextInput("001")
        compose.onNodeWithText(compose.activity.getString(R.string.cancel)).performClick()
        compose.runOnIdle {
            assertEquals(1, dismissed)
            assertTrue(selected.isEmpty())
        }
    }

    @Test
    fun disablingTheOptionFillsOnlyTheCardDetails() {
        val selected = mutableListOf<String>()
        compose.setContent {
            MidoriBrowserTheme { CreditCardPicker(listOf(card), false, 0.dp, { _, code -> selected += code }, {}) }
        }
        compose.onNodeWithTag("autofill_option_0").performClick()
        compose.onNodeWithTag("card_security_code").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(""), selected) }
    }
}
