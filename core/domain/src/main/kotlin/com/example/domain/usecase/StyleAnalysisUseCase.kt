package com.example.domain.usecase

import com.example.core.db.entities.StyleProfileEntity
import com.example.domain.repository.MessageRepository
import com.example.domain.repository.StyleProfileRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StyleAnalysisUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val styleProfileRepository: StyleProfileRepository
) {
    suspend operator fun invoke(): Boolean {
        val sinceMs = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000L
        val sentMessages = messageRepository.getRecentForStyleAnalysis(sinceMs, 100)
        if (sentMessages.isEmpty()) return false

        val texts = sentMessages.map { it.messageText }
        analyzeAndSave(texts, "AUTO_ANALYSIS")
        return true
    }

    suspend fun analyzeAndSave(texts: List<String>, source: String) {
        if (texts.isEmpty()) return

        // 1. Average Length
        val avgLength = texts.map { it.length }.average()

        // 2. Emojis Count (Iterating over surrogate code points)
        var totalEmojis = 0
        var totalChars = 0
        texts.forEach { text ->
            totalChars += text.length
            var i = 0
            while (i < text.length) {
                val codePoint = text.codePointAt(i)
                if (codePoint.isEmojiCodePoint()) totalEmojis++
                i += Character.charCount(codePoint)
            }
        }
        val emojiDensity = if (totalChars > 0) totalEmojis.toDouble() / totalChars.toDouble() else 0.0

        // 3. Devanagari Unicode Range Detection (U+0900–U+097F) for Hindi/Hinglish
        val hasDevanagari = texts.any { text -> text.any { it in '\u0900'..'\u097F' } }
        val preferredLanguage = if (hasDevanagari) "hi" else "en"

        // 4. Identify common bi-grams (phrases)
        val bigrams = mutableListOf<String>()
        texts.forEach { text ->
            val words = text.lowercase().replace(NON_ALPHANUMERIC_OR_SPACE_REGEX, "").split(WHITESPACE_REGEX).filter { it.length > 2 }
            for (i in 0 until words.size - 1) {
                bigrams.add("${words[i]} ${words[i+1]}")
            }
        }
        val topPhrases = bigrams.groupBy { it }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
            .take(5)
            .map { it.key }

        // 5. Common greetings and closings
        val greetings = texts.mapNotNull { text ->
            val words = text.trim().split(WHITESPACE_REGEX)
            if (words.isNotEmpty()) words.take(2).joinToString(" ") else null
        }
        val topGreetings = greetings.groupBy { it.lowercase() }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
            .take(3)
            .map { entry -> greetings.first { it.lowercase() == entry.key } }

        val closings = texts.mapNotNull { text ->
            val words = text.trim().split(WHITESPACE_REGEX)
            if (words.isNotEmpty()) words.takeLast(2).joinToString(" ") else null
        }
        val topClosings = closings.groupBy { it.lowercase() }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
            .take(3)
            .map { entry -> closings.first { it.lowercase() == entry.key } }

        // 6. Identify top emojis
        val topEmojis = findTopEmojis(texts)
        val emojiSetJson = org.json.JSONArray(topEmojis).toString()

        // 7. Tone & Formality analysis
        val toneList = mutableListOf<String>()
        val allTextJoined = texts.joinToString(" ").lowercase()

        val informalOpeners = listOf("hey", "hi", "dude", "bro", "wassup", "hello there", "what's up", "yaar")
        val formalOpeners = listOf("dear", "hello", "respected", "good morning", "good afternoon", "greetings")

        var informalCount = 0
        var formalCount = 0
        texts.forEach { text ->
            val lower = text.lowercase().trim()
            if (informalOpeners.any { lower.startsWith(it) }) informalCount++
            if (formalOpeners.any { lower.startsWith(it) }) formalCount++
        }
        val formality = if (formalCount > informalCount) "FORMAL" else "CASUAL"

        if (formality == "FORMAL") {
            toneList.add("polite")
            toneList.add("courteous")
        } else {
            toneList.add("casual")
            toneList.add("friendly")
        }

        if (hasDevanagari) {
            toneList.add("uses_hindi")
        }
        if (allTextJoined.contains("yaar") || allTextJoined.contains("bhai")) {
            toneList.add("hinglish_mix")
        }
        if (allTextJoined.contains("haha") || allTextJoined.contains("lol") || allTextJoined.contains("joke") || allTextJoined.contains("fun")) {
            toneList.add("funny")
        }
        if (allTextJoined.contains("love") || allTextJoined.contains("miss") || allTextJoined.contains("dear") || allTextJoined.contains("warm")) {
            toneList.add("warm")
        }
        if (emojiDensity > 0.02) {
            toneList.add("expressive")
        }
        if (avgLength < 50) {
            toneList.add("concise")
        } else if (avgLength > 120) {
            toneList.add("detailed")
        }

        val toneDescriptorsJson = org.json.JSONArray(toneList.distinct()).toString()
        val currentProfile = styleProfileRepository.getProfileOnce() ?: StyleProfileEntity()

        val newProfile = currentProfile.copy(
            sampleMessagesJson = augmentSamplesWithAnalysis(currentProfile.sampleMessagesJson, texts),
            usesEmoji = emojiDensity > 0.01,
            avgMessageLength = avgLength.toInt(),
            commonPhrasesJson = org.json.JSONArray(topPhrases).toString(),
            commonGreetingsJson = org.json.JSONArray(topGreetings).toString(),
            formalityLevel = formality,
            preferredLanguage = preferredLanguage,
            emojiSetJson = emojiSetJson,
            toneDescriptors = toneDescriptorsJson,
            sampleCount = texts.size,
            updatedAtMs = System.currentTimeMillis()
        )

        // Save style profile snapshot to history
        val profileJson = org.json.JSONObject().apply {
            put("formalityLevel", formality)
            put("preferredLanguage", preferredLanguage)
            put("avgMessageLength", avgLength.toInt())
            put("usesEmoji", emojiDensity > 0.01)
            put("toneDescriptors", org.json.JSONArray(toneList.distinct()))
            put("commonPhrases", org.json.JSONArray(topPhrases))
        }.toString()

        val historyEntity = com.example.core.db.entities.StyleProfileHistoryEntity(
            profileJson = profileJson,
            savedAtMs = System.currentTimeMillis(),
            source = source
        )

        styleProfileRepository.upsertWithHistory(newProfile, historyEntity)
    }

    private fun findCommonPhrases(texts: List<String>): List<String> {
        val wordFreq = mutableMapOf<String, Int>()
        texts.forEach { text ->
            text.lowercase().split(WHITESPACE_REGEX).distinct().forEach { word ->
                val cleanWord = word.replace(NON_ALPHANUMERIC_REGEX, "")
                if (cleanWord.length > 3) {
                    wordFreq[cleanWord] = wordFreq.getOrDefault(cleanWord, 0) + 1
                }
            }
        }
        return wordFreq.filter { it.value > texts.size * 0.2 }.keys.toList()
    }

    private fun findTopEmojis(texts: List<String>): List<String> {
        val emojiFreq = mutableMapOf<String, Int>()
        texts.forEach { text ->
            var i = 0
            while (i < text.length) {
                val codePoint = text.codePointAt(i)
                if (codePoint.isEmojiCodePoint()) {
                    val emojiStr = String(Character.toChars(codePoint))
                    emojiFreq[emojiStr] = emojiFreq.getOrDefault(emojiStr, 0) + 1
                }
                i += Character.charCount(codePoint)
            }
        }
        return emojiFreq.entries.sortedByDescending { it.value }.take(5).map { it.key }
    }

    private fun augmentSamplesWithAnalysis(existingJson: String, texts: List<String>): String {
        val samples = mutableListOf<String>()
        try {
            val arr = org.json.JSONArray(existingJson)
            for (i in 0 until arr.length()) samples.add(arr.getString(i))
        } catch (_: Exception) {}

        // Add top 3 most representative messages
        val sorted = texts.sortedByDescending { it.length }.take(3)
        samples.addAll(sorted)

        return org.json.JSONArray(samples.distinct()).toString()
    }

    private fun Int.isEmojiCodePoint(): Boolean {
        return this in 0x1F600..0x1F64F || // Emoticons
               this in 0x1F300..0x1F5FF || // Misc Symbols
               this in 0x1F680..0x1F6FF || // Transport and Map
               this in 0x1F1E6..0x1F1FF || // Flag indicators
               this in 0x2600..0x27BF      // Dingbats & Misc
    }

    companion object {
        private val NON_ALPHANUMERIC_OR_SPACE_REGEX = Regex("[^a-zA-Z0-9\\s\u0900-\u097F]")
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val NON_ALPHANUMERIC_REGEX = Regex("[^a-zA-Z0-9]")
    }
}
