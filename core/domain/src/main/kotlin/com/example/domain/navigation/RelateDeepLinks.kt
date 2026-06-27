package com.example.domain.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object RelateDeepLinks {
    const val SCHEME = "relateai"

    object Contact {
        const val HOST = "contact"
        const val CONTACT_ID_ARG = "contactId"
        const val pattern = "$SCHEME://$HOST/{$CONTACT_ID_ARG}"

        fun uri(contactId: String): String {
            return "$SCHEME://$HOST/${encodePathSegment(contactId)}"
        }
    }

    object Wish {
        const val HOST = "wish"
        const val CONTACT_ID_ARG = "contactId"
        const val MESSAGE_REF_ARG = "messageRef"
        const val pattern = "$SCHEME://$HOST/{$CONTACT_ID_ARG}/{$MESSAGE_REF_ARG}"

        fun uri(contactId: String, messageRef: String): String {
            return "$SCHEME://$HOST/${encodePathSegment(contactId)}/${encodePathSegment(messageRef)}"
        }
    }

    object Settings {
        const val HOST = "settings"
        const val uri = "$SCHEME://$HOST"
        const val pattern = uri
    }

    object BackupRestore {
        const val HOST = "backup-restore"
        const val uri = "$SCHEME://$HOST"
        const val pattern = uri
    }

    fun encodePathSegment(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
    }
}
