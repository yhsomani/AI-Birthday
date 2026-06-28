package com.example.ui.screenshots

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.screens.splash.SplashContent
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@Category(ScreenshotTests::class)
@RunWith(AndroidJUnit4::class)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w360dp-h800dp-xhdpi",
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SplashScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun splashDefault_compactPhone() {
        setSplashContent()

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/splash_default_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun splashDefault_typicalPhone() {
        setSplashContent()

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/splash_default_typical_phone.png")
    }

    @Test
    fun splashDefault_compactPhoneLargeFont() {
        setSplashContent(fontScale = LargeFontScale)

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/splash_default_compact_phone_large_font.png")
    }

    private fun setSplashContent(fontScale: Float = DefaultFontScale) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setRelateScreenshotContent(fontScale = fontScale) {
            SplashContent(alpha = VisibleContentAlpha)
        }
        composeRule.mainClock.advanceTimeBy(ProgressAnimationFrameMillis)
        composeRule.waitForIdle()
    }

    private companion object {
        const val VisibleContentAlpha = 1f
        const val ProgressAnimationFrameMillis = 750L
    }
}
