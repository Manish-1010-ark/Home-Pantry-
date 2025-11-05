package com.example.homepantry.ui.settings

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.homepantry.R
import com.example.homepantry.ui.AppRoutes
import com.example.homepantry.ui.auth.AuthViewModel
import com.example.homepantry.ui.inventory.InventoryListViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController
) {
    // Inject ViewModels
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val authViewModel: AuthViewModel = hiltViewModel()
// Get the NavController's back stack entry for the inventory list
    val viewModelStoreOwner = remember(navController.currentBackStackEntry) {
        navController.getBackStackEntry("inventory_list")
    }
// Get the *same instance* of the ViewModel
    val listViewModel: InventoryListViewModel = hiltViewModel(viewModelStoreOwner)
    // Collect states
    val importState by settingsViewModel.importState.collectAsState()
    val items by listViewModel.items.collectAsState()
    val currentLanguage by settingsViewModel.appLanguage.collectAsState("en")
    val currentTheme by settingsViewModel.appTheme.collectAsState("light")

    // Get house info
    val houseName = authViewModel.getCurrentHouseName()
    val houseId = authViewModel.getCurrentHouseId()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = (context as? Activity)
    val coroutineScope = rememberCoroutineScope()

    // Bottom sheet states for conflict resolution
    val conflictSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showConflictSheet by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showLoadingDialog by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }

    // Update items cache for import operations
    LaunchedEffect(items) {
        settingsViewModel.updateItemsCache(items)
    }

    // Handle snackbar messages
    LaunchedEffect(Unit) {
        settingsViewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    // Handle import state changes (conflicts and reports)
    LaunchedEffect(importState) {
        when (val state = importState) {
            is ImportState.Conflict -> {
                Log.d("SettingsScreen", "Conflict detected, showing bottom sheet")
                showConflictSheet = true
                showLoadingDialog = false
            }

            is ImportState.SuccessReport -> {
                Log.d("SettingsScreen", "Import complete, showing report")
                showReportDialog = true
                showLoadingDialog = false
            }

            is ImportState.Loading -> {
                Log.d("SettingsScreen", "Import in progress: ${state.progress}")
                showConflictSheet = false
                showReportDialog = false
                showLoadingDialog = true
                loadingMessage = state.progress
            }

            is ImportState.Idle -> {
                showConflictSheet = false
                showReportDialog = false
                showLoadingDialog = false
            }

            is ImportState.Error -> {
                showConflictSheet = false
                showReportDialog = false
                showLoadingDialog = false
            }
        }
    }

    // File picker launcher for Excel import
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            houseId?.let { id ->
                settingsViewModel.importItemsFromExcel(uri, context, id)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Account Section
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(title = stringResource(R.string.settings_section_account))
                        AccountCard(houseName = houseName)
                    }
                }

                // General Section
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(title = stringResource(R.string.settings_section_general))

                        // Language Toggle
                        CleanSettingItem(
                            icon = Icons.Outlined.Language,
                            title = stringResource(R.string.settings_language_title),
                            description = if (currentLanguage == "en") {
                                stringResource(R.string.language_english)
                            } else {
                                stringResource(R.string.language_hindi)
                            },
                            enabled = true,
                            onClick = {
                                val newLanguage = if (currentLanguage == "en") "hi" else "en"
                                Log.d("LanguageToggle", "Switching to $newLanguage")

                                settingsViewModel.setAppLanguage(newLanguage) {
                                    activity?.recreate()
                                }
                            }
                        )

                        // Theme Toggle
                        CleanSettingToggle(
                            icon = Icons.Outlined.LightMode,
                            title = "App Theme",
                            description = if (currentTheme == "light") "Light" else "Dark",
                            checked = currentTheme == "dark",
                            onCheckedChange = { isChecked ->
                                val newTheme = if (isChecked) "dark" else "light"
                                settingsViewModel.setAppTheme(newTheme)
                            }
                        )

                        CleanSettingItem(
                            icon = Icons.Outlined.CloudSync,
                            title = stringResource(R.string.settings_cloud_title),
                            description = stringResource(R.string.settings_cloud_desc),
                            enabled = false,
                            onClick = {}
                        )
                    }
                }

                // Data Management Section
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(title = stringResource(R.string.settings_section_data))

                        val isImportLoading = importState is ImportState.Loading

                        ImportCard(
                            onImportClick = {
                                filePickerLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                            },
                            isLoading = isImportLoading
                        )
                    }
                }

                // Advanced Features Section
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(title = stringResource(R.string.settings_section_advanced))

                        CleanSettingItem(
                            icon = Icons.Outlined.AutoAwesome,
                            title = stringResource(R.string.settings_ai_title),
                            description = stringResource(R.string.settings_ai_desc),
                            enabled = false,
                            onClick = {}
                        )

                        CleanSettingItem(
                            icon = Icons.Outlined.Notifications,
                            title = stringResource(R.string.settings_alerts_title),
                            description = stringResource(R.string.settings_coming_soon),
                            enabled = false,
                            onClick = {}
                        )

                        CleanSettingItem(
                            icon = Icons.Outlined.BarChart,
                            title = stringResource(R.string.settings_analytics_title),
                            description = stringResource(R.string.settings_coming_soon),
                            enabled = false,
                            onClick = {}
                        )
                    }
                }

                // App Info
                item {
                    AppInfoCard()
                }

                // Logout Button
                item {
                    LogoutButton(
                        onClick = {
                            authViewModel.logout()
                            navController.navigate(AppRoutes.PIN_ENTRY) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        isLoading = false
                    )
                }

                // Bottom spacing
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Loading Dialog
        if (showLoadingDialog) {
            Dialog(
                onDismissRequest = { },
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            ) {
                LoadingDialog(message = loadingMessage)
            }
        }

        // Conflict Resolution Bottom Sheet
        if (showConflictSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    coroutineScope.launch {
                        conflictSheetState.hide()
                        showConflictSheet = false
                        settingsViewModel.cancelImport()
                    }
                },
                sheetState = conflictSheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                val conflictState = importState as? ImportState.Conflict
                conflictState?.let { state ->
                    val currentConflict = state.conflicts.firstOrNull()
                    currentConflict?.let { conflict ->
                        ImportConflictDialog(
                            conflict = conflict,
                            currentIndex = state.currentConflictIndex,
                            totalConflicts = state.totalConflicts,
                            onAction = { action ->
                                settingsViewModel.resolveConflict(action)
                            },
                            onCancel = {
                                coroutineScope.launch {
                                    conflictSheetState.hide()
                                    showConflictSheet = false
                                    settingsViewModel.cancelImport()
                                }
                            }
                        )
                    }
                }
            }
        }

        // Success Report Dialog
        if (showReportDialog) {
            Dialog(
                onDismissRequest = {
                    showReportDialog = false
                    settingsViewModel.clearImportState()
                },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                val reportState = importState as? ImportState.SuccessReport
                reportState?.let { state ->
                    ImportSuccessReportDialog(
                        report = state.report,
                        onDismiss = {
                            showReportDialog = false
                            settingsViewModel.clearImportState()
                        }
                    )
                }
            }
        }
    }
}

/**
 * NEW: Loading Dialog with progress indicator
 */
@Composable
fun LoadingDialog(message: String) {
    Box(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large
            )
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LinearProgressIndicator(
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}