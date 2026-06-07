package com.example.ui.viewmodel

import android.app.Activity
import android.content.Intent
import com.example.core.auth.AuthManager
import com.example.core.auth.UserProfile
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.verify
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthViewModelTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockAuthManager: AuthManager

    private val testDispatcher = StandardTestDispatcher()
    private val userProfileFlow = MutableStateFlow(UserProfile())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockAuthManager.userProfile } returns userProfileFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects signed in status`() {
        every { mockAuthManager.isSignedIn() } returns true
        val viewModel = AuthViewModel(mockApplicationContext(), mockAuthManager)
        assertTrue(viewModel.uiState.value.isSignedIn)
    }

    @Test
    fun `initial state reflects not signed in`() {
        every { mockAuthManager.isSignedIn() } returns false
        val viewModel = AuthViewModel(mockApplicationContext(), mockAuthManager)
        assertFalse(viewModel.uiState.value.isSignedIn)
    }

    @Test
    fun `handleResult with cancelled result sets error`() = runTest(testDispatcher) {
        every { mockAuthManager.isSignedIn() } returns false
        val viewModel = AuthViewModel(mockApplicationContext(), mockAuthManager)

        viewModel.handleResult(Activity.RESULT_CANCELED, null)
        advanceUntilIdle()

        assertEquals("Sign in cancelled", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSignedIn)
    }
}

private fun mockApplicationContext(): android.content.Context {
    return org.robolectric.RuntimeEnvironment.getApplication()
}
