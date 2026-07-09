package com.example.domain.model

object MessageChannelSetCodec {
    private val tokenPattern = Regex("\"([A-Za-z_]+)\"")

    fun parse(raw: String?): Set<MessageChannel> {
        if (raw.isNullOrBlank()) return emptySet()
        return tokenPattern.findAll(raw)
            .map { match -> MessageChannel.fromRaw(match.groupValues[1]) }
            .filter { channel -> channel != MessageChannel.UNKNOWN }
            .toSet()
    }

    fun toJsonArray(channels: Iterable<MessageChannel>): String {
        return channels.asSequence()
            .filter { channel -> channel != MessageChannel.UNKNOWN }
            .map { channel -> channel.raw }
            .distinct()
            .sorted()
            .joinToString(separator = ",", prefix = "[", postfix = "]") { channel -> "\"$channel\"" }
    }
}
