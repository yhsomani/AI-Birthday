package com.example.ui.screens.contacts

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.ContactListViewModel

internal object ContactListTestTags {
    const val SEARCH_FIELD = "contact_list_search_field"
    const val SYNC_ERROR_CARD = "contact_list_sync_error_card"
    const val FILTER_PREFIX = "contact_list_filter_"
    const val SORT_PREFIX = "contact_list_sort_"
    const val ROW_PREFIX = "contact_list_row_"
    const val QUALITY_PREFIX = "contact_list_quality_"
}

@Composable
fun ContactListScreen(
    onContactClick: (String) -> Unit = {},
    viewModel: ContactListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refresh()
    }
    val syncContacts = {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.refresh()
        } else {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    ContactListContent(
        state = state,
        onContactClick = onContactClick,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onClearSearch = { viewModel.updateSearchQuery("") },
        onFilterSelected = viewModel::selectFilter,
        onSortSelected = viewModel::selectSort,
        onRefresh = syncContacts,
        onDismissSyncError = { viewModel.dismissSyncError() },
    )
}
