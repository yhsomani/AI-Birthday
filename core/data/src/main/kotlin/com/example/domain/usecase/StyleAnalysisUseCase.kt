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
    suspend operator fun invoke() {
        val sentMessages = messageRepository.getAllSent().first()
        if (sentMessages.isEmpty()) return

        val texts = sentMessages.map { it.messageText }
        
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

        // 3. Identify common phrases
        val commonPhrases = findCommonPhrases(texts)
        val commonPhrasesJson = org.json.JSONArray(commonPhrases).toString()

        // 4. Identify top emojis
        val topEmojis = findTopEmojis(texts)
        val emojiSetJson = org.json.JSONArray(topEmojis).toString()

        // 5. Tone & Formality analysis
        val toneList = mutableListOf<String>()
        val allTextJoined = texts.joinToString(" ").lowercase()
        
        val informalWords = listOf("hey", "hi", "dude", "yaar", "bro", "wassup", "bday", "congrats", "party", "haha", "lol")
        val formalWords = listOf("dear", "hello", "regards", "warm", "wishing", "sincere", "success", "career", "health", "prosperous")
        
        val informalCount = informalWords.sumOf { word -> allTextJoined.split(word).size - 1 }
        val formalCount = formalWords.sumOf { word -> allTextJoined.split(word).size - 1 }
        
        val formality = if (formalCount > informalCount) "FORMAL" else "CASUAL"
        
        if (formality == "FORMAL") {
            toneList.add("polite")
            toneList.add("courteous")
        } else {
            toneList.add("casual")
            toneList.add("friendly")
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
        
        // Update profile with fully analyzed data
        styleProfileRepository.upsert(currentProfile.copy(
            sampleMessagesJson = augmentSamplesWithAnalysis(currentProfile.sampleMessagesJson, texts),
            usesEmoji = emojiDensity > 0.01,
            avgMessageLength = avgLength.toInt(),
            commonPhrasesJson = commonPhrasesJson,
            formalityLevel = formality,
            emojiSetJson = emojiSetJson,
            toneDescriptors = toneDescriptorsJson,
            sampleCount = texts.size,
            updatedAtMs = System.currentTimeMillis()
        ))
    }

    private fun findCommonPhrases(texts: List<String>): List<String> {
        val wordFreq = mutableMapOf<String, Int>()
        texts.forEach { text ->
            text.lowercase().split(Regex("\\s+")).distinct().forEach { word ->
                val cleanWord = word.replace(Regex("[^a-zA-Z0-9]"), "")
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
}
