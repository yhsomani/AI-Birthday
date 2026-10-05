import 'package:flutter_test/flutter_test.dart';

import 'tier1_features_test.dart' as tier1;
import 'tier2_boundary_corner_test.dart' as tier2;
import 'tier3_cross_feature_test.dart' as tier3;
import 'tier4_user_journeys_test.dart' as tier4;

/// Master composite E2E requirement test suite for AI-Birthday.
/// Executes Tiers 1 through 4 in sequence.
void main() {
  group('AI-Birthday E2E Requirement Test Suite (Tiers 1-4)', () {
    tier1.main();
    tier2.main();
    tier3.main();
    tier4.main();
  });
}
