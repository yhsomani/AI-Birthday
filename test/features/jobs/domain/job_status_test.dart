import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/features/jobs/domain/job.dart';

void main() {
  test('unknown job status is rejected rather than queued', () {
    expect(() => JobStatus.fromString('futureState'), throwsFormatException);
    expect(() => JobStatus.fromString(null), throwsFormatException);
  });

  test('retrying is active and eligible for future execution', () {
    expect(JobStatus.retrying.isActive, isTrue);
    expect(JobStatus.failed.isActive, isFalse);
  });
}
