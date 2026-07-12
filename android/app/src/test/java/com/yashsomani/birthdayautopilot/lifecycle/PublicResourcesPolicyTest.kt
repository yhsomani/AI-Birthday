package com.yashsomani.birthdayautopilot.lifecycle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublicResourcesPolicyTest {
  @Test
  fun `projects only strict google cloud project ids`() {
    assertEquals(
      "https://birthday-autopilot.web.app",
      PublicResourcesPolicy.baseUrl("birthday-autopilot"),
    )
    assertEquals(
      "https://a1234z.web.app",
      PublicResourcesPolicy.baseUrl("a1234z"),
    )
  }

  @Test
  fun `rejects ids that could alter the trusted origin`() {
    listOf(
      null,
      "",
      "abcde",
      "A-project",
      "1-project",
      "project-",
      "project_name",
      "project.web.app",
      "project/terms",
      "project.example.com",
      "a".repeat(31),
    ).forEach { assertNull(it, PublicResourcesPolicy.baseUrl(it)) }
  }
}
