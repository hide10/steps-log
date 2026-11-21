package com.kbcode.steps.ui.billing

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kbcode.steps.billing.*
import com.kbcode.steps.testing.ComponentActivityRegistrationRule
import com.kbcode.steps.domain.ThemeMode
import com.kbcode.steps.ui.theme.StepsAppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LogoUiTest {
    @get:Rule(order = 0) val componentActivity = ComponentActivityRegistrationRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun logoIsVisibleAtBottomLeftAndDoesNotInterceptTouchesOrExposeText() {
        var clicks = 0
        compose.setContent {
            Box(Modifier.size(320.dp, 480.dp).background(Color.White)) {
                LogoOverlay(owned = false) {
                    Box(Modifier.fillMaxSize().testTag("free-map").clickable { clicks++ })
                }
            }
        }
        val logo = compose.onNodeWithTag("steps-logo", useUnmergedTree = true)
        logo.assertExists().assertHasNoClickAction()
        logo.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        @Suppress("DEPRECATION")
        logo.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.InvisibleToUser))
        val logoBounds = logo.fetchSemanticsNode().boundsInRoot
        val map = compose.onNodeWithTag("free-map")
        val mapBounds = map.fetchSemanticsNode().boundsInRoot
        assertTrue(logoBounds.left < mapBounds.width / 4)
        assertTrue(logoBounds.top > mapBounds.height * 0.9f)
        map.performTouchInput { click(Offset(logoBounds.center.x - mapBounds.left, logoBounds.center.y - mapBounds.top)) }
        compose.runOnIdle { assertEquals(1, clicks) }
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            val view = compose.activity.findViewById<View>(android.R.id.content)
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        var darkest = 1f
        for (y in logoBounds.top.toInt() until logoBounds.bottom.toInt()) {
            for (x in logoBounds.left.toInt() until logoBounds.right.toInt()) {
                darkest = minOf(darkest, android.graphics.Color.red(bitmap.getPixel(x, y)) / 255f)
            }
        }
        System.getProperty("steps.testEvidenceDir")?.let { directory ->
            java.io.File(directory, "logo-unowned.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        // The trial label's fill follows the theme's onBackground color at alpha .55 over white,
        // so fully covered glyph pixels darken to .45; the outline layer uses the background
        // color and does not darken a white background.
        assertTrue("Visible watermark expected: $darkest", darkest < 0.99f)
        assertTrue("Logo is too dark: $darkest", darkest >= 0.40f)
    }

    @Test fun lightThemeLogoFollowsOnBackground() = assertThemeLogo(darkTheme = false)

    @Test fun darkThemeLogoFollowsOnBackground() = assertThemeLogo(darkTheme = true)

    private fun assertThemeLogo(darkTheme: Boolean) {
        val owned = mutableStateOf(false)
        var clicks = 0
        var background = Color.Unspecified
        var foreground = Color.Unspecified
        compose.setContent {
            StepsAppTheme(themeMode = if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT) {
                background = MaterialTheme.colorScheme.background
                foreground = MaterialTheme.colorScheme.onBackground
                Box(Modifier.size(320.dp, 480.dp).background(background)) {
                    LogoOverlay(owned.value) {
                        Box(Modifier.fillMaxSize().testTag("themed-map").clickable { clicks++ })
                    }
                }
            }
        }
        val logo = compose.onNodeWithTag("steps-logo", useUnmergedTree = true)
        logo.assertIsDisplayed().assertHasNoClickAction()
        logo.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        @Suppress("DEPRECATION")
        logo.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.InvisibleToUser))
        val bounds = logo.fetchSemanticsNode().boundsInRoot
        val map = compose.onNodeWithTag("themed-map")
        val mapBounds = map.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.left < mapBounds.width / 4)
        assertTrue(bounds.top > mapBounds.height * 0.9f)
        map.performTouchInput { click(bounds.center - mapBounds.topLeft) }
        compose.runOnIdle { assertEquals(1, clicks) }

        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            val view = compose.activity.findViewById<View>(android.R.id.content)
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        val bg = floatArrayOf(background.red, background.green, background.blue)
        val fg = floatArrayOf(foreground.red, foreground.green, foreground.blue)
        val coverage = FloatArray(3)
        for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
            for (x in bounds.left.toInt() until bounds.right.toInt()) {
                val pixel = bitmap.getPixel(x, y)
                val channels = intArrayOf(android.graphics.Color.red(pixel),
                    android.graphics.Color.green(pixel), android.graphics.Color.blue(pixel))
                for (channel in 0..2) {
                    val alpha = (channels[channel] / 255f - bg[channel]) / (fg[channel] - bg[channel])
                    assertTrue("Pixel must blend toward onBackground: $alpha", alpha >= -0.02f)
                    coverage[channel] = maxOf(coverage[channel], alpha)
                }
            }
        }
        // Fully covered glyph pixels blend onBackground at .55; the outline layer blends
        // toward the background color, and edges are antialiased.
        coverage.forEach { assertEquals("Theme color and trial alpha", 0.55f, it, 0.025f) }
        compose.runOnIdle { owned.value = true }
        logo.assertDoesNotExist()
        map.performClick()
        compose.runOnIdle { assertEquals(2, clicks) }
    }

    @Test fun purchasedLogoIsNotComposedAndFreeContentRemainsAcrossFailureStates() {
        val state = mutableStateOf(LogoBillingState())
        var actions = 0
        compose.setContent {
            LogoOverlay(state.value.owned) {
                Box(Modifier.fillMaxSize().testTag("free-content").clickable { actions++ })
            }
        }
        for (status in listOf(BillingStatus.PENDING, BillingStatus.CANCELED, BillingStatus.OFFLINE, BillingStatus.ERROR)) {
            compose.runOnIdle { state.value = LogoBillingState(status = status) }
            compose.onNodeWithTag("steps-logo", useUnmergedTree = true).assertExists()
            compose.onNodeWithTag("free-content").performClick()
        }
        compose.runOnIdle { state.value = LogoBillingState(owned = true) }
        compose.onNodeWithTag("steps-logo", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("free-content").performClick()
        compose.runOnIdle { assertEquals(5, actions) }
    }

    @Test fun purchaseAndRestoreButtonsRecoverAfterPurchaseWaitIsReconciled() {
        val state = mutableStateOf(LogoBillingState(
            product = LogoProduct("Console title", "TEST PRICE"), status = BillingStatus.PURCHASING
        ))
        var buys = 0
        var restores = 0
        compose.setContent {
            MaterialTheme { LogoPurchaseSection(state.value, { buys++ }, { restores++ }) }
        }
        compose.onNodeWithText("Buy · TEST PRICE").assertIsNotEnabled()
        compose.onNodeWithText("Restore purchases / Retry").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(status = BillingStatus.CHECKING, product = null) }
        compose.onNodeWithText("Restore purchases / Retry").assertIsNotEnabled()
        compose.runOnIdle {
            state.value = state.value.copy(status = BillingStatus.READY, product = LogoProduct("Console title", "TEST PRICE"))
        }
        compose.onNodeWithText("Buy · TEST PRICE").assertIsEnabled().performClick()
        compose.onNodeWithText("Restore purchases / Retry").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, buys); assertEquals(1, restores) }
    }

    @Test fun purchaseSectionUsesStorePriceAndDisablesPendingPurchase() {
        val state = mutableStateOf(LogoBillingState(product = LogoProduct("Console title", "TEST PRICE"), status = BillingStatus.READY))
        var buys = 0
        var restores = 0
        compose.setContent {
            MaterialTheme { LogoPurchaseSection(state.value, { buys++ }, { restores++ }) }
        }
        // The store product name is not rendered: it duplicates the section title.
        compose.onNodeWithText("Console title").assertDoesNotExist()
        compose.onNodeWithText("Buy · TEST PRICE").performClick()
        compose.runOnIdle { assertEquals(1, buys); state.value = state.value.copy(status = BillingStatus.PENDING) }
        compose.onNodeWithText("Buy · TEST PRICE").assertIsNotEnabled()
        compose.onNodeWithText("Restore purchases / Retry").performClick()
        compose.runOnIdle { assertEquals(1, restores); state.value = state.value.copy(owned = true, status = BillingStatus.READY) }
        compose.onNodeWithText("Purchased").assertExists()
        compose.onNodeWithText("Buy · TEST PRICE").assertDoesNotExist()
    }
}
