package com.yashsomani.birthdayautopilot.automation.sms

internal sealed interface CallbackAllocationResult<out Allocation> {
  data class Success<Allocation>(val allocations: List<Allocation>) :
    CallbackAllocationResult<Allocation>

  data class Failed(val rollbackComplete: Boolean) : CallbackAllocationResult<Nothing>
}

/** Small cross-platform transaction shell around non-transactional Android PendingIntent calls. */
internal object CallbackAllocationTransaction {
  fun <Item, Allocation> allocate(
    items: List<Item>,
    collisionExists: (Item) -> Boolean,
    create: (Item) -> Allocation?,
    cancel: (Allocation) -> Unit,
  ): CallbackAllocationResult<Allocation> {
    if (items.isEmpty()) return CallbackAllocationResult.Failed(rollbackComplete = true)
    val created = ArrayList<Allocation>(items.size)
    return try {
      for (item in items) {
        if (collisionExists(item)) {
          return CallbackAllocationResult.Failed(rollback(created, cancel))
        }
        val allocation = create(item)
          ?: return CallbackAllocationResult.Failed(rollback(created, cancel))
        created += allocation
      }
      CallbackAllocationResult.Success(created)
    } catch (_: Exception) {
      CallbackAllocationResult.Failed(rollback(created, cancel))
    } catch (_: LinkageError) {
      CallbackAllocationResult.Failed(rollback(created, cancel))
    }
  }

  fun <Allocation> rollback(
    allocations: Iterable<Allocation>,
    cancel: (Allocation) -> Unit,
  ): Boolean {
    var complete = true
    allocations.forEach { allocation ->
      try {
        cancel(allocation)
      } catch (_: Exception) {
        complete = false
      } catch (_: LinkageError) {
        complete = false
      }
    }
    return complete
  }
}
