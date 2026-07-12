package com.yashsomani.birthdayautopilot.messages

import com.yashsomani.birthdayautopilot.contacts.UnicodeTextSafety
import java.text.Normalizer
import java.util.Locale

enum class MessageLanguage {
  ENGLISH,
  HINDI,
}

enum class TemplatePlaceholderMode {
  PERSONALIZED_FIRST_NAME,
  GENERIC_NO_NAME,
}

enum class TemplateSource {
  BUILT_IN,
  USER_EDITED,
  GEMINI_SELECTED,
}

data class MessageTemplate(
  val version: String,
  val language: MessageLanguage,
  val placeholderMode: TemplatePlaceholderMode,
  val source: TemplateSource,
  val text: String,
) {
  override fun toString(): String =
    "MessageTemplate(version=$version, language=$language, placeholderMode=$placeholderMode, source=$source, text=<redacted>)"
}

enum class TemplateValidationError {
  VERSION_INVALID,
  SEGMENT_CAP_INVALID,
  EMPTY,
  TOO_LARGE_TO_VALIDATE,
  UNSAFE_UNICODE,
  PLACEHOLDER_REQUIRED,
  PLACEHOLDER_NOT_ALLOWED,
  PLACEHOLDER_COUNT_INVALID,
  UNRESOLVED_VARIABLE,
  GIVEN_NAME_REQUIRED_OR_UNSAFE,
  BIRTHDAY_INTENT_REQUIRED,
  URL_NOT_ALLOWED,
  TRACKING_OR_HASHTAG_NOT_ALLOWED,
  PROMOTIONAL_CONTENT_NOT_ALLOWED,
  SENSITIVE_OR_INVENTED_CLAIM_NOT_ALLOWED,
  LANGUAGE_MISMATCH,
  SEGMENT_CAP_EXCEEDED,
}

data class RenderedMessagePreview(
  val exactText: String,
  val metrics: SmsEncodingEstimate,
  val templateVersion: String,
  val placeholderMode: TemplatePlaceholderMode,
  val validatorVersion: String,
) {
  override fun toString(): String =
    "RenderedMessagePreview(metrics=$metrics, templateVersion=$templateVersion, " +
      "placeholderMode=$placeholderMode, validatorVersion=$validatorVersion, exactText=<redacted>)"
}

data class TemplateValidationResult(
  val preview: RenderedMessagePreview?,
  val errors: Set<TemplateValidationError>,
) {
  val valid: Boolean get() = preview != null && errors.isEmpty()
}

object BuiltInMessageTemplates {
  const val CATALOG_VERSION = "birthday-defaults-v1"

  fun personalized(language: MessageLanguage): MessageTemplate = when (language) {
    MessageLanguage.ENGLISH -> MessageTemplate(
      version = "$CATALOG_VERSION-en-personalized",
      language = language,
      placeholderMode = TemplatePlaceholderMode.PERSONALIZED_FIRST_NAME,
      source = TemplateSource.BUILT_IN,
      text = "Happy birthday, {firstName}! Wishing you a wonderful day.",
    )
    MessageLanguage.HINDI -> MessageTemplate(
      version = "$CATALOG_VERSION-hi-personalized",
      language = language,
      placeholderMode = TemplatePlaceholderMode.PERSONALIZED_FIRST_NAME,
      source = TemplateSource.BUILT_IN,
      text = "जन्मदिन मुबारक हो, {firstName}! आपका दिन शानदार हो।",
    )
  }

  fun generic(language: MessageLanguage): MessageTemplate = when (language) {
    MessageLanguage.ENGLISH -> MessageTemplate(
      version = "$CATALOG_VERSION-en-generic",
      language = language,
      placeholderMode = TemplatePlaceholderMode.GENERIC_NO_NAME,
      source = TemplateSource.BUILT_IN,
      text = "Happy birthday! Wishing you a wonderful day.",
    )
    MessageLanguage.HINDI -> MessageTemplate(
      version = "$CATALOG_VERSION-hi-generic",
      language = language,
      placeholderMode = TemplatePlaceholderMode.GENERIC_NO_NAME,
      source = TemplateSource.BUILT_IN,
      text = "जन्मदिन मुबारक हो! आपका दिन शानदार हो।",
    )
  }
}

class MessageTemplateValidator(
  private val smsLengthCalculator: SmsLengthCalculator = SmsEncodingEstimator,
) {
  fun validateAndRender(
    template: MessageTemplate,
    givenName: String?,
    segmentCap: Int = DEFAULT_SEGMENT_CAP,
  ): TemplateValidationResult {
    val errors = linkedSetOf<TemplateValidationError>()
    if (!VERSION.matches(template.version)) errors += TemplateValidationError.VERSION_INVALID
    if (segmentCap !in 1..DEFAULT_SEGMENT_CAP) errors += TemplateValidationError.SEGMENT_CAP_INVALID
    if (template.text.length > MAX_TEMPLATE_INPUT_UTF16_UNITS) {
      errors += TemplateValidationError.TOO_LARGE_TO_VALIDATE
      return TemplateValidationResult(preview = null, errors = errors.toSet())
    }

    val normalized = UnicodeTextSafety.normalizeNfc(template.text)
    val codePointCount = normalized.codePointCount(0, normalized.length)
    if (normalized.isBlank()) errors += TemplateValidationError.EMPTY
    if (codePointCount > MAX_TEMPLATE_CODE_POINTS) {
      errors += TemplateValidationError.TOO_LARGE_TO_VALIDATE
      return TemplateValidationResult(preview = null, errors = errors.toSet())
    }
    if (UnicodeTextSafety.containsUnsafeMessageCodePoint(normalized)) {
      errors += TemplateValidationError.UNSAFE_UNICODE
    }
    if (!matchesDeclaredLanguage(normalized, template.language)) {
      errors += TemplateValidationError.LANGUAGE_MISMATCH
    }

    val placeholderCount = countOccurrences(normalized, FIRST_NAME_PLACEHOLDER)
    when (template.placeholderMode) {
      TemplatePlaceholderMode.PERSONALIZED_FIRST_NAME -> when (placeholderCount) {
        0 -> errors += TemplateValidationError.PLACEHOLDER_REQUIRED
        1 -> Unit
        else -> errors += TemplateValidationError.PLACEHOLDER_COUNT_INVALID
      }
      TemplatePlaceholderMode.GENERIC_NO_NAME -> if (placeholderCount != 0) {
        errors += TemplateValidationError.PLACEHOLDER_NOT_ALLOWED
      }
    }
    if (containsUnresolvedVariable(normalized)) errors += TemplateValidationError.UNRESOLVED_VARIABLE
    val templatePolicyErrors = MessageContentPolicy.validate(normalized, template.language)
    errors += templatePolicyErrors

    val safeName = when (template.placeholderMode) {
      TemplatePlaceholderMode.PERSONALIZED_FIRST_NAME -> UnicodeTextSafety.smsGivenName(givenName)
        ?: run {
          errors += TemplateValidationError.GIVEN_NAME_REQUIRED_OR_UNSAFE
          null
        }
      TemplatePlaceholderMode.GENERIC_NO_NAME -> null
    }
    var rendered = when {
      template.placeholderMode == TemplatePlaceholderMode.GENERIC_NO_NAME -> normalized
      safeName != null && placeholderCount == 1 -> normalized.replace(FIRST_NAME_PLACEHOLDER, safeName)
      else -> null
    }

    if (rendered != null) {
      val renderedPolicyErrors = MessageContentPolicy.validate(rendered, template.language)
      if (
        template.placeholderMode == TemplatePlaceholderMode.PERSONALIZED_FIRST_NAME &&
        safeName != null &&
        templatePolicyErrors.isEmpty() &&
        renderedPolicyErrors.isNotEmpty()
      ) {
        // A contact name can itself look like a URL, promotion, claim, or harmful phrase. The
        // template remains safe, but changing personalized approval semantics without review is
        // not allowed. Force the contact to explicit generic/no-name review instead.
        errors += TemplateValidationError.GIVEN_NAME_REQUIRED_OR_UNSAFE
        rendered = null
      } else {
        errors += renderedPolicyErrors
      }
    }

    val preview = rendered?.let { exactText ->
      if (containsUnresolvedVariable(exactText)) errors += TemplateValidationError.UNRESOLVED_VARIABLE
      val metrics = smsLengthCalculator.calculate(exactText)
      if (segmentCap in 1..DEFAULT_SEGMENT_CAP && metrics.segmentCount > segmentCap) {
        errors += TemplateValidationError.SEGMENT_CAP_EXCEEDED
      }
      RenderedMessagePreview(
        exactText = exactText,
        metrics = metrics,
        templateVersion = template.version,
        placeholderMode = template.placeholderMode,
        validatorVersion = VALIDATOR_VERSION,
      )
    }

    return TemplateValidationResult(
      preview = preview,
      errors = errors.toSet(),
    )
  }

  private fun matchesDeclaredLanguage(value: String, language: MessageLanguage): Boolean {
    val templateOnly = value.replace(FIRST_NAME_PLACEHOLDER, "")
    val letterScripts = templateOnly.codePoints().toArray()
      .filter(Character::isLetter)
      .map(Character.UnicodeScript::of)
    return when (language) {
      MessageLanguage.ENGLISH -> letterScripts.isNotEmpty() && letterScripts.all { it == Character.UnicodeScript.LATIN }
      MessageLanguage.HINDI ->
        Character.UnicodeScript.DEVANAGARI in letterScripts &&
          letterScripts.all { it == Character.UnicodeScript.DEVANAGARI }
    }
  }

  private fun countOccurrences(value: String, needle: String): Int {
    var count = 0
    var from = 0
    while (true) {
      val index = value.indexOf(needle, from)
      if (index < 0) return count
      count += 1
      from = index + needle.length
    }
  }

  private fun containsUnresolvedVariable(value: String): Boolean {
    val withoutSupported = value.replace(FIRST_NAME_PLACEHOLDER, "")
    return '{' in withoutSupported || '}' in withoutSupported
  }

  companion object {
    const val DEFAULT_SEGMENT_CAP = 2
    const val VALIDATOR_VERSION = "sms-template-validator-v2"
    const val FIRST_NAME_PLACEHOLDER = "{firstName}"
    private const val MAX_TEMPLATE_CODE_POINTS = 1_000
    private const val MAX_TEMPLATE_INPUT_UTF16_UNITS = 2_000
    private val VERSION = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$")
  }
}

internal enum class MessageContentCategory(val fixtureId: String) {
  BIRTHDAY_INTENT_REQUIRED("birthday-intent-required"),
  URL("url"),
  TRACKING_OR_AFFILIATE("tracking-or-affiliate"),
  PROMOTION("promotion"),
  LITERAL_PERSONAL_DATA("literal-personal-data"),
  AGE("age"),
  GENDER("gender"),
  RELIGION("religion"),
  HEALTH("health"),
  RELATIONSHIP("relationship"),
  PRIVATE_MEMORY("private-memory"),
  HATE("hate"),
  SEXUAL("sexual"),
  SELF_HARM("self-harm"),
  VIOLENCE("violence"),
  DECEPTION("deception"),
}

internal object MessageContentPolicy {
  const val POLICY_VERSION = "birthday-message-semantic-v2"

  private val BIRTHDAY_INTENT_EN = policyRegex(
    """\b(?:birthday|b[\s-]?day|bday)\b|\bmany\s+happy\s+returns\b""",
  )
  private val BIRTHDAY_INTENT_HI = policyRegex("""(?:जन्म\s*दिन|जन्मदिवस)""")

  private val URL_SCHEME_OR_WWW = policyRegex(
    """(?:\b(?:https?|ftp)\s*:\s*/\s*/|\b(?:mailto|tel|sms|smsto)\s*:|\bwww\.)\S+""",
  )
  private val URL_DOMAIN = policyRegex(
    """\b(?:[\p{L}\p{N}](?:[\p{L}\p{N}-]{0,62}[\p{L}\p{N}])?\.)+""" +
      """(?:[a-z]{2,63}|xn--[a-z0-9-]{2,59})""" +
      """(?:[/?:#]\S*)?""",
  )
  private val URL_OBFUSCATED_DOMAIN = policyRegex(
    """\b[\p{L}\p{N}][\p{L}\p{N}-]{0,62}\s*""" +
      """(?:\[\s*dot\s*]|\(\s*dot\s*\)|\s+dot\s+)\s*""" +
      """(?:[a-z]{2,63}|xn--[a-z0-9-]{2,59})\b""",
  )
  private val IPV4 = policyRegex("""\b(?:[0-9]{1,3}\.){3}[0-9]{1,3}\b""")
  private val EMAIL = policyRegex(
    """\b[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9-]+(?:\.[a-z0-9-]+)+\b""",
  )

  private val TRACKING_OR_AFFILIATE = policyRegex(
    """(?:\butm_[a-z0-9_]+\s*=|\b(?:gclid|fbclid|msclkid|ref|referrer|affiliate_id|aff_id)\s*=|""" +
      """#[\p{L}\p{N}_]+|\b(?:affiliate|referral|sponsored)\s+(?:link|code|post)|""" +
      """\buse\s+(?:my|code)\s+(?:affiliate\s+)?code\b|\bearns?\s+(?:a\s+)?commission\b|""" +
      """(?:रेफरल|एफिलिएट|संबद्ध)\s*(?:लिंक|कोड)|(?:प्रायोजित|कमीशन))""",
  )

  private val PROMOTION = policyRegex(
    """\b(?:limited(?:[- ]time)? offer|special offer|special deal|flash sale|birthday sale|""" +
      """discount(?: code)?|""" +
      """coupon(?: code)?|promo(?: code)?|buy now|shop now|order now|free offer|free gift|""" +
      """claim (?:your )?(?:offer|gift|discount)|save [0-9]{1,3}%|[0-9]{1,3}% off|""" +
      """subscribe(?: now| today)?|start (?:a|your) subscription)\b|""" +
      """(?:सीमित|खास|विशेष)\s*(?:समय का\s*)?ऑफर|अभी\s*(?:खरीदें|ऑर्डर करें)|""" +
      """(?:विशेष\s*)?छूट|कूपन|प्रोमो\s*कोड|मुफ़्त\s*(?:ऑफर|उपहार)|फ्लैश\s*सेल|""" +
      """सदस्यता\s*लें""",
  )

  private val PHONE_NUMBER = policyRegex(
    """(?<![\p{L}\p{N}])(?:\+?[0-9०-९][\s().-]*){10,15}(?![\p{L}\p{N}])""",
  )
  private val NUMERIC_DATE = policyRegex(
    """\b(?:[0-9]{4}[-/.][0-9]{1,2}[-/.][0-9]{1,2}|""" +
      """[0-9]{1,2}[-/.][0-9]{1,2}[-/.][0-9]{2,4})\b""",
  )
  private val ENGLISH_DATE = policyRegex(
    """\b(?:(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|""" +
      """aug(?:ust)?|sept?(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\s+""" +
      """[0-9]{1,2}(?:st|nd|rd|th)?(?:,?\s+[0-9]{4})?|[0-9]{1,2}(?:st|nd|rd|th)?\s+""" +
      """(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|""" +
      """sept?(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)(?:\s+[0-9]{4})?)\b""",
  )
  private val HINDI_DATE = policyRegex(
    """[0-9०-९]{1,2}\s*(?:जनवरी|फरवरी|मार्च|अप्रैल|मई|जून|जुलाई|अगस्त|सितंबर|""" +
      """अक्टूबर|नवंबर|दिसंबर)(?:\s*[0-9०-९]{2,4})?""",
  )
  private val IDENTITY_LABEL = policyRegex(
    """\b(?:your|my)\s+(?:full name|phone number|mobile number|email address|home address|""" +
      """aadhaar(?: number)?|passport(?: number)?|social security number|ssn|date of birth|birth date)\b|""" +
      """(?:आपका|आपकी|मेरा|मेरी)\s*(?:पूरा नाम|फोन नंबर|मोबाइल नंबर|ईमेल|घर का पता|""" +
      """आधार नंबर|पासपोर्ट नंबर|जन्म तिथि)""",
  )

  private val AGE_EN = policyRegex(
    """\b(?:turning\s+[0-9]{1,3}\b(?!\s+(?:pages?|chapters?|books?|degrees?|minutes?|""" +
      """seconds?|ideas?|recipes?))|[0-9]{1,3}(?:st|nd|rd|th)\s+birthday|""" +
      """[0-9]{1,3}\s+years?\s+old|(?:age|aged)\s+[0-9]{1,3}|[0-9]{1,3}\s+candles?)\b""",
  )
  private val AGE_HI = policyRegex(
    """[0-9०-९]{1,3}\s*(?:वां|वाँ|वीं)?\s*जन्मदिन|[0-9०-९]{1,3}\s*साल\s*के\s*हो\s*गए|""" +
      """उम्र\s*[0-9०-९]{1,3}|[0-9०-९]{1,3}\s*मोमबत्त""",
  )
  private val GENDER = policyRegex(
    """\b(?:birthday\s+(?:girl|boy|woman|man)|you\s+are\s+(?:a\s+)?(?:woman|man|girl|boy|""" +
      """female|male)|as\s+(?:a|the)\s+(?:woman|man|girl|boy))\b|""" +
      """(?:आप|तुम)\s*(?:एक\s*)?(?:(?:शानदार|अच्छी|अच्छा|मजबूत)\s+)?""" +
      """(?:महिला|पुरुष|लड़की|लड़का)\s*(?:हैं|हो)|""" +
      """जन्मदिन\s+(?:की\s+लड़की|का\s+लड़का)""",
  )
  private val RELIGION = policyRegex(
    """\b(?:god|jesus|allah|christ|lord)\s+(?:bless|protect|guide)s?\s+you\b|""" +
      """\b(?:as\s+(?:a|your)\s+|you\s+are\s+(?:a\s+)?)""" +
      """(?:hindu|muslim|christian|jewish|sikh|buddhist)\b|""" +
      """(?:भगवान|ईश्वर|अल्लाह|यीशु|वाहेगुरु)\s*(?:आपको|तुम्हें)?\s*आशीर्वाद|""" +
      """(?:आप|तुम)\s*(?:हिंदू|मुसलमान|ईसाई|सिख|बौद्ध)\s*(?:हैं|हो)""",
  )
  private val HEALTH = policyRegex(
    """\b(?:your\s+(?:illness|diagnosis|disease|disability|medical condition|cancer|diabetes)|""" +
      """recover(?:y|ing)?\s+from\s+(?:your\s+)?(?:illness|diagnosis|surgery|cancer|disease)|""" +
      """get well soon|beat(?:ing)?\s+(?:cancer|your illness|the disease))\b|""" +
      """(?:आपकी|तुम्हारी)\s*(?:बीमारी|निदान|विकलांगता|चिकित्सा स्थिति|कैंसर|मधुमेह)|""" +
      """(?:बीमारी|ऑपरेशन|कैंसर)\s*से\s*जल्द\s*ठीक""",
  )
  private val RELATIONSHIP = policyRegex(
    """\b(?:(?:my|your)\s+(?:wife|husband|girlfriend|boyfriend|partner|daughter|son|""" +
      """mother|father|sister|brother|best friend)|as\s+your\s+(?:wife|husband|girlfriend|""" +
      """boyfriend|partner)|our\s+(?:marriage|relationship|friendship))\b|""" +
      """(?:मेरी|आपकी|तुम्हारी)\s*(?:पत्नी|पति|प्रेमिका|प्रेमी|बेटी|बेटा|माँ|पिता|बहन|भाई)|""" +
      """हमारा\s*(?:विवाह|रिश्ता)""",
  )
  private val PRIVATE_MEMORY = policyRegex(
    """\b(?:remember\s+(?:when(?!\s+to\b)|our|the time)|our\s+secret\b(?!\s+recipe)|inside\s+joke|""" +
      """the\s+trip\s+we\s+took|that\s+night\s+we)\b|""" +
      """(?:याद\s+है\s+जब|हमारा\s+राज़|हमारी\s+गुप्त\s+(?:यात्रा|बात)|हम\s+जब\s+साथ)""",
  )
  private val HATE = policyRegex(
    """\b(?:hate|despise)\s+(?:all\s+)?(?:women|men|muslims?|hindus?|christians?|jews?|""" +
      """sikhs?|gays?|lesbians?|transgender\s+people|disabled\s+people|people\s+of\s+(?:a\s+)?""" +
      """(?:race|caste|religion))\b|\b(?:inferior|disgusting)\s+(?:race|caste|religion)\b|""" +
      """(?:महिलाओं|पुरुषों|मुसलमानों|हिंदुओं|ईसाइयों|सिखों|समलैंगिकों|विकलांगों)\s*से\s*नफरत|""" +
      """(?:जाति|धर्म)\s*(?:नीच|घटिया)""",
  )
  private val SEXUAL = policyRegex(
    """\b(?:sex(?:ual)?|sexy|nude|naked|porn(?:ography)?|sleep\s+with\s+me|explicit\s+photos?)\b|""" +
      """(?:यौन|सेक्सी|नग्न|अश्लील|पोर्न)""",
  )
  private val SELF_HARM = policyRegex(
    """\b(?:kill\s+yourself|end\s+your\s+(?:life|pain)|commit\s+suicide|suicide|self[- ]?harm|""" +
      """hurt\s+yourself)\b|(?:आत्महत्या|खुद\s+को\s+मार|अपनी\s+जान\s+ले|खुद\s+को\s+नुकसान)""",
  )
  private val VIOLENCE = policyRegex(
    """\b(?:kill|murder|hurt|attack|shoot|stab|beat)\s+(?:you|him|her|them|someone|people)\b|""" +
      """\b(?:death|bomb)\s+threat\b|(?:आपको|तुम्हें|उसे|उन्हें)\s*(?:मार\s*(?:दूँगा|दूंगा|डालूँगा|""" +
      """डालूंगा)|गोली\s+मार|चाकू\s+मार|पीट)|(?:जान\s+से\s+मारने|बम)\s+की\s+धमकी""",
  )
  private val DECEPTION = policyRegex(
    """\b(?:you(?:'ve| have)\s+won\s+(?:a\s+)?(?:prize|lottery)|share\s+your\s+(?:otp|pin|""" +
      """password)|send\s+(?:money|payment|your\s+otp)|your\s+(?:bank\s+)?account\s+is\s+""" +
      """(?:locked|suspended)|i\s+am\s+from\s+your\s+bank|guaranteed\s+(?:prize|returns?)|""" +
      """urgent\s+payment)\b|(?:आपका|तुम्हारा)\s*बैंक\s*खाता\s*(?:बंद|निलंबित)|""" +
      """(?:otp|पिन|पासवर्ड)\s*(?:भेजें|बताएं|साझा करें)|आप\s*(?:इनाम|लॉटरी)\s*जीत""",
  )

  fun classify(
    text: String,
    language: MessageLanguage? = null,
  ): Set<MessageContentCategory> {
    val semanticText = semanticView(text)
    return buildSet {
      val hasBirthdayIntent = when (language) {
        MessageLanguage.ENGLISH -> BIRTHDAY_INTENT_EN.containsMatchIn(semanticText)
        MessageLanguage.HINDI -> BIRTHDAY_INTENT_HI.containsMatchIn(semanticText)
        null -> BIRTHDAY_INTENT_EN.containsMatchIn(semanticText) ||
          BIRTHDAY_INTENT_HI.containsMatchIn(semanticText)
      }
      if (!hasBirthdayIntent) add(MessageContentCategory.BIRTHDAY_INTENT_REQUIRED)
      if (
        URL_SCHEME_OR_WWW.containsMatchIn(semanticText) ||
        containsNonBenignUrlDomain(semanticText) ||
        URL_OBFUSCATED_DOMAIN.containsMatchIn(semanticText) ||
        IPV4.containsMatchIn(semanticText) ||
        EMAIL.containsMatchIn(semanticText)
      ) add(MessageContentCategory.URL)
      if (TRACKING_OR_AFFILIATE.containsMatchIn(semanticText)) {
        add(MessageContentCategory.TRACKING_OR_AFFILIATE)
      }
      if (PROMOTION.containsMatchIn(semanticText)) add(MessageContentCategory.PROMOTION)
      if (
        PHONE_NUMBER.containsMatchIn(semanticText) ||
        NUMERIC_DATE.containsMatchIn(semanticText) ||
        ENGLISH_DATE.containsMatchIn(semanticText) ||
        HINDI_DATE.containsMatchIn(semanticText) ||
        IDENTITY_LABEL.containsMatchIn(semanticText) ||
        EMAIL.containsMatchIn(semanticText)
      ) add(MessageContentCategory.LITERAL_PERSONAL_DATA)
      if (AGE_EN.containsMatchIn(semanticText) || AGE_HI.containsMatchIn(semanticText)) {
        add(MessageContentCategory.AGE)
      }
      if (GENDER.containsMatchIn(semanticText)) add(MessageContentCategory.GENDER)
      if (RELIGION.containsMatchIn(semanticText)) add(MessageContentCategory.RELIGION)
      if (HEALTH.containsMatchIn(semanticText)) add(MessageContentCategory.HEALTH)
      if (RELATIONSHIP.containsMatchIn(semanticText)) add(MessageContentCategory.RELATIONSHIP)
      if (PRIVATE_MEMORY.containsMatchIn(semanticText)) add(MessageContentCategory.PRIVATE_MEMORY)
      if (HATE.containsMatchIn(semanticText)) add(MessageContentCategory.HATE)
      if (SEXUAL.containsMatchIn(semanticText)) add(MessageContentCategory.SEXUAL)
      if (SELF_HARM.containsMatchIn(semanticText)) add(MessageContentCategory.SELF_HARM)
      if (VIOLENCE.containsMatchIn(semanticText)) add(MessageContentCategory.VIOLENCE)
      if (DECEPTION.containsMatchIn(semanticText)) add(MessageContentCategory.DECEPTION)
    }
  }

  fun validate(
    text: String,
    language: MessageLanguage? = null,
  ): Set<TemplateValidationError> = classify(text, language)
    .mapTo(linkedSetOf()) { it.validationError() }

  private fun semanticView(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT)
    .replace(SEMANTIC_WHITESPACE, " ")
    .trim()

  private fun containsNonBenignUrlDomain(text: String): Boolean = URL_DOMAIN.findAll(text).any { match ->
    match.value.lowercase(Locale.ROOT) !in BENIGN_DOTTED_TERMS
  }

  private fun MessageContentCategory.validationError(): TemplateValidationError = when (this) {
    MessageContentCategory.BIRTHDAY_INTENT_REQUIRED -> TemplateValidationError.BIRTHDAY_INTENT_REQUIRED
    MessageContentCategory.URL -> TemplateValidationError.URL_NOT_ALLOWED
    MessageContentCategory.TRACKING_OR_AFFILIATE -> TemplateValidationError.TRACKING_OR_HASHTAG_NOT_ALLOWED
    MessageContentCategory.PROMOTION -> TemplateValidationError.PROMOTIONAL_CONTENT_NOT_ALLOWED
    MessageContentCategory.LITERAL_PERSONAL_DATA,
    MessageContentCategory.AGE,
    MessageContentCategory.GENDER,
    MessageContentCategory.RELIGION,
    MessageContentCategory.HEALTH,
    MessageContentCategory.RELATIONSHIP,
    MessageContentCategory.PRIVATE_MEMORY,
    MessageContentCategory.HATE,
    MessageContentCategory.SEXUAL,
    MessageContentCategory.SELF_HARM,
    MessageContentCategory.VIOLENCE,
    MessageContentCategory.DECEPTION,
    -> TemplateValidationError.SENSITIVE_OR_INVENTED_CLAIM_NOT_ALLOWED
  }

  private fun policyRegex(pattern: String): Regex = Regex(pattern, RegexOption.IGNORE_CASE)

  private val BENIGN_DOTTED_TERMS = setOf("node.js", "dr.strange")
  private val SEMANTIC_WHITESPACE = Regex("[\\p{Z}\\s]+")
}
