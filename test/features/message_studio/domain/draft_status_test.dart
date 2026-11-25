import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/features/message_studio/domain/models/message_draft.dart';

void main() {
  test(
    'unknown draft status is rejected rather than silently treated as draft',
    () {
      expect(
        () => DraftStatus.fromString('futureState'),
        throwsFormatException,
      );
      expect(() => DraftStatus.fromString(null), throwsFormatException);
    },
  );
}
