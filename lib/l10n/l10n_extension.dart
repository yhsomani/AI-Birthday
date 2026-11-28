import 'package:flutter/widgets.dart';

import 'app_localizations.dart';
import 'app_localizations_en.dart';

/// Safe localization accessor that falls back to [AppLocalizationsEn] when a
/// widget is pumped in an isolated test harness without `localizationsDelegates`.
extension AppL10nX on BuildContext {
  AppLocalizations get l10n =>
      Localizations.of<AppLocalizations>(this, AppLocalizations) ??
      AppLocalizationsEn();
}
