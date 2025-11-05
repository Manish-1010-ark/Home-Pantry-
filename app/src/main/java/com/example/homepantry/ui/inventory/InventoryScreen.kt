package com.example.homepantry.ui.inventory

import android.app.Activity
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.homepantry.R
import com.example.homepantry.ui.auth.AuthViewModel
import com.example.homepantry.ui.auth.LoginState
import com.example.homepantry.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InventoryScreen(
    navController: NavController,
    onAddItem: () -> Unit,
    onEditItem: (Long) -> Unit,
    onGoToSettings: () -> Unit
) {
    val TAG = "InventoryScreen"

    val viewModelStoreOwner = remember(navController.currentBackStackEntry) {
        navController.getBackStackEntry("inventory_list")
    }

    val listViewModel: InventoryListViewModel = hiltViewModel(viewModelStoreOwner)
    val authViewModel: AuthViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()

    val items by listViewModel.items.collectAsState()
    val syncState by listViewModel.syncState.collectAsState()
    val searchTerm by listViewModel.searchTerm.collectAsState()
    val selectedMainCategory by listViewModel.selectedMainCategory.collectAsState()
    val selectedSubCategory by listViewModel.selectedSubCategory.collectAsState()
    val sortOrder by listViewModel.sortOrder.collectAsState()

    val houseName = authViewModel.getCurrentHouseName()
    val currentLanguage by settingsViewModel.appLanguage.collectAsState("en")
    val context = LocalContext.current
    val activity = (context as? Activity)

    val snackbarHostState = remember { SnackbarHostState() }
    val allCategoryString = stringResource(R.string.category_all)

    val isLoadingInitialData = items.isEmpty() && syncState is SyncState.Syncing

    // State for sort dropdown
    var sortDropdownExpanded by remember { mutableStateOf(false) }

    // State for filter bottom sheet
    val filterSheetState = rememberModalBottomSheetState()
    var showFilterSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(items) {
        Log.d(TAG, "Items state updated: ${items.size} items")
    }

    val loginState by authViewModel.loginState.collectAsState()
    LaunchedEffect(loginState) {
        if (loginState is LoginState.Success) {
            val houseId = (loginState as LoginState.Success).houseId
            Log.d(
                TAG,
                "Login is Success. Telling ListViewModel to fetch items for houseId: $houseId"
            )
            listViewModel.startCollectingItems(houseId)
        } else {
            Log.d(TAG, "LoginState is not Success ($loginState), not fetching items.")
        }
    }

    val itemsWithParsedCategories = remember(items) {
        items.mapNotNull { item ->
            val hierarchy = parseCategory(item.category)
            if (hierarchy != null) {
                item to hierarchy
            } else {
                Log.w(TAG, "Failed to parse category for item: ${item.name} - ${item.category}")
                null
            }
        }
    }

    val mainCategories = remember(itemsWithParsedCategories) {
        val categories = itemsWithParsedCategories
            .map { it.second.mainCategory }
            .distinct()
            .sorted()
        listOf(allCategoryString) + categories
    }

    val subCategories =
        remember(itemsWithParsedCategories, selectedMainCategory, allCategoryString) {
            if (selectedMainCategory == allCategoryString) {
                listOf(allCategoryString)
            } else {
                val subs = itemsWithParsedCategories
                    .filter { it.second.mainCategory == selectedMainCategory }
                    .map { it.second.subCategory }
                    .distinct()
                    .sorted()
                listOf(allCategoryString) + subs
            }
        }

    // Enhanced search and filtering
    val filteredItems = remember(
        items,
        itemsWithParsedCategories,
        searchTerm,
        selectedMainCategory,
        selectedSubCategory
    ) {
        itemsWithParsedCategories.filter { (item, hierarchy) ->
            // Powerful search - check all fields
            val matchesSearch = if (searchTerm.isBlank()) {
                true
            } else {
                val lowerSearchTerm = searchTerm.lowercase()
                item.name.lowercase().contains(lowerSearchTerm) ||
                        (item.nameHindi?.lowercase()?.contains(lowerSearchTerm) == true) ||
                        hierarchy.mainCategory.lowercase().contains(lowerSearchTerm) ||
                        hierarchy.subCategory.lowercase().contains(lowerSearchTerm) ||
                        item.quantity.toString().contains(lowerSearchTerm) ||
                        item.unit.lowercase().contains(lowerSearchTerm) ||
                        (item.location?.lowercase()?.contains(lowerSearchTerm) == true) ||
                        (item.notes?.lowercase()?.contains(lowerSearchTerm) == true)
            }

            val matchesMainCategory = selectedMainCategory == allCategoryString ||
                    hierarchy.mainCategory == selectedMainCategory

            val matchesSubCategory = selectedSubCategory == allCategoryString ||
                    hierarchy.subCategory == selectedSubCategory

            matchesSearch && matchesMainCategory && matchesSubCategory
        }.map { it.first }
    }

    // Apply sorting
    val sortedFilteredItems = remember(filteredItems, sortOrder) {
        when (sortOrder) {
            SortOrder.ALPHABETICAL_ASC -> filteredItems.sortedBy { it.name.lowercase() }
            SortOrder.ALPHABETICAL_DESC -> filteredItems.sortedByDescending { it.name.lowercase() }
            SortOrder.QUANTITY_ASC -> filteredItems.sortedBy { it.quantity }
            SortOrder.QUANTITY_DESC -> filteredItems.sortedByDescending { it.quantity }
            SortOrder.CATEGORY_ASC -> filteredItems.sortedBy { it.category }
            SortOrder.DATE_ADDED -> filteredItems.sortedByDescending { it.id ?: 0 }
        }
    }

    // Group items by main category, then by sub-category
    val groupedItems = remember(sortedFilteredItems) {
        sortedFilteredItems.groupBy { item ->
            val hierarchy = parseCategory(item.category)
            hierarchy?.mainCategory ?: "Miscellaneous"
        }.mapValues { (_, items) ->
            items.groupBy { item ->
                val hierarchy = parseCategory(item.category)
                hierarchy?.subCategory ?: "Other"
            }
        }
    }

    Log.d(
        TAG,
        "Filtered: ${sortedFilteredItems.size} items (Main: $selectedMainCategory, Sub: $selectedSubCategory, Sort: $sortOrder)"
    )

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
                        Column {
                            Text(
                                text = houseName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.items_count, items.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        SyncStatusIndicator(syncState = syncState)

                        IconButton(
                            onClick = {
                                val newLanguage = if (currentLanguage == "en") "hi" else "en"
                                Log.d(
                                    "LanguageToggle",
                                    "InventoryScreen: Switching to $newLanguage"
                                )
                                settingsViewModel.setAppLanguage(newLanguage) {
                                    Log.d("LanguageToggle", "Save complete, recreating activity")
                                    activity?.recreate()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Language,
                                contentDescription = stringResource(R.string.settings_language_title),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }

                        IconButton(onClick = onGoToSettings) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.cd_settings),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onAddItem,
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.cd_add_item)
                    )
                }
            }
        ) { padding ->
            if (isLoadingInitialData) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.loading_inventory),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.please_wait),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                ) {
                    AnimatedVisibility(
                        visible = syncState is SyncState.Error,
                        enter = slideInVertically() + fadeIn(),
                        exit = slideOutVertically() + fadeOut()
                    ) {
                        SyncStatusBanner(syncState = syncState)
                    }

                    // Search Bar with Berry Fresh theme
                    OutlinedTextField(
                        value = searchTerm,
                        onValueChange = { listViewModel.updateSearchTerm(it) },
                        label = { Text(stringResource(R.string.search_placeholder)) },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        singleLine = true
                    )

                    // Control Bar - Sort and Filter buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Sort Button with Dropdown
                        Box(modifier = Modifier.weight(1f)) {
                            Surface(
                                onClick = { sortDropdownExpanded = true },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp,
                                        vertical = 12.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Sort,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Sort by",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                                    alpha = 0.7f
                                                )
                                            )
                                            Text(
                                                text = when (sortOrder) {
                                                    SortOrder.ALPHABETICAL_ASC -> "A → Z"
                                                    SortOrder.ALPHABETICAL_DESC -> "Z → A"
                                                    SortOrder.QUANTITY_ASC -> "Quantity ↑"
                                                    SortOrder.QUANTITY_DESC -> "Quantity ↓"
                                                    SortOrder.CATEGORY_ASC -> "Category"
                                                    SortOrder.DATE_ADDED -> "Recently Added"
                                                },
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Outlined.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = sortDropdownExpanded,
                                onDismissRequest = { sortDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("A → Z") },
                                    onClick = {
                                        listViewModel.updateSortOrder(SortOrder.ALPHABETICAL_ASC)
                                        sortDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Z → A") },
                                    onClick = {
                                        listViewModel.updateSortOrder(SortOrder.ALPHABETICAL_DESC)
                                        sortDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Quantity (Low to High)") },
                                    onClick = {
                                        listViewModel.updateSortOrder(SortOrder.QUANTITY_ASC)
                                        sortDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Quantity (High to Low)") },
                                    onClick = {
                                        listViewModel.updateSortOrder(SortOrder.QUANTITY_DESC)
                                        sortDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("By Category") },
                                    onClick = {
                                        listViewModel.updateSortOrder(SortOrder.CATEGORY_ASC)
                                        sortDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Recently Added") },
                                    onClick = {
                                        listViewModel.updateSortOrder(SortOrder.DATE_ADDED)
                                        sortDropdownExpanded = false
                                    }
                                )
                            }
                        }

                        // Filter Button
                        Surface(
                            onClick = { showFilterSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            tonalElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FilterList,
                                    contentDescription = "Filter",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Filter",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Items List with Nested Sticky Headers or Empty State
                    if (sortedFilteredItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Inventory,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.outlineVariant
                                )
                                Text(
                                    text = if (items.isEmpty()) {
                                        stringResource(R.string.no_items_empty)
                                    } else {
                                        stringResource(R.string.no_items_found)
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (items.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.tap_to_add),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            groupedItems.forEach { (mainCategory, subCategoryMap) ->
                                // Main Category Sticky Header
                                stickyHeader(key = "main_$mainCategory") {
                                    MainCategoryHeader(categoryName = mainCategory)
                                }

                                subCategoryMap.forEach { (subCategory, itemsList) ->
                                    // Sub-Category Sticky Header
                                    stickyHeader(key = "sub_${mainCategory}_$subCategory") {
                                        SubCategoryHeader(subCategoryName = subCategory)
                                    }

                                    // Items under this sub-category
                                    items(itemsList, key = { it.id!! }) { item ->
                                        HierarchicalItemCard(
                                            item = item,
                                            onClick = { item.id?.let { onEditItem(it) } },
                                            currentLang = currentLanguage
                                        )
                                    }
                                }
                            }

                            // Bottom padding for FAB
                            item {
                                Spacer(modifier = Modifier.height(72.dp))
                            }
                        }
                    }
                }
            }
        }

        // Filter Bottom Sheet
        if (showFilterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                sheetState = filterSheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Title
                    Text(
                        text = "Filter & Sort",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Main Category Filter
                    if (mainCategories.size > 1) {
                        Text(
                            text = stringResource(R.string.category_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        MainCategoryFilter(
                            categories = mainCategories,
                            selectedCategory = selectedMainCategory,
                            onCategorySelected = { listViewModel.updateSelectedMainCategory(it) }
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Sub-Category Filter
                    AnimatedVisibility(
                        visible = selectedMainCategory != allCategoryString && subCategories.size > 1,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.sub_category_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            SubCategoryFilter(
                                subCategories = subCategories,
                                selectedSubCategory = selectedSubCategory,
                                onSubCategorySelected = { listViewModel.updateSelectedSubCategory(it) }
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // Clear All Filters Button
                    Button(
                        onClick = {
                            listViewModel.clearAllFilters()
                            scope.launch {
                                showFilterSheet = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(
                            text = "Clear All Filters",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}