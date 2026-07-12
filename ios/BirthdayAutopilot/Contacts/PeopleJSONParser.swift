import CoreFoundation
import Foundation

final class IOSPeopleJSONParser {
  private let maximumPagePeople: Int

  init(maximumPagePeople: Int) {
    precondition((1...1_000).contains(maximumPagePeople))
    self.maximumPagePeople = maximumPagePeople
  }

  func parse(_ data: Data) -> Result<IOSPeoplePage, IOSPeoplePageParseError> {
    guard !data.isEmpty else { return .failure(.invalidJSON) }
    do {
      let value = try JSONSerialization.jsonObject(with: data, options: [])
      guard Self.hasBoundedShape(value, depth: 0, remainingNodes: 200_000),
        let root = value as? [String: Any]
      else {
        return .failure(.invalidJSON)
      }
      return .success(try parsePage(root, encodedBytes: data.count))
    } catch let error as IOSPeoplePageParseError {
      return .failure(error)
    } catch {
      return .failure(.invalidJSON)
    }
  }

  private func parsePage(
    _ root: [String: Any],
    encodedBytes: Int
  ) throws -> IOSPeoplePage {
    let rawConnections = try optionalArray(root, "connections") ?? []
    guard rawConnections.count <= maximumPagePeople else {
      throw IOSPeoplePageParseError.invalidPage
    }
    let contacts = try rawConnections.map { value -> IOSPeopleContactDelta in
      guard let object = value as? [String: Any] else {
        throw IOSPeoplePageParseError.malformedPerson
      }
      return try parsePerson(object)
    }
    guard Set(contacts.map(\.resourceName)).count == contacts.count else {
      throw IOSPeoplePageParseError.duplicatePerson
    }

    return IOSPeoplePage(
      contacts: contacts,
      nextPageToken: try optionalToken(root, "nextPageToken"),
      nextSyncToken: try optionalToken(root, "nextSyncToken"),
      totalItems: try optionalNonnegativeInteger(root, "totalItems"),
      encodedBytes: encodedBytes
    )
  }

  private func parsePerson(_ person: [String: Any]) throws -> IOSPeopleContactDelta {
    let resourceName = try requiredProviderIdentifier(person, "resourceName", maximumBytes: 300)
    guard resourceName.hasPrefix("people/"), resourceName.count > "people/".count else {
      throw IOSPeoplePageParseError.malformedPerson
    }
    let previousNames = try (optionalArray(person, "previousResourceNames") ?? []).map {
      guard let value = $0 as? String,
        IOSPeopleValuePolicy.providerIdentifier(value, maximumBytes: 300),
        value.hasPrefix("people/")
      else {
        throw IOSPeoplePageParseError.malformedPerson
      }
      return value
    }
    guard previousNames.count <= 32,
      Set(previousNames).count == previousNames.count,
      !previousNames.contains(resourceName)
    else {
      throw IOSPeoplePageParseError.malformedPerson
    }

    let metadata = try requiredObject(person, "metadata", personError: true)
    let deleted = try optionalBoolean(metadata, "deleted") ?? false
    let sources = try requiredArray(metadata, "sources", personError: true)
    guard sources.count == 1, let source = sources.first as? [String: Any],
      try requiredProviderIdentifier(source, "type", maximumBytes: 64) == "CONTACT"
    else {
      throw IOSPeoplePageParseError.partialSourceMerge
    }
    let sourceId = try requiredProviderIdentifier(source, "id", maximumBytes: 300)

    let names = try parseNames(person, sourceId: sourceId)
    let birthdays = try parseBirthdays(person, sourceId: sourceId)
    let phones = try parsePhones(person, sourceId: sourceId)
    guard !deleted || (names.isEmpty && birthdays.isEmpty && phones.isEmpty) else {
      throw IOSPeoplePageParseError.malformedPerson
    }
    return IOSPeopleContactDelta(
      resourceName: resourceName,
      previousResourceNames: previousNames,
      contactSourceId: sourceId,
      deleted: deleted,
      names: names,
      birthdays: birthdays,
      phoneNumbers: phones
    )
  }

  private func parseNames(
    _ person: [String: Any],
    sourceId: String
  ) throws -> [IOSPeopleName] {
    let values = try optionalArray(person, "names") ?? []
    guard values.count <= 16 else { throw IOSPeoplePageParseError.malformedPerson }
    return try values.map { value in
      guard let object = value as? [String: Any] else {
        throw IOSPeoplePageParseError.malformedPerson
      }
      try requireFieldSource(object, sourceId: sourceId)
      return IOSPeopleName(
        displayName: try optionalPrivateString(object, "displayName", maximumCharacters: 1_024),
        givenName: try optionalPrivateString(object, "givenName", maximumCharacters: 512)
      )
    }
  }

  private func parseBirthdays(
    _ person: [String: Any],
    sourceId: String
  ) throws -> [IOSPeopleBirthday] {
    let values = try optionalArray(person, "birthdays") ?? []
    guard values.count <= 16 else { throw IOSPeoplePageParseError.malformedPerson }
    return try values.map { value in
      guard let object = value as? [String: Any] else {
        throw IOSPeoplePageParseError.malformedPerson
      }
      try requireFieldSource(object, sourceId: sourceId)
      guard let date = try optionalObject(object, "date") else {
        return IOSPeopleBirthday(year: nil, month: nil, day: nil)
      }
      let year = try optionalInteger(date, "year")
      let month = try optionalInteger(date, "month")
      let day = try optionalInteger(date, "day")
      guard year.map({ (1...9_999).contains($0) }) ?? true,
        month.map({ (1...12).contains($0) }) ?? true,
        day.map({ (1...31).contains($0) }) ?? true,
        Self.validCivilDate(year: year, month: month, day: day)
      else {
        throw IOSPeoplePageParseError.malformedPerson
      }
      return IOSPeopleBirthday(year: year, month: month, day: day)
    }
  }

  private func parsePhones(
    _ person: [String: Any],
    sourceId: String
  ) throws -> [IOSPeoplePhone] {
    let values = try optionalArray(person, "phoneNumbers") ?? []
    guard values.count <= 100 else { throw IOSPeoplePageParseError.malformedPerson }
    return try values.map { value in
      guard let object = value as? [String: Any] else {
        throw IOSPeoplePageParseError.malformedPerson
      }
      try requireFieldSource(object, sourceId: sourceId)
      guard let rawValue = try optionalPrivateString(
        object,
        "value",
        maximumCharacters: 512
      ) else {
        throw IOSPeoplePageParseError.malformedPerson
      }
      return IOSPeoplePhone(
        value: rawValue,
        type: try optionalPrivateString(object, "type", maximumCharacters: 128)
      )
    }
  }

  private func requireFieldSource(
    _ field: [String: Any],
    sourceId: String
  ) throws {
    let metadata = try requiredObject(field, "metadata", personError: true)
    let source = try requiredObject(metadata, "source", personError: true)
    guard try requiredProviderIdentifier(source, "type", maximumBytes: 64) == "CONTACT",
      try requiredProviderIdentifier(source, "id", maximumBytes: 300) == sourceId
    else {
      throw IOSPeoplePageParseError.partialSourceMerge
    }
  }

  private func requiredObject(
    _ object: [String: Any],
    _ key: String,
    personError: Bool
  ) throws -> [String: Any] {
    guard let value = object[key] as? [String: Any] else {
      if personError { throw IOSPeoplePageParseError.malformedPerson }
      throw IOSPeoplePageParseError.invalidPage
    }
    return value
  }

  private func optionalObject(
    _ object: [String: Any],
    _ key: String
  ) throws -> [String: Any]? {
    guard let raw = object[key], !(raw is NSNull) else { return nil }
    guard let value = raw as? [String: Any] else {
      throw IOSPeoplePageParseError.malformedPerson
    }
    return value
  }

  private func requiredArray(
    _ object: [String: Any],
    _ key: String,
    personError: Bool
  ) throws -> [Any] {
    guard let value = object[key] as? [Any] else {
      if personError { throw IOSPeoplePageParseError.malformedPerson }
      throw IOSPeoplePageParseError.invalidPage
    }
    return value
  }

  private func optionalArray(
    _ object: [String: Any],
    _ key: String
  ) throws -> [Any]? {
    guard let raw = object[key], !(raw is NSNull) else { return nil }
    guard let value = raw as? [Any] else { throw IOSPeoplePageParseError.invalidPage }
    return value
  }

  private func requiredProviderIdentifier(
    _ object: [String: Any],
    _ key: String,
    maximumBytes: Int
  ) throws -> String {
    guard let value = object[key] as? String,
      IOSPeopleValuePolicy.providerIdentifier(value, maximumBytes: maximumBytes)
    else {
      throw IOSPeoplePageParseError.malformedPerson
    }
    return value
  }

  private func optionalPrivateString(
    _ object: [String: Any],
    _ key: String,
    maximumCharacters: Int
  ) throws -> String? {
    guard let raw = object[key], !(raw is NSNull) else { return nil }
    guard let value = raw as? String,
      let safe = IOSPeopleValuePolicy.privateText(value, maximumCharacters: maximumCharacters)
    else {
      throw IOSPeoplePageParseError.malformedPerson
    }
    return safe
  }

  private func optionalToken(_ object: [String: Any], _ key: String) throws -> String? {
    guard let raw = object[key], !(raw is NSNull) else { return nil }
    guard let value = raw as? String,
      let token = IOSPeopleValuePolicy.token(value)
    else {
      throw IOSPeoplePageParseError.invalidPage
    }
    return token
  }

  private func optionalBoolean(_ object: [String: Any], _ key: String) throws -> Bool? {
    guard let raw = object[key], !(raw is NSNull) else { return nil }
    guard let number = raw as? NSNumber,
      CFGetTypeID(number) == CFBooleanGetTypeID()
    else {
      throw IOSPeoplePageParseError.malformedPerson
    }
    return number.boolValue
  }

  private func optionalInteger(_ object: [String: Any], _ key: String) throws -> Int? {
    guard let raw = object[key], !(raw is NSNull) else { return nil }
    guard let number = raw as? NSNumber,
      CFGetTypeID(number) != CFBooleanGetTypeID(),
      number.doubleValue.isFinite,
      number.doubleValue.rounded(.towardZero) == number.doubleValue,
      number.doubleValue >= Double(Int.min),
      number.doubleValue <= Double(Int.max)
    else {
      throw IOSPeoplePageParseError.malformedPerson
    }
    return number.intValue
  }

  private func optionalNonnegativeInteger(
    _ object: [String: Any],
    _ key: String
  ) throws -> Int? {
    guard let value = try optionalInteger(object, key), value >= 0 else {
      if object[key] == nil || object[key] is NSNull { return nil }
      throw IOSPeoplePageParseError.invalidPage
    }
    return value
  }

  private static func validCivilDate(year: Int?, month: Int?, day: Int?) -> Bool {
    guard let month, let day else { return true }
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(secondsFromGMT: 0)!
    let referenceYear = year ?? 2_000
    let components = DateComponents(
      calendar: calendar,
      timeZone: calendar.timeZone,
      year: referenceYear,
      month: month,
      day: day
    )
    guard let date = calendar.date(from: components) else { return false }
    let roundTrip = calendar.dateComponents([.year, .month, .day], from: date)
    return roundTrip.year == referenceYear && roundTrip.month == month && roundTrip.day == day
  }

  private static func hasBoundedShape(
    _ value: Any,
    depth: Int,
    remainingNodes: Int
  ) -> Bool {
    guard depth <= 64, remainingNodes > 0 else { return false }
    if let array = value as? [Any] {
      guard array.count <= remainingNodes else { return false }
      var budget = remainingNodes - array.count
      for child in array {
        guard hasBoundedShape(child, depth: depth + 1, remainingNodes: budget) else {
          return false
        }
        budget -= 1
      }
      return true
    }
    if let dictionary = value as? [String: Any] {
      guard dictionary.count <= remainingNodes else { return false }
      var budget = remainingNodes - dictionary.count
      for (key, child) in dictionary {
        guard key.utf8.count <= 1_024,
          hasBoundedShape(child, depth: depth + 1, remainingNodes: budget)
        else {
          return false
        }
        budget -= 1
      }
      return true
    }
    return value is String || value is NSNumber || value is NSNull
  }
}

enum IOSPeopleAPIErrorParser {
  static func isExpiredSyncToken(_ data: Data) -> Bool {
    guard data.count <= 64 * 1_024,
      let root = try? JSONSerialization.jsonObject(with: data),
      let object = root as? [String: Any],
      let error = object["error"] as? [String: Any],
      let details = error["details"] as? [[String: Any]]
    else {
      return false
    }
    return details.contains { detail in
      (detail["@type"] as? String) == "type.googleapis.com/google.rpc.ErrorInfo" &&
        (detail["reason"] as? String) == "EXPIRED_SYNC_TOKEN"
    }
  }
}
