import 'package:firebase_app_check/firebase_app_check.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:timezone/data/latest_all.dart' as tz_data;

import 'app/app.dart';
import 'core/config/firebase_config.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  tz_data.initializeTimeZones();
  try {
    await Firebase.initializeApp(
      options: const FirebaseOptions(
        apiKey: FirebaseConfig.webApiKey,
        appId: FirebaseConfig.androidAppId,
        messagingSenderId: FirebaseConfig.projectNumber,
        projectId: FirebaseConfig.projectId,
      ),
    );
    await FirebaseAppCheck.instance.activate(
      providerAndroid: const AndroidPlayIntegrityProvider(),
    );
    await FirebaseAppCheck.instance.setTokenAutoRefreshEnabled(true);
  } catch (_) {
    // Local people, birthdays, reminders and manual drafts must remain usable.
    // Cloud capabilities fail closed until Firebase/App Check is available.
  }
  runApp(const ProviderScope(child: AiBirthdayApp()));
}
