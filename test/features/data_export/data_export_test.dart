import 'dart:convert';

import 'package:ai_birthday/core/database/app_database.dart';
import 'package:ai_birthday/features/data_export/data_export.dart';
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  late AppDatabase db;
  setUp(() async {
    db = AppDatabase(NativeDatabase.memory());
    await db.customStatement(
      "INSERT INTO persons (id,name,birthday_month,birthday_day,relationship,relationship_closeness,preferred_language,preferred_tone,important_facts,preferred_delivery_channel,auto_send_policy,created_at,updated_at,version,deleted_at) VALUES ('p','Legacy',NULL,NULL,'future-value','close','en','warm','[]','share','manual',1,2,3,4)",
    );
    await db.customStatement(
      "INSERT INTO birthdays (id,person_id,cycle_year,date,status,created_at,updated_at) VALUES ('b','p',2026,'2026-03-01','future',1,2)",
    );
    await db.customStatement(
      "INSERT INTO message_drafts (id,birthday_id,person_id,body,tone,length,status,provider_type,created_at,updated_at) VALUES ('d','b','p','hello','warm','standard','draft','local',1,2)",
    );
    await db.customStatement(
      "INSERT INTO reminder_settings_entries (key,enabled,kinds,quiet_hours_start_minutes,quiet_hours_end_minutes) VALUES ('default',1,'[]',22,7)",
    );
    await db.customStatement(
      "INSERT INTO delivery_events (id,birthday_id,channel,handed_off_at) VALUES ('e','b','share',1)",
    );
    await db.customStatement(
      "INSERT INTO jobs (id,type,status,subject_id,payload,created_at) VALUES ('j','cloud_sync','queued','account-secret','{\"token\":\"SECRET_SENTINEL\"}',1)",
    );
  });
  tearDown(() async => db.close());

  test(
    'all supported categories round-trip with accurate counts and legacy values',
    () async {
      final encoded = await LocalDataExporter(
        db,
      ).create(ExportCategory.values.toSet());
      final parsed = parseExport(encoded);
      expect(parseExport(jsonEncode(parsed)), parsed);
      final manifest = parsed['manifest'] as Map<String, dynamic>;
      expect(manifest['counts'], {
        for (final c in ExportCategory.values) c.name: 1,
      });
      expect(manifest['warnings'], exportWarnings);
      final person = (parsed['data']['people'] as List).single;
      expect(person['birthday_month'], isNull);
      expect(person['relationship'], 'future-value');
      expect(person['deleted_at'], 4);
      expect(parsed['data']['birthdays'][0]['date'], '2026-03-01');
      expect(parsed['data']['birthdays'][0]['deleted_at'], isNull);
      expect(parsed['data']['drafts'][0]['deleted_at'], isNull);
      expect(encoded, isNot(contains('SECRET_SENTINEL')));
      expect(encoded, isNot(contains('account-secret')));
      expect(encoded, isNot(contains('payload')));
    },
  );

  test('selection is exact and empty categories have zero counts', () async {
    await db.customStatement('DELETE FROM message_drafts');
    final result = parseExport(
      await LocalDataExporter(db).create({ExportCategory.drafts}),
    );
    expect(result['data'], {'drafts': []});
    expect(result['manifest']['counts'], {'drafts': 0});
    await expectLater(
      LocalDataExporter(db).create({}),
      throwsA(isA<DataExportException>()),
    );
  });

  test(
    'rejects malformed, future, unknown, mismatched and secret-bearing schemas',
    () async {
      final good = await LocalDataExporter(db).create({ExportCategory.people});
      for (final mutate in <void Function(Map<String, dynamic>)>[
        (v) => v['manifest']['version'] = 2,
        (v) => v['manifest']['version'] = 1.0,
        (v) => v['manifest']['counts']['people'] = 2,
        (v) => v['manifest']['categories'].add('jobs'),
        (v) => v['data']['people'][0]['api_key'] = 'secret',
        (v) => v['data']['people'][0]['version'] = '3',
        (v) => v['data']['people'][0].remove('id'),
        (v) => v['manifest']['warnings'] = [],
      ]) {
        final value = jsonDecode(good) as Map<String, dynamic>;
        mutate(value);
        expect(
          () => parseExport(jsonEncode(value)),
          throwsA(isA<DataExportException>()),
        );
      }
      expect(() => parseExport('{'), throwsA(isA<DataExportException>()));
    },
  );

  test('fails closed on unreviewed schema changes', () async {
    await db.customStatement(
      'ALTER TABLE persons ADD COLUMN secret_token TEXT',
    );
    await expectLater(
      LocalDataExporter(db).create({ExportCategory.people}),
      throwsA(isA<DataExportException>()),
    );
  });
}
