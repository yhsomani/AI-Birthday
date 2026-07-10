import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, it } from 'node:test';

const appSourceText = readFileSync(join(process.cwd(), 'src/App.tsx'), 'utf8');

const sliceBetween = (start: string, end: string) => {
  const startIndex = appSourceText.indexOf(start);
  const endIndex = appSourceText.indexOf(end, startIndex + start.length);
  assert.ok(startIndex >= 0, `Missing source marker ${start}`);
  assert.ok(endIndex > startIndex, `Missing source marker ${end}`);
  return appSourceText.slice(startIndex, endIndex);
};

const assertContains = (source: string, pattern: RegExp, message: string) => {
  assert.match(source, pattern, message);
};

describe('React Native primary interaction contract', () => {
  const appShell = sliceBetween('const App = () => {', 'const LockScreen =');
  const manualHandoffHandler = sliceBetween('const handleManualHandoff =', 'const handleSendEmail =');
  const onboardingScreen = sliceBetween('const OnboardingScreen =', 'const HomeScreen =');
  const homeScreen = sliceBetween('const HomeScreen =', 'const EventsScreen =');
  const eventsScreen = sliceBetween('const EventsScreen =', 'const EventMonthGrid =');
  const eventForm = sliceBetween('const EventForm =', 'const EventCard =');
  const eventCard = sliceBetween('const EventCard =', 'const MessagesScreen =');
  const messagesAndCards = sliceBetween('const MessagesScreen =', 'const ContactsScreen =');
  const contactsScreen = sliceBetween('const ContactsScreen =', 'const ContactDetail =');
  const contactDetail = sliceBetween('const ContactDetail =', 'const ChatHistory =');
  const contactEssentialsCard = sliceBetween(
    "title={t(locale, 'feature.contactDetail.essentials.title')}",
    '{relationshipInsight ? ('
  );
  const giftAdvisorCard = sliceBetween(
    "title={t(locale, 'feature.contactDetail.gift.title')}",
    "<SectionTitle title={t(locale, 'feature.contactDetail.timeline.title')}"
  );
  const wishPreview = sliceBetween('const WishPreview =', 'const ManualComposer =');
  const manualComposer = sliceBetween('const ManualComposer =', 'const MoreScreen =');
  const moreScreen = sliceBetween('const MoreScreen =', 'const Metric =');
  const styleCoachCard = sliceBetween(
    "<Text style={styles.cardTitle}>{t(locale, 'feature.more.styleCoach.title')}</Text>",
    "<Text style={styles.cardTitle}>{t(locale, 'feature.more.aiProvider.title')}</Text>"
  );

  it('keeps the primary tab model and active-screen rendering aligned', () => {
    const primaryTabsBlock = sliceBetween('const primaryTabs:', 'const tones:');
    const configuredTabs = [...primaryTabsBlock.matchAll(/key:\s*'([^']+)',\s*labelKey:\s*'([^']+)'/g)].map(match => ({
      key: match[1],
      labelKey: match[2]
    }));

    assert.deepEqual(configuredTabs, [
      { key: 'home', labelKey: 'nav.home' },
      { key: 'events', labelKey: 'nav.events' },
      { key: 'messages', labelKey: 'nav.messages' },
      { key: 'contacts', labelKey: 'nav.contacts' },
      { key: 'more', labelKey: 'nav.more' }
    ]);
    assertContains(
      appShell,
      /primaryTabs\.map\(tab =>[\s\S]+onPress=\{\(\) => dispatch\(\{ type: 'navigate', screen: tab\.key \}\)\}/,
      'Bottom tabs must navigate through the reducer.'
    );
    assertContains(
      appShell,
      /syncHomeWidgetSummary\(buildLocalizedHomeWidgetSummary\(state\)\)/,
      'The app shell should sync the safe widget summary to the native widget bridge after hydration.'
    );
    for (const [screen, component] of [
      ['home', 'HomeScreen'],
      ['events', 'EventsScreen'],
      ['messages', 'MessagesScreen'],
      ['contacts', 'ContactsScreen'],
      ['more', 'MoreScreen']
    ]) {
      assertContains(
        appShell,
        new RegExp(`state\\.activeScreen === '${screen}'[\\s\\S]+<${component}\\b`),
        `${screen} should render ${component}.`
      );
    }
  });

  it('keeps Home, Events, and event creation workflows reachable from primary screens', () => {
    assertContains(onboardingScreen, /onboardingGoalLabel\(locale, goal\)/, 'Onboarding should localize setup goal choices.');
    assertContains(onboardingScreen, /setupStatusDisplayLabel\(locale, step\.status\)/, 'Onboarding should localize setup step status labels.');

    assertContains(homeScreen, /screen: 'wishPreview'/, 'Home should route pending reviews to Wish Preview.');
    assertContains(homeScreen, /screen: 'manualComposer'/, 'Home should route check-ins to Manual Composer.');
    assertContains(homeScreen, /screen: 'events'/, 'Home should route event prep to Events.');
    assertContains(homeScreen, /buildLocalizedHomeWidgetSummary\(state\)/, 'Home should expose the localized widget fallback summary.');
    assertContains(homeScreen, /buildCheckInReminderQueue\(state\)/, 'Home should use the relationship check-in queue.');
    assertContains(homeScreen, /checkInQueueSummary\(locale, checkInQueue\)/, 'Home should localize check-in queue summaries.');
    assertContains(homeScreen, /checkInQueueEmptyMessage\(locale, checkInQueue, state\.contacts\.length\)/, 'Home should localize check-in empty states.');
    assertContains(homeScreen, /checkInReminderTitle\(locale, reminder\)/, 'Home should localize check-in reminder titles.');
    assertContains(homeScreen, /checkInReminderDetail\(locale, reminder\)/, 'Home should localize check-in reminder details.');
    assertContains(homeScreen, /checkInReminderPrimaryActionLabel\(locale, reminder\)/, 'Home should localize check-in primary actions.');
    assertContains(homeScreen, /checkInReminderSecondaryActionLabel\(locale\)/, 'Home should localize check-in secondary actions.');
    assertContains(homeScreen, /type: 'snoozeCheckIn'/, 'Home should let users snooze due check-ins.');
    assertContains(homeScreen, /type: 'markContactedElsewhere'/, 'Home should let users mark a check-in contacted elsewhere.');
    assert.doesNotMatch(
      homeScreen,
      /feature\.home\.active|Roadmap items|Useful additions/,
      'Home should stay focused on relationship actions instead of exposing implementation or roadmap inventory.'
    );

    assertContains(eventsScreen, /screen: 'eventForm'/, 'Events should open manual event creation.');
    assertContains(eventsScreen, /setViewMode\('Month'\)/, 'Events should switch to Month view.');
    assertContains(eventsScreen, /setTypeFilter\(item\)/, 'Events should expose event type filters.');
    assertContains(eventsScreen, /primaryEventTypeFilters/, 'Events should show birthday, anniversary, and custom filters first.');
    assertContains(eventsScreen, /showAdvancedEventTypes[\s\S]+advancedEventTypeFilters/, 'Events should reveal advanced event filters explicitly.');
    assertContains(eventsScreen, /feature\.events\.showAdvancedTypes/, 'Events should localize the advanced-type reveal control.');
    assertContains(eventsScreen, /eventTypeLabel\(state\.settings\.locale, item\)/, 'Events should localize event type filter labels.');
    assertContains(eventsScreen, /setTimeFilter\(item\)/, 'Events should expose event time filters.');
    assertContains(eventsScreen, /eventTimeFilterLabel\(state\.settings\.locale, item\)/, 'Events should localize event time filter labels.');
    assertContains(eventsScreen, /formatMonthForLocale\(monthView\.monthKey, state\.settings\.locale\)/, 'Events should localize month headings.');
    assertContains(eventsScreen, /shiftMonth\(value, -1\)/, 'Events should support previous month navigation.');
    assertContains(eventsScreen, /shiftMonth\(value, 1\)/, 'Events should support next month navigation.');

    assertContains(eventForm, /validateManualEventInput\(input, state\.contacts, state\.events\)/, 'Event form must validate input.');
    assertContains(eventForm, /eventTypeLabel\(state\.settings\.locale, item\)/, 'Event form should localize event type choices.');
    assertContains(eventForm, /primaryManualEventTypes/, 'Event form should default to birthday, anniversary, and custom event types.');
    assertContains(eventForm, /showAdvancedEventTypes[\s\S]+manualEventTypes/, 'Event form should reveal advanced event types explicitly.');
    assertContains(eventForm, /feature\.eventForm\.showAdvancedTypes/, 'Event form should localize the advanced-type reveal control.');
    assertContains(eventForm, /type: 'addManualEvent'/, 'Event form must save through the reducer.');
    assertContains(eventForm, /confirmConflict/, 'Event form must keep conflict confirmation explicit.');
    assertContains(eventCard, /buildEventPreparationPlan\(state, event\.id\)/, 'Event cards should use the preparation checklist model.');
    assertContains(eventCard, /eventTypeLabel\(locale, event\.type\)/, 'Event cards should localize event type metadata.');
    assertContains(eventCard, /eventPreparationStatusLabel\(locale, item\.status\)/, 'Event cards should localize preparation status labels.');
    assertContains(eventCard, /preparation\.summary/, 'Event cards should show preparation progress and next-step guidance.');
    assertContains(eventCard, /type: 'togglePreparationStep'/, 'Event cards should toggle canonical preparation steps.');
    assertContains(eventCard, /label=\{t\(locale, 'feature\.eventCard\.planReminders'\)\}/, 'Event cards should expose reminder planning from the preparation workflow.');
  });

  it('keeps manual handoff user-controlled after opening the destination app', () => {
    assertContains(manualHandoffHandler, /buildHandoffTarget\(contact, message\)/, 'Manual handoff should use the shared handoff target contract.');
    assertContains(
      manualHandoffHandler,
      /showAppAlert\(t\(locale, 'feedback\.manualHandoffTitle'\)/,
      'Manual handoff should present localized choices before leaving the app.'
    );
    assertContains(manualHandoffHandler, /text: t\(locale, 'action\.cancel'\)/, 'Manual handoff should localize cancel actions.');
    assertContains(manualHandoffHandler, /target\.fallbackLabel/, 'Manual handoff should always offer copy/share fallback.');
    assertContains(manualHandoffHandler, /target\.completionTitle/, 'Manual handoff should ask before marking sent.');
    assertContains(manualHandoffHandler, /target\.markSentLabel[\s\S]+dispatch\(\{ type: 'manualHandoff'/, 'Manual handoff should mark sent only from explicit confirmation.');
  });

  it('keeps message review, advanced bulk tools, contacts, and contact detail workflows interactive', () => {
    assertContains(messagesAndCards, /buildMessageInbox\(state/, 'Messages should build the inbox view model.');
    assertContains(messagesAndCards, /setQuery/, 'Messages should expose search input.');
    assertContains(messagesAndCards, /setTab\(item\)/, 'Messages should expose status tabs.');
    assertContains(messagesAndCards, /setChannel\(item\)/, 'Messages should expose channel filters.');
    assertContains(messagesAndCards, /setSort\(item\)/, 'Messages should expose sorting.');
    assertContains(messagesAndCards, /messageInboxTabLabel\(locale, item\)/, 'Messages should localize status tab labels.');
    assertContains(messagesAndCards, /messageChannelLabel\(locale, item\)/, 'Messages should localize channel filter labels.');
    assertContains(messagesAndCards, /messageInboxSortLabel\(locale, item\)/, 'Messages should localize sort labels.');
    assertContains(messagesAndCards, /messageBulkActionLabel\(locale, report\.action\)/, 'Messages should localize bulk action labels.');
    assertContains(messagesAndCards, /messageStatusLabel\(locale, message\.status\)/, 'Message cards should localize message status labels.');
    assertContains(messagesAndCards, /messageQualityLabel\(locale, message\.quality\)/, 'Message cards should localize message quality labels.');
    assertContains(messagesAndCards, /showBulkTools \? 'feature\.messages\.bulk\.hideTools' : 'feature\.messages\.bulk\.showTools'/, 'Messages should keep bulk tools behind an explicit reveal.');
    assertContains(messagesAndCards, /inbox\.rows\.length > 0 && showBulkTools/, 'Messages should show the bulk action panel only after the user opens bulk tools.');
    assertContains(messagesAndCards, /bulkSelectionEnabled=\{showBulkTools\}/, 'Message selection controls should be hidden until bulk tools are open.');
    assertContains(messagesAndCards, /selectVisibleMessages/, 'Messages should support visible selection.');
    assertContains(messagesAndCards, /type: 'bulkMessageAction'/, 'Messages should run bulk actions through the reducer.');
    assertContains(messagesAndCards, /showAppAlert\(t\(locale, 'feature\.messages\.bulk\.confirmTitle'\)/, 'Destructive or broad message actions must confirm.');
    assertContains(messagesAndCards, /screen: 'wishPreview'/, 'Message cards should open Wish Preview.');
    assertContains(messagesAndCards, /type: 'scheduleMessageFollowUp'/, 'Sent messages should offer follow-up scheduling.');
    assertContains(wishPreview, /type: 'testMessageRoute'/, 'Wish Preview should expose a safe test-send route check.');
    assertContains(wishPreview, /messageStatusLabel\(locale, message\.status\)/, 'Wish Preview should localize status labels.');
    assertContains(wishPreview, /messageChannelLabel\(locale, message\.channel\)/, 'Wish Preview should localize channel labels.');
    assertContains(wishPreview, /messageQualityLabel\(locale, message\.quality\)/, 'Wish Preview should localize quality labels.');
    assertContains(wishPreview, /messageVariantLabel\(locale, variant\)/, 'Wish Preview should localize variant labels.');
    assertContains(
      wishPreview,
      /hasEditedMessageBody[\s\S]+feature\.wishPreview\.confirmVariantTitle[\s\S]+dispatchVariantSelection\(variant, true\)/,
      'Wish Preview should confirm before replacing edited text with a saved variant.'
    );
    assertContains(wishPreview, /validateMessageBodyForChannel\(message\)/, 'Wish Preview should use the shared channel body policy.');
    assertContains(wishPreview, /messageBodyPolicy\.warning/, 'Wish Preview should show non-blocking channel body warnings.');
    assertContains(wishPreview, /buildTonePreferenceSummary\(state, message\.contactId, message\.id\)/, 'Wish Preview should explain recipient-specific tone impact.');
    assertContains(wishPreview, /tonePreferenceSummary\.influenceSummary/, 'Wish Preview should show how tone preferences affected the draft.');
    assertContains(wishPreview, /screen: tonePreferenceSummary\.adjustAction\.screen/, 'Wish Preview should route users to contact tone controls.');
    assertContains(wishPreview, /buildWishFeedbackPlan\(message/, 'Wish Preview should build a regeneration feedback plan.');
    assertContains(wishPreview, /selectedFeedbackOptionIds/, 'Wish Preview should expose selectable regeneration feedback chips.');
    assertContains(wishPreview, /customFeedback/, 'Wish Preview should expose custom regeneration feedback.');
    assertContains(wishPreview, /feedback: feedbackPlan\.requestFeedback/, 'Wish Preview regeneration should pass user feedback into generation.');
    assertContains(
      wishPreview,
      /showAppAlert\(\s*t\(locale, 'feature\.wishPreview\.confirmApproveTitle'\)/,
      'Wish Preview approval should confirm scheduling with localized copy.'
    );
    assertContains(wishPreview, /type: 'approveMessage'[\s\S]+reviewNext: true/, 'Wish Preview should approve and advance through the reducer.');
    assertContains(
      wishPreview,
      /showAppAlert\(t\(locale, 'feature\.wishPreview\.confirmRejectTitle'\)/,
      'Wish Preview rejection should confirm before discarding with localized copy.'
    );
    assertContains(wishPreview, /type: 'rejectMessage'[\s\S]+reviewNext: true/, 'Wish Preview should reject and advance through the reducer.');

    assertContains(manualComposer, /buildManualComposerState\(state, contact\.id/, 'Manual Composer should use the shared composer model.');
    assertContains(manualComposer, /manualComposerReasons\.map\(item =>/, 'Manual Composer should expose the supported writing reasons.');
    assertContains(manualComposer, /composerReasonLabel\(locale, item\)/, 'Manual Composer should localize writing reason choices.');
    assertContains(manualComposer, /composerModel\.context\.detail/, 'Manual Composer should explain included and excluded context.');
    assertContains(manualComposer, /disabled=\{!composerModel\.templateAction\.enabled\}/, 'Manual Composer should disable invalid template drafts through the composer model.');
    assertContains(manualComposer, /composerModel\.aiAction\.detail/, 'Manual Composer should explain AI provider readiness or fallback behavior.');
    assertContains(manualComposer, /type: 'createTemplateDraft'/, 'Manual Composer should create local template drafts through the reducer.');
    assertContains(manualComposer, /onGenerateMessage\(contact\.id, undefined, reason\)/, 'Manual Composer should keep the separate AI draft path reachable.');

    assertContains(contactsScreen, /buildContactBrowserRows\(state/, 'Contacts should use the browser view model.');
    assertContains(contactsScreen, /type: 'setSearch'/, 'Contacts should expose search.');
    assertContains(contactsScreen, /setGroupFilter\(filter\)/, 'Contacts should expose group filters.');
    assertContains(contactsScreen, /contactGroupLabel\(locale, filter\)/, 'Contacts should localize group filter labels.');
    assertContains(contactsScreen, /setQualityFilter\(filter\)/, 'Contacts should expose quality filters.');
    assertContains(contactsScreen, /contactQualityLabel\(locale, filter\)/, 'Contacts should localize quality filter labels.');
    assertContains(contactsScreen, /setContactSort\(sort\)/, 'Contacts should expose sorting.');
    assertContains(contactsScreen, /contactSortLabel\(locale, sort\)/, 'Contacts should localize sort labels.');
    assertContains(contactsScreen, /feature\.contacts\.card\.meta/, 'Contact cards should localize metadata formatting.');
    assertContains(contactsScreen, /screen: 'contactDetail'/, 'Contacts should open contact detail.');
    assertContains(contactsScreen, /screen: 'manualComposer'/, 'Contacts should open manual composer.');

    assertContains(contactDetail, /buildRelationshipHealthInsight\(state, contact\.id\)/, 'Contact detail should expose health insight.');
    assertContains(contactDetail, /relationshipGroupDisplayLabel\(locale, contact\.group\)/, 'Contact detail should localize header group metadata.');
    assertContains(contactDetail, /automationModeDisplayLabel\(locale, preferences\.automationMode\)/, 'Contact detail should localize automation mode status.');
    assertContains(contactDetail, /checkInStatusLabel\(locale, contactCheckIn\.status\)/, 'Contact detail should localize check-in status labels.');
    assertContains(contactDetail, /checkInReminderTitle\(locale, contactCheckIn\)/, 'Contact detail should localize check-in reminder titles.');
    assertContains(contactDetail, /checkInReminderDetail\(locale, contactCheckIn\)/, 'Contact detail should localize check-in reminder details.');
    assertContains(contactDetail, /contactLanguageLabel\(locale, language\)/, 'Contact detail should localize contact language choices.');
    assertContains(contactDetail, /relationshipHealthLabel\(locale, relationshipInsight\.label\)/, 'Contact detail should localize relationship health labels.');
    assertContains(contactDetail, /confidenceLabel\(locale, relationshipInsight\.suggestion\.confidence\)/, 'Contact detail should localize relationship suggestion confidence.');
    assertContains(contactDetail, /relationshipGroupDisplayLabel\(locale, group\)/, 'Contact detail should localize relationship group choices.');
    assertContains(contactDetail, /checkInCadenceLabel\(locale, days\)/, 'Contact detail should localize check-in cadence choices.');
    assertContains(contactDetail, /automationModeDisplayLabel\(locale, mode\)/, 'Contact detail should localize contact automation choices.');
    assertContains(
      contactDetail,
      /visibleContactAutomationModes\.map\(mode =>/,
      'Contact detail automation choices should be filtered through the advanced reveal.'
    );
    assertContains(
      contactDetail,
      /showAdvancedContactAutomation[\s\S]+feature\.more\.settings\.hideAdvancedAutomation[\s\S]+feature\.more\.settings\.showAdvancedAutomation[\s\S]+setShowAdvancedContactAutomation\(value => !value\)/,
      'Contact detail should keep full automation behind an explicit advanced reveal.'
    );
    assert.doesNotMatch(
      contactDetail,
      /automationModes\.map\(mode =>/,
      'Contact detail should not expose every automation mode as ordinary visible pills.'
    );
    assertContains(contactDetail, /buildContactEnrichmentPlan\(state, contact\.id\)/, 'Contact detail should expose guided enrichment.');
    assertContains(contactDetail, /enrichmentPlan\.summary/, 'Contact detail should explain personalization quality and the next missing detail.');
    assertContains(contactDetail, /enrichmentPlan\.missingSignals\.length/, 'Contact detail should show missing personalization signal count.');
    assertContains(contactDetail, /prompt\.improvesSignal/, 'Contact detail should explain what each enrichment prompt improves.');
    assertContains(contactDetail, /buildGiftSuggestions\(state, contact\.id, giftOccasion\)/, 'Contact detail should expose gift suggestions.');
    assertContains(contactDetail, /buildContactTimeline\(state, contact\.id, timelineFilter\)/, 'Contact detail should expose timeline filtering.');
    assertContains(contactDetail, /contactTimelineFilterLabel\(locale, filter\)/, 'Contact detail should localize timeline filter labels.');
    assertContains(contactDetail, /contactTimelineEntryTypeLabel\(locale, entry\.type\)/, 'Contact detail should localize timeline entry type labels.');
    assertContains(contactDetail, /contactTimelineEmptyMessage\(locale, timelineFilter\)/, 'Contact detail should localize timeline empty states.');
    assertContains(contactDetail, /contactTimelineEntryTitle\(locale, entry, state\)/, 'Contact detail should localize timeline entry titles.');
    assertContains(contactDetail, /contactTimelineEntryDetail\(locale, entry, state\)/, 'Contact detail should localize timeline entry details.');
    assertContains(contactDetail, /buildMemoryVaultReport\(state, contact\.id, memoryQuery\)/, 'Contact detail should expose searchable Memory Vault state.');
    assertContains(contactDetail, /memoryCategoryLabel\(locale, category\)/, 'Contact detail should localize memory category choices.');
    assertContains(contactDetail, /memoryAiUseLabel\(locale, memory\.category\)/, 'Contact detail should localize memory AI-use labels.');
    assertContains(contactDetail, /giftCategoryLabel\(locale, category\)/, 'Contact detail should localize gift category choices.');
    assertContains(contactDetail, /giftFeedbackLabel\(locale, feedback\)/, 'Contact detail should localize gift feedback choices.');
    assertContains(contactDetail, /giftBudgetFitLabel\(locale, suggestion\.budgetFit\)/, 'Contact detail should localize Gift Advisor budget-fit labels.');
    assertContains(contactDetail, /giftSuggestionConfidenceLabel\(locale, suggestion\.confidence\)/, 'Contact detail should localize Gift Advisor confidence labels.');
    assert.doesNotMatch(
      contactEssentialsCard,
      /feature\.contactDetail\.essentials\.annualGiftBudget|annualGiftBudget/,
      'Contact detail essentials should not expose Gift Advisor budget as a primary profile field.'
    );
    assertContains(giftAdvisorCard, /feature\.contactDetail\.gift\.adjustBudget/, 'Gift Advisor should expose optional budget editing.');
    assertContains(giftAdvisorCard, /feature\.contactDetail\.gift\.annualBudget/, 'Gift Advisor should label its budget input.');
    assertContains(contactDetail, /validateGiftBudgetInput\(\{ annualGiftBudget: giftBudgetDraft \}\)/, 'Gift Advisor should validate budget edits.');
    assertContains(contactDetail, /type: 'updateGiftBudget'/, 'Gift Advisor should save budget edits through its own reducer action.');
    assertContains(contactDetail, /buildCheckInReminderQueue\(state\)/, 'Contact detail should expose per-contact check-in status.');
    assertContains(contactDetail, /validateContactEssentials\(essentialsInput, preferences\.preferredChannel\)/, 'Contact detail should validate editable profile essentials.');
    assertContains(contactDetail, /type: 'updateContactEssentials'/, 'Contact detail should save editable profile essentials through the reducer.');
    assertContains(contactDetail, /type: 'addMemory'/, 'Contact detail should save memory notes through the reducer.');
    assertContains(contactDetail, /type: 'editMemory'/, 'Contact detail should edit memory notes through the reducer.');
    assertContains(contactDetail, /type: 'toggleMemoryPin'/, 'Contact detail should pin and unpin memory notes through the reducer.');
    assertContains(contactDetail, /type: 'deleteMemory'/, 'Contact detail should delete memory notes through the reducer.');
    assertContains(
      contactDetail,
      /feature\.contactDetail\.memory\.confirmDelete/,
      'Contact detail should require delete confirmation for memory notes.'
    );
    assertContains(contactDetail, /type: 'markContactedElsewhere'/, 'Contact detail should let users mark contact outside the app.');
    assertContains(contactDetail, /type: 'addGift'/, 'Contact detail should save gifts through the reducer.');
    assertContains(contactDetail, /type: 'deleteGift'/, 'Contact detail should delete gift history through the reducer.');
    assertContains(contactDetail, /feature\.contactDetail\.gift\.deleteGift/, 'Contact detail should expose gift deletion.');
    assertContains(contactDetail, /feature\.contactDetail\.gift\.confirmDelete/, 'Contact detail should confirm gift deletion.');
    assertContains(contactDetail, /type: 'updateContactTone'/, 'Contact detail should expose recipient-specific tone controls.');
    assertContains(contactDetail, /type: 'setContactAutomationMode'/, 'Contact detail should expose contact automation overrides.');
    assertContains(contactDetail, /type: 'useGroupDefaultsForContact'/, 'Contact detail should let users return to group defaults.');
  });

  it('keeps More screen operational controls explicit and review-first', () => {
    for (const [label, pattern] of [
      ['device contact import', /onPress=\{importDevice\}/],
      ['calendar export', /onPress=\{exportCalendar\}/],
      ['calendar import', /onPress=\{importCalendar\}/],
      ['reminder scheduling', /onPress=\{scheduleReminders\}/],
      ['AI provider test', /onPress=\{testAiProvider\}/],
      ['backup export', /exportBackup\(backupPassphrase\)/],
      ['backup file selection', /pickBackup\(\)/],
      ['analytics summary sharing', /shareAnalyticsSummary\(\)/],
      ['analytics CSV reveal state', /showAnalyticsExportTools/],
      ['event file picker', /pickEventImportFile\(\)/],
      ['template library model', /buildMessageTemplateLibrary\(state/],
      ['template library body editing', /setTemplateLibraryBody/]
    ] as Array<[string, RegExp]>) {
      assertContains(moreScreen, pattern, `More should expose ${label}.`);
    }
    assertContains(moreScreen, /manualComposerReasons\.map\(reason =>/, 'Message Template Library should expose occasion choices.');
    assertContains(moreScreen, /composerReasonLabel\(locale, reason\)/, 'Message Template Library should localize occasion choices.');
    assertContains(moreScreen, /tones\.map\(tone =>/, 'Message Template Library should expose tone choices.');
    assertContains(moreScreen, /localizedToneLabel\(locale, tone\)/, 'Message Template Library and settings should localize tone choices.');
    assertContains(moreScreen, /type: 'createTemplateDraft'[\s\S]+templateLibrary\.selectedTemplate\.id/, 'Message Template Library should create review-first template drafts.');
    assertContains(moreScreen, /parseEventImportText\(raw, eventImportFormat\)/, 'More should parse pasted or selected event files.');
    assertContains(moreScreen, /buildShareableAnalyticsSummary\(analyticsDashboard\)/, 'Analytics should expose a redacted shareable summary.');
    assertContains(
      moreScreen,
      /showAnalyticsExportTools[\s\S]+feature\.more\.analytics\.hideCsvExport[\s\S]+feature\.more\.analytics\.showCsvExport[\s\S]+setShowAnalyticsExportTools\(value => !value\)/,
      'Analytics CSV export should stay behind an explicit power-user reveal.'
    );
    assertContains(
      moreScreen,
      /showAnalyticsExportTools \? \([\s\S]+feature\.more\.analytics\.csvExportDetail[\s\S]+shareAnalyticsReport\(\)/,
      'Analytics CSV export should only be actionable after the reveal is opened.'
    );
    assertContains(
      moreScreen,
      /showEmailProviderSetup[\s\S]+feature\.more\.settings\.hideEmailProviderSetup[\s\S]+feature\.more\.settings\.showEmailProviderSetup[\s\S]+setShowEmailProviderSetup\(value => !value\)/,
      'Email provider setup should stay behind an explicit reveal.'
    );
    assertContains(
      moreScreen,
      /showEmailProviderSetup \? \([\s\S]+feature\.more\.settings\.senderEmailLabel[\s\S]+feature\.more\.settings\.emailProviderStatus/,
      'Provider email sender and status controls should only be visible after the reveal is opened.'
    );
    assertContains(
      moreScreen,
      /showAppAlert\([\s\S]+feature\.more\.analytics\.csvConfirmTitle[\s\S]+feature\.more\.analytics\.csvConfirmAction[\s\S]+exportAnalyticsCsvReport\(report\)/,
      'Analytics CSV export should require explicit confirmation before sharing.'
    );
    assertContains(
      moreScreen,
      /refreshSetupDoctorReport[\s\S]+feature\.more\.setupCheck\.refreshTitle[\s\S]+setupDoctorReport\.readyCount[\s\S]+setupDoctorNeedsActionCount[\s\S]+setupDoctorWarningCount/,
      'Setup Check refresh should re-report current readiness without creating a dry-run snapshot.'
    );
    assertContains(
      moreScreen,
      /feature\.more\.setupCheck\.refresh[\s\S]+onPress=\{refreshSetupDoctorReport\}/,
      'Setup Check should expose an explicit refresh action.'
    );
    assertContains(moreScreen, /buildSetupDoctorDryRunSnapshot\(setupDoctorReport\)/, 'Setup Check should build a redacted dry-run snapshot.');
    assertContains(moreScreen, /type: 'setupDoctorDryRunRecorded'/, 'Setup Check dry run should record a redacted activity snapshot.');
    assertContains(moreScreen, /setupWizardSummaryLabel\(\)/, 'Setup Wizard should localize dynamic readiness summaries.');
    assertContains(moreScreen, /setupWizardStepTitleLabel\(step\)/, 'Setup Wizard should localize step titles.');
    assertContains(moreScreen, /setupWizardStepDetailLabel\(step\)/, 'Setup Wizard should localize step details.');
    assertContains(moreScreen, /setupWizardStepActionLabel\(step\)/, 'Setup Wizard should localize step actions.');
    assertContains(moreScreen, /setupDoctorSummaryLabel\(\)/, 'Setup Check should localize diagnostic summaries.');
    assertContains(moreScreen, /setupDoctorDryRunMessage\(\)/, 'Setup Check should localize dry-run safety copy.');
    assertContains(moreScreen, /setupDoctorCheckTitleLabel\(check\)/, 'Setup Check should localize check titles.');
    assertContains(moreScreen, /setupDoctorImpactLabel\(check\)/, 'Setup Check should localize check impacts.');
    assertContains(moreScreen, /setupDoctorActionLabel\(check\)/, 'Setup Check should localize check actions.');
    assertContains(
      styleCoachCard,
      /feature\.more\.styleCoach\.improveStyle[\s\S]+type: 'trainStyleFromSamples'/,
      'Style Coach should present manual retraining as an Improve my style action.'
    );
    assert.doesNotMatch(
      styleCoachCard,
      /history|snapshot/i,
      'Style Coach should keep profile history and snapshots out of the main UI.'
    );
    assertContains(
      moreScreen,
      /feature\.more\.setupCheck\.runDryCheck/,
      'Setup Check should expose an explicit localized dry-run action.'
    );
    assertContains(moreScreen, /type: 'calendarImported', candidates: parsed\.candidates/, 'Event files should reuse review-first calendar import.');
    assertContains(moreScreen, /buildAccountExitPlan\(state, 'disconnect-account'\)/, 'Account disconnect should use the account-exit checklist.');
    assertContains(moreScreen, /buildAccountExitPlan\(state, 'clear-local-data'\)/, 'Local data clearing should use the account-exit checklist.');
    assertContains(moreScreen, /showAppAlert\(plan\.confirmationTitle, plan\.confirmationBody/, 'Account exit actions must confirm with planned consequences.');
    assertContains(moreScreen, /confirmAccountAction\(clearLocalDataPlan/, 'Local data clearing must confirm through the account-exit plan.');
    assertContains(moreScreen, /type: 'disconnectAccount'/, 'Account disconnect must use the explicit reducer action.');
    assertContains(
      moreScreen,
      /const clearLocalData = onClearLocalData[\s\S]+confirmAccountAction\(clearLocalDataPlan, clearLocalData\)/,
      'Local data clearing must use the confirmed transactional lifecycle handler.'
    );
    assertContains(
      moreScreen,
      /mode === 'Fully auto'[\s\S]+feature\.more\.settings\.fullAutoConfirmTitle[\s\S]+type: 'setAutomationMode'/,
      'Global fully auto should confirm with localized advanced-mode copy before changing automation mode.'
    );
    assertContains(
      moreScreen,
      /feature\.more\.settings\.fullAutoAdvancedNotice/,
      'Automation settings should present full automation as an advanced option with review safeguards.'
    );
    assertContains(
      moreScreen,
      /showAdvancedAutomationModes[\s\S]+feature\.more\.settings\.hideAdvancedAutomation[\s\S]+feature\.more\.settings\.showAdvancedAutomation[\s\S]+setShowAdvancedAutomationModes\(value => !value\)/,
      'More should keep advanced automation choices behind an explicit reveal.'
    );
    assertContains(
      moreScreen,
      /visibleAutomationModesFor\(state\.settings\.automationMode\)\.map\(mode =>/,
      'Global automation mode choices should be filtered through the advanced reveal.'
    );
    assertContains(
      moreScreen,
      /visibleAutomationModesFor\(defaults\.automationMode\)\.map\(mode =>/,
      'Group default automation choices should be filtered through the advanced reveal.'
    );
    assert.doesNotMatch(
      moreScreen,
      /automationModes\.map\(mode =>/,
      'More should not expose every automation mode as ordinary visible pills.'
    );
    assertContains(
      moreScreen,
      /feature\.more\.backup\.restoreConfirmTitle/,
      'Backup restore must confirm with localized consequence copy.'
    );
    assertContains(
      moreScreen,
      /feature\.more\.backup\.previewDetail[\s\S]+selectedBackup\.preview\.version[\s\S]+selectedBackup\.preview\.persistenceVersion/,
      'Backup restore preview must disclose backup version, data version, and restore-mode copy before confirmation.'
    );
    assertContains(moreScreen, /restoreBackup\(selectedBackup\.raw, backupPassphrase/, 'Backup restore must use selected file and passphrase.');
    assertContains(moreScreen, /type: 'toggleWhatsAppHandoffConsent'/, 'Manual WhatsApp handoff consent must be revocable.');
    assertContains(
      moreScreen,
      /feature\.more\.settings\.title/,
      'Settings controls should be localized in the More screen.'
    );
    assertContains(moreScreen, /relationshipGroupOptions\.map\(group =>/, 'Settings should expose relationship group defaults.');
    assertContains(moreScreen, /state\.settings\.groupDefaults\[group\]/, 'Group default controls should read persisted defaults.');
    assertContains(moreScreen, /type: 'setRelationshipGroupDefault'/, 'Group default changes must go through the reducer.');
    assertContains(moreScreen, /buildActivityHistory\(state\.activity,[\s\S]+state/, 'Activity History should validate recovery targets against current app state.');
    assertContains(
      moreScreen,
      /feature\.more\.activityHistory\.title/,
      'Activity History should expose localized workflow controls.'
    );
    assertContains(moreScreen, /activityTitleLabel\(locale, row\.item\)/, 'Activity History should localize system activity titles.');
    assertContains(moreScreen, /activityDetailLabel\(locale, row\.item\)/, 'Activity History should localize system activity details.');
    assertContains(moreScreen, /messageId: row\.messageId/, 'Activity History recovery navigation should carry message context when available.');
    assertContains(moreScreen, /contactId: row\.contactId/, 'Activity History recovery navigation should carry contact context when available.');
  });
});
