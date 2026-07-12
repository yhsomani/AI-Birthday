import Foundation

@main
enum PeopleParserContractTests {
  static func main() throws {
    let firstGeneration = IOSPeopleSyncFencePolicy.freshGeneration()
    let secondGeneration = IOSPeopleSyncFencePolicy.freshGeneration()
    guard IOSPeopleSyncFencePolicy.isValidGeneration(firstGeneration),
      IOSPeopleSyncFencePolicy.isValidGeneration(secondGeneration),
      firstGeneration != secondGeneration,
      IOSPeopleSyncFencePolicy.permitsCommit(
        capturedGeneration: firstGeneration,
        durableGeneration: firstGeneration,
        exactAccountGenerationMatches: true
      ),
      !IOSPeopleSyncFencePolicy.permitsCommit(
        capturedGeneration: firstGeneration,
        durableGeneration: secondGeneration,
        exactAccountGenerationMatches: true
      ),
      !IOSPeopleSyncFencePolicy.permitsCommit(
        capturedGeneration: firstGeneration,
        durableGeneration: firstGeneration,
        exactAccountGenerationMatches: false
      )
    else {
      fatalError("People sync cancellation fence accepted stale or wrong-account work")
    }

    let parser = IOSPeopleJSONParser(maximumPagePeople: 1_000)
    let source = ["type": "CONTACT", "id": "source-1"]
    let fieldMetadata = ["source": source]
    let person: [String: Any] = [
      "resourceName": "people/one",
      "metadata": ["sources": [source]],
      "names": [["displayName": "Example", "givenName": "Example", "metadata": fieldMetadata]],
      "birthdays": [["date": ["month": 7, "day": 12], "metadata": fieldMetadata]],
      "phoneNumbers": [["value": "+919999999999", "type": "mobile", "metadata": fieldMetadata]],
    ]
    let page: [String: Any] = [
      "connections": [person],
      "nextSyncToken": "sync-token",
      "totalItems": 1,
    ]
    let encoded = try JSONSerialization.data(withJSONObject: page, options: [.sortedKeys])
    guard case .success(let parsed) = parser.parse(encoded),
      parsed.contacts.count == 1,
      parsed.contacts[0].contactSourceId == "source-1",
      parsed.nextSyncToken == "sync-token"
    else {
      fatalError("valid People page rejected")
    }

    var duplicatePage = page
    duplicatePage["connections"] = [person, person]
    let duplicate = try JSONSerialization.data(withJSONObject: duplicatePage)
    guard parser.parse(duplicate) == .failure(.duplicatePerson) else {
      fatalError("duplicate resource was accepted")
    }

    var mixedPerson = person
    mixedPerson["phoneNumbers"] = [[
      "value": "+919999999999",
      "metadata": ["source": ["type": "PROFILE", "id": "profile-1"]],
    ]]
    let mixed = try JSONSerialization.data(withJSONObject: [
      "connections": [mixedPerson], "nextSyncToken": "sync-token",
    ])
    guard parser.parse(mixed) == .failure(.partialSourceMerge) else {
      fatalError("mixed-source field was accepted")
    }

    let expired = try JSONSerialization.data(withJSONObject: [
      "error": ["details": [[
        "@type": "type.googleapis.com/google.rpc.ErrorInfo",
        "reason": "EXPIRED_SYNC_TOKEN",
      ]]],
    ])
    guard IOSPeopleAPIErrorParser.isExpiredSyncToken(expired) else {
      fatalError("expired sync token was not recognized")
    }
  }
}
