import '../../birthdays/domain/birthday_engine.dart';
import '../../people/domain/person.dart';

/// One feed row: a recipient with its engine-resolved next occurrence.
class HomeItem {
  const HomeItem({required this.person, required this.next});

  final Person person;
  final NextBirthday next;

  /// Human countdown label: Today / Tomorrow / N days.
  String get countdownLabel {
    if (next.daysUntil <= 0) return 'Today';
    if (next.daysUntil == 1) return 'Tomorrow';
    return '${next.daysUntil} days';
  }

  String? get turnLabel {
    final year = next.year;
    final birthYear = person.birthYear;
    if (birthYear == null) return null;
    return 'turns ${year - birthYear}';
  }

  @override
  bool operator ==(Object other) =>
      other is HomeItem &&
      other.person.id == person.id &&
      other.next.year == next.year &&
      other.next.month == next.month &&
      other.next.day == next.day;

  @override
  int get hashCode => Object.hash(person.id, next.year, next.month, next.day);
}

/// Home command-center feed (SSOT §15).
class HomeFeed {
  const HomeFeed({required this.today, required this.upcoming});

  /// People whose birthday is [DateTime] today.
  final List<HomeItem> today;

  /// People with a birthday in the next 30 days, nearest first.
  final List<HomeItem> upcoming;

  /// Birthdays needing a message within the next 7 days (message status in
  /// Message Studio; this represents the approach window today).
  int get actionNeededCount =>
      today.length + upcoming.where((i) => i.next.daysUntil <= 7).length;

  bool get isEmpty => today.isEmpty && upcoming.isEmpty;
}

/// Composes the home feed from the live people list and the birthday engine.
class HomeFeedBuilder {
  const HomeFeedBuilder({this.engine = const BirthdayEngine()});

  final BirthdayEngine engine;

  /// Upcoming window; birthdays beyond this horizon are not shown.
  static const int upcomingHorizonDays = 30;

  HomeFeed build({required List<Person> people, DateTime? reference}) {
    final ref = reference ?? DateTime.now();
    final today = <HomeItem>[];
    final upcoming = <HomeItem>[];

    for (final person in people) {
      final next = engine.computeNext(
        month: person.birthdayMonth,
        day: person.birthdayDay,
        birthYear: person.birthYear,
        timezoneName: person.timezone,
        reference: ref,
      );
      if (next.isToday) {
        today.add(HomeItem(person: person, next: next));
      } else if (next.daysUntil <= upcomingHorizonDays) {
        upcoming.add(HomeItem(person: person, next: next));
      }
    }

    upcoming.sort((a, b) {
      final byDays = a.next.daysUntil.compareTo(b.next.daysUntil);
      return byDays != 0 ? byDays : a.person.name.compareTo(b.person.name);
    });
    today.sort((a, b) => a.person.name.compareTo(b.person.name));

    return HomeFeed(today: today, upcoming: upcoming);
  }
}
