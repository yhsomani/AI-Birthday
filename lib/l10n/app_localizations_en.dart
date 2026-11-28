// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appTitle => 'AI-Birthday';

  @override
  String get navToday => 'Today';

  @override
  String get navPeople => 'People';

  @override
  String get navCalendar => 'Calendar';

  @override
  String get navActivity => 'Activity';

  @override
  String get navSettingsTooltip => 'Settings';

  @override
  String get commonRetry => 'Try again';

  @override
  String get commonCancel => 'Cancel';

  @override
  String get commonSave => 'Save';

  @override
  String get commonDelete => 'Delete';

  @override
  String get commonLoading => 'Loading…';

  @override
  String get commonErrorTitle => 'Something went wrong';

  @override
  String get commonEmptyTitle => 'Nothing here yet';

  @override
  String get commonContinueAnyway => 'Continue anyway';

  @override
  String get countdownToday => 'Today';

  @override
  String get countdownTomorrow => 'Tomorrow';

  @override
  String countdownInDays(int days) {
    return 'In $days days';
  }

  @override
  String countdownInMonths(int months) {
    return 'In $months months';
  }

  @override
  String get startupCheckingTitle => 'Preparing your local data…';

  @override
  String get startupCheckingBody =>
      'AI-Birthday is checking the on-device database. This check is limited to 10 seconds.';

  @override
  String get startupRecoveryTitle => 'Local data recovery needed';

  @override
  String get startupRecoveryBody =>
      'AI-Birthday stopped rather than risk opening or changing unreadable data. No cloud upload or local write has been attempted.';

  @override
  String get startupRecoveryTimeoutDiagnostic =>
      'Database check exceeded 10 seconds.';

  @override
  String startupRecoveryErrorDiagnostic(String errorType) {
    return 'Database initialization failed ($errorType).';
  }

  @override
  String get startupRetryButton => 'Retry database initialization';

  @override
  String get startupRecoveryHint =>
      'If this repeats, restart the device, ensure storage space is available, and preserve app data. Reinstalling may remove it.';

  @override
  String get notFoundAppBarTitle => 'Screen not found';

  @override
  String get notFoundEmptyPathTitle => 'That screen does not exist.';

  @override
  String notFoundPathTitle(String location) {
    return 'No screen matches \"$location\".';
  }

  @override
  String get notFoundMessage => 'Let\'s get you back to your celebrations.';

  @override
  String get notFoundBackToDashboard => 'Back to dashboard';

  @override
  String get onboardingSkip => 'Skip';

  @override
  String get onboardingNext => 'Next';

  @override
  String get onboardingGetStarted => 'Get Started';

  @override
  String get onboardingSaveError =>
      'Could not save your progress. Please try again.';

  @override
  String onboardingPageIndicatorSemantics(int current, int total) {
    return 'Step $current of $total';
  }

  @override
  String get onboardingPage1Title => 'Never miss a birthday that matters.';

  @override
  String get onboardingPage1Loop =>
      'Remember → Prepare → Personalize → Review → Send';

  @override
  String get onboardingPage1Feature1Title => 'Always Prepared Ahead';

  @override
  String get onboardingPage1Feature1Desc =>
      'Enable optional reminders in Settings to prepare thoughtful wishes days in advance.';

  @override
  String get onboardingPage1Feature2Title => 'Thoughtful, Personal Greetings';

  @override
  String get onboardingPage1Feature2Desc =>
      'Write warm drafts yourself or with optional AI tailored to the details you add.';

  @override
  String get onboardingPage1Feature3Title => 'Local by Default';

  @override
  String get onboardingPage1Feature3Desc =>
      'Your birthday data stays on this device unless you explicitly choose cloud backup or an external service.';

  @override
  String get onboardingPage2Title => 'Bring in your birthdays';

  @override
  String get onboardingPage2Subtitle =>
      'Add your first birthday now or explore the dashboard.';

  @override
  String get onboardingAddManualTitle => 'Add Birthday Manually';

  @override
  String get onboardingAddManualSubtitle =>
      'Enter name, date, relationship, and tone.';

  @override
  String get onboardingImportContactsTitle => 'Open Phone Contact Import';

  @override
  String get onboardingImportContactsSubtitle =>
      'Opens People, where Import from Phone asks for contacts permission and reviews matches before saving.';

  @override
  String get onboardingPage3Title => 'Prepared ahead of time';

  @override
  String get onboardingPage3Subtitle =>
      'Reminders are optional and start only after you enable them in Settings. Greetings are always yours to review.';

  @override
  String get onboardingTimeline7Days => '7 Days';

  @override
  String get onboardingTimeline7DaysDesc =>
      'Upcoming reminder: Plan gifts or special messages.';

  @override
  String get onboardingTimeline2Days => '2 Days';

  @override
  String get onboardingTimeline2DaysDesc =>
      'Prepare: Open Message Studio and create or review your greeting.';

  @override
  String get onboardingTimelineToday => 'Today';

  @override
  String get onboardingTimelineTodayDesc =>
      'Send day: Review your greeting, then hand it to your messaging app and confirm if you sent it.';
}
