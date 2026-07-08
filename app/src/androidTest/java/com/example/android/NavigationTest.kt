package com.example.android

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NavigationTest {

    @get: Rule
    val composeTestRule = createComposeRule()

    lateinit var navController: TestNavHostController

    @Before
    fun setupMidoriNavHost() {
        composeTestRule.setContent {
            navController = TestNavHostController(LocalContext.current)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            // MidoriNavHost(navController = navController, store = LocalContext.current.components.core.store)
        }
    }

    @Test
    fun midoriNavHost_startDestination() {
        composeTestRule
            .onNodeWithText("Browser")
            .assertIsDisplayed()
    }

    @Test
    fun midoriNavHost_forcedFail() {
        fail()
    }
}