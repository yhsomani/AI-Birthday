import 'package:flutter/material.dart';

import '../../../shared/design_system/empty_state.dart';

/// History — message/delivery activity timeline.
class HistoryScreen extends StatelessWidget {
  const HistoryScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('History')),
      body: const EmptyState(
        icon: Icons.history_outlined,
        title: 'No activity yet',
        message: 'Prepared and sent messages will appear here.',
      ),
    );
  }
}
