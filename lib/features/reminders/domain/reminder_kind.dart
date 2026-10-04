/// The notification cadence before a birthday (SSOT §17).
///
/// [daysBefore] is how many whole days ahead of the birthday the reminder is
/// delivered (0 = on the birthday itself).
enum ReminderKind {
  approaching('Approaching', 7, '7 days before'),
  prepare('Prepare message', 2, '2 days before'),
  ready('Message ready', 1, '1 day before'),
  birthday('Birthday today', 0, 'Today');

  const ReminderKind(this.title, this.daysBefore, this.when);

  final String title;
  final int daysBefore;
  final String when;
}
