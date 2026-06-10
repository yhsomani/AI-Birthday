package com.example.ui.feedback

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

sealed interface UiText {
    data class Resource(
        @StringRes val resId: Int,
        val args: List<Any> = emptyList(),
    ) : UiText

    data class Dynamic(val value: String) : UiText
}

data class FeedbackEvent(
    val id: Long = System.nanoTime(),
    val message: UiText,
    val type: FeedbackType = FeedbackType.INFO,
)

enum class FeedbackType {
    INFO,
    SUCCESS,
    ERROR,
}

@Composable
fun UiText.asString(): String {
    return when (this) {
        is UiText.Dynamic -> value
        is UiText.Resource -> stringResource(resId, *args.toTypedArray())
    }
}
