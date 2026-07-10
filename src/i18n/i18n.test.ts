import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  formatCurrencyForLocale,
  formatDateForLocale,
  formatMonthForLocale,
  localeMetadata,
  resolveLocale,
  supportedLocales,
  t,
  tc,
  translations,
  type TranslationKey
} from './i18n';

describe('localization contract', () => {
  const englishKeys = Object.keys(translations['en-IN']) as TranslationKey[];
  const placeholdersFor = (value: string) =>
    [...value.matchAll(/\{([A-Za-z][A-Za-z0-9_]*)\}/g)].map(match => match[1]).sort();

  it('keeps every supported locale complete for user-facing UI keys', () => {
    const englishKeys = Object.keys(translations['en-IN']) as TranslationKey[];

    for (const locale of supportedLocales) {
      for (const key of englishKeys) {
        assert.equal(typeof translations[locale][key], 'string', `${locale} missing ${key}`);
        assert.ok(translations[locale][key].trim().length > 0, `${locale} has blank ${key}`);
      }
    }
  });

  it('keeps interpolation placeholders aligned across locales', () => {
    for (const key of englishKeys) {
      const expected = placeholdersFor(translations['en-IN'][key]);
      for (const locale of supportedLocales) {
        assert.deepEqual(
          placeholdersFor(translations[locale][key]),
          expected,
          `${locale} placeholder drift for ${key}`
        );
      }
    }
  });

  it('falls back unsupported locales to English India', () => {
    assert.equal(resolveLocale('fr-FR'), 'en-IN');
    assert.equal(resolveLocale('hi'), 'hi-IN');
    assert.equal(resolveLocale('hi-Deva-IN'), 'hi-IN');
    assert.equal(t(resolveLocale(undefined), 'nav.home'), 'Home');
  });

  it('interpolates variables and localized pluralized counts without exposing placeholders', () => {
    assert.equal(t('en-IN', 'feature.home.next.reviewDetail', { name: 'Mira' }), 'Mira has a draft ready.');
    assert.equal(
      t('hi-IN', 'feature.home.next.checkInDetail', {
        count: tc('hi-IN', 3, {
          one: 'common.count.day.one',
          other: 'common.count.day.other'
        })
      }),
      '3 दिन से संपर्क नहीं हुआ. हल्का संदेश लिखें.'
    );
    assert.equal(
      tc('en-Hinglish', 2, {
        one: 'common.count.tile.one',
        other: 'common.count.tile.other'
      }),
      '2 tiles'
    );
    assert.equal(
      tc('hi-IN', 2, {
        one: 'feature.home.widget.tile.pendingApprovals.title.one',
        other: 'feature.home.widget.tile.pendingApprovals.title.other'
      }),
      '2 संदेश समीक्षा के लिए'
    );
    assert.equal(
      t('en-Hinglish', 'feature.home.widget.emptyState'),
      'Abhi koi event ya approval attention nahi chahta.'
    );
  });

  it('localizes primary screen headings beyond the home dashboard', () => {
    assert.equal(t('en-IN', 'feature.messages.title'), 'Messages');
    assert.equal(t('hi-IN', 'feature.contacts.title'), 'संपर्क');
    assert.equal(t('en-Hinglish', 'feature.more.detail'), 'Secondary tools core navigation se bahar rehte hain.');
  });

  it('localizes Onboarding and Home dashboard static controls', () => {
    assert.equal(t('hi-IN', 'feature.onboarding.goal'), 'लक्ष्य');
    assert.equal(t('hi-IN', 'label.onboardingGoal.remindersFirst'), 'रिमाइंडर पहले');
    assert.equal(t('en-Hinglish', 'label.onboardingGoal.manualRelationshipManager'), 'Manual relationship manager');
    assert.equal(t('en-Hinglish', 'feature.onboarding.action.skipForNow'), 'Abhi skip karo');
    assert.equal(t('en-IN', 'feature.home.checkIns.dueCount', { count: 2 }), '2 due');
    assert.equal(t('hi-IN', 'feature.checkIn.queue.summaryDue', { count: 2 }), '2 रिलेशनशिप चेक-इन समीक्षा चाहते हैं.');
    assert.equal(t('hi-IN', 'feature.checkIn.queue.emptyNoDue'), 'अभी कोई चेक-इन देय नहीं है.');
    assert.equal(t('en-Hinglish', 'feature.checkIn.reminder.title.due', { name: 'Mira' }), 'Mira ko check-in chahiye');
    assert.equal(
      t('hi-IN', 'feature.checkIn.reminder.detail.daysSince', {
        days: tc('hi-IN', 3, { one: 'common.count.day.one', other: 'common.count.day.other' }),
        cadence: tc('hi-IN', 30, { one: 'common.count.day.one', other: 'common.count.day.other' })
      }),
      'अंतिम संपर्क से 3 दिन; आवृत्ति 30 दिन है.'
    );
    assert.equal(t('en-Hinglish', 'feature.checkIn.reminder.action.markContacted'), 'Contacted mark karo');
    assert.equal(t('hi-IN', 'feature.home.setup.openOnboarding'), 'ऑनबोर्डिंग खोलें');
  });

  it('localizes Events and Add Event workflow controls', () => {
    assert.equal(t('en-IN', 'feature.events.addAction'), 'Add event');
    assert.equal(t('hi-IN', 'label.eventType.workAnniversary'), 'कार्य वर्षगांठ');
    assert.equal(t('en-Hinglish', 'feature.events.time.thisMonth'), 'This month');
    assert.equal(t('hi-IN', 'feature.events.weekday.sun'), 'रवि');
    assert.equal(t('hi-IN', 'feature.events.showAdvancedTypes'), 'और इवेंट प्रकार');
    assert.equal(t('en-Hinglish', 'feature.eventForm.hideAdvancedTypes'), 'Advanced types hide karo');
    assert.equal(
      t('en-IN', 'feature.events.month.dayWithEvents', {
        date: '2026-07-09',
        count: tc('en-IN', 2, { one: 'common.count.event.one', other: 'common.count.event.other' })
      }),
      '2026-07-09, 2 events'
    );
    assert.equal(t('hi-IN', 'feature.events.emptyFiltered'), 'इन फ़िल्टर से कोई इवेंट नहीं मिला.');
    assert.equal(
      t('hi-IN', 'feature.eventCard.meta', {
        contact: 'Mira',
        type: t('hi-IN', 'label.eventType.birthday'),
        date: '9 जुल॰ 2026'
      }),
      'Mira - जन्मदिन - 9 जुल॰ 2026'
    );
    assert.equal(t('hi-IN', 'feature.eventCard.stepStatus.needsAction'), 'कार्रवाई चाहिए');
    assert.equal(
      t('en-IN', 'feature.eventCard.nextAction', { action: 'Write wish', detail: 'Draft before Friday' }),
      'Next action: Write wish - Draft before Friday'
    );
    assert.equal(t('hi-IN', 'feature.eventCard.planReminders'), 'रिमाइंडर प्लान करें');
    assert.equal(t('en-Hinglish', 'feature.eventForm.who'), 'Yeh kiske liye hai?');
    assert.equal(t('hi-IN', 'feature.eventForm.saveReviewed'), 'समीक्षित इवेंट सेव करें');
  });

  it('localizes Messages workflow controls with count placeholders', () => {
    assert.equal(t('hi-IN', 'feature.messages.searchLabel'), 'संदेश खोजें');
    assert.equal(t('hi-IN', 'label.messageStatus.needsReview'), 'समीक्षा चाहिए');
    assert.equal(t('hi-IN', 'label.messageQuality.needsMoreContext'), 'अधिक संदर्भ चाहिए');
    assert.equal(t('en-Hinglish', 'label.channel.email'), 'Email');
    assert.equal(t('en-IN', 'feature.messages.tabCount', { tab: 'Review', count: 3 }), 'Review 3');
    assert.equal(t('hi-IN', 'feature.messages.sort.newest'), 'नवीनतम');
    assert.equal(
      t('en-IN', 'feature.messages.bulk.selectionSummary', { selected: 2, visible: 5 }),
      '2 selected from 5 visible message(s).'
    );
    assert.equal(t('hi-IN', 'feature.messages.bulk.action.approve'), 'स्वीकृत करें');
    assert.equal(
      t('en-Hinglish', 'feature.messages.bulk.eligibleSummary', {
        action: t('en-Hinglish', 'feature.messages.bulk.action.revokeApproval'),
        count: 2
      }),
      'Approval revoke karo: 2 eligible'
    );
    assert.equal(t('en-Hinglish', 'feature.messages.bulk.noneSelected'), 'Kuch selected nahi hai');
    assert.equal(
      t('hi-IN', 'feature.messages.card.meta', {
        reason: 'Birthday',
        channel: t('hi-IN', 'label.channel.email'),
        status: t('hi-IN', 'label.messageStatus.scheduled')
      }),
      'Birthday - ईमेल - शेड्यूल'
    );
    assert.equal(t('hi-IN', 'feature.messages.card.revokeApproval'), 'स्वीकृति वापस लें');
  });

  it('localizes Contacts workflow controls with profile placeholders', () => {
    assert.equal(t('hi-IN', 'feature.contacts.searchLabel'), 'संपर्क खोजें');
    assert.equal(t('hi-IN', 'label.contactGroup.all'), 'सभी');
    assert.equal(t('hi-IN', 'label.contactQuality.missingChannel'), 'चैनल विवरण नहीं है');
    assert.equal(t('en-Hinglish', 'label.contactSort.healthPriority'), 'Health priority');
    assert.equal(t('en-Hinglish', 'feature.contacts.emptyFiltered'), 'Current filters se koi contact match nahi hua.');
    assert.equal(t('en-IN', 'feature.contacts.card.health', { score: 82 }), 'Health 82');
    assert.equal(
      t('hi-IN', 'feature.contacts.card.meta', {
        relationship: 'Friend',
        group: 'दोस्त',
        health: t('hi-IN', 'feature.contacts.card.health', { score: 82 })
      }),
      'Friend - दोस्त - हेल्थ 82'
    );
    assert.equal(
      t('hi-IN', 'feature.contacts.card.nextEvent', { label: 'Birthday', date: '9 Jul 2026' }),
      'अगला इवेंट: Birthday, 9 Jul 2026'
    );
  });

  it('localizes Contact Detail static workflow controls', () => {
    assert.equal(t('en-IN', 'feature.contactDetail.status.checkInCadence', { days: 14 }), '14d check-in');
    assert.equal(
      t('hi-IN', 'feature.contactDetail.status.automationReview', {
        mode: t('hi-IN', 'feature.more.settings.automation.vipApprove')
      }),
      'VIP प्राथमिक review समीक्षा'
    );
    assert.equal(t('hi-IN', 'label.checkInStatus.snoozed'), 'स्नूज़');
    assert.equal(t('hi-IN', 'feature.contactDetail.action.snoozeCheckIn'), 'चेक-इन स्नूज़ करें');
    assert.equal(
      t('en-Hinglish', 'feature.contactDetail.essentials.detail'),
      'Saved profile details messages, reminders, gifts, aur analytics update karte hain.'
    );
    assert.equal(t('hi-IN', 'feature.contactDetail.gift.annualBudget'), 'वार्षिक उपहार बजट');
    assert.equal(t('hi-IN', 'label.contactLanguage.hindi'), 'हिंदी');
    assert.equal(t('hi-IN', 'label.relationshipHealth.needsAttention'), 'ध्यान चाहिए');
    assert.equal(
      t('hi-IN', 'feature.contactDetail.relationshipHealth.suggestedGroup', {
        group: t('hi-IN', 'feature.more.settings.group.closeFriends')
      }),
      'सुझाया गया समूह: करीबी दोस्त'
    );
    assert.equal(
      t('hi-IN', 'feature.contactDetail.relationshipHealth.suggestionConfidence', {
        confidence: t('hi-IN', 'label.confidence.medium'),
        rationale: 'Signals match'
      }),
      'मध्यम भरोसा. Signals match'
    );
    assert.equal(t('hi-IN', 'feature.contactDetail.relationshipHealth.applySuggestion'), 'सुझाव लागू करें');
    assert.equal(
      t('en-Hinglish', 'feature.contactDetail.enrichment.personalization', { score: 85 }),
      'Personalization 85%'
    );
    assert.equal(
      t('hi-IN', 'feature.contactDetail.enrichment.improves', { signal: 'Favorite food' }),
      'सुधारता है: Favorite food'
    );
    assert.equal(
      t('en-IN', 'feature.contactDetail.memory.stats', { visible: 2, total: 5, eligible: 1, private: 3, pinned: 1 }),
      '2 of 5 note(s). 1 AI-eligible, 3 private, 1 pinned.'
    );
    assert.equal(t('hi-IN', 'feature.contactDetail.memory.confirmDelete'), 'डिलीट की पुष्टि करें');
    assert.equal(
      t('en-Hinglish', 'feature.contactDetail.memory.characterCount', { count: 12, max: 500 }),
      '12/500 characters'
    );
    assert.equal(t('hi-IN', 'label.memoryCategory.preference'), 'पसंद');
    assert.equal(
      t('hi-IN', 'feature.contactDetail.memory.noteMeta', {
        status: t('hi-IN', 'feature.contactDetail.memory.pinned'),
        category: t('hi-IN', 'label.memoryCategory.private')
      }),
      'पिन किया - निजी'
    );
    assert.equal(
      t('en-Hinglish', 'feature.contactDetail.memory.aiUse.eligible'),
      'AI-eligible note: AI enabled hone par drafts aur Gift Advisor suggestions improve kar sakta hai.'
    );
    assert.equal(
      t('en-IN', 'feature.contactDetail.gift.budgetSummary', {
        annual: '₹5,000.00',
        spent: '₹2,000.00',
        remaining: '₹3,000.00'
      }),
      'Annual budget: ₹5,000.00. Spent this year: ₹2,000.00. Remaining: ₹3,000.00.'
    );
    assert.equal(t('hi-IN', 'label.giftCategory.personal'), 'व्यक्तिगत');
    assert.equal(t('hi-IN', 'label.giftFeedback.liked'), 'पसंद आया');
    assert.equal(t('hi-IN', 'label.giftBudgetFit.overBudget'), 'बजट से अधिक');
    assert.equal(t('hi-IN', 'label.confidence.high'), 'उच्च');
    assert.equal(
      t('hi-IN', 'feature.contactDetail.gift.historyMeta', {
        category: t('hi-IN', 'label.giftCategory.books'),
        occasion: 'Birthday',
        year: 2026,
        cost: '₹500',
        feedback: t('hi-IN', 'label.giftFeedback.liked')
      }),
      'किताबें, Birthday, 2026 - ₹500 - पसंद आया'
    );
    assert.equal(t('hi-IN', 'feature.contactDetail.gift.record'), 'उपहार रिकॉर्ड करें');
    assert.equal(t('en-Hinglish', 'feature.contactDetail.gift.deleteGift'), 'Gift delete karo');
    assert.equal(t('hi-IN', 'feature.contactDetail.tone.title'), 'प्राप्तकर्ता टोन');
    assert.equal(
      t('en-Hinglish', 'feature.contactDetail.channel.detail'),
      'Provider setup trusted hone tak manual safest hai.'
    );
    assert.equal(
      t('en-IN', 'feature.contactDetail.timeline.summary', { events: 2, memories: 3, gifts: 1, sent: 4 }),
      'Events 2 - Memories 3 - Gifts 1 - Sent 4'
    );
    assert.equal(t('hi-IN', 'feature.contactDetail.timeline.filter.messages'), 'संदेश');
    assert.equal(
      t('hi-IN', 'feature.contactDetail.timeline.eventMeta', {
        type: t('hi-IN', 'label.eventType.birthday'),
        status: t('hi-IN', 'feature.contactDetail.timeline.eventNeedsReview')
      }),
      'जन्मदिन - समीक्षा चाहिए'
    );
    assert.equal(
      t('hi-IN', 'feature.contactDetail.timeline.giftMeta', {
        occasion: 'Birthday',
        feedback: t('hi-IN', 'label.giftFeedback.liked')
      }),
      'Birthday - पसंद आया'
    );
    assert.equal(
      t('en-Hinglish', 'feature.contactDetail.timeline.messageMeta', {
        channel: t('en-Hinglish', 'label.channel.whatsApp'),
        status: t('en-Hinglish', 'label.messageStatus.sent')
      }),
      'WhatsApp - Sent'
    );
    assert.equal(t('hi-IN', 'feature.contactDetail.timeline.empty.gifts'), 'इस संपर्क के लिए कोई उपहार नहीं मिला.');
  });

  it('localizes Manual Composer workflow copy with contact placeholders', () => {
    assert.equal(
      t('en-IN', 'feature.manualComposer.detail', { name: 'Mira' }),
      'Write to Mira without needing an event.'
    );
    assert.equal(t('hi-IN', 'label.composerReason.thanks'), 'धन्यवाद');
    assert.equal(t('en-Hinglish', 'label.composerReason.apology'), 'Apology');
    assert.equal(t('hi-IN', 'feature.manualComposer.templates'), 'टेम्पलेट');
    assert.equal(t('en-Hinglish', 'feature.manualComposer.templateMessagePlaceholder'), 'Selected template edit karo');
  });

  it('localizes Chat History workflow copy with contact placeholders', () => {
    assert.equal(t('en-IN', 'feature.chatHistory.detail', { name: 'Mira' }), 'Mira - sent RelateAI messages');
    assert.equal(t('hi-IN', 'feature.chatHistory.searchLabel'), 'चैट इतिहास खोजें');
    assert.equal(
      t('hi-IN', 'feature.chatHistory.messageMeta', {
        channel: t('hi-IN', 'label.channel.whatsApp'),
        date: '9 जुल॰ 2026'
      }),
      'WhatsApp - 9 जुल॰ 2026'
    );
    assert.equal(
      t('en-Hinglish', 'feature.chatHistory.empty.contactUnavailable'),
      'Yeh contact ab available nahi hai. Historical messages honge to yahan dikhengi.'
    );
  });

  it('localizes Wish Preview review copy with safety placeholders', () => {
    assert.equal(t('en-IN', 'feature.wishPreview.detail', { name: 'Mira', reason: 'Birthday' }), 'Mira - Birthday');
    assert.equal(t('hi-IN', 'feature.wishPreview.scheduledFor', { date: '9 जुल॰ 2026' }), 'शेड्यूल: 9 जुल॰ 2026');
    assert.equal(t('hi-IN', 'feature.wishPreview.variant.warm'), 'गर्मजोशी');
    assert.equal(t('en-Hinglish', 'feature.wishPreview.variant.standard'), 'Standard');
    assert.equal(
      t('hi-IN', 'feature.wishPreview.confirmVariantBody', { variant: 'गर्मजोशी' }),
      'गर्मजोशी पर जाने से आपका बदला हुआ संदेश उस सेव वैरिएंट से बदल जाएगा.'
    );
    assert.equal(
      t('hi-IN', 'feature.wishPreview.aiContext.priorMessagesDetail', { count: 2 }),
      '2 योग्य पहले भेजे संदेश. संदेश का टेक्स्ट यहां नहीं दिखाया जाता.'
    );
    assert.equal(
      t('en-Hinglish', 'feature.wishPreview.feedbackLastUsedWithCustom', { count: 3 }),
      'Last regeneration ne 3 feedback instruction(s) aur custom guidance use ki.'
    );
    assert.equal(t('hi-IN', 'feature.wishPreview.action.continueAnyway'), 'फिर भी जारी रखें');
    assert.equal(
      t('en-Hinglish', 'feature.wishPreview.confirmApproveBody'),
      'Yeh message selected route ke liye schedule karta hai. Handoff ya provider delivery se pehle approval revoke kar sakte ho.'
    );
    assert.equal(t('hi-IN', 'feature.wishPreview.confirmRejectTitle'), 'ड्राफ्ट अस्वीकार करें?');
  });

  it('localizes More Account and Privacy controls', () => {
    assert.equal(
      t('en-IN', 'feature.more.account.modeSummary', { mode: 'Local', summary: '3 permission(s) enabled.' }),
      'Mode: Local. 3 permission(s) enabled.'
    );
    assert.equal(t('hi-IN', 'feature.more.account.clearLocalData'), 'लोकल डेटा साफ करें');
    assert.equal(t('en-Hinglish', 'feature.more.account.markDenied'), 'Denied mark karo');
    assert.equal(t('hi-IN', 'feature.more.account.whatsappConsentTitle'), 'Manual WhatsApp handoff सहमति');
    assert.doesNotMatch(t('hi-IN', 'feature.more.account.whatsappConsentDetail'), /ऑटोमेशन|automation/i);
    assert.equal(t('hi-IN', 'feature.more.account.whatsappConsentOff'), 'बंद');
    assert.equal(t('en-Hinglish', 'feature.more.account.revokeConsent'), 'Consent revoke karo');
  });

  it('localizes More Calendar Sync and file import controls', () => {
    assert.equal(t('en-IN', 'feature.more.calendar.counts', { exported: 2, imported: 3 }), 'Exported: 2. Imported: 3.');
    assert.equal(t('hi-IN', 'feature.more.calendar.autoDetect'), 'स्वतः पहचानें');
    assert.equal(t('en-Hinglish', 'feature.more.calendar.importPasted'), 'Pasted events import karo');
    assert.equal(t('hi-IN', 'feature.more.calendar.selectFile'), 'CSV/vCard फ़ाइल चुनें');
    assert.equal(
      t('en-IN', 'feature.more.calendar.importSummaryImported', { count: 2, source: 'family.csv', skipped: 1 }),
      '2 event candidate(s) imported from family.csv. 1 skipped; review imported events before messaging.'
    );
    assert.equal(t('hi-IN', 'feedback.calendarExportCompleteTitle'), 'कैलेंडर निर्यात पूरा');
    assert.equal(
      t('en-Hinglish', 'feedback.calendarImportCompleteMessage', { count: 3 }),
      '3 candidate event(s) import ke liye review hue.'
    );
    assert.equal(t('hi-IN', 'feedback.eventFileImportFailedFallback'), 'इवेंट आयात फ़ाइल पढ़ी नहीं जा सकी.');
  });

  it('localizes More Notification Reminder controls', () => {
    assert.equal(t('en-IN', 'feature.more.reminders.plannedCount', { count: 4 }), 'Planned reminders: 4');
    assert.equal(
      t('hi-IN', 'feature.more.reminders.blackoutRange', { start: '2026-07-09', end: '2026-07-12' }),
      '2026-07-09 से 2026-07-12'
    );
    assert.equal(
      t('en-Hinglish', 'feature.more.reminders.noBlackouts'),
      'Koi blackout window nahi hai. Zarurat par holidays, travel, ya no-reminder periods add karo.'
    );
    assert.equal(t('hi-IN', 'feature.more.reminders.planSchedule'), 'रिमाइंडर प्लान और शेड्यूल करें');
    assert.equal(
      t('hi-IN', 'feedback.remindersScheduledMessage', { scheduled: 2, skipped: 1 }),
      '2 शेड्यूल, 1 छोड़े गए.'
    );
    assert.equal(
      t('en-Hinglish', 'feedback.reminderSchedulingFailedFallback'),
      'Notification reminders schedule nahi ho paye.'
    );
  });

  it('localizes More Contact Import and Template Library controls', () => {
    assert.equal(t('hi-IN', 'feature.more.contactImport.importDevice'), 'डिवाइस संपर्क आयात करें');
    assert.equal(t('hi-IN', 'feedback.contactImportFailedTitle'), 'संपर्क आयात विफल');
    assert.equal(
      t('en-Hinglish', 'feature.more.contactImport.detail'),
      'Contacts import karo, phone/email/name se dedupe karo, aur imported birthdays sending se pehle review ke liye mark karo.'
    );
    assert.equal(t('en-Hinglish', 'feedback.manualHandoffTitle'), 'Manual handoff');
    assert.equal(t('hi-IN', 'feature.more.templateLibrary.messagePlaceholder'), 'चुना हुआ टेम्पलेट संपादित करें');
    assert.equal(t('en-Hinglish', 'feature.more.templateLibrary.createDraft'), 'Review draft create karo');
  });

  it('localizes More Persistence, Setup Wizard, and Setup Check controls', () => {
    assert.equal(
      t('hi-IN', 'feature.more.persistence.statusSaved', { status: 'तैयार', date: '9 जुल॰ 2026' }),
      'स्थिति: तैयार - सेव हुआ 9 जुल॰ 2026'
    );
    assert.equal(t('hi-IN', 'feature.more.setup.status.needsAction'), 'कार्रवाई चाहिए');
    assert.equal(t('en-Hinglish', 'feature.more.setupWizard.goal.remindersOnly'), 'Reminders only');
    assert.equal(
      t('hi-IN', 'feature.more.setupWizard.summaryProgress', {
        ready: 2,
        total: 4,
        goal: t('hi-IN', 'feature.more.setupWizard.goal.remindersOnly')
      }),
      'सिर्फ़ रिमाइंडर के लिए 2/4 सेटअप चरण तैयार हैं.'
    );
    assert.equal(t('hi-IN', 'feature.more.setupWizard.step.notifications.title'), 'नोटिफिकेशन');
    assert.match(
      t('en-IN', 'feature.more.setupWizard.detail.emailProviderOptional'),
      /provider delivery stays optional/
    );
    assert.equal(
      t('en-Hinglish', 'feature.more.setupWizard.detail.schedulingReady', {
        start: '22:00',
        end: '08:00',
        count: 1
      }),
      'Reminder planning 22:00-08:00 quiet hours aur 1 blackout window(s) respect karti hai.'
    );
    assert.equal(t('hi-IN', 'feature.more.setupWizard.recommendedNextStep'), 'सुझाया अगला कदम');
    assert.equal(t('hi-IN', 'feature.more.setupCheck.check.emailOptional.title'), 'ईमेल प्रदाता वैकल्पिक');
    assert.equal(t('en-Hinglish', 'feature.more.setupCheck.refresh'), 'Check refresh karo');
    assert.equal(
      t('hi-IN', 'feature.more.setupCheck.refreshMessage', {
        ready: 7,
        total: 10,
        blockers: 1,
        warnings: 2
      }),
      '7/10 checks तैयार. 1 blocker(s) और 2 warning(s) मिले.'
    );
    assert.equal(t('en-Hinglish', 'feature.more.setupCheck.runDryCheck'), 'Dry check run karo');
    assert.equal(t('hi-IN', 'feature.more.setupCheck.group.reliability'), 'विश्वसनीयता');
    assert.equal(
      t('hi-IN', 'feature.more.setupCheck.summaryNextFix', {
        ready: 6,
        total: 9,
        title: t('hi-IN', 'feature.more.setupCheck.check.pendingReview.title')
      }),
      '6/9 checks तैयार. अगला सुधार: समीक्षा का इंतजार करते संदेश.'
    );
    assert.equal(
      t('en-Hinglish', 'feature.more.setupCheck.impact.pendingReview', { count: 3 }),
      '3 message(s) ko schedule ya send hone se pehle review chahiye.'
    );
    assert.equal(
      t('hi-IN', 'feature.more.setupCheck.impact.privacyRecommendation', { count: 1 }),
      '1 privacy recommendation(s) उपलब्ध हैं.'
    );
    assert.match(t('en-Hinglish', 'feature.more.setupCheck.impact.emailProviderOptional'), /manual email handoff/);
  });

  it('localizes More Style Coach and AI Provider controls', () => {
    assert.equal(
      t('hi-IN', 'feature.more.styleCoach.summary', {
        confidence: 'Growing',
        formality: 'Warm',
        averageLength: 82
      }),
      'Growing: Warm. औसत 82 अक्षर.'
    );
    assert.equal(
      t('en-Hinglish', 'feature.more.styleCoach.lowConfidence'),
      'Confidence badhane ke liye aur representative samples add karo.'
    );
    assert.equal(t('hi-IN', 'feature.more.styleCoach.improveStyle'), 'मेरी शैली सुधारें');
    assert.equal(t('en-Hinglish', 'feature.more.styleCoach.improveStyle'), 'Mera style improve karo');
    assert.equal(
      t('hi-IN', 'feature.more.aiProvider.statusDetail', { status: 'तैयार' }),
      'स्थिति: तैयार. ड्राफ्टिंग केवल स्वीकृत संदर्भ भेजती है और निजी नोट, फ़ोन नंबर, ईमेल पते, क्रेडेंशियल और कच्चे प्रदाता ID बाहर रखती है.'
    );
    assert.equal(
      t('en-Hinglish', 'feature.more.aiProvider.observation', {
        result: 'failed',
        errorKind: ' (timeout)',
        durationMs: 120,
        memoryCount: 2,
        priorCount: 1
      }),
      'Last provider check: failed (timeout) in 120 ms. Context used: 2 memory item(s), 1 prior message(s).'
    );
    assert.equal(t('hi-IN', 'feature.more.aiProvider.test'), 'AI प्रदाता टेस्ट करें');
  });

  it('localizes More Analytics controls and share feedback', () => {
    assert.equal(t('hi-IN', 'feature.more.analytics.range.last30Days'), 'पिछले 30 दिन');
    assert.equal(
      t('hi-IN', 'feature.more.analytics.reconnectDetail', {
        overdueDays: 5,
        cadenceDays: 14,
        healthScore: 62
      }),
      '14-दिन कैडेंस से 5 दिन आगे. हेल्थ 62.'
    );
    assert.equal(t('en-Hinglish', 'feature.more.analytics.shareSummary'), 'Summary share karo');
    assert.equal(t('hi-IN', 'feature.more.analytics.showCsvExport'), 'CSV एक्सपोर्ट दिखाएं');
    assert.equal(t('en-Hinglish', 'feature.more.analytics.hideCsvExport'), 'CSV export chhupao');
    assert.equal(t('hi-IN', 'feature.more.analytics.csvConfirmAction'), 'CSV एक्सपोर्ट करें');
    assert.equal(t('en-Hinglish', 'feature.more.analytics.csvConfirmTitle'), 'CSV report export karein?');
    assert.equal(
      t('en-Hinglish', 'feature.more.analytics.summaryShareTitle', { range: 'Last 30 days' }),
      'Last 30 days relationship summary'
    );
    assert.equal(
      t('hi-IN', 'feature.more.analytics.shareUnavailable'),
      'शेयर उपलब्ध नहीं है. इस डिवाइस से फिर कोशिश करें.'
    );
  });

  it('localizes More Backup and Restore controls and feedback', () => {
    assert.equal(
      t('hi-IN', 'feature.more.backup.summary', { date: '9 जुल॰ 2026' }),
      'आखिरी बैकअप: 9 जुल॰ 2026. बैकअप स्पष्ट, एन्क्रिप्टेड और ऐसे पासफ़्रेज़ से सुरक्षित हैं जिसे सेव नहीं किया जाता.'
    );
    assert.equal(t('en-Hinglish', 'feature.more.backup.exportEncryptedFile'), 'Encrypted file export karo');
    assert.equal(
      t('hi-IN', 'feature.more.backup.previewDetail', {
        app: 'RelateAI',
        date: '9 जुल॰ 2026',
        count: 12,
        backupVersion: 1,
        dataVersion: 4
      }),
      'RelateAI बैकअप 9 जुल॰ 2026 से. रिकॉर्ड: 12. रीस्टोर मोड: लोकल डेटा बदलें. बैकअप संस्करण: 1. डेटा संस्करण: 4.'
    );
    assert.equal(
      t('en-Hinglish', 'feedback.backupExportedMessageShared', { count: '12 records' }),
      '12 records encrypted hue. Share sheet se destination choose karo.'
    );
    assert.equal(
      t('hi-IN', 'feedback.backupRestoreFailedFallback'),
      'बैकअप रिस्टोर नहीं हुआ. मौजूदा डेटा बदला नहीं गया.'
    );
  });

  it('localizes More Activity History controls and recovery states', () => {
    assert.equal(t('hi-IN', 'feature.more.activityHistory.searchLabel'), 'गतिविधि इतिहास खोजें');
    assert.equal(t('en-Hinglish', 'feature.more.activityHistory.type.message'), 'Message');
    assert.equal(t('hi-IN', 'feature.more.activityHistory.severity.warning'), 'चेतावनी');
    assert.equal(t('en-Hinglish', 'feature.more.activityHistory.date.last7Days'), 'Last 7 days');
    assert.equal(
      t('hi-IN', 'feature.more.activityHistory.rowMeta', { type: 'संदेश', date: '9 जुल॰ 2026' }),
      'संदेश - 9 जुल॰ 2026'
    );
    assert.equal(t('en-Hinglish', 'feature.more.activityHistory.openSetup'), 'Setup kholo');
    assert.equal(
      t('hi-IN', 'feature.more.activityHistory.recoveryMissingContact'),
      'लिंक किया गया संपर्क अब उपलब्ध नहीं है, इसलिए यह एक्शन निकटतम सुरक्षित रिकवरी स्क्रीन खोलता है.'
    );
    assert.equal(
      t('en-Hinglish', 'feature.more.activityHistory.emptyNoMatches'),
      'In filters se koi activity match nahi karti.'
    );
    assert.equal(t('hi-IN', 'feature.more.activityHistory.title.messageApproved'), 'संदेश स्वीकृत हुआ');
    assert.equal(
      t('hi-IN', 'feature.more.activityHistory.title.bulkAction', {
        action: t('hi-IN', 'feature.messages.bulk.action.approve'),
        status: t('hi-IN', 'feature.more.activityHistory.title.bulkPartiallyApplied')
      }),
      'Bulk स्वीकृत करें आंशिक रूप से लागू हुआ'
    );
    assert.equal(
      t('en-Hinglish', 'feature.more.activityHistory.title.permissionDecision', {
        capability: 'Contacts',
        decision: t('en-Hinglish', 'feature.more.activityHistory.permission.denied')
      }),
      'Contacts permission denied'
    );
    assert.equal(
      t('hi-IN', 'feature.more.activityHistory.title.whatsappHandoffConsentUpdated'),
      'Manual WhatsApp handoff सहमति अपडेट हुई'
    );
    assert.equal(
      t('hi-IN', 'feature.more.activityHistory.detail.draftReady', {
        reason: t('hi-IN', 'label.composerReason.birthday')
      }),
      'जन्मदिन ड्राफ्ट समीक्षा के लिए तैयार है.'
    );
    assert.equal(
      t('en-Hinglish', 'feature.more.activityHistory.detail.profileUpdatedWithReview', { name: 'Mira', count: 2 }),
      'Mira ka profile update hua. 2 unsent message(s) review mein wapas aaye.'
    );
  });

  it('localizes More Settings controls and defaults', () => {
    assert.equal(t('hi-IN', 'feature.more.settings.title'), 'सेटिंग्स');
    assert.equal(
      t('en-Hinglish', 'feature.more.settings.senderEmailPlaceholder'),
      'Provider delivery ke liye sender email'
    );
    assert.equal(
      t('hi-IN', 'feature.more.settings.emailProviderStatus', { status: 'तैयार', error: '' }),
      'ईमेल प्रदाता: तैयार.'
    );
    assert.equal(t('hi-IN', 'feature.more.settings.showEmailProviderSetup'), 'ईमेल प्रदाता सेटअप दिखाएं');
    assert.equal(t('en-Hinglish', 'feature.more.settings.hideEmailProviderSetup'), 'Email provider setup chhupao');
    assert.match(t('en-IN', 'feature.more.settings.emailProviderSetupDetail'), /Manual mail handoff/);
    assert.equal(t('en-Hinglish', 'feature.more.settings.toggleStatus', { setting: 'AI', status: 'on' }), 'AI on');
    assert.equal(
      t('hi-IN', 'feature.more.settings.automationSummary', {
        mode: 'हमेशा पूछें',
        start: '22:00',
        end: '08:00'
      }),
      'समीक्षा कार्यप्रवाह: हमेशा पूछें. बिना देखे send नहीं होता. शांत समय: 22:00 से 08:00.'
    );
    assert.match(t('en-IN', 'feature.more.settings.fullAutoAdvancedNotice'), /review-controlled.*not available/i);
    assert.equal(t('hi-IN', 'feature.more.settings.showAdvancedAutomation'), 'Automation availability दिखाएं');
    assert.equal(t('en-Hinglish', 'feature.more.settings.hideAdvancedAutomation'), 'Automation availability chhupao');
    assert.match(t('hi-IN', 'feature.more.settings.fullAutoConfirmBody'), /user review.*generate.*send/);
    assert.equal(t('hi-IN', 'feature.more.settings.group.closeFriends'), 'करीबी दोस्त');
    assert.equal(t('hi-IN', 'feature.more.settings.cadenceDays', { days: 30 }), '30 दिन');
    assert.equal(t('en-Hinglish', 'feature.more.settings.automation.vipApprove'), 'VIP prioritized review');
    assert.equal(t('hi-IN', 'feature.more.settings.resetDemoData'), 'डेमो डेटा रीसेट करें');
  });

  it('formats dates, months, and currency through the selected locale', () => {
    const iso = '2026-07-09T00:00:00.000Z';

    assert.match(formatDateForLocale(iso, 'en-IN'), /2026/);
    assert.match(formatDateForLocale(undefined, 'hi-IN'), /शेड्यूल/);
    assert.match(formatDateForLocale('not-a-date', 'en-Hinglish'), /valid nahi/);
    assert.match(formatMonthForLocale('2026-07', 'hi-IN'), /2026/);
    assert.equal(formatMonthForLocale('bad-month', 'en-Hinglish'), 'Date valid nahi hai');
    assert.match(formatCurrencyForLocale(2500, 'en-IN'), /₹|INR/);
  });

  it('keeps locale metadata aligned to the supported locale list', () => {
    assert.deepEqual(new Set(supportedLocales), new Set(Object.keys(localeMetadata)));
    for (const locale of supportedLocales) {
      assert.equal(localeMetadata[locale].locale, locale);
      assert.ok(localeMetadata[locale].label.length > 0);
      assert.equal(localeMetadata[locale].currency, 'INR');
    }
  });
});
