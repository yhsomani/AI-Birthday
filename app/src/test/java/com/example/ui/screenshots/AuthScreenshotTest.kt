package com.example.ui.screenshots

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.screens.auth.AuthContent
import com.example.ui.viewmodel.AuthUiState
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
class AuthScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun authDefault_compactPhone() {
        setAuthContent()

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/auth_default_compact_phone.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun authDefault_typicalPhone() {
        setAuthContent()

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/auth_default_typical_phone.png")
    }

    @Test
    fun authDefault_compactPhoneLargeFont() {
        setAuthContent(fontScale = LargeFontScale)

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/baseline/auth_default_compact_phone_large_font.png")
    }

    private fun setAuthContent(fontScale: Float = DefaultFontScale) {
        composeRule.setRelateScreenshotContent(fontScale = fontScale) {
            AuthContent(
                state = AuthUiState(),
                onSignIn = {},
            )
        }
    }
}
