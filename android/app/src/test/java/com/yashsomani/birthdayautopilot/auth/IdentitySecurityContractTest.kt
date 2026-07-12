package com.yashsomani.birthdayautopilot.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentitySecurityContractTest {
  @Test
  fun `tier policy accepts only exact application and generated client configuration`() {
    assertTrue(
      IdentityTierPolicy.accepts(
        environment = "prod",
        applicationId = "com.yashsomani.birthdayautopilot",
        webClientId = "123456789-abc_DEF.apps.googleusercontent.com",
        googleAppId = "1:123456789:android:abcdef123456",
        firebaseProjectId = "birthday-prod",
      ),
    )
    assertFalse(
      IdentityTierPolicy.accepts(
        environment = "prod",
        applicationId = "com.yashsomani.birthdayautopilot.dev",
        webClientId = "123456789-abc.apps.googleusercontent.com",
        googleAppId = "1:123456789:android:abcdef",
        firebaseProjectId = "birthday-prod",
      ),
    )
    assertFalse(
      IdentityTierPolicy.accepts(
        environment = "dev",
        applicationId = "com.yashsomani.birthdayautopilot.dev",
        webClientId = "YOUR_WEB_CLIENT_ID",
        googleAppId = "1:123456789:android:abcdef",
        firebaseProjectId = "birthday-dev",
      ),
    )
    assertFalse(
      IdentityTierPolicy.accepts(
        environment = "staging",
        applicationId = "com.yashsomani.birthdayautopilot.staging",
        webClientId = "123.apps.googleusercontent.com",
        googleAppId = "1:123:ios:abcdef",
        firebaseProjectId = "birthday-staging",
      ),
    )
  }

  @Test
  fun `contacts authorization accepts only the singleton readonly scope and no auth code`() {
    assertNull(
      ContactsScopePolicy.validate(
        grantedScopes = listOf(CONTACTS_READONLY_SCOPE),
        accessTokenPresent = true,
        serverAuthCodePresent = false,
      ),
    )
    assertEquals(
      ContactsAuthorizationFailure.PERMISSION_DENIED,
      ContactsScopePolicy.validate(emptyList(), accessTokenPresent = true, serverAuthCodePresent = false),
    )
    assertEquals(
      ContactsAuthorizationFailure.PARTIAL_SCOPE_GRANT,
      ContactsScopePolicy.validate(
        listOf(CONTACTS_READONLY_SCOPE, "https://www.googleapis.com/auth/contacts"),
        accessTokenPresent = true,
        serverAuthCodePresent = false,
      ),
    )
    assertEquals(
      ContactsAuthorizationFailure.ACCESS_TOKEN_MISSING,
      ContactsScopePolicy.validate(
        listOf(CONTACTS_READONLY_SCOPE),
        accessTokenPresent = false,
        serverAuthCodePresent = false,
      ),
    )
    assertEquals(
      ContactsAuthorizationFailure.UNEXPECTED_AUTHORIZATION_CODE,
      ContactsScopePolicy.validate(
        listOf(CONTACTS_READONLY_SCOPE),
        accessTokenPresent = true,
        serverAuthCodePresent = true,
      ),
    )
  }

  @Test
  fun `credential wrappers and identity DTOs never stringify private values`() {
    val secret = "access-token-value"
    val token = EphemeralToken.from(secret)!!
    assertFalse(token.toString().contains(secret))
    assertTrue(token.isPresent())
    token.clear()
    assertFalse(token.isPresent())

    val profile = IdentityProfile(
      displayEmail = "private@example.com",
      displayName = "Private Person",
    )
    val text = profile.toString()
    assertFalse(text.contains(profile.displayEmail))
    assertFalse(text.contains(profile.displayName!!))
  }

  @Test
  fun `identity values reject bidi default ignorable and non ASCII subject confusion`() {
    assertTrue(IdentityValuePolicy.isGoogleSubject("1234567890"))
    assertFalse(IdentityValuePolicy.isGoogleSubject("1234\u202E5678"))
    assertFalse(IdentityValuePolicy.isGoogleSubject("subject@example.com"))
    assertEquals("person@example.com", IdentityValuePolicy.email(" person@example.com "))
    assertNull(IdentityValuePolicy.email("person\u200B@example.com"))
    assertNull(IdentityValuePolicy.email("person\n@example.com"))
    assertEquals("Ada Lovelace", IdentityValuePolicy.displayName(" Ada  Lovelace "))
    assertNull(IdentityValuePolicy.displayName("Ada\u202ELovelace"))
    assertNull(IdentityValuePolicy.displayName("Ada\u200DLovelace"))
  }
}
