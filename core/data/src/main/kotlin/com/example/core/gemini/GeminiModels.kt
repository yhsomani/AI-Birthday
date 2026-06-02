package com.example.core.gemini

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GenerationConfig
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Double = 0.85,
    val maxOutputTokens: Int = 512,
    @Json(name = "responseMimeType") val responseMimeType: String = "application/json"
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: ResponseContent?
)

@JsonClass(generateAdapter = true)
data class ResponseContent(
    val parts: List<ResponsePart>?
)

@JsonClass(generateAdapter = true)
data class ResponsePart(
    val text: String?
)
