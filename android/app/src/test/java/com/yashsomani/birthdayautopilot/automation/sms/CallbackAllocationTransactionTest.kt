package com.yashsomani.birthdayautopilot.automation.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallbackAllocationTransactionTest {
  private val items = listOf(0, 1, 2, 3)

  @Test
  fun collisionAtEveryIndexCancelsEveryEarlierAllocation() {
    items.indices.forEach { collisionIndex ->
      val created = mutableListOf<Int>()
      val cancelled = mutableListOf<Int>()

      val result = CallbackAllocationTransaction.allocate(
        items = items,
        collisionExists = { it == collisionIndex },
        create = { it.also(created::add) },
        cancel = cancelled::add,
      )

      assertEquals(items.take(collisionIndex), created)
      assertEquals(created, cancelled)
      assertTrue(result is CallbackAllocationResult.Failed && result.rollbackComplete)
    }
  }

  @Test
  fun exceptionAfterAnyNumberOfCreatesRollsEverythingBack() {
    items.indices.forEach { failureIndex ->
      val created = mutableListOf<Int>()
      val cancelled = mutableListOf<Int>()

      val result = CallbackAllocationTransaction.allocate(
        items = items,
        collisionExists = { false },
        create = { item ->
          if (item == failureIndex) error("platform allocation failed")
          item.also(created::add)
        },
        cancel = cancelled::add,
      )

      assertEquals(items.take(failureIndex), created)
      assertEquals(created, cancelled)
      assertTrue(result is CallbackAllocationResult.Failed && result.rollbackComplete)
    }
  }

  @Test
  fun nullAfterAnyNumberOfCreatesRollsEverythingBack() {
    items.indices.forEach { failureIndex ->
      val created = mutableListOf<Int>()
      val cancelled = mutableListOf<Int>()

      val result = CallbackAllocationTransaction.allocate<Int, Int>(
        items = items,
        collisionExists = { false },
        create = { item ->
          if (item == failureIndex) null else item.also(created::add)
        },
        cancel = cancelled::add,
      )

      assertEquals(items.take(failureIndex), created)
      assertEquals(created, cancelled)
      assertTrue(result is CallbackAllocationResult.Failed && result.rollbackComplete)
    }
  }

  @Test
  fun rollbackContinuesAfterOneCancellationFailureAndReportsIncomplete() {
    val cancellationAttempts = mutableListOf<Int>()
    val result = CallbackAllocationTransaction.allocate(
      items = items,
      collisionExists = { it == items.last() },
      create = { it },
      cancel = { allocation ->
        cancellationAttempts += allocation
        if (allocation == 1) error("cancel failed")
      },
    )

    assertEquals(items.dropLast(1), cancellationAttempts)
    assertTrue(result is CallbackAllocationResult.Failed)
    assertFalse((result as CallbackAllocationResult.Failed).rollbackComplete)
  }

  @Test
  fun successReturnsEveryAllocationInStableOrder() {
    val result = CallbackAllocationTransaction.allocate(
      items = items,
      collisionExists = { false },
      create = { "allocation-$it" },
      cancel = { error("rollback must not run") },
    )

    assertEquals(
      items.map { "allocation-$it" },
      (result as CallbackAllocationResult.Success).allocations,
    )
  }
}
