package com.yashsomani.birthdayautopilot.people

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal class PeopleJsonParser(
  private val maxPagePeople: Int,
) {
  init {
    require(maxPagePeople in 1..1_000)
  }

  fun parse(bytes: ByteArray): PeoplePageParseResult {
    val root = try {
      val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
      val reader = JsonReader(
        InputStreamReader(ByteArrayInputStream(bytes), decoder),
      ).apply {
        setStrictness(Strictness.STRICT)
        setNestingLimit(MAX_JSON_NESTING)
      }
      val parsed = JsonParser.parseReader(reader).requireObject()
      if (reader.peek() != JsonToken.END_DOCUMENT) malformed()
      parsed
    } catch (_: Exception) {
      return PeoplePageParseResult.Failure(PeopleMalformedReason.INVALID_JSON)
    }
    return try {
      parsePage(root, bytes.size)
    } catch (_: PartialSourceMergeException) {
      PeoplePageParseResult.Failure(PeopleMalformedReason.PARTIAL_SOURCE_MERGE)
    } catch (_: MalformedPersonException) {
      PeoplePageParseResult.Failure(PeopleMalformedReason.MALFORMED_PERSON)
    } catch (_: RuntimeException) {
      PeoplePageParseResult.Failure(PeopleMalformedReason.INVALID_PAGE)
    }
  }

  private fun parsePage(root: JsonObject, byteCount: Int): PeoplePageParseResult {
    val connections = root.optionalArray("connections") ?: JsonArray()
    if (connections.size() > maxPagePeople) {
      return PeoplePageParseResult.Failure(PeopleMalformedReason.INVALID_PAGE)
    }
    val contacts = connections.map { parsePerson(it.requireObject()) }
    if (contacts.map(PeopleContactDelta::resourceName).toSet().size != contacts.size) {
      return PeoplePageParseResult.Failure(PeopleMalformedReason.DUPLICATE_PERSON)
    }
    val nextPageToken = root.optionalBoundedString("nextPageToken", 8_192)
    val nextSyncToken = root.optionalBoundedString("nextSyncToken", 8_192)
    if (nextPageToken != null && nextSyncToken != null) {
      return PeoplePageParseResult.Failure(PeopleMalformedReason.INVALID_PAGE)
    }
    val totalItems = root.optionalNonNegativeInt("totalItems")
    return PeoplePageParseResult.Success(
      PeoplePage(
        contacts = contacts,
        nextPageToken = nextPageToken,
        nextSyncToken = nextSyncToken,
        totalItems = totalItems,
        encodedBytes = byteCount,
      ),
    )
  }

  private fun parsePerson(person: JsonObject): PeopleContactDelta {
    val resourceName = person.requiredBoundedString("resourceName", 300)
    if (!resourceName.startsWith("people/") || resourceName.length <= "people/".length) malformed()
    val metadata = person.requiredObject("metadata")
    val deleted = metadata.optionalBoolean("deleted") ?: false
    val sources = metadata.requiredArray("sources")
    if (sources.size() != 1) partialSourceMerge()
    val source = sources.single().requireObject()
    if (source.requiredBoundedString("type", 64) != "CONTACT") partialSourceMerge()
    val contactSourceId = source.requiredBoundedString("id", 300)
    if (!validOpaque(contactSourceId)) malformed()

    val names = parseNames(person.optionalArray("names"), contactSourceId)
    val birthdays = parseBirthdays(person.optionalArray("birthdays"), contactSourceId)
    val phones = parsePhones(person.optionalArray("phoneNumbers"), contactSourceId)
    if (deleted && (names.isNotEmpty() || birthdays.isNotEmpty() || phones.isNotEmpty())) malformed()
    return PeopleContactDelta(
      resourceName = resourceName,
      contactSourceId = contactSourceId,
      deleted = deleted,
      names = names,
      birthdays = birthdays,
      phoneNumbers = phones,
    )
  }

  private fun parseNames(array: JsonArray?, contactSourceId: String): List<PeopleName> {
    val values = array ?: return emptyList()
    if (values.size() > 16) malformed()
    return values.map { element ->
      val name = element.requireObject()
      requireContactFieldSource(name, contactSourceId)
      PeopleName(
        displayName = name.optionalPrivateString("displayName", 1_024),
        givenName = name.optionalPrivateString("givenName", 512),
      )
    }
  }

  private fun parseBirthdays(array: JsonArray?, contactSourceId: String): List<PeopleBirthday> {
    val values = array ?: return emptyList()
    if (values.size() > 16) malformed()
    return values.map { element ->
      val birthday = element.requireObject()
      requireContactFieldSource(birthday, contactSourceId)
      val date = birthday.optionalObject("date")
      val year = date?.optionalInt("year")
      val month = date?.optionalInt("month")
      val day = date?.optionalInt("day")
      if (year != null && year !in 1..9_999) malformed()
      if (month != null && month !in 1..12) malformed()
      if (day != null && day !in 1..31) malformed()
      PeopleBirthday(year, month, day)
    }
  }

  private fun parsePhones(array: JsonArray?, contactSourceId: String): List<PeoplePhone> {
    val values = array ?: return emptyList()
    if (values.size() > 100) malformed()
    return values.map { element ->
      val phone = element.requireObject()
      requireContactFieldSource(phone, contactSourceId)
      PeoplePhone(
        value = phone.requiredPrivateString("value", 512),
        type = phone.optionalPrivateString("type", 128),
      )
    }
  }

  private fun requireContactFieldSource(field: JsonObject, expectedSourceId: String) {
    val source = field.requiredObject("metadata").requiredObject("source")
    if (
      source.requiredBoundedString("type", 64) != "CONTACT" ||
      source.requiredBoundedString("id", 300) != expectedSourceId
    ) {
      partialSourceMerge()
    }
  }

  private fun validOpaque(value: String): Boolean =
    value.isNotBlank() && value.none { it.isISOControl() || it.isWhitespace() }

  private fun JsonElement.requireObject(): JsonObject =
    takeIf { it.isJsonObject }?.asJsonObject ?: malformed()

  private fun JsonObject.requiredObject(name: String): JsonObject =
    get(name)?.takeUnless(JsonElement::isJsonNull)?.requireObject() ?: malformed()

  private fun JsonObject.optionalObject(name: String): JsonObject? =
    get(name)?.takeUnless(JsonElement::isJsonNull)?.requireObject()

  private fun JsonObject.requiredArray(name: String): JsonArray =
    get(name)?.takeUnless(JsonElement::isJsonNull)?.takeIf(JsonElement::isJsonArray)?.asJsonArray ?: malformed()

  private fun JsonObject.optionalArray(name: String): JsonArray? =
    get(name)?.takeUnless(JsonElement::isJsonNull)?.let {
      it.takeIf(JsonElement::isJsonArray)?.asJsonArray ?: malformed()
    }

  private fun JsonObject.requiredBoundedString(name: String, maxLength: Int): String =
    optionalBoundedString(name, maxLength) ?: malformed()

  private fun JsonObject.optionalBoundedString(name: String, maxLength: Int): String? =
    optionalString(name, maxLength, privateValue = false)

  private fun JsonObject.requiredPrivateString(name: String, maxLength: Int): String =
    optionalString(name, maxLength, privateValue = true) ?: malformed()

  private fun JsonObject.optionalPrivateString(name: String, maxLength: Int): String? =
    optionalString(name, maxLength, privateValue = true)

  private fun JsonObject.optionalString(
    name: String,
    maxLength: Int,
    privateValue: Boolean,
  ): String? {
    val element = get(name)?.takeUnless(JsonElement::isJsonNull) ?: return null
    if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) malformed()
    val value = element.asString
    if (
      value.isBlank() ||
      value.length > maxLength ||
      value.any { it.isISOControl() || it in UNSAFE_UNICODE_CONTROLS }
    ) {
      malformed()
    }
    if (!privateValue && value.any(Char::isWhitespace)) malformed()
    return value
  }

  private fun JsonObject.optionalBoolean(name: String): Boolean? {
    val element = get(name)?.takeUnless(JsonElement::isJsonNull) ?: return null
    if (!element.isJsonPrimitive || !element.asJsonPrimitive.isBoolean) malformed()
    return element.asBoolean
  }

  private fun JsonObject.optionalInt(name: String): Int? {
    val element = get(name)?.takeUnless(JsonElement::isJsonNull) ?: return null
    if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) malformed()
    val asString = element.asString
    if (!INTEGER_PATTERN.matches(asString)) malformed()
    return asString.toIntOrNull() ?: malformed()
  }

  private fun JsonObject.optionalNonNegativeInt(name: String): Int? =
    optionalInt(name)?.also { if (it < 0) malformed() }

  private class MalformedPersonException : RuntimeException() {
    override val message: String? = null
  }

  private class PartialSourceMergeException : RuntimeException() {
    override val message: String? = null
  }

  private companion object {
    val INTEGER_PATTERN = Regex("^-?[0-9]+$")
    const val MAX_JSON_NESTING = 64
    val UNSAFE_UNICODE_CONTROLS = setOf(
      '\u061C',
      '\u200E',
      '\u200F',
      '\u202A',
      '\u202B',
      '\u202C',
      '\u202D',
      '\u202E',
      '\u2066',
      '\u2067',
      '\u2068',
      '\u2069',
    )

    fun malformed(): Nothing = throw MalformedPersonException()

    fun partialSourceMerge(): Nothing = throw PartialSourceMergeException()
  }
}
