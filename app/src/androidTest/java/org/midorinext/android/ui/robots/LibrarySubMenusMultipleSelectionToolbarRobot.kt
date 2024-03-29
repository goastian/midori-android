/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.ui.robots

import android.net.Uri
import android.widget.TextView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.midorinext.android.R
import org.midorinext.android.helpers.TestAssetHelper.waitingTime
import org.midorinext.android.helpers.click
import org.midorinext.android.helpers.ext.waitNotNull
import org.midorinext.android.helpers.TestAssetHelper
import org.midorinext.android.helpers.TestHelper.mDevice
import org.midorinext.android.helpers.TestHelper.packageName

/*
 * Implementation of Robot Pattern for the multiple selection toolbar of History and Bookmarks menus.
 */
class LibrarySubMenusMultipleSelectionToolbarRobot {

    fun verifyMultiSelectionCheckmark(rule: ComposeTestRule) = assertMultiSelectionCheckmark(rule)

    fun verifyMultiSelectionCheckmark(url: Uri, rule: ComposeTestRule) = assertMultiSelectionCheckmark(url, rule)

    fun verifyMultiSelectionCounter() = assertMultiSelectionCounter()

    fun verifyShareHistoryButton() = assertShareHistoryButton()

    fun verifyShareBookmarksButton() = assertShareBookmarksButton()

    fun verifyShareOverlay() = assertShareOverlay()

    fun verifyShareAppsLayout() = assertShareAppsLayout()

    fun verifyShareTabFavicon() = assertShareTabFavicon()

    fun verifyShareTabTitle() = assertShareTabTitle()

    fun verifyShareTabUrl() = assertShareTabUrl()

    fun verifyCloseToolbarButton() = assertCloseToolbarButton()

    fun clickShareHistoryButton() {
        shareHistoryButton().click()

        mDevice.waitNotNull(
            Until.findObject(
                By.text("ALL ACTIONS")
            ),
            waitingTime
        )
    }

    fun clickShareBookmarksButton() {
        shareBookmarksButton().click()

        mDevice.waitNotNull(
            Until.findObject(
                By.text("ALL ACTIONS")
            ),
            waitingTime
        )
    }

    fun clickMultiSelectionDelete() {
        deleteButton().click()
    }

    class Transition {
        fun closeShareDialogReturnToPage(interact: BrowserRobot.() -> Unit): BrowserRobot.Transition {

            BrowserRobot().interact()
            return BrowserRobot.Transition()
        }

        fun closeToolbarReturnToHistory(interact: HistoryRobot.() -> Unit): HistoryRobot.Transition {
            closeToolbarButton().click()

            HistoryRobot().interact()
            return HistoryRobot.Transition()
        }

        fun closeToolbarReturnToBookmarks(interact: BookmarksRobot.() -> Unit): BookmarksRobot.Transition {
            closeToolbarButton().click()

            BookmarksRobot().interact()
            return BookmarksRobot.Transition()
        }

        fun clickOpenNewTab(interact: TabDrawerRobot.() -> Unit): TabDrawerRobot.Transition {
            openInNewTabButton().click()
            mDevice.waitNotNull(
                Until.findObject(By.res("$packageName:id/tab_layout")),
                waitingTime
            )

            TabDrawerRobot().interact()
            return TabDrawerRobot.Transition()
        }

        fun clickOpenPrivateTab(interact: TabDrawerRobot.() -> Unit): TabDrawerRobot.Transition {
            openInPrivateTabButton().click()
            mDevice.waitNotNull(
                Until.findObject(By.res("$packageName:id/tab_layout")),
                waitingTime
            )

            TabDrawerRobot().interact()
            return TabDrawerRobot.Transition()
        }
    }
}

fun multipleSelectionToolbar(interact: LibrarySubMenusMultipleSelectionToolbarRobot.() -> Unit): LibrarySubMenusMultipleSelectionToolbarRobot.Transition {
    LibrarySubMenusMultipleSelectionToolbarRobot().interact()
    return LibrarySubMenusMultipleSelectionToolbarRobot.Transition()
}

private fun closeToolbarButton() = onView(withContentDescription("Navigate up"))

private fun shareHistoryButton() = onView(withId(R.id.share_history_multi_select))

private fun shareBookmarksButton() = onView(withId(R.id.share_bookmark_multi_select))

private fun openInNewTabButton() = onView(withText("Open in new tab"))

private fun openInPrivateTabButton() = onView(withText("Open in private tab"))

private fun deleteButton() = onView(withText("Delete"))

private fun assertMultiSelectionCheckmark(rule: ComposeTestRule) =
    rule.onNodeWithTag("library.site.item.checkmark", useUnmergedTree = true)
        .assertIsDisplayed()

private fun assertMultiSelectionCheckmark(url: Uri, rule: ComposeTestRule) =
    rule.onAllNodesWithTag("library.site.item.checkmark", useUnmergedTree = true)
        .filterToOne(hasParent(hasAnySibling(hasText(url.toString()))))
        .assertIsDisplayed()

private fun assertMultiSelectionCounter() =
    onView(withText("1 selected")).check(matches(isDisplayed()))

private fun assertShareHistoryButton() =
    shareHistoryButton().check(matches(isDisplayed()))

private fun assertShareBookmarksButton() =
    shareBookmarksButton().check(matches(isDisplayed()))

private fun assertShareOverlay() =
    onView(withId(R.id.shareWrapper)).check(matches(isDisplayed()))

private fun assertShareAppsLayout() = {
    val sendToDeviceTitle = mDevice.findObject(
        UiSelector()
            .instance(0)
            .className(TextView::class.java)
    )
    sendToDeviceTitle.waitForExists(TestAssetHelper.waitingTime)
}

private fun assertShareTabTitle() =
    onView(withId(R.id.share_tab_title)).check(matches(isDisplayed()))

private fun assertShareTabFavicon() =
    onView(withId(R.id.share_tab_favicon)).check(matches(isDisplayed()))

private fun assertShareTabUrl() = onView(withId(R.id.share_tab_url))

private fun assertCloseToolbarButton() = closeToolbarButton().check(matches(isDisplayed()))
