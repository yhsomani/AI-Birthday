package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.feature.dashboard.MainViewModel
import com.example.feature.login.LoginViewModel
import com.example.core.prefs.SecurePrefs
import com.example.core.auth.AuthManager
import com.example.feature.dashboard.MainAppScreen
import com.example.feature.login.LoginScreen
import com.example.feature.splash.SplashScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.json.JSONArray

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val prefs = remember { SecurePrefs(context) }
            val systemDark = isSystemInDarkTheme()
            val themeModePref = remember { mutableStateOf(prefs.getThemeMode()) }
            
            val isDark = when(themeModePref.value) {
                "DARK" -> true
                "LIGHT" -> false
                else -> systemDark
            }
            
            MyApplicationTheme(darkTheme = isDark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))
                            )
                        )
                ) {
                    val navController = rememberNavController()
                    
                    NavHost(navController = navController, startDestination = "splash") {
                        composable("splash") {
                            val context = LocalContext.current
                            val prefs = remember { SecurePrefs(context) }
                            SplashScreen(
                                onSplashFinished = {
                                    val destination = if (prefs.isOnboardingComplete()) {
                                        "main"
                                    } else if (authManager.getCurrentUser() != null || prefs.isGuestMode()) {
                                        "onboarding"
                                    } else {
                                        "login"
                                    }
                                    navController.navigate(destination) {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("login") {
                            val context = LocalContext.current
                            val scope = rememberCoroutineScope()
                            val loginViewModel: LoginViewModel = hiltViewModel()
                            
                            val signInLauncher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.StartActivityForResult()
                            ) { result ->
                                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                                try {
                                    val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                                    if (account != null) {
                                        loginViewModel.signInWithGoogle(account) { success ->
                                            if (success) {
                                                navController.navigate("onboarding") {
                                                    popUpTo("login") { inclusive = true }
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("MainActivity", "Google Sign-In failed", e)
                                }
                            }
                            
                            LoginScreen(
                                onLoginClick = {
                                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                        .requestIdToken(context.getString(R.string.default_web_client_id))
                                        .requestEmail()
                                        .requestScopes(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/contacts.readonly"))
                                        .build()
                                    val client = GoogleSignIn.getClient(context, gso)
                                    signInLauncher.launch(client.signInIntent)
                                },
                                onGuestClick = {
                                    val prefs = SecurePrefs(context)
                                    prefs.setGuestMode(true)
                                    navController.navigate("onboarding") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("onboarding") {
                            val context = LocalContext.current
                            com.example.feature.onboarding.OnboardingScreen(
                                onFinish = {
                                    val prefs = SecurePrefs(context)
                                    prefs.setOnboardingComplete(true)
                                    navController.navigate("main") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("main") {
                            val scope = rememberCoroutineScope()
                            val mainViewModel: MainViewModel = hiltViewModel()
                            MainAppScreen(
                                onSaveTrainingText = { text ->
                                    mainViewModel.saveTrainingText(text)
                                },
                                onAddBirthday = { contactId, day, month, year ->
                                    mainViewModel.addBirthday(contactId, day, month, year)
                                },
                                onSignOut = {
                                    scope.launch {
                                        val prefs = SecurePrefs(context)
                                        prefs.clearAll()
                                        
                                        com.example.core.db.AppDatabase.getInstance(context).clearAllTables()
                                        
                                        authManager.signOut()
                                        GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
                                        
                                        navController.navigate("login") {
                                            popUpTo("main") { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

