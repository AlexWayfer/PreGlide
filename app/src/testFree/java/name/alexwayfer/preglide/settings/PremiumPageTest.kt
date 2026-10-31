package name.alexwayfer.preglide.settings

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import name.alexwayfer.preglide.ime.ComposeHost
import name.alexwayfer.preglide.premium.PREMIUM_PURCHASE_URL_USD
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PremiumPageTest {
    @get:Rule(order = 0)
    val host = ComposeHost()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val opened = mutableListOf<String>()
    private var backs = 0

    private fun showPage() {
        val links = object : UriHandler {
            override fun openUri(uri: String) {
                opened += uri
            }
        }
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides links) {
                PremiumPage(onBack = { backs++ })
            }
        }
    }

    @Test
    fun upgradeOpensThePurchaseAndLeavesThePage() {
        showPage()

        compose.onNodeWithText("Upgrade to Premium").performClick()

        // Robolectric's device has no SIM, which sees the price in dollars.
        assertEquals(listOf(PREMIUM_PURCHASE_URL_USD), opened)
        assertEquals(1, backs)
    }

    @Test
    fun notNowLeavesThePageAndOpensNothing() {
        showPage()

        compose.onNodeWithText("Not now").performClick()

        assertEquals(emptyList<String>(), opened)
        assertEquals(1, backs)
    }

    @Test
    fun preTypeInTheSyncLineOpensItsSiteAndStaysOnThePage() {
        showPage()

        compose.onNodeWithText("PreType", substring = true).performFirstLinkClick()

        assertEquals(listOf(PRETYPE_URL), opened)
        assertEquals(0, backs)
    }

    @Test
    fun theListEndsSayingMoreIsComing() {
        showPage()

        compose.onNodeWithText("More in the works").assertExists()
    }

    @Test
    fun theLockedSyncRowOpensThePremiumPage() {
        val pages = mutableListOf<SettingsPage>()
        compose.setContent { SyncMenuEntry { pages += it } }

        compose.onNodeWithText("Sync").performClick()

        assertEquals(listOf(SettingsPage.Premium), pages)
    }
}
