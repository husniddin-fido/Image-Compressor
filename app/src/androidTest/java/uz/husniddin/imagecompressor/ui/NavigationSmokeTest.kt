package uz.husniddin.imagecompressor.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uz.husniddin.imagecompressor.MainActivity

/** Launches the real app and walks the three tabs. */
@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun tab(label: String) = compose.onNode(hasText(label) and hasClickAction())

    @Test
    fun compressTab_showsPrimaryActions() {
        tab("Compress").assertIsSelected()
        compose.onNodeWithText("Select images").assertIsDisplayed()
        compose.onNodeWithText("Compress a single photo").assertIsDisplayed()
        compose.onNodeWithText("Processed on your device. Your images are never uploaded.").assertIsDisplayed()
    }

    @Test
    fun tabs_switchBetweenCompressHistoryAndSettings() {
        tab("Settings").performClick()
        tab("Settings").assertIsSelected()
        compose.onNodeWithText("Appearance").assertIsDisplayed()

        tab("History").performClick()
        tab("History").assertIsSelected()
        // The screen title (not the tab label); history content depends on existing data.
        compose.onNode(hasText("History") and !hasClickAction()).assertIsDisplayed()

        tab("Compress").performClick()
        tab("Compress").assertIsSelected()
        compose.onNodeWithText("Select images").assertIsDisplayed()
    }
}
