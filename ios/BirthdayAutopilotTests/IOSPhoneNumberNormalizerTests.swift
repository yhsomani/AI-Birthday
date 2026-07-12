import XCTest
@testable import BirthdayAutopilot

final class IOSPhoneNumberNormalizerTests: XCTestCase {
  private let normalizer = IOSPhoneNumberNormalizer.shared

  func testExactMetadataNormalizesInternationalAndRegionalMobileNumbers() throws {
    let international = try accepted(
      normalizer.normalize("+91 98765 43210", homeRegion: nil)
    )
    let regional = try accepted(
      normalizer.normalize("98765 43210", homeRegion: "in")
    )

    XCTAssertEqual(international.e164, "+919876543210")
    XCTAssertEqual(regional.e164, international.e164)
    XCTAssertEqual(international.kind, .mobile)
    XCTAssertEqual(international.maskedDisplay, "•••• 3210")
    XCTAssertEqual(
      IOSPhoneNumberNormalizer.metadataRelease,
      "libPhoneNumber-iOS-1.7.3"
    )
  }

  func testPremiumAndNonSMSCapableMetadataNeverBecomeCandidates() throws {
    XCTAssertEqual(
      try rejected(normalizer.normalize("+44 901 234 5678", homeRegion: nil)).issue,
      .premiumRate
    )
    XCTAssertEqual(
      try rejected(normalizer.normalize("+1 800 234 5678", homeRegion: nil)).issue,
      .notSMSCapable
    )
  }

  func testEmergencyAndShortNumberMetadataFailClosed() throws {
    XCTAssertEqual(
      try rejected(normalizer.normalize("112", homeRegion: "IN")).issue,
      .emergencyNumber
    )
    XCTAssertEqual(
      try rejected(normalizer.normalize("+1 911", homeRegion: nil)).issue,
      .emergencyNumber
    )
    XCTAssertEqual(
      try rejected(normalizer.normalize("611", homeRegion: "US")).issue,
      .shortCode
    )
  }

  func testExtensionsAndAmbiguousRegionalInputStayBlocked() throws {
    XCTAssertEqual(
      try rejected(
        normalizer.normalize("+91 98765 43210 ext. 42", homeRegion: nil)
      ).issue,
      .extensionNotSupported
    )
    XCTAssertEqual(
      try rejected(normalizer.normalize("ext. 42", homeRegion: nil)).issue,
      .extensionNotSupported
    )
    XCTAssertEqual(
      try rejected(normalizer.normalize("98765 43210", homeRegion: nil)).issue,
      .regionRequired
    )
    XCTAssertEqual(
      try rejected(normalizer.normalize("98765 43210", homeRegion: "ZZ")).issue,
      .regionInvalid
    )
    XCTAssertEqual(
      try rejected(
        normalizer.normalize(String(repeating: "9", count: 500), homeRegion: "IN")
      ).issue,
      .malformed
    )
  }

  func testSafetyPolicyOrdersEveryAbsoluteBlockerBeforeValidityAndType() {
    let base = IOSPhoneSafetyEvidence(
      extensionPresent: false,
      emergency: false,
      shortCode: false,
      premiumRate: false,
      possible: true,
      valid: true,
      kind: .mobile
    )
    XCTAssertNil(IOSPhoneSafetyPolicy.rejection(for: base))
    XCTAssertEqual(
      IOSPhoneSafetyPolicy.rejection(for: replacing(base, extensionPresent: true)),
      .extensionNotSupported
    )
    XCTAssertEqual(
      IOSPhoneSafetyPolicy.rejection(for: replacing(base, emergency: true)),
      .emergencyNumber
    )
    XCTAssertEqual(
      IOSPhoneSafetyPolicy.rejection(for: replacing(base, shortCode: true)),
      .shortCode
    )
    XCTAssertEqual(
      IOSPhoneSafetyPolicy.rejection(for: replacing(base, premiumRate: true)),
      .premiumRate
    )
    XCTAssertEqual(
      IOSPhoneSafetyPolicy.rejection(for: replacing(base, possible: false)),
      .notValid
    )
    XCTAssertEqual(
      IOSPhoneSafetyPolicy.rejection(for: replacing(base, kind: .fixedLine)),
      .notSMSCapable
    )
  }

  func testDescriptionsNeverExposeCanonicalOrRawNumbers() throws {
    let raw = "+919876543210"
    let result = normalizer.normalize(raw, homeRegion: nil)
    let value = try accepted(result)
    XCTAssertFalse(result.description.contains(raw))
    XCTAssertFalse(value.description.contains(raw))
  }

  private func accepted(
    _ result: IOSPhoneNormalizationResult,
    file: StaticString = #filePath,
    line: UInt = #line
  ) throws -> IOSNormalizedPhoneNumber {
    guard case .accepted(let value) = result else {
      XCTFail("Expected an accepted redacted phone result", file: file, line: line)
      throw TestFailure.unexpectedResult
    }
    return value
  }

  private func rejected(
    _ result: IOSPhoneNormalizationResult,
    file: StaticString = #filePath,
    line: UInt = #line
  ) throws -> IOSRejectedPhoneNumber {
    guard case .rejected(let value) = result else {
      XCTFail("Expected a rejected redacted phone result", file: file, line: line)
      throw TestFailure.unexpectedResult
    }
    return value
  }

  private func replacing(
    _ value: IOSPhoneSafetyEvidence,
    extensionPresent: Bool? = nil,
    emergency: Bool? = nil,
    shortCode: Bool? = nil,
    premiumRate: Bool? = nil,
    possible: Bool? = nil,
    valid: Bool? = nil,
    kind: IOSPhoneNumberKind? = nil
  ) -> IOSPhoneSafetyEvidence {
    IOSPhoneSafetyEvidence(
      extensionPresent: extensionPresent ?? value.extensionPresent,
      emergency: emergency ?? value.emergency,
      shortCode: shortCode ?? value.shortCode,
      premiumRate: premiumRate ?? value.premiumRate,
      possible: possible ?? value.possible,
      valid: valid ?? value.valid,
      kind: kind ?? value.kind
    )
  }

  private enum TestFailure: Error {
    case unexpectedResult
  }
}
