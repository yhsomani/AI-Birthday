package com.example.core.contacts

import com.example.domain.model.contact.ContactSyncRecord
import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.toContactSyncRecord(): ContactSyncRecord? {
    val resourceName = optString("resourceName")
    if (resourceName.isEmpty()) return null

    val metadata = optJSONObject("metadata")
    val isDeleted = metadata?.optBoolean("deleted", false) ?: false
    if (isDeleted) {
        return ContactSyncRecord(
            id = resourceName,
            googleContactId = resourceName,
            displayName = "",
            isDeleted = true,
        )
    }

    return ContactSyncRecord(
        id = resourceName,
        googleContactId = resourceName,
        displayName = displayName(),
        nickname = firstObjectValue("nicknames", "value"),
        primaryPhone = firstObjectValue("phoneNumbers", "value"),
        primaryEmail = firstObjectValue("emailAddresses", "value"),
        company = firstObjectValue("organizations", "name"),
        jobTitle = firstObjectValue("organizations", "title"),
        birthdayDay = firstGoogleDateComponent("birthdays", "day"),
        birthdayMonth = firstGoogleDateComponent("birthdays", "month"),
        birthdayYear = firstGoogleDateComponent("birthdays", "year"),
        anniversaryDay = firstEventDateComponent(type = "anniversary", component = "day"),
        anniversaryMonth = firstEventDateComponent(type = "anniversary", component = "month"),
        workStartDay = firstEventDateComponent(type = "work_anniversary", component = "day"),
        workStartMonth = firstEventDateComponent(type = "work_anniversary", component = "month"),
        workStartYear = firstEventDateComponent(type = "work_anniversary", component = "year"),
        contactGroup = firstContactGroupName(),
        relationsJson = relationsJson(),
        address = firstObjectValue("addresses", "formattedValue"),
        profilePhotoUri = firstObjectValue("photos", "url"),
        notesText = firstObjectValue("biographies", "value").orEmpty(),
    )
}

private fun JSONObject.displayName(): String {
    return firstObjectValue("names", "displayName")
        ?.takeUnless(String::isEmpty)
        ?: firstObjectValue("nicknames", "value")?.takeUnless(String::isEmpty)
        ?: firstObjectValue("phoneNumbers", "value")?.takeUnless(String::isEmpty)
        ?: firstObjectValue("emailAddresses", "value")?.takeUnless(String::isEmpty)
        ?: "Unnamed Contact"
}

private fun JSONObject.firstObjectValue(arrayName: String, fieldName: String): String? {
    val array = optJSONArray(arrayName)
    if (array == null || array.length() == 0) return null
    return array.getJSONObject(0).optString(fieldName)
}

private fun JSONObject.firstGoogleDateComponent(arrayName: String, component: String): Int? {
    val array = optJSONArray(arrayName)
    if (array == null || array.length() == 0) return null
    val date = array.getJSONObject(0).optJSONObject("date") ?: return null
    return date.optionalInt(component)
}

private fun JSONObject.firstEventDateComponent(type: String, component: String): Int? {
    val events = optJSONArray("events") ?: return null
    for (index in 0 until events.length()) {
        val event = events.getJSONObject(index)
        if (event.optString("type") == type) {
            val date = event.optJSONObject("date") ?: return null
            return date.optionalInt(component)
        }
    }
    return null
}

private fun JSONObject.firstContactGroupName(): String? {
    val memberships = optJSONArray("memberships") ?: return null
    for (index in 0 until memberships.length()) {
        val membership = memberships.getJSONObject(index)
        val contactGroupMembership = membership.optJSONObject("contactGroupMembership")
        val resourceName = contactGroupMembership?.optString("contactGroupResourceName", "").orEmpty()
        if (resourceName.isNotEmpty()) {
            return resourceName.removePrefix("contactGroups/")
        }
    }
    return null
}

private fun JSONObject.relationsJson(): String {
    val relations = optJSONArray("relations")
    if (relations == null || relations.length() == 0) return "[]"

    val mappedRelations = JSONArray()
    for (index in 0 until relations.length()) {
        val relation = relations.getJSONObject(index)
        val personName = relation.optString("person", "")
        val relationType = relation.optString("type", "")
        if (personName.isNotEmpty()) {
            mappedRelations.put(
                JSONObject().apply {
                    put("person", personName)
                    put("type", relationType)
                },
            )
        }
    }
    return if (mappedRelations.length() > 0) mappedRelations.toString() else "[]"
}

private fun JSONObject.optionalInt(fieldName: String): Int? {
    return if (has(fieldName)) getInt(fieldName) else null
}
