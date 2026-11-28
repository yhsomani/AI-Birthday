import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations)!;
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[Locale('en')];

  /// Application title
  ///
  /// In en, this message translates to:
  /// **'AI-Birthday'**
  String get appTitle;

  /// Bottom navigation destination: today's birthday overview
  ///
  /// In en, this message translates to:
  /// **'Today'**
  String get navToday;

  /// Bottom navigation destination: tracked people list
  ///
  /// In en, this message translates to:
  /// **'People'**
  String get navPeople;

  /// Bottom navigation destination: birthday calendar
  ///
  /// In en, this message translates to:
  /// **'Calendar'**
  String get navCalendar;

  /// Bottom navigation destination: message and delivery activity
  ///
  /// In en, this message translates to:
  /// **'Activity'**
  String get navActivity;

  /// Tooltip for settings icon button
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get navSettingsTooltip;

  /// Generic retry button label
  ///
  /// In en, this message translates to:
  /// **'Try again'**
  String get commonRetry;

  /// Generic cancel button label
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get commonCancel;

  /// Generic save button label
  ///
  /// In en, this message translates to:
  /// **'Save'**
  String get commonSave;

  /// Generic delete button label
  ///
  /// In en, this message translates to:
  /// **'Delete'**
  String get commonDelete;

  /// Semantic loading announcement
  ///
  /// In en, this message translates to:
  /// **'Loading…'**
  String get commonLoading;

  /// Default error state title
  ///
  /// In en, this message translates to:
  /// **'Something went wrong'**
  String get commonErrorTitle;

  /// Default empty state title
  ///
  /// In en, this message translates to:
  /// **'Nothing here yet'**
  String get commonEmptyTitle;

  /// Fallback action allowing user to proceed past a non-fatal persistence warning
  ///
  /// In en, this message translates to:
  /// **'Continue anyway'**
  String get commonContinueAnyway;

  /// Countdown label when birthday is today
  ///
  /// In en, this message translates to:
  /// **'Today'**
  String get countdownToday;

  /// Countdown label when birthday is tomorrow
  ///
  /// In en, this message translates to:
  /// **'Tomorrow'**
  String get countdownTomorrow;

  /// Countdown label for N days away
  ///
  /// In en, this message translates to:
  /// **'In {days} days'**
  String countdownInDays(int days);

  /// Countdown label for N months away
  ///
  /// In en, this message translates to:
  /// **'In {months} months'**
  String countdownInMonths(int months);

  /// Title shown while checking local database at startup
  ///
  /// In en, this message translates to:
  /// **'Preparing your local data…'**
  String get startupCheckingTitle;

  /// Body shown while checking local database at startup
  ///
  /// In en, this message translates to:
  /// **'AI-Birthday is checking the on-device database. This check is limited to 10 seconds.'**
  String get startupCheckingBody;

  /// Title shown when database initialization fails
  ///
  /// In en, this message translates to:
  /// **'Local data recovery needed'**
  String get startupRecoveryTitle;

  /// Body explaining safe stop during startup database recovery
  ///
  /// In en, this message translates to:
  /// **'AI-Birthday stopped rather than risk opening or changing unreadable data. No cloud upload or local write has been attempted.'**
  String get startupRecoveryBody;

  /// Diagnostic text when startup database check times out
  ///
  /// In en, this message translates to:
  /// **'Database check exceeded 10 seconds.'**
  String get startupRecoveryTimeoutDiagnostic;

  /// Diagnostic text when startup database check fails
  ///
  /// In en, this message translates to:
  /// **'Database initialization failed ({errorType}).'**
  String startupRecoveryErrorDiagnostic(String errorType);

  /// Retry button label on startup recovery screen
  ///
  /// In en, this message translates to:
  /// **'Retry database initialization'**
  String get startupRetryButton;

  /// Guidance shown on startup recovery screen
  ///
  /// In en, this message translates to:
  /// **'If this repeats, restart the device, ensure storage space is available, and preserve app data. Reinstalling may remove it.'**
  String get startupRecoveryHint;

  /// AppBar title on unknown route screen
  ///
  /// In en, this message translates to:
  /// **'Screen not found'**
  String get notFoundAppBarTitle;

  /// Title on unknown route screen when path is empty
  ///
  /// In en, this message translates to:
  /// **'That screen does not exist.'**
  String get notFoundEmptyPathTitle;

  /// Title on unknown route screen with requested location
  ///
  /// In en, this message translates to:
  /// **'No screen matches \"{location}\".'**
  String notFoundPathTitle(String location);

  /// Message on unknown route screen
  ///
  /// In en, this message translates to:
  /// **'Let\'s get you back to your celebrations.'**
  String get notFoundMessage;

  /// CTA returning user from unknown route to dashboard
  ///
  /// In en, this message translates to:
  /// **'Back to dashboard'**
  String get notFoundBackToDashboard;

  /// Skip onboarding button label
  ///
  /// In en, this message translates to:
  /// **'Skip'**
  String get onboardingSkip;

  /// Advance to next onboarding page button label
  ///
  /// In en, this message translates to:
  /// **'Next'**
  String get onboardingNext;

  /// Complete onboarding button label
  ///
  /// In en, this message translates to:
  /// **'Get Started'**
  String get onboardingGetStarted;

  /// Error message when persisting onboarding completion fails
  ///
  /// In en, this message translates to:
  /// **'Could not save your progress. Please try again.'**
  String get onboardingSaveError;

  /// Accessibility label for onboarding page progress dots
  ///
  /// In en, this message translates to:
  /// **'Step {current} of {total}'**
  String onboardingPageIndicatorSemantics(int current, int total);

  /// Title on onboarding page 1
  ///
  /// In en, this message translates to:
  /// **'Never miss a birthday that matters.'**
  String get onboardingPage1Title;

  /// Core 5-step loop tagline on onboarding page 1
  ///
  /// In en, this message translates to:
  /// **'Remember → Prepare → Personalize → Review → Send'**
  String get onboardingPage1Loop;

  /// Feature 1 title on onboarding page 1
  ///
  /// In en, this message translates to:
  /// **'Always Prepared Ahead'**
  String get onboardingPage1Feature1Title;

  /// Feature 1 description on onboarding page 1
  ///
  /// In en, this message translates to:
  /// **'Enable optional reminders in Settings to prepare thoughtful wishes days in advance.'**
  String get onboardingPage1Feature1Desc;

  /// Feature 2 title on onboarding page 1
  ///
  /// In en, this message translates to:
  /// **'Thoughtful, Personal Greetings'**
  String get onboardingPage1Feature2Title;

  /// Feature 2 description on onboarding page 1
  ///
  /// In en, this message translates to:
  /// **'Write warm drafts yourself or with optional AI tailored to the details you add.'**
  String get onboardingPage1Feature2Desc;

  /// Feature 3 title on onboarding page 1
  ///
  /// In en, this message translates to:
  /// **'Local by Default'**
  String get onboardingPage1Feature3Title;

  /// Feature 3 description on onboarding page 1
  ///
  /// In en, this message translates to:
  /// **'Your birthday data stays on this device unless you explicitly choose cloud backup or an external service.'**
  String get onboardingPage1Feature3Desc;

  /// Title on onboarding page 2
  ///
  /// In en, this message translates to:
  /// **'Bring in your birthdays'**
  String get onboardingPage2Title;

  /// Subtitle on onboarding page 2
  ///
  /// In en, this message translates to:
  /// **'Add your first birthday now or explore the dashboard.'**
  String get onboardingPage2Subtitle;

  /// Manual add on-ramp title on onboarding page 2
  ///
  /// In en, this message translates to:
  /// **'Add Birthday Manually'**
  String get onboardingAddManualTitle;

  /// Manual add on-ramp subtitle on onboarding page 2
  ///
  /// In en, this message translates to:
  /// **'Enter name, date, relationship, and tone.'**
  String get onboardingAddManualSubtitle;

  /// Phone import on-ramp title on onboarding page 2
  ///
  /// In en, this message translates to:
  /// **'Open Phone Contact Import'**
  String get onboardingImportContactsTitle;

  /// Phone import on-ramp subtitle on onboarding page 2
  ///
  /// In en, this message translates to:
  /// **'Opens People, where Import from Phone asks for contacts permission and reviews matches before saving.'**
  String get onboardingImportContactsSubtitle;

  /// Title on onboarding page 3
  ///
  /// In en, this message translates to:
  /// **'Prepared ahead of time'**
  String get onboardingPage3Title;

  /// Subtitle on onboarding page 3
  ///
  /// In en, this message translates to:
  /// **'Reminders are optional and start only after you enable them in Settings. Greetings are always yours to review.'**
  String get onboardingPage3Subtitle;

  /// 7-day reminder timeline badge on onboarding page 3
  ///
  /// In en, this message translates to:
  /// **'7 Days'**
  String get onboardingTimeline7Days;

  /// 7-day reminder timeline description on onboarding page 3
  ///
  /// In en, this message translates to:
  /// **'Upcoming reminder: Plan gifts or special messages.'**
  String get onboardingTimeline7DaysDesc;

  /// 2-day reminder timeline badge on onboarding page 3
  ///
  /// In en, this message translates to:
  /// **'2 Days'**
  String get onboardingTimeline2Days;

  /// 2-day reminder timeline description on onboarding page 3
  ///
  /// In en, this message translates to:
  /// **'Prepare: Open Message Studio and create or review your greeting.'**
  String get onboardingTimeline2DaysDesc;

  /// Today reminder timeline badge on onboarding page 3
  ///
  /// In en, this message translates to:
  /// **'Today'**
  String get onboardingTimelineToday;

  /// Today reminder timeline description on onboarding page 3
  ///
  /// In en, this message translates to:
  /// **'Send day: Review your greeting, then hand it to your messaging app and confirm if you sent it.'**
  String get onboardingTimelineTodayDesc;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
