import 'package:ai_birthday/features/dashboard/domain/home_feed.dart';
import 'package:ai_birthday/features/people/domain/person.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final reference = DateTime(2026, 3, 14);

  Person person({
    String id = 'p',
    String name = 'Ana',
    int month = 3,
    int day = 15,
    int? birthYear,
  }) {
    final now = DateTime.utc(2026, 1, 1, 8);
    return Person(
      id: id,
      name: name,
      birthdayMonth: month,
      birthdayDay: day,
      birthYear: birthYear,
      createdAt: now,
      updatedAt: now,
    );
  }

  test('partitions today birthdays and upcoming birthdays', () {
    final feed = const HomeFeedBuilder().build(
      people: [
        person(id: 'a', name: 'Ana', month: 3, day: 14),
        person(id: 'b', name: 'Bri', month: 3, day: 15),
        person(id: 'c', name: 'Ced', month: 3, day: 19),
      ],
      reference: reference,
    );

    expect(feed.today.map((i) => i.person.name), ['Ana']);
    expect(feed.upcoming.map((i) => i.person.name), ['Bri', 'Ced']);
  });

  test('sorts upcoming nearest first, then by name', () {
    final feed = const HomeFeedBuilder().build(
      people: [
        person(id: 'c', name: 'Zoe', month: 3, day: 17),
        person(id: 'a', name: 'Ana', month: 3, day: 16),
        person(id: 'b', name: 'Bri', month: 3, day: 16),
      ],
      reference: reference,
    );

    expect(feed.upcoming.map((i) => i.person.name), ['Ana', 'Bri', 'Zoe']);
  });

  test('excludes birthdays beyond the 30-day horizon', () {
    final feed = const HomeFeedBuilder().build(
      people: [person(id: 'far', name: 'Far', month: 4, day: 23)],
      reference: reference,
    );

    expect(feed.today, isEmpty);
    expect(feed.upcoming, isEmpty);
    expect(feed.isEmpty, isTrue);
  });

  test('actionNeededCount is today plus upcoming within 7 days', () {
    final feed = const HomeFeedBuilder().build(
      people: [
        person(id: 'a', name: 'Ana', month: 3, day: 14), // today
        person(id: 'b', name: 'Bri', month: 3, day: 15), // 1 day
        person(id: 'c', name: 'Ced', month: 3, day: 19), // 5 days
        person(id: 'd', name: 'Dot', month: 3, day: 25), // 11 days
      ],
      reference: reference,
    );

    expect(feed.actionNeededCount, 3);
  });

  test('leap-day birthdays resolve through the engine', () {
    final febReference = DateTime(2026, 2, 1);
    final feed = const HomeFeedBuilder().build(
      people: [person(id: 'l', name: 'Leap', month: 2, day: 29)],
      reference: febReference,
    );

    expect(feed.upcoming.single.countdownLabel, '27 days');
  });

  test('countdown and turn labels render plainly', () {
    final feed = const HomeFeedBuilder().build(
      people: [
        person(id: 'a', name: 'Ana', month: 3, day: 14, birthYear: 2000),
        person(id: 'b', name: 'Bri', month: 3, day: 15, birthYear: 2001),
      ],
      reference: reference,
    );

    final today = feed.today.single;
    expect(today.countdownLabel, 'Today');
    expect(today.turnLabel, 'turns 26');

    final next = feed.upcoming.single;
    expect(next.countdownLabel, 'Tomorrow');
    expect(next.turnLabel, 'turns 25');
  });

  test('excludes people without a birthday from both today and upcoming', () {
    final now = DateTime.utc(2026, 1, 1, 8);
    final noBdayPerson = Person(
      id: 'no-bday',
      name: 'No Birthday',
      createdAt: now,
      updatedAt: now,
    );

    final feed = const HomeFeedBuilder().build(
      people: [
        noBdayPerson,
        person(id: 'a', name: 'Ana', month: 3, day: 14),
      ],
      reference: reference,
    );

    expect(feed.today.map((i) => i.person.name), ['Ana']);
    expect(feed.upcoming, isEmpty);
  });
}
