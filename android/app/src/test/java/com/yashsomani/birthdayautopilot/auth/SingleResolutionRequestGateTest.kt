package com.yashsomani.birthdayautopilot.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleResolutionRequestGateTest {
  @Test
  fun `allows one request and permits the next only after completion`() {
    val gate = SingleResolutionRequestGate()

    assertTrue(gate.begin())
    assertFalse(gate.begin())
    assertTrue(gate.finish())
    assertTrue(gate.begin())
  }

  @Test
  fun `destroy is terminal and reports whether an active request must be completed`() {
    val idle = SingleResolutionRequestGate()
    assertFalse(idle.destroy())
    assertFalse(idle.begin())
    assertFalse(idle.finish())

    val active = SingleResolutionRequestGate()
    assertTrue(active.begin())
    assertTrue(active.destroy())
    assertFalse(active.begin())
    assertFalse(active.finish())
  }
}
