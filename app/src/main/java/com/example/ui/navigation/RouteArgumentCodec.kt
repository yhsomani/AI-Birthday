package com.example.ui.navigation

import com.example.domain.navigation.RelateDeepLinks
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal object RouteArgumentCodec {
    fun encode(value: String): String {
        return RelateDeepLinks.encodePathSegment(value)
    }

    fun decode(value: String?): String {
        if (value == null) return ""
        return runCatching {
            URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        }.getOrElse {
            value
        }
    }
}
