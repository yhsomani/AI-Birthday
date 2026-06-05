package com.example.navigation

/**
 * Navigation routes for the RelateAI app.
 */
object AppRoutes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val DASHBOARD = "dashboard"
    const val CONTACTS = "contacts"
    const val CONTACT_DETAIL = "contact_detail/{contactId}"
    const val EVENTS = "events"
    const val MESSAGES = "messages"
    const val MORE = "more"
    const val SETTINGS = "settings"
    const val ANALYTICS = "analytics"
    const val STYLE_COACH = "style_coach"
    const val MESSAGE_APPROVAL = "message_approval/{messageId}"

    fun contactDetail(contactId: String) = "contact_detail/$contactId"
    fun messageApproval(messageId: String) = "message_approval/$messageId"
}
