package com.example.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import android.app.TimePickerDialog
import java.util.*

@Composable
fun RelateTimePicker(
    initialHour: Int,
    initialMinute: Int,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                onTimeSelected(hour, minute)
            },
            initialHour,
            initialMinute,
            true
        ).show()
    }
    
    // This is a wrapper that triggers the native TimePickerDialog
    // We don't actually render anything in Compose here, as we use the native dialog
}
