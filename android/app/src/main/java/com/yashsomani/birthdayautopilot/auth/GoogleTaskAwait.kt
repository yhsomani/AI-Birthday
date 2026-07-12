package com.yashsomani.birthdayautopilot.auth

import com.google.android.gms.tasks.Task
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

internal sealed interface TaskResult<out T> {
  data class Success<T>(val value: T) : TaskResult<T> {
    override fun toString(): String = "TaskResult.Success(<redacted>)"
  }

  data class Failure(val category: TaskFailureCategory) : TaskResult<Nothing>
}

internal enum class TaskFailureCategory {
  CANCELLED,
  FAILED,
}

/** Awaits a Play services/Firebase task without allowing exception text into domain results. */
internal suspend fun <T> Task<T>.awaitSanitized(
  timeoutMillis: Long = DEFAULT_TASK_TIMEOUT_MILLIS,
): TaskResult<T> {
  require(timeoutMillis in 1..MAXIMUM_TASK_TIMEOUT_MILLIS)
  return withTimeoutOrNull(timeoutMillis) {
    suspendCancellableCoroutine { continuation ->
      addOnCompleteListener(DIRECT_TASK_EXECUTOR) { task ->
        val result: TaskResult<T> = when {
          task.isCanceled -> TaskResult.Failure(TaskFailureCategory.CANCELLED)
          task.isSuccessful -> runCatching<TaskResult<T>> {
            @Suppress("UNCHECKED_CAST")
            TaskResult.Success(task.result as T)
          }.getOrElse { TaskResult.Failure(TaskFailureCategory.FAILED) }
          else -> TaskResult.Failure(TaskFailureCategory.FAILED)
        }
        // CancellableContinuation drops this stable resume when cancellation or timeout won.
        continuation.resume(result)
      }
    }
  } ?: TaskResult.Failure(TaskFailureCategory.FAILED)
}

/** Avoids an implicit Android-main-looper dependency; resuming a continuation is thread-safe. */
private val DIRECT_TASK_EXECUTOR = Executor { command -> command.run() }

private const val DEFAULT_TASK_TIMEOUT_MILLIS = 30_000L
private const val MAXIMUM_TASK_TIMEOUT_MILLIS = 60_000L
