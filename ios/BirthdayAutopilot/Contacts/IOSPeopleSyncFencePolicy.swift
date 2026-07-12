import Foundation

/// Pure policy for the durable People-sync cancellation fence.
///
/// Every synchronization captures a newly persisted generation before issuing
/// an OAuth or network request. Privacy shutdown advances the generation. A
/// response may commit only while its exact captured generation is still the
/// current durable value, so a delayed response cannot resurrect erased data.
enum IOSPeopleSyncFencePolicy {
  static func freshGeneration() -> String {
    UUID().uuidString.lowercased()
  }

  static func isValidGeneration(_ value: String?) -> Bool {
    guard let value, let uuid = UUID(uuidString: value) else { return false }
    return uuid.uuidString.lowercased() == value
  }

  static func permitsCommit(
    capturedGeneration: String,
    durableGeneration: String?,
    exactAccountGenerationMatches: Bool
  ) -> Bool {
    exactAccountGenerationMatches
      && isValidGeneration(capturedGeneration)
      && capturedGeneration == durableGeneration
  }
}
