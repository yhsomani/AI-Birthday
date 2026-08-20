import type {
  MessageDraft,
  MessageDraftInput,
  PlaceholderMode,
} from '../messages/model';
import type { PrivateMessageText } from '../shared/brand';
import type { FieldIssue, UiDraftValidation } from '../shared/result';

const FIRST_NAME_PLACEHOLDER = '{firstName}';
const PLACEHOLDER_PATTERN = /\{[^{}]+\}/gu;
const BIDI_CONTROL_PATTERN = /[\u061C\u200E\u200F\u202A-\u202E\u2066-\u2069]/u;
const FORBIDDEN_INVISIBLE_PATTERN = /[\u200B\u2060\uFEFF]/u;

// Keep Unicode-property expressions in RegExp constructor strings. Metro's
// compatibility transform expands property escapes into very large character
// tables even though every supported Hermes/JSC runtime implements them. An
// unexpectedly old runtime fails closed instead of crashing the whole app.
const unicodePattern = (source: string, flags: string): RegExp | undefined => {
  try {
    return new RegExp(source, flags);
  } catch {
    return undefined;
  }
};

const UNSAFE_UNICODE_CATEGORY_PATTERN = unicodePattern(
  '[\\p{Cc}\\p{Zl}\\p{Zp}]',
  'u',
);
const UNICODE_SEPARATOR_PATTERN = unicodePattern('[\\p{Z}\\s]+', 'gu');
const UNICODE_LETTER_PATTERN = unicodePattern('\\p{L}', 'gu');
const DEVANAGARI_LETTER_PATTERN = unicodePattern(
  '^\\p{Script=Devanagari}$',
  'u',
);
const LATIN_LETTER_PATTERN = unicodePattern('^\\p{Script=Latin}$', 'u');
export const BIRTHDAY_MESSAGE_SEMANTIC_POLICY_VERSION =
  'birthday-message-semantic-v2' as const;

export const BIRTHDAY_MESSAGE_CONTENT_CATEGORIES = [
  'birthday-intent-required',
  'url',
  'tracking-or-affiliate',
  'promotion',
  'literal-personal-data',
  'age',
  'gender',
  'religion',
  'health',
  'relationship',
  'private-memory',
  'hate',
  'sexual',
  'self-harm',
  'violence',
  'deception',
] as const;

export type BirthdayMessageContentCategory =
  (typeof BIRTHDAY_MESSAGE_CONTENT_CATEGORIES)[number];

const BIRTHDAY_INTENT_EN =
  /\b(?:birthday|b[\s-]?day|bday)\b|\bmany\s+happy\s+returns\b/iu;
const BIRTHDAY_INTENT_HI = /(?:जन्म\s*दिन|जन्मदिवस)/u;
const URL_SCHEME_OR_WWW =
  /(?:\b(?:https?|ftp)\s*:\s*\/\s*\/|\b(?:mailto|tel|sms|smsto)\s*:|\bwww\.)\S+/iu;
const URL_DOMAIN = unicodePattern(
  '\\b(?:[\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]{0,62}[\\p{L}\\p{N}])?\\.)+(?:[a-z]{2,63}|xn--[a-z0-9-]{2,59})(?:[/?:#]\\S*)?',
  'giu',
);
const URL_OBFUSCATED_DOMAIN = unicodePattern(
  '\\b[\\p{L}\\p{N}][\\p{L}\\p{N}-]{0,62}\\s*(?:\\[\\s*dot\\s*\\]|\\(\\s*dot\\s*\\)|\\s+dot\\s+)\\s*(?:[a-z]{2,63}|xn--[a-z0-9-]{2,59})\\b',
  'iu',
);
const IPV4 = /\b(?:[0-9]{1,3}\.){3}[0-9]{1,3}\b/u;
const EMAIL = /\b[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9-]+(?:\.[a-z0-9-]+)+\b/iu;
const TRACKING_OR_AFFILIATE = unicodePattern(
  '(?:\\butm_[a-z0-9_]+\\s*=|\\b(?:gclid|fbclid|msclkid|ref|referrer|affiliate_id|aff_id)\\s*=|#[\\p{L}\\p{N}_]{1,254}|\\b(?:affiliate|referral|sponsored)\\s+(?:link|code|post)|\\buse\\s+(?:my|code)\\s+(?:affiliate\\s+)?code\\b|\\bearns?\\s+(?:a\\s+)?commission\\b|(?:रेफरल|एफिलिएट|संबद्ध)\\s*(?:लिंक|कोड)|(?:प्रायोजित|कमीशन))',
  'iu',
);
const PROMOTION =
  /\b(?:limited(?:[- ]time)? offer|special offer|special deal|flash sale|birthday sale|discount(?: code)?|coupon(?: code)?|promo(?: code)?|buy now|shop now|order now|free offer|free gift|claim (?:your )?(?:offer|gift|discount)|save [0-9]{1,3}%|[0-9]{1,3}% off|subscribe(?: now| today)?|start (?:a|your) subscription)\b|(?:सीमित|खास|विशेष)\s*(?:समय का\s*)?ऑफर|अभी\s*(?:खरीदें|ऑर्डर करें)|(?:विशेष\s*)?छूट|कूपन|प्रोमो\s*कोड|मुफ़्त\s*(?:ऑफर|उपहार)|फ्लैश\s*सेल|सदस्यता\s*लें/iu;
const PHONE_NUMBER = unicodePattern(
  '(?:^|[^\\p{L}\\p{N}])(?:\\+?[0-9०-९][\\s().-]{0,254}){10,15}(?:$|[^\\p{L}\\p{N}])',
  'u',
);
const NUMERIC_DATE =
  /\b(?:[0-9]{4}[-/.][0-9]{1,2}[-/.][0-9]{1,2}|[0-9]{1,2}[-/.][0-9]{1,2}[-/.][0-9]{2,4})\b/u;
const ENGLISH_DATE =
  /\b(?:(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sept?(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\s+[0-9]{1,2}(?:st|nd|rd|th)?(?:,?\s+[0-9]{4})?|[0-9]{1,2}(?:st|nd|rd|th)?\s+(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sept?(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)(?:\s+[0-9]{4})?)\b/iu;
const HINDI_DATE =
  /[0-9०-९]{1,2}\s*(?:जनवरी|फरवरी|मार्च|अप्रैल|मई|जून|जुलाई|अगस्त|सितंबर|अक्टूबर|नवंबर|दिसंबर)(?:\s*[0-9०-९]{2,4})?/u;
const IDENTITY_LABEL =
  /\b(?:your|my)\s+(?:full name|phone number|mobile number|email address|home address|aadhaar(?: number)?|passport(?: number)?|social security number|ssn|date of birth|birth date)\b|(?:आपका|आपकी|मेरा|मेरी)\s*(?:पूरा नाम|फोन नंबर|मोबाइल नंबर|ईमेल|घर का पता|आधार नंबर|पासपोर्ट नंबर|जन्म तिथि)/iu;
const AGE_EN =
  /\b(?:turning\s+[0-9]{1,3}\b(?!\s+(?:pages?|chapters?|books?|degrees?|minutes?|seconds?|ideas?|recipes?))|[0-9]{1,3}(?:st|nd|rd|th)\s+birthday|[0-9]{1,3}\s+years?\s+old|(?:age|aged)\s+[0-9]{1,3}|[0-9]{1,3}\s+candles?)\b/iu;
const AGE_HI =
  /[0-9०-९]{1,3}\s*(?:वां|वाँ|वीं)?\s*जन्मदिन|[0-9०-९]{1,3}\s*साल\s*के\s*हो\s*गए|उम्र\s*[0-9०-९]{1,3}|[0-9०-९]{1,3}\s*मोमबत्त/iu;
const GENDER =
  /\b(?:birthday\s+(?:girl|boy|woman|man)|you\s+are\s+(?:a\s+)?(?:woman|man|girl|boy|female|male)|as\s+(?:a|the)\s+(?:woman|man|girl|boy))\b|(?:आप|तुम)\s*(?:एक\s*)?(?:शानदार\s+)?(?:महिला|पुरुष|लड़की|लड़का)\s*(?:हैं|हो)|जन्मदिन\s+(?:की\s+लड़की|का\s+लड़का)/iu;
const RELIGION =
  /\b(?:god|jesus|allah|christ|lord)\s+(?:bless|protect|guide)s?\s+you\b|\b(?:as\s+(?:a|your)\s+|you\s+are\s+(?:a\s+)?)(?:hindu|muslim|christian|jewish|sikh|buddhist)\b|(?:भगवान|ईश्वर|अल्लाह|यीशु|वाहेगुरु)\s*(?:आपको|तुम्हें)?\s*आशीर्वाद|(?:आप|तुम)\s*(?:हिंदू|मुसलमान|ईसाई|सिख|बौद्ध)\s*(?:हैं|हो)/iu;
const HEALTH =
  /\b(?:your\s+(?:illness|diagnosis|disease|disability|medical condition|cancer|diabetes)|recover(?:y|ing)?\s+from\s+(?:your\s+)?(?:illness|diagnosis|surgery|cancer|disease)|get well soon|beat(?:ing)?\s+(?:cancer|your illness|the disease))\b|(?:आपकी|तुम्हारी)\s*(?:बीमारी|निदान|विकलांगता|चिकित्सा स्थिति|कैंसर|मधुमेह)|(?:बीमारी|ऑपरेशन|कैंसर)\s*से\s*जल्द\s*ठीक/iu;
const RELATIONSHIP =
  /\b(?:(?:my|your)\s+(?:wife|husband|girlfriend|boyfriend|partner|daughter|son|mother|father|sister|brother|best friend)|as\s+your\s+(?:wife|husband|girlfriend|boyfriend|partner)|our\s+(?:marriage|relationship|friendship))\b|(?:मेरी|आपकी|तुम्हारी)\s*(?:पत्नी|पति|प्रेमिका|प्रेमी|बेटी|बेटा|माँ|पिता|बहन|भाई)|हमारा\s*(?:विवाह|रिश्ता)/iu;
const PRIVATE_MEMORY =
  /\b(?:remember\s+(?:when(?!\s+to\b)|our|the time)|our\s+secret\b(?!\s+recipe)|inside\s+joke|the\s+trip\s+we\s+took|that\s+night\s+we)\b|(?:याद\s+है\s+जब|हमारा\s+राज़|हमारी\s+गुप्त\s+(?:यात्रा|बात)|हम\s+जब\s+साथ)/iu;
const HATE =
  /\b(?:hate|despise)\s+(?:all\s+)?(?:women|men|muslims?|hindus?|christians?|jews?|sikhs?|gays?|lesbians?|transgender\s+people|disabled\s+people|people\s+of\s+(?:a\s+)?(?:race|caste|religion))\b|\b(?:inferior|disgusting)\s+(?:race|caste|religion)\b|(?:महिलाओं|पुरुषों|मुसलमानों|हिंदुओं|ईसाइयों|सिखों|समलैंगिकों|विकलांगों)\s*से\s*नफरत|(?:जाति|धर्म)\s*(?:नीच|घटिया)/iu;
const SEXUAL =
  /\b(?:sex(?:ual)?|sexy|nude|naked|porn(?:ography)?|sleep\s+with\s+me|explicit\s+photos?)\b|(?:यौन|सेक्सी|नग्न|अश्लील|पोर्न)/iu;
const SELF_HARM =
  /\b(?:kill\s+yourself|end\s+your\s+(?:life|pain)|commit\s+suicide|suicide|self[- ]?harm|hurt\s+yourself)\b|(?:आत्महत्या|खुद\s+को\s+मार|अपनी\s+जान\s+ले|खुद\s+को\s+नुकसान)/iu;
const VIOLENCE =
  /\b(?:kill|murder|hurt|attack|shoot|stab|beat)\s+(?:you|him|her|them|someone|people)\b|\b(?:death|bomb)\s+threat\b|(?:आपको|तुम्हें|उसे|उन्हें)\s*(?:मार\s*(?:दूँगा|दूंगा|डालूँगा|डालूंगा)|गोली\s+मार|चाकू\s+मार|पीट)|(?:जान\s+से\s+मारने|बम)\s+की\s+धमकी/iu;
const DECEPTION =
  /\b(?:you(?:'ve| have)\s+won\s+(?:a\s+)?(?:prize|lottery)|share\s+your\s+(?:otp|pin|password)|send\s+(?:money|payment|your\s+otp)|your\s+(?:bank\s+)?account\s+is\s+(?:locked|suspended)|i\s+am\s+from\s+your\s+bank|guaranteed\s+(?:prize|returns?)|urgent\s+payment)\b|(?:आपका|तुम्हारा)\s*बैंक\s*खाता\s*(?:बंद|निलंबित)|(?:otp|पिन|पासवर्ड)\s*(?:भेजें|बताएं|साझा करें)|आप\s*(?:इनाम|लॉटरी)\s*जीत/iu;

const semanticView = (text: string): string =>
  text
    .normalize('NFKC')
    .toLocaleLowerCase('en-US')
    .replace(UNICODE_SEPARATOR_PATTERN ?? /\s+/gu, ' ')
    .trim();

const BENIGN_DOTTED_TERMS = new Set(['node.js', 'dr.strange']);
const containsNonBenignUrlDomain = (text: string): boolean =>
  URL_DOMAIN === undefined ||
  Array.from(text.matchAll(URL_DOMAIN)).some(
    match => !BENIGN_DOTTED_TERMS.has(match[0].toLocaleLowerCase('en-US')),
  );

export const classifyBirthdayMessageContent = (
  text: string,
  language: MessageDraftInput['language'],
): readonly BirthdayMessageContentCategory[] => {
  const value = semanticView(text);
  const categories: BirthdayMessageContentCategory[] = [];
  if (
    !(language === 'hi' ? BIRTHDAY_INTENT_HI : BIRTHDAY_INTENT_EN).test(value)
  ) {
    categories.push('birthday-intent-required');
  }
  if (
    URL_SCHEME_OR_WWW.test(value) ||
    containsNonBenignUrlDomain(value) ||
    URL_OBFUSCATED_DOMAIN?.test(value) !== false ||
    IPV4.test(value) ||
    EMAIL.test(value)
  ) {
    categories.push('url');
  }
  if (TRACKING_OR_AFFILIATE?.test(value) !== false)
    categories.push('tracking-or-affiliate');
  if (PROMOTION.test(value)) {
    categories.push('promotion');
  }
  if (
    PHONE_NUMBER?.test(value) !== false ||
    NUMERIC_DATE.test(value) ||
    ENGLISH_DATE.test(value) ||
    HINDI_DATE.test(value) ||
    IDENTITY_LABEL.test(value) ||
    EMAIL.test(value)
  ) {
    categories.push('literal-personal-data');
  }
  if (AGE_EN.test(value) || AGE_HI.test(value)) categories.push('age');
  if (GENDER.test(value)) categories.push('gender');
  if (RELIGION.test(value)) categories.push('religion');
  if (HEALTH.test(value)) categories.push('health');
  if (RELATIONSHIP.test(value)) categories.push('relationship');
  if (PRIVATE_MEMORY.test(value)) categories.push('private-memory');
  if (HATE.test(value)) categories.push('hate');
  if (SEXUAL.test(value)) categories.push('sexual');
  if (SELF_HARM.test(value)) categories.push('self-harm');
  if (VIOLENCE.test(value)) categories.push('violence');
  if (DECEPTION.test(value)) categories.push('deception');
  return categories;
};

const hasUnsafeControlCharacter = (value: string): boolean =>
  UNSAFE_UNICODE_CATEGORY_PATTERN === undefined ||
  Array.from(value).some(
    character =>
      UNSAFE_UNICODE_CATEGORY_PATTERN.test(character) ||
      FORBIDDEN_INVISIBLE_PATTERN.test(character),
  );

const matchesDeclaredLanguage = (
  value: string,
  language: MessageDraftInput['language'],
): boolean => {
  const templateOnly = value.replaceAll(FIRST_NAME_PLACEHOLDER, '');
  const letterPattern = UNICODE_LETTER_PATTERN;
  const scriptPattern =
    language === 'hi' ? DEVANAGARI_LETTER_PATTERN : LATIN_LETTER_PATTERN;
  if (letterPattern === undefined || scriptPattern === undefined) {
    return false;
  }
  const letters = templateOnly.match(letterPattern) ?? [];
  if (letters.length === 0) {
    return false;
  }
  return letters.every(letter => scriptPattern.test(letter));
};

const issue = (code: FieldIssue['code']): FieldIssue => ({
  field: 'template',
  code,
});

export const validateTemplateDraft = (
  input: MessageDraftInput,
): UiDraftValidation<MessageDraft> => {
  const issues: FieldIssue[] = [];
  const text = input.text.normalize('NFC');

  if (text.trim().length === 0) {
    issues.push(issue('template-empty'));
  }

  const placeholders = text.match(PLACEHOLDER_PATTERN) ?? [];
  const textWithoutCompletePlaceholders = text.replace(PLACEHOLDER_PATTERN, '');
  if (
    placeholders.some(value => value !== FIRST_NAME_PLACEHOLDER) ||
    /[{}]/u.test(textWithoutCompletePlaceholders)
  ) {
    issues.push(issue('template-unsupported-placeholder'));
  }

  const firstNameCount = placeholders.filter(
    value => value === FIRST_NAME_PLACEHOLDER,
  ).length;
  const requiredCount = input.placeholderMode === 'given-name' ? 1 : 0;
  if (firstNameCount !== requiredCount) {
    issues.push(issue('template-placeholder-count'));
  }

  if (hasUnsafeControlCharacter(text)) {
    issues.push(issue('template-control-character'));
  }
  if (BIDI_CONTROL_PATTERN.test(text)) {
    issues.push(issue('template-bidi-control'));
  }
  const semanticCategories = classifyBirthdayMessageContent(
    text,
    input.language,
  );
  if (semanticCategories.includes('url')) {
    issues.push(issue('template-url-not-allowed'));
  }
  if (semanticCategories.includes('tracking-or-affiliate')) {
    issues.push(issue('template-tracking-not-allowed'));
  }
  if (semanticCategories.includes('promotion')) {
    issues.push(issue('template-promotional-content'));
  }
  if (
    semanticCategories.some(category =>
      [
        'literal-personal-data',
        'age',
        'gender',
        'religion',
        'health',
        'relationship',
        'private-memory',
        'hate',
        'sexual',
        'self-harm',
        'violence',
        'deception',
      ].includes(category),
    )
  ) {
    issues.push(issue('template-sensitive-content'));
  }
  if (semanticCategories.includes('birthday-intent-required')) {
    issues.push(issue('template-birthday-intent-required'));
  }
  if (!matchesDeclaredLanguage(text, input.language)) {
    issues.push(issue('template-language-mismatch'));
  }
  if (input.requestedSegmentCap !== 1 && input.requestedSegmentCap !== 2) {
    issues.push(issue('invalid-segment-cap'));
  }

  if (issues.length > 0) {
    return { kind: 'invalid', authority: 'ui-only', issues };
  }

  const placeholderMode: PlaceholderMode =
    input.placeholderMode === 'given-name'
      ? { kind: 'given-name', requiredCount: 1 }
      : { kind: 'generic', requiredCount: 0 };
  const requestedSegmentCap = input.requestedSegmentCap as 1 | 2;

  return {
    kind: 'valid',
    authority: 'ui-only',
    value: {
      language: input.language,
      tone: input.tone,
      placeholderMode,
      text: text as PrivateMessageText,
      requestedSegmentCap,
    },
  };
};
