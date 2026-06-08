package com.example.feature.onboarding

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun WhatsAppSetupScreen(
    navController: NavController,
    context: Context = LocalContext.current
) {
    Column {
        Text("Please enable Accessibility for WhatsApp automation.")
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e("WhatsAppSetupScreen", "Failed to open accessibility settings", e)
                }
                navController.navigate("battery_opt")
            },
            modifier = Modifier
        ) {
            Text("Open Settings")
        }
    }
}
