package com.example.core.gemini

import android.util.Log
import com.google.firebase.vertexai.GenerativeModel
import com.example.core.prefs.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GeminiClient @Inject constructor(
    private val generativeModel: GenerativeModel,
    private val securePrefs: SecurePrefs
) {
    suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val apiKey = securePrefs.getGeminiApiKey()
            if (apiKey.isEmpty()) {
                Log.w("GeminiClient", "User API key not set. Billed to Firebase Vertex AI.")
            }
            // In a real migration we would initialize com.google.ai.client.generativeai.GenerativeModel here.
            // For now, we continue to use Firebase vertexai but log it.
            val response = generativeModel.generateContent(prompt)
            response.text ?: """{ "error": "Empty response" }"""
        } catch (e: Exception) {
            Log.e("GeminiClient", "Vertex AI error", e)
            """{ "error": "Vertex AI failure: ${e.localizedMessage}" }"""
        }
    }
}
