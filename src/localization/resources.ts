import { liveEnglish, liveHindi } from './liveResources';

const english = {
  ...liveEnglish,
  'common.back': 'Back',
  'common.continue': 'Continue',
  'common.close': 'Close preview',
  'common.fixtureNotice':
    'Interactive UI fixture • Synthetic data • No account, reminder, or message action is performed.',
  'common.layoutFixture': 'Layout fixture',
  'common.notVerified': 'Not verified',
  'common.ready': 'Ready',
  'common.off': 'Off',
  'common.enabled': 'Enabled',
  'common.needsAttention': 'Needs attention',
  'common.excluded': 'Excluded',
  'common.viewDetails': 'View details',
  'common.selected': 'Selected',
  'common.notSelected': 'Not selected',
  'common.fixtureOnly': 'Fixture only',
  'tabs.home': 'Home',
  'tabs.people': 'People',
  'tabs.settings': 'Settings',
  'setup.progress': 'Step {{step}} of {{total}}',
  'setup.welcomeTitle': 'Never miss an approved birthday greeting',
  'setup.androidEdition': 'Android Automation Edition',
  'setup.androidEditionBody':
    'After real device checks, explicit approval, and a successful test, Android can send an approved SMS from this phone during a delivery window.',
  'setup.iosEdition': 'iOS Companion mode',
  'setup.iosEditionBody':
    'Plan birthdays and receive reminders. You always review the draft and choose to open Apple’s Messages composer; iPhone never sends in the background.',
  'setup.reliabilityTitle': 'A window, not an exact minute',
  'setup.reliabilityBody':
    'Device and network conditions can delay a reminder or Android job. The app reports what it knows without promising delivery.',
  'setup.costTitle': 'Your carrier may charge',
  'setup.costBody':
    'Android SMS uses your selected or validated default SIM. Segment and roaming costs can apply.',
  'setup.contactsTitle': 'Connect one Google account',
  'setup.contactsBody':
    'The real app requests read-only access to names, birthdays, phone numbers, and source metadata. Raw contact values stay on this device.',
  'setup.contactsSafety':
    'Gemini receives no contact names, numbers, birthdays, or message history.',
  'setup.syntheticConnect': 'Continue with synthetic account fixture',
  'setup.chooseTitle': 'Choose people',
  'setup.chooseBody':
    'Everyone starts Off. Select only people you intend to include, then review the exact draft.',
  'setup.chooseRequired': 'Choose at least one Ready person to continue.',
  'setup.reviewSelection': 'Review selection',
  'setup.reviewTitle': 'Review this platform plan',
  'setup.androidReview':
    'SMS permission, SIM, background settings, online safety coordination, and a real test are still required. None is verified by this fixture.',
  'setup.iosReview':
    'Notification permission and MessageUI capability are still required. A reminder never opens Messages or sends by itself.',
  'setup.messageLabel': 'Proposed message',
  'setup.windowLabel': 'Delivery or reminder window',
  'setup.windowValue': '09:00–11:00 local time',
  'setup.finishAndroid': 'Finish Automation Edition preview',
  'setup.finishIos': 'Finish Companion preview',
  'home.title': 'Birthday Autopilot',
  'home.androidMode': 'Automation Edition',
  'home.iosMode': 'Companion mode',
  'home.androidStatus': 'Automation preview is not active',
  'home.androidStatusBody':
    'This UI fixture has not verified an account, SMS permission, SIM, test receipt, background readiness, or online safety coordination. No text can be sent.',
  'home.iosStatus': 'Companion preview is not scheduled',
  'home.iosStatusBody':
    'This UI fixture has not requested notification access or scheduled reminders. Messages always requires your foreground review and Send action.',
  'home.attentionResolved':
    'Fixture review complete; real device checks remain',
  'home.fix': 'Review attention item',
  'home.next': 'Next',
  'home.noUpcoming': 'No person is enabled in this fixture.',
  'home.choosePeople': 'Choose people',
  'home.viewApproved': 'View approved-message preview',
  'home.reviewComposer': 'Review companion draft',
  'home.today': 'Today',
  'home.nextSeven': 'Next 7 days',
  'home.enabledCount': 'Enabled',
  'home.contactsStatus': 'Contacts source',
  'home.contactsFixture': 'Synthetic fixture data only',
  'home.safetyStatus': 'Safety coordination',
  'home.safetyUnverified': 'No live coordination check',
  'home.reminderStatus': 'Reminder scheduler',
  'home.reminderUnverified': 'No reminders scheduled',
  'home.workerStatus': 'Android worker',
  'home.workerUnverified': 'No heartbeat verified',
  'home.pause': 'Pause fixture plan',
  'home.resume': 'Resume fixture plan',
  'home.paused': 'Fixture plan paused',
  'home.activity': 'Activity',
  'people.title': 'People',
  'people.search': 'Search people',
  'people.clearSearch': 'Clear search',
  'people.searchHint': 'Search synthetic names stored in this fixture',
  'people.filterAll': 'All',
  'people.filterEnabled': 'Enabled',
  'people.filterReady': 'Ready',
  'people.filterAttention': 'Needs attention',
  'people.filterExcluded': 'Excluded',
  'people.empty': 'No people match this search and filter.',
  'people.maskedPhone': 'Masked phone {{phone}}',
  'people.openPerson': 'Open details for {{name}}',
  'person.source': 'Source',
  'person.sourceValue': 'Synthetic Google Contacts fixture',
  'person.birthday': 'Birthday',
  'person.phone': 'Chosen phone',
  'person.message': 'Final proposed draft',
  'person.enable': 'Enable in fixture',
  'person.pause': 'Pause in fixture',
  'person.repair': 'Run repair fixture',
  'person.repaired':
    'Fixture repair complete. Real source changes would require sync and approval.',
  'person.attentionBody':
    'A real user must resolve the birthday or phone choice before approval. The app never guesses.',
  'message.title': 'Approved-message preview',
  'message.androidDisclosure':
    'This is a preview only. Android may submit this exact approved text only after every real activation gate passes. Submission would not prove delivery.',
  'message.iosDisclosure':
    'This is the proposed prefill. Review the recipient and text, then decide whether to tap Send. Apple Messages and iOS control the available sender line and final transport; this app cannot select or guarantee either.',
  'message.segmentTitle': 'Estimated SMS plan',
  'message.segmentValue': '1 Unicode segment • Carrier charges may apply',
  'message.composerFixture': 'Try companion handoff fixture',
  'message.composerResult':
    'Composer handoff recorded in this fixture. The real system composer was not opened, no final payload is known, and no message was sent.',
  'activity.title': 'Activity',
  'activity.empty': 'No fixture activity yet.',
  'activity.detailTitle': 'Activity detail',
  'activity.androidSubmitted': 'Sending from this phone',
  'activity.androidSent': 'Sent from this phone; delivery not confirmed.',
  'activity.deliveryUnknown': 'Delivery not confirmed',
  'activity.iosOpened': 'Composer opened',
  'activity.iosReported':
    'Composer reported sent; final edited payload and carrier delivery are unknown.',
  'activity.syntheticDetail':
    'Synthetic, content-minimized record. It contains no name, number, birthday, or message text.',
  'attention.title': 'Needs your attention',
  'attention.androidIssue': 'Android readiness is not verified',
  'attention.androidIssueBody':
    'Installer eligibility, SMS permission, SIM, background settings, test receipt, and online safety coordination still need real checks.',
  'attention.iosIssue': 'Companion readiness is not verified',
  'attention.iosIssueBody':
    'Notification permission, reminder scheduling, and MessageUI capability still need real checks.',
  'attention.recheck': 'Run fixture recheck',
  'attention.recheckResult':
    'Fixture review complete. Real device state was not checked, so no readiness claim was created.',
  'attention.returnHome': 'Return home',
  'settings.title': 'Settings',
  'settings.platform': 'Platform behavior',
  'settings.androidPlatform':
    'Android may automate only immutable approved SMS after all real gates pass.',
  'settings.iosPlatform':
    'iOS schedules best-effort reminders and requires a foreground, user-confirmed system composer.',
  'settings.appearance': 'Appearance',
  'settings.system': 'System',
  'settings.light': 'Light',
  'settings.dark': 'Dark',
  'settings.reminders': 'Companion reminder fixture',
  'settings.remindersBody':
    'Changes in-memory fixture state only; no notification is scheduled.',
  'settings.automation': 'Automation fixture plan',
  'settings.automationBody':
    'Changes in-memory fixture state only; it cannot enable SMS.',
  'settings.readiness': 'Device readiness',
  'settings.readinessBody': 'Review the platform-specific unresolved checks.',
  'settings.activity': 'Activity and outcomes',
  'settings.activityBody': 'View content-minimized synthetic records.',
  'settings.privacy': 'Privacy and data boundary',
  'settings.privacyBody':
    'See what the product may store and what it cannot erase.',
  'settings.replay': 'Replay setup fixture',
  'settings.replayHint': 'Clears only this in-memory synthetic UI state.',
  'privacy.title': 'Privacy and data boundary',
  'privacy.localTitle': 'Private working data stays local',
  'privacy.localBody':
    'Names, birthdays, phone numbers, approvals, schedules, and message text belong in protected on-device storage, not analytics or diagnostics.',
  'privacy.cloudTitle': 'Cloud coordination is content-free',
  'privacy.cloudBody':
    'Only opaque duplicate-safety and sender-fence records may reach the coordination service. This fixture sends nothing.',
  'privacy.externalTitle': 'External copies remain external',
  'privacy.externalBody':
    'Deleting app data cannot erase a carrier record, recipient copy, Android SMS-provider copy, Apple Messages copy, or device backup outside app control.',
  'privacy.clearFixture': 'Clear fixture and return to setup',
};

const hindi: typeof english = {
  ...english,
  ...liveHindi,
  'common.back': 'वापस',
  'common.continue': 'आगे बढ़ें',
  'common.close': 'पूर्वावलोकन बंद करें',
  'common.fixtureNotice':
    'इंटरैक्टिव UI नमूना • केवल काल्पनिक डेटा • कोई खाता, रिमाइंडर या संदेश कार्रवाई नहीं होती।',
  'common.layoutFixture': 'लेआउट नमूना',
  'common.notVerified': 'सत्यापित नहीं',
  'common.ready': 'तैयार',
  'common.off': 'बंद',
  'common.enabled': 'चालू',
  'common.needsAttention': 'ध्यान चाहिए',
  'common.excluded': 'बाहर रखा गया',
  'common.viewDetails': 'विवरण देखें',
  'common.selected': 'चुना गया',
  'common.notSelected': 'नहीं चुना गया',
  'common.fixtureOnly': 'केवल नमूना',
  'tabs.home': 'होम',
  'tabs.people': 'लोग',
  'tabs.settings': 'सेटिंग्स',
  'setup.progress': 'चरण {{step}} / {{total}}',
  'setup.welcomeTitle': 'स्वीकृत जन्मदिन संदेश याद रखें',
  'setup.androidEdition': 'Android ऑटोमेशन संस्करण',
  'setup.androidEditionBody':
    'वास्तविक डिवाइस जाँच, स्पष्ट स्वीकृति और सफल टेस्ट के बाद Android तय समय-सीमा में इस फ़ोन से स्वीकृत SMS भेज सकता है।',
  'setup.iosEdition': 'iOS कम्पैनियन मोड',
  'setup.iosEditionBody':
    'जन्मदिन की योजना और रिमाइंडर पाएँ। ड्राफ़्ट की समीक्षा करके Apple Messages कंपोज़र खोलना हमेशा आपका निर्णय है; iPhone पृष्ठभूमि में नहीं भेजता।',
  'setup.reliabilityTitle': 'समय-सीमा, सटीक मिनट नहीं',
  'setup.reliabilityBody':
    'डिवाइस और नेटवर्क की स्थिति Android कार्य या रिमाइंडर में देरी कर सकती है। ऐप डिलीवरी का वादा नहीं करता।',
  'setup.costTitle': 'कैरियर शुल्क लग सकता है',
  'setup.costBody':
    'Android SMS चुने या सत्यापित डिफ़ॉल्ट SIM से जाता है। सेगमेंट और रोमिंग शुल्क लागू हो सकते हैं।',
  'setup.contactsTitle': 'एक Google खाता जोड़ें',
  'setup.contactsBody':
    'वास्तविक ऐप नाम, जन्मदिन, फ़ोन नंबर और स्रोत मेटाडेटा के केवल-पढ़ने अधिकार माँगता है। कच्चे संपर्क मान इसी डिवाइस पर रहते हैं।',
  'setup.contactsSafety':
    'Gemini को संपर्क नाम, नंबर, जन्मदिन या संदेश इतिहास नहीं मिलता।',
  'setup.syntheticConnect': 'काल्पनिक खाता नमूने के साथ आगे बढ़ें',
  'setup.chooseTitle': 'लोग चुनें',
  'setup.chooseBody':
    'हर व्यक्ति शुरू में बंद है। केवल इच्छित लोगों को चुनें, फिर सटीक ड्राफ़्ट देखें।',
  'setup.chooseRequired': 'आगे बढ़ने के लिए कम से कम एक तैयार व्यक्ति चुनें।',
  'setup.reviewSelection': 'चयन की समीक्षा करें',
  'setup.reviewTitle': 'इस प्लेटफ़ॉर्म की योजना देखें',
  'setup.androidReview':
    'SMS अनुमति, SIM, पृष्ठभूमि सेटिंग, ऑनलाइन सुरक्षा समन्वय और वास्तविक टेस्ट अभी भी ज़रूरी हैं। यह नमूना इन्हें सत्यापित नहीं करता।',
  'setup.iosReview':
    'सूचना अनुमति और MessageUI क्षमता अभी भी ज़रूरी हैं। रिमाइंडर खुद Messages नहीं खोलता या भेजता।',
  'setup.messageLabel': 'प्रस्तावित संदेश',
  'setup.windowLabel': 'डिलीवरी या रिमाइंडर समय-सीमा',
  'setup.windowValue': 'स्थानीय समय 09:00–11:00',
  'setup.finishAndroid': 'ऑटोमेशन संस्करण पूर्वावलोकन पूरा करें',
  'setup.finishIos': 'कम्पैनियन पूर्वावलोकन पूरा करें',
  'home.title': 'Birthday Autopilot',
  'home.androidMode': 'ऑटोमेशन संस्करण',
  'home.iosMode': 'कम्पैनियन मोड',
  'home.androidStatus': 'ऑटोमेशन पूर्वावलोकन सक्रिय नहीं है',
  'home.androidStatusBody':
    'इस UI नमूने ने खाता, SMS अनुमति, SIM, टेस्ट रसीद, पृष्ठभूमि तैयारी या ऑनलाइन सुरक्षा समन्वय सत्यापित नहीं किया। कोई टेक्स्ट नहीं भेजा जा सकता।',
  'home.iosStatus': 'कम्पैनियन पूर्वावलोकन निर्धारित नहीं है',
  'home.iosStatusBody':
    'इस UI नमूने ने सूचना अधिकार नहीं माँगा या रिमाइंडर तय नहीं किया। Messages में आपकी सामने की समीक्षा और Send कार्रवाई हमेशा ज़रूरी है।',
  'home.attentionResolved': 'नमूना समीक्षा पूरी; वास्तविक डिवाइस जाँच बाकी',
  'home.fix': 'ध्यान वाली चीज़ देखें',
  'home.next': 'अगला',
  'home.noUpcoming': 'इस नमूने में कोई व्यक्ति चालू नहीं है।',
  'home.choosePeople': 'लोग चुनें',
  'home.viewApproved': 'स्वीकृत संदेश का पूर्वावलोकन देखें',
  'home.reviewComposer': 'कम्पैनियन ड्राफ़्ट देखें',
  'home.today': 'आज',
  'home.nextSeven': 'अगले 7 दिन',
  'home.enabledCount': 'चालू',
  'home.contactsStatus': 'संपर्क स्रोत',
  'home.contactsFixture': 'केवल काल्पनिक नमूना डेटा',
  'home.safetyStatus': 'सुरक्षा समन्वय',
  'home.safetyUnverified': 'कोई लाइव समन्वय जाँच नहीं',
  'home.reminderStatus': 'रिमाइंडर शेड्यूलर',
  'home.reminderUnverified': 'कोई रिमाइंडर तय नहीं',
  'home.workerStatus': 'Android वर्कर',
  'home.workerUnverified': 'हार्टबीट सत्यापित नहीं',
  'home.pause': 'नमूना योजना रोकें',
  'home.resume': 'नमूना योजना फिर शुरू करें',
  'home.paused': 'नमूना योजना रुकी है',
  'home.activity': 'गतिविधि',
  'people.title': 'लोग',
  'people.search': 'लोग खोजें',
  'people.clearSearch': 'Clear search',
  'people.searchHint': 'इस नमूने में रखे काल्पनिक नाम खोजें',
  'people.filterAll': 'सभी',
  'people.filterEnabled': 'चालू',
  'people.filterReady': 'तैयार',
  'people.filterAttention': 'ध्यान चाहिए',
  'people.filterExcluded': 'बाहर',
  'people.empty': 'इस खोज और फ़िल्टर से कोई व्यक्ति नहीं मिला।',
  'people.maskedPhone': 'छिपा फ़ोन {{phone}}',
  'people.openPerson': '{{name}} का विवरण खोलें',
  'person.source': 'स्रोत',
  'person.sourceValue': 'काल्पनिक Google Contacts नमूना',
  'person.birthday': 'जन्मदिन',
  'person.phone': 'चुना फ़ोन',
  'person.message': 'अंतिम प्रस्तावित ड्राफ़्ट',
  'person.enable': 'नमूने में चालू करें',
  'person.pause': 'नमूने में रोकें',
  'person.repair': 'मरम्मत नमूना चलाएँ',
  'person.repaired':
    'नमूना मरम्मत पूरी। वास्तविक स्रोत बदलाव के बाद सिंक और स्वीकृति चाहिए।',
  'person.attentionBody':
    'वास्तविक उपयोगकर्ता को स्वीकृति से पहले जन्मदिन या फ़ोन विकल्प ठीक करना होगा। ऐप अनुमान नहीं लगाता।',
  'message.title': 'स्वीकृत संदेश पूर्वावलोकन',
  'message.androidDisclosure':
    'यह केवल पूर्वावलोकन है। सभी वास्तविक सक्रियण जाँच पास होने पर ही Android यही स्वीकृत टेक्स्ट जमा कर सकता है। जमा होना डिलीवरी का प्रमाण नहीं है।',
  'message.iosDisclosure':
    'यह प्रस्तावित प्रीफ़िल है। प्राप्तकर्ता और टेक्स्ट की समीक्षा करके तय करें कि Send दबाना है या नहीं। उपलब्ध भेजने वाली लाइन और अंतिम ट्रांसपोर्ट को Apple Messages व iOS नियंत्रित करते हैं; यह ऐप किसी को चुन या पक्का नहीं कर सकता।',
  'message.segmentTitle': 'अनुमानित SMS योजना',
  'message.segmentValue': '1 यूनिकोड सेगमेंट • कैरियर शुल्क लग सकता है',
  'message.composerFixture': 'कम्पैनियन हैंडऑफ़ नमूना आज़माएँ',
  'message.composerResult':
    'इस नमूने में कंपोज़र हैंडऑफ़ दर्ज हुआ। वास्तविक सिस्टम कंपोज़र नहीं खुला, अंतिम सामग्री ज्ञात नहीं और कोई संदेश नहीं भेजा गया।',
  'activity.title': 'गतिविधि',
  'activity.empty': 'अभी कोई नमूना गतिविधि नहीं।',
  'activity.detailTitle': 'गतिविधि विवरण',
  'activity.androidSubmitted': 'इस फ़ोन से भेजा जा रहा है',
  'activity.androidSent': 'इस फ़ोन से भेजा गया; डिलीवरी की पुष्टि नहीं।',
  'activity.deliveryUnknown': 'डिलीवरी की पुष्टि नहीं',
  'activity.iosOpened': 'कंपोज़र खुला',
  'activity.iosReported':
    'कंपोज़र ने भेजा बताया; अंतिम संपादित सामग्री और कैरियर डिलीवरी अज्ञात हैं।',
  'activity.syntheticDetail':
    'काल्पनिक, कम-सामग्री रिकॉर्ड। इसमें नाम, नंबर, जन्मदिन या संदेश टेक्स्ट नहीं है।',
  'attention.title': 'आपका ध्यान चाहिए',
  'attention.androidIssue': 'Android तैयारी सत्यापित नहीं है',
  'attention.androidIssueBody':
    'इंस्टॉलर पात्रता, SMS अनुमति, SIM, पृष्ठभूमि सेटिंग, टेस्ट रसीद और ऑनलाइन सुरक्षा समन्वय की वास्तविक जाँच बाकी है।',
  'attention.iosIssue': 'कम्पैनियन तैयारी सत्यापित नहीं है',
  'attention.iosIssueBody':
    'सूचना अनुमति, रिमाइंडर शेड्यूलिंग और MessageUI क्षमता की वास्तविक जाँच बाकी है।',
  'attention.recheck': 'नमूना दोबारा जाँचें',
  'attention.recheckResult':
    'नमूना समीक्षा पूरी। वास्तविक डिवाइस स्थिति नहीं जाँची गई, इसलिए तैयारी का दावा नहीं बनाया गया।',
  'attention.returnHome': 'होम पर लौटें',
  'settings.title': 'सेटिंग्स',
  'settings.platform': 'प्लेटफ़ॉर्म व्यवहार',
  'settings.androidPlatform':
    'सभी वास्तविक जाँच पास होने पर ही Android अपरिवर्तनीय स्वीकृत SMS स्वचालित कर सकता है।',
  'settings.iosPlatform':
    'iOS अनुमानित रिमाइंडर तय करता है और सामने उपयोगकर्ता-पुष्ट सिस्टम कंपोज़र माँगता है।',
  'settings.appearance': 'रूप',
  'settings.system': 'सिस्टम',
  'settings.light': 'हल्का',
  'settings.dark': 'गहरा',
  'settings.reminders': 'कम्पैनियन रिमाइंडर नमूना',
  'settings.remindersBody':
    'केवल मेमोरी में नमूना स्थिति बदलती है; कोई सूचना तय नहीं होती।',
  'settings.automation': 'ऑटोमेशन नमूना योजना',
  'settings.automationBody':
    'केवल मेमोरी में नमूना स्थिति बदलती है; इससे SMS चालू नहीं हो सकता।',
  'settings.readiness': 'डिवाइस तैयारी',
  'settings.readinessBody': 'प्लेटफ़ॉर्म की अधूरी जाँच देखें।',
  'settings.activity': 'गतिविधि और परिणाम',
  'settings.activityBody': 'कम-सामग्री काल्पनिक रिकॉर्ड देखें।',
  'settings.privacy': 'गोपनीयता और डेटा सीमा',
  'settings.privacyBody':
    'देखें उत्पाद क्या रख सकता है और क्या नहीं मिटा सकता।',
  'settings.replay': 'सेटअप नमूना दोहराएँ',
  'settings.replayHint': 'केवल मेमोरी का काल्पनिक UI डेटा साफ़ होता है।',
  'privacy.title': 'गोपनीयता और डेटा सीमा',
  'privacy.localTitle': 'निजी कार्य डेटा स्थानीय रहता है',
  'privacy.localBody':
    'नाम, जन्मदिन, फ़ोन नंबर, स्वीकृति, शेड्यूल और संदेश टेक्स्ट सुरक्षित ऑन-डिवाइस स्टोरेज में होने चाहिए, एनालिटिक्स या डायग्नोस्टिक्स में नहीं।',
  'privacy.cloudTitle': 'क्लाउड समन्वय सामग्री-रहित है',
  'privacy.cloudBody':
    'केवल अपारदर्शी डुप्लिकेट-सुरक्षा और सेंडर-फेंस रिकॉर्ड समन्वय सेवा तक जा सकते हैं। यह नमूना कुछ नहीं भेजता।',
  'privacy.externalTitle': 'बाहरी प्रतियाँ बाहर रहती हैं',
  'privacy.externalBody':
    'ऐप डेटा मिटाने से कैरियर रिकॉर्ड, प्राप्तकर्ता प्रति, Android SMS-provider प्रति, Apple Messages प्रति या ऐप नियंत्रण से बाहर बैकअप नहीं मिटता।',
  'privacy.clearFixture': 'नमूना साफ़ करके सेटअप पर लौटें',
};

const pseudo = Object.fromEntries(
  Object.entries(english).map(([key, value]) => [key, `⟦ ${value} ··· ⟧`]),
) as typeof english;

export const resources = {
  en: { translation: english },
  hi: { translation: hindi },
  'ar-XB': { translation: pseudo },
} as const;

export type { TranslationKey } from './productionResources';
