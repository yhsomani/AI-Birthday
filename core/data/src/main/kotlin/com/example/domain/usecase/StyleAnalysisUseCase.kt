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
        
        // Basic analysis
        val avgLength = texts.map { it.length }.average()
        val emojiCount = texts.sumOf { text -> 
            text.count { it.isEmoji() } 
        }
        val emojiDensity = emojiCount.toDouble() / texts.sumOf { it.length }.toDouble()

        // Identify common phrases (simple n-gram approach)
        val commonPhrases = findCommonPhrases(texts)

        val currentProfile = styleProfileRepository.getProfileOnce() ?: StyleProfileEntity()
        
        // Update profile with analyzed data
        styleProfileRepository.upsert(currentProfile.copy(
            // We store analysis results in common phrases or a dedicated field if available
            // For now, let's add them to sampleMessagesJson or a new field
            // Since we don't have an 'analysis' field, we'll augment the samples
            sampleMessagesJson = augmentSamplesWithAnalysis(currentProfile.sampleMessagesJson, texts),
            updatedAtMs = System.currentTimeMillis()
        ))
    }

    private fun findCommonPhrases(texts: List<String>): List<String> {
        // Simple implementation: find words appearing in > 20% of messages
        val wordFreq = mutableMapOf<String, Int>()
        texts.forEach { text ->
            text.lowercase().split(Regex("\\s+")).distinct().forEach { word ->
                if (word.length > 3) wordFreq[word] = wordFreq.getOrDefault(word, 0) + 1
            }
        }
        return wordFreq.filter { it.value > texts.size * 0.2 }.keys.toList()
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

    private fun Char.isEmoji(): Boolean {
        // Simplified emoji check
        return this.code in 0x1F600..0x1F64F || this.code in 0x1F300..0x1F5FF || this.code in 0x1F680..0x1F6FF
    }
}
