package com.example.domain.model.common

object JsonTextCodec {
    fun parseStringArray(raw: String): List<String> {
        return runCatching {
            JsonStringArrayParser(raw).parse()
        }.getOrDefault(emptyList())
    }

    fun hasStringArrayContent(raw: String?): Boolean {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return false

        val parsed = runCatching {
            JsonStringArrayParser(trimmed).parse()
        }.getOrNull()
        return parsed?.isNotEmpty() ?: (trimmed != "[]")
    }

    fun countStringArrayItems(raw: String?): Int {
        if (raw.isNullOrBlank()) return 0
        return parseStringArray(raw).size
    }

    fun readStringField(raw: String, key: String): String? {
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        return pattern.find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::decodeJsonString)
            ?.takeIf { it.isNotBlank() }
    }

    fun readIntField(raw: String, key: String): Int? {
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+)")
        return pattern.find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    fun encodeStringArray(values: Iterable<String>): String {
        return values.joinToString(separator = ",", prefix = "[", postfix = "]") { value ->
            encodeString(value)
        }
    }

    fun encodeObject(fields: Iterable<Pair<String, Any?>>): String {
        return fields.joinToString(separator = ",", prefix = "{", postfix = "}") { (key, value) ->
            "${encodeString(key)}:${encodeValue(value)}"
        }
    }

    fun encodeString(value: String): String {
        val builder = StringBuilder(value.length + 2)
        builder.append('"')
        value.forEach { char ->
            when (char) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\b' -> builder.append("\\b")
                '\u000C' -> builder.append("\\f")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                in '\u0000'..'\u001F' -> {
                    builder.append("\\u")
                    builder.append(char.code.toString(16).padStart(4, '0'))
                }
                else -> builder.append(char)
            }
        }
        builder.append('"')
        return builder.toString()
    }

    private fun encodeValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> encodeString(value)
            is Number -> value.toString()
            is Boolean -> value.toString()
            is Iterable<*> -> value.joinToString(separator = ",", prefix = "[", postfix = "]") { item ->
                encodeValue(item)
            }
            else -> encodeString(value.toString())
        }
    }

    private fun decodeJsonString(raw: String): String {
        val builder = StringBuilder(raw.length)
        var index = 0
        while (index < raw.length) {
            val char = raw[index++]
            if (char != '\\') {
                builder.append(char)
                continue
            }
            if (index >= raw.length) return builder.toString()
            when (val escaped = raw[index++]) {
                '"' -> builder.append('"')
                '\\' -> builder.append('\\')
                '/' -> builder.append('/')
                'b' -> builder.append('\b')
                'f' -> builder.append('\u000C')
                'n' -> builder.append('\n')
                'r' -> builder.append('\r')
                't' -> builder.append('\t')
                'u' -> {
                    if (index + 4 <= raw.length) {
                        val hex = raw.substring(index, index + 4)
                        val codePoint = hex.toIntOrNull(16)
                        if (codePoint != null) builder.append(codePoint.toChar())
                        index += 4
                    }
                }
                else -> builder.append(escaped)
            }
        }
        return builder.toString()
    }

    private class JsonStringArrayParser(
        private val raw: String,
    ) {
        private var index = 0

        fun parse(): List<String> {
            skipWhitespace()
            require(consume('['))
            skipWhitespace()
            if (consume(']')) return emptyList()

            val values = mutableListOf<String>()
            while (index < raw.length) {
                skipWhitespace()
                values += parseString()
                skipWhitespace()
                when {
                    consume(',') -> continue
                    consume(']') -> {
                        skipWhitespace()
                        require(index == raw.length)
                        return values
                    }
                    else -> error("Expected comma or closing bracket")
                }
            }
            error("Unterminated array")
        }

        private fun parseString(): String {
            require(consume('"'))
            val builder = StringBuilder()
            while (index < raw.length) {
                val char = raw[index++]
                when (char) {
                    '"' -> return builder.toString()
                    '\\' -> builder.append(parseEscape())
                    else -> builder.append(char)
                }
            }
            error("Unterminated string")
        }

        private fun parseEscape(): Char {
            require(index < raw.length)
            return when (val escaped = raw[index++]) {
                '"' -> '"'
                '\\' -> '\\'
                '/' -> '/'
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    require(index + 4 <= raw.length)
                    val codePoint = raw.substring(index, index + 4).toInt(16)
                    index += 4
                    codePoint.toChar()
                }
                else -> escaped
            }
        }

        private fun skipWhitespace() {
            while (index < raw.length && raw[index].isWhitespace()) index++
        }

        private fun consume(expected: Char): Boolean {
            if (index >= raw.length || raw[index] != expected) return false
            index++
            return true
        }
    }
}
