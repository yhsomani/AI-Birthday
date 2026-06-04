package com.example.domain.model

/**
 * Sealed class representing automation modes for message dispatch.
 * Replaces magic strings like "FULLY_AUTO", "SMART_APPROVE", etc.
 */
sealed class AutomationMode(val value: String) {
    object FullyAuto : AutomationMode("FULLY_AUTO")
    object SmartApprove : AutomationMode("SMART_APPROVE")
    object VipApprove : AutomationMode("VIP_APPROVE")
    object AlwaysAsk : AutomationMode("ALWAYS_ASK")
    object Default : AutomationMode("DEFAULT")
    
    companion object {
        fun fromValue(value: String): AutomationMode {
            return when (value) {
                "FULLY_AUTO" -> FullyAuto
                "SMART_APPROVE" -> SmartApprove
                "VIP_APPROVE" -> VipApprove
                "ALWAYS_ASK" -> AlwaysAsk
                "DEFAULT" -> Default
                else -> Default
            }
        }
    }
}

/**
 * Sealed class representing communication channels.
 * Replaces magic strings like "SMS", "WHATSAPP", "EMAIL".
 */
sealed class CommunicationChannel(val value: String) {
    object Sms : CommunicationChannel("SMS")
    object WhatsApp : CommunicationChannel("WHATSAPP")
    object Email : CommunicationChannel("EMAIL")
    
    companion object {
        fun fromValue(value: String): CommunicationChannel {
            return when (value) {
                "SMS" -> Sms
                "WHATSAPP" -> WhatsApp
                "EMAIL" -> Email
                else -> Sms
            }
        }
    }
}

/**
 * Sealed class representing relationship types.
 * Replaces magic strings like "FAMILY", "BEST_FRIEND", etc.
 */
sealed class RelationshipType(val value: String) {
    object Family : RelationshipType("FAMILY")
    object BestFriend : RelationshipType("BEST_FRIEND")
    object CloseFriend : RelationshipType("CLOSE_FRIEND")
    object Friend : RelationshipType("FRIEND")
    object Colleague : RelationshipType("COLLEAGUE")
    object Client : RelationshipType("CLIENT")
    object Mentor : RelationshipType("MENTOR")
    object Relative : RelationshipType("RELATIVE")
    object Acquaintance : RelationshipType("ACQUAINTANCE")
    object Unknown : RelationshipType("UNKNOWN")
    
    companion object {
        fun fromValue(value: String): RelationshipType {
            return when (value) {
                "FAMILY" -> Family
                "BEST_FRIEND" -> BestFriend
                "CLOSE_FRIEND" -> CloseFriend
                "FRIEND" -> Friend
                "COLLEAGUE" -> Colleague
                "CLIENT" -> Client
                "MENTOR" -> Mentor
                "RELATIVE" -> Relative
                "ACQUAINTANCE" -> Acquaintance
                "UNKNOWN" -> Unknown
                else -> Unknown
            }
        }
    }
}
