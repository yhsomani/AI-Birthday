package com.example.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.core.db.AppDatabase
import com.example.core.db.entities.StyleProfileEntity

@Composable
fun WritingStyleScreen(navController: NavController) {
    var sample1 by rememberSaveable { mutableStateOf("") }
    var sample2 by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    OnboardingWrapper(
        title = "Voice DNA Blueprinting",
        subtitle = "Type or paste two sample texts you typically write to train models to mimic your sentence structure, emoji density, and general tone.",
        currentStep = 8,
        onNext = {
            scope.launch {
                val db = AppDatabase.getInstance(context)
                val existing = db.styleProfileDao().get() ?: StyleProfileEntity()

                val samples = mutableListOf<String>()
                if (sample1.isNotBlank()) samples.add(sample1.trim())
                if (sample2.isNotBlank()) samples.add(sample2.trim())

                val jsonArr = org.json.JSONArray()
                samples.forEach { jsonArr.put(it) }

                db.styleProfileDao().upsert(existing.copy(sampleMessagesJson = jsonArr.toString()))
                navController.navigate("automation_prefs")
            }
        },
        nextText = "Configure Persona Blueprint",
        onBack = { navController.popBackStack() },
        onSkip = { navController.navigate("automation_prefs") },
        skipText = "Default Voice"
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = sample1,
                onValueChange = { sample1 = it },
                label = { Text("Write Sample Text A") },
                placeholder = { Text("e.g. 'Hey! Happy birthday buddy! Hope you have an awesome year ahead directly filled with joy! Cheers'") },
                modifier = Modifier.fillMaxWidth().height(110.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            OutlinedTextField(
                value = sample2,
                onValueChange = { sample2 = it },
                label = { Text("Write Sample Text B") },
                placeholder = { Text("e.g. 'Hi Prof! Hope you are doing well. Just wanted to catch up on the research details sometime...'") },
                modifier = Modifier.fillMaxWidth().height(110.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
        }
    }
}
