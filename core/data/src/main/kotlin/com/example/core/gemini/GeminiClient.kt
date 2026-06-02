package com.example.core.gemini

import android.util.Log
import com.google.firebase.vertexai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GeminiClient @Inject constructor(
    private val generativeModel: GenerativeModel
) {
    suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val response = generativeModel.generateContent(prompt)
            response.text ?: """{ "error": "Empty response" }"""
        } catch (e: Exception) {
            Log.e("GeminiClient", "Vertex AI error", e)
            """{ "error": "Vertex AI failure: ${e.localizedMessage}" }"""
        }
    }
}
