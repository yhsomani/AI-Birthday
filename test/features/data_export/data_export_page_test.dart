import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:ai_birthday/core/database/app_database.dart';
import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/features/data_export/data_export.dart';
import 'package:ai_birthday/features/data_export/data_export_page.dart';
import 'package:drift/native.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

class FakePicker extends FilePicker {
  Uint8List? bytes;
  Object? failure;
  String? result = '/selected/export.json';

  @override
  Future<String?> saveFile({
    String? dialogTitle,
    String? fileName,
    String? initialDirectory,
    FileType type = FileType.any,
    List<String>? allowedExtensions,
    Uint8List? bytes,
    bool lockParentWindow = false,
  }) async {
    this.bytes = bytes;
    if (failure != null) throw failure!;
    return result;
  }
}

class FailingDatabase extends AppDatabase {
  FailingDatabase() : super(NativeDatabase.memory());

  @override
  Future<T> transaction<T>(
    Future<T> Function() action, {
    bool requireNew = false,
  }) async => throw SqliteException(1, 'sensitive database details');
}

void main() {
  testWidgets(
    'requires selection and confirmation, saves only selected content',
    (tester) async {
      final db = AppDatabase(NativeDatabase.memory());
      addTearDown(db.close);
      final picker = FakePicker();
      tester.view.physicalSize = const Size(1000, 1800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(
        MaterialApp(
          home: DataExportPage(database: db, filePicker: picker),
        ),
      );
      expect(
        tester
            .widget<FilledButton>(
              find.widgetWithText(FilledButton, 'Prepare export'),
            )
            .onPressed,
        isNull,
      );
      await tester.tap(find.text('People (including deleted records)'));
      await tester.pump();
      await tester.tap(find.text('Prepare export'));
      await tester.runAsync(() async {
        await Future<void>.delayed(const Duration(milliseconds: 100));
      });
      await tester.pumpAndSettle();
      expect(find.text('people: 0'), findsOneWidget);
      expect(picker.bytes, isNull);
      await tester.tap(find.text('Choose destination'));
      await tester.pumpAndSettle();
      expect(parseExport(utf8.decode(picker.bytes!))['data'], {'people': []});
      expect(find.textContaining('Verify the file'), findsOneWidget);
    },
  );

  for (final failure in [
    PlatformException(code: 'save', message: 'sensitive exception details'),
    MissingPluginException('sensitive exception details'),
    const FileSystemException('sensitive exception details'),
  ]) {
    testWidgets('${failure.runtimeType} picker errors are sanitized', (
      tester,
    ) async {
      final db = AppDatabase(NativeDatabase.memory());
      addTearDown(db.close);
      final picker = FakePicker()..failure = failure;
      final logger = RecordingLogger();
      tester.view.physicalSize = const Size(1000, 1800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(
        MaterialApp(
          home: DataExportPage(
            database: db,
            filePicker: picker,
            logger: logger,
          ),
        ),
      );
      await tester.tap(find.text('Message drafts'));
      await tester.pump();
      await tester.tap(find.text('Prepare export'));
      await tester.runAsync(() async {
        await Future<void>.delayed(const Duration(milliseconds: 100));
      });
      await tester.pumpAndSettle();
      await tester.tap(find.text('Choose destination'));
      await tester.pumpAndSettle();
      expect(
        find.textContaining('The file could not be saved'),
        findsOneWidget,
      );
      expect(find.textContaining('sensitive exception details'), findsNothing);
      expect(logger.records.single.errorCategory, 'save');
      expect(logger.records.single.error, isNull);
      expect(logger.records.single.stackTrace, isNull);
    });
  }

  testWidgets('a null picker result does not claim definite cancellation', (
    tester,
  ) async {
    final db = AppDatabase(NativeDatabase.memory());
    addTearDown(db.close);
    final picker = FakePicker()..result = null;
    tester.view.physicalSize = const Size(1000, 1800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      MaterialApp(
        home: DataExportPage(database: db, filePicker: picker),
      ),
    );
    await tester.tap(find.text('Message drafts'));
    await tester.pump();
    await tester.tap(find.text('Prepare export'));
    await tester.runAsync(() async {
      await Future<void>.delayed(const Duration(milliseconds: 100));
    });
    await tester.pumpAndSettle();
    await tester.tap(find.text('Choose destination'));
    await tester.pumpAndSettle();
    expect(
      find.textContaining('Saving may have been cancelled'),
      findsOneWidget,
    );
    expect(picker.bytes, isNotNull);
  });

  testWidgets('schema validation fails visibly without invoking picker', (
    tester,
  ) async {
    final db = AppDatabase(NativeDatabase.memory());
    addTearDown(db.close);
    await tester.runAsync(() async {
      await db.customStatement('ALTER TABLE persons ADD COLUMN secret TEXT');
    });
    final picker = FakePicker();
    final logger = RecordingLogger();
    tester.view.physicalSize = const Size(1000, 1800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      MaterialApp(
        home: DataExportPage(database: db, filePicker: picker, logger: logger),
      ),
    );
    await tester.tap(find.text('People (including deleted records)'));
    await tester.pump();
    await tester.tap(find.text('Prepare export'));
    await tester.runAsync(() async {
      await Future<void>.delayed(const Duration(milliseconds: 100));
    });
    await tester.pumpAndSettle();
    expect(find.textContaining('Export could not be prepared'), findsOneWidget);
    expect(logger.records.single.errorCategory, 'export_validation');
    expect(picker.bytes, isNull);
  });

  testWidgets('unexpected errors are not swallowed', (tester) async {
    final db = AppDatabase(NativeDatabase.memory());
    addTearDown(db.close);
    final picker = FakePicker()..failure = StateError('unexpected');
    tester.view.physicalSize = const Size(1000, 1800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      MaterialApp(
        home: DataExportPage(database: db, filePicker: picker),
      ),
    );
    await tester.tap(find.text('Message drafts'));
    await tester.pump();
    Object? reported;
    runZonedGuarded(
      () => tester
          .widget<FilledButton>(
            find.widgetWithText(FilledButton, 'Prepare export'),
          )
          .onPressed!(),
      (error, _) => reported = error,
    );
    await tester.runAsync(() async {
      await Future<void>.delayed(const Duration(milliseconds: 100));
    });
    await tester.pumpAndSettle();
    await tester.tap(find.text('Choose destination'));
    await tester.pumpAndSettle();
    expect(reported, isA<StateError>());
    expect(find.textContaining('The file could not be saved'), findsNothing);
  });

  testWidgets('database errors are sanitized', (tester) async {
    final db = FailingDatabase();
    addTearDown(db.close);
    final picker = FakePicker();
    final logger = RecordingLogger();
    tester.view.physicalSize = const Size(1000, 1800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      MaterialApp(
        home: DataExportPage(database: db, filePicker: picker, logger: logger),
      ),
    );
    await tester.tap(find.text('Message drafts'));
    await tester.pump();
    await tester.tap(find.text('Prepare export'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Local data could not be read'), findsOneWidget);
    expect(find.textContaining('sensitive database details'), findsNothing);
    expect(logger.records.single.errorCategory, 'database');
    expect(logger.records.single.error, isNull);
    expect(picker.bytes, isNull);
  });
}
