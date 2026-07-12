package com.yashsomani.birthdayautopilot.localization

import android.content.Context
import java.util.Locale

/**
 * The current Android locale split into the two concerns that native code actually owns.
 *
 * Presentation is deliberately limited to the app's reviewed English and Hindi copy. The phone
 * region remains the user's current ISO region even when their language is not supported, so a
 * device using fr-FR is presented as en-FR while national phone numbers still resolve as French.
 */
internal data class AndroidNativeLocale(
  val presentationLocale: Locale,
  val phoneRegion: String?,
)

internal object AndroidNativeLocalePolicy {
  private val isoRegions = Locale.getISOCountries().toSet()

  fun resolve(deviceLocale: Locale?): AndroidNativeLocale {
    val presentationLanguage = if (
      deviceLocale?.language?.lowercase(Locale.ROOT) == HINDI_LANGUAGE
    ) {
      HINDI_LANGUAGE
    } else {
      ENGLISH_LANGUAGE
    }
    val phoneRegion = deviceLocale?.country
      ?.uppercase(Locale.ROOT)
      ?.takeIf(isoRegions::contains)
    val presentationLocale = if (phoneRegion == null) {
      Locale.forLanguageTag(presentationLanguage)
    } else {
      Locale.forLanguageTag("$presentationLanguage-$phoneRegion")
    }
    return AndroidNativeLocale(presentationLocale, phoneRegion)
  }

  private const val ENGLISH_LANGUAGE = "en"
  private const val HINDI_LANGUAGE = "hi"
}

/** Reads Android resources on every call so a live configuration change cannot leave a snapshot. */
internal class AndroidNativeLocaleProvider(
  private val localeSource: () -> Locale?,
) {
  constructor(context: Context) : this(
    localeSource = {
      val locales = context.applicationContext.resources.configuration.locales
      if (locales.isEmpty) null else locales[0]
    },
  )

  fun current(): AndroidNativeLocale = AndroidNativeLocalePolicy.resolve(localeSource())
}
