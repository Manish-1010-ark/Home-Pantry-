package com.example.homepantry.ui.screens

import android.app.Activity
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.homepantry.R
import com.example.homepantry.data.Item
import com.example.homepantry.ui.InventoryViewModel
import com.example.homepantry.ui.SyncState
import kotlinx.coroutines.delay

/**
 * Data class to represent hierarchical category structure
 */
data class CategoryHierarchy(
    val mainCategory: String,
    val subCategory: String,
    val fullCategory: String // Original "Main:Sub" format
)

/**
 * Parses category string in "Main:Sub" format
 * Returns null for invalid format
 */
fun parseCategory(category: String): CategoryHierarchy? {
    return if (category.contains(":")) {
        val parts = category.split(":", limit = 2)
        if (parts.size == 2) {
            CategoryHierarchy(
                mainCategory = parts[0].trim(),
                subCategory = parts[1].trim(),
                fullCategory = category
            )
        } else null
    } else {
        // Backward compatibility: treat as sub-category under Miscellaneous
        CategoryHierarchy(
            mainCategory = "Miscellaneous",
            subCategory = category.trim(),
            fullCategory = category
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    navController: NavController,
    onAddItem: () -> Unit,
    onEditItem: (Long) -> Unit,
    onGoToSettings: () -> Unit
) {
    val TAG = "InventoryScreen"

    // FIX: Scope ViewModel to navigation graph to preserve state across navigation
    // NOTE: Replace "inventory_list" with your actual parent navigation graph route if different
    val viewModelStoreOwner = remember(navController.currentBackStackEntry) {
        navController.getBackStackEntry("inventory_list")
    }
    val viewModel: InventoryViewModel = hiltViewModel(viewModelStoreOwner)

    // Collect states
    val items by viewModel.items.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Get house name from ViewModel
    val houseName = viewModel.getCurrentHouseName()

    val searchTerm by viewModel.searchTerm.collectAsState()
    val selectedMainCategory by viewModel.selectedMainCategory.collectAsState()
    val selectedSubCategory by viewModel.selectedSubCategory.collectAsState()

    val allCategoryString = stringResource(R.string.category_all)

    val currentLanguage by viewModel.appLanguage.collectAsState("en")
    val context = LocalContext.current
    val activity = (context as? Activity)

    // Handle snackbar messages
    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    // Show loading state when items are initially loading
    val isLoadingInitialData = items.isEmpty() && syncState is SyncState.Syncing

    // Debug logging
    LaunchedEffect(items) {
        Log.d(TAG, "Items state updated: ${items.size} items")
    }

    // Parse categories from items
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

    // Extract unique main categories
    val mainCategories = remember(itemsWithParsedCategories) {
        val categories = itemsWithParsedCategories
            .map { it.second.mainCategory }
            .distinct()
            .sorted()
        listOf(allCategoryString) + categories
    }

    // Extract sub-categories based on selected main category
    val subCategories = remember(itemsWithParsedCategories, selectedMainCategory, allCategoryString) {
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

    // Reset sub-category when main category changes
    LaunchedEffect(selectedMainCategory) {
        viewModel.updateSelectedSubCategory(selectedSubCategory)
    }

    // Filtered items based on all filters
    val filteredItems = remember(items, itemsWithParsedCategories, searchTerm, selectedMainCategory, selectedSubCategory) {
        itemsWithParsedCategories.filter { (item, hierarchy) ->
            // Search filter
            val matchesSearch = if (searchTerm.isBlank()) {
                true
            } else {
                item.name.contains(searchTerm, ignoreCase = true) ||
                        (item.nameHindi?.contains(searchTerm, ignoreCase = true) == true) ||
                        hierarchy.mainCategory.contains(searchTerm, ignoreCase = true) ||
                        hierarchy.subCategory.contains(searchTerm, ignoreCase = true)
            }

            // Main category filter
            val matchesMainCategory = selectedMainCategory == allCategoryString ||
                    hierarchy.mainCategory == selectedMainCategory

            // Sub-category filter
            val matchesSubCategory = selectedSubCategory == allCategoryString ||
                    hierarchy.subCategory == selectedSubCategory

            matchesSearch && matchesMainCategory && matchesSubCategory
        }.map { it.first }
    }

    Log.d(TAG, "Filtered: ${filteredItems.size} items (Main: $selectedMainCategory, Sub: $selectedSubCategory)")

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
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.items_count, items.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        // Sync Status Indicator
                        SyncStatusIndicator(syncState = syncState)

                        // Language Toggle Button
                        IconButton(
                            onClick = {
                                val newLanguage = if (currentLanguage == "en") "hi" else "en"
                                Log.d("LanguageToggle", "InventoryScreen: Switching to $newLanguage")

                                viewModel.setAppLanguage(newLanguage) {
                                    // Only recreate AFTER save completes
                                    Log.d("LanguageToggle", "Save complete, recreating activity")
                                    activity?.recreate()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Language,
                                contentDescription = stringResource(R.string.settings_language_title),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Settings Button
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
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_item))
                }
            }
        ) { padding ->
            if (isLoadingInitialData) {
                // Show loading state while initial data is being fetched
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
                    // Sync Status Banner (appears below toolbar)
                    AnimatedVisibility(
                        visible = syncState is SyncState.Error,
                        enter = slideInVertically() + fadeIn(),
                        exit = slideOutVertically() + fadeOut()
                    ) {
                        SyncStatusBanner(syncState = syncState)
                    }

                    // Search Bar
                    OutlinedTextField(
                        value = searchTerm,
                        onValueChange = { viewModel.updateSearchTerm(it) },
                        label = { Text(stringResource(R.string.search_placeholder)) },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        singleLine = true
                    )

                    // Main Category Filter
                    if (mainCategories.size > 1) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.category_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            MainCategoryFilter(
                                categories = mainCategories,
                                selectedCategory = selectedMainCategory,
                                onCategorySelected = { viewModel.updateSelectedMainCategory(it) }                            )
                        }
                    }

                    // Sub-Category Filter (only shown when a main category is selected)
                    AnimatedVisibility(
                        visible = selectedMainCategory != allCategoryString && subCategories.size > 1,  // ✅ Use variable!
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.sub_category_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            SubCategoryFilter(
                                subCategories = subCategories,
                                selectedSubCategory = selectedSubCategory,
                                onSubCategorySelected = { viewModel.updateSelectedSubCategory(it) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Items List or Empty State
                    if (filteredItems.isEmpty()) {
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
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            items(filteredItems, key = { it.id!! }) { item ->
                                HierarchicalItemCard(
                                    item = item,
                                    onClick = { item.id?.let { onEditItem(it)}},
                                    currentLang = currentLanguage
                                )
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
    }
}

@Composable
fun SyncStatusIndicator(syncState: SyncState) {
    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = Modifier.padding(end = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        when (syncState) {
            is SyncState.Syncing -> {
                Icon(
                    imageVector = Icons.Outlined.Sync,
                    contentDescription = stringResource(R.string.cd_syncing),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation)
                )
            }
            is SyncState.Success -> {
                var visible by remember { mutableStateOf(true) }
                LaunchedEffect(syncState) {
                    visible = true
                    delay(2000)
                    visible = false
                }

                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = stringResource(R.string.cd_synced),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            is SyncState.Error -> {
                Icon(
                    imageVector = Icons.Outlined.CloudOff,
                    contentDescription = stringResource(R.string.cd_offline),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            SyncState.Idle -> { }
        }
    }
}

@Composable
fun SyncStatusBanner(syncState: SyncState) {
    if (syncState !is SyncState.Error) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = syncState.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Enhanced Item Card with hierarchical category badge
 */
@Composable
fun HierarchicalItemCard(
    item: Item,
    onClick: () -> Unit,
    currentLang: String  // Add this parameter
) {
    val hierarchy = parseCategory(item.category)

    val displayName = if (currentLang == "hi" && !item.nameHindi.isNullOrBlank()) {
        item.nameHindi!!  // Safe to use !! here because we checked isNullOrBlank()
    } else {
        item.name
    }
    
    val secondaryName = if (currentLang == "hi" && !item.nameHindi.isNullOrBlank()) {
        item.name  // Show English name as secondary
    } else {
        null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = displayName,  // Use displayName instead of item.name
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Show secondary name if available
                    secondaryName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic
                        )
                    }

                    // Sub-category badge
                    if (hierarchy != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = hierarchy.subCategory,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    item.location?.let {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${item.quantity} ${item.unit}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Main Category Filter (Top level)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainCategoryFilter(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    ) {
        items(categories) { category ->
            val displayText = if (category == stringResource(R.string.category_all)) {
                stringResource(R.string.all_categories)
            } else category

            FilterChip(
                selected = category == selectedCategory,
                onClick = { onCategorySelected(category) },
                label = {
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                border = if (category == selectedCategory) {
                    FilterChipDefaults.filterChipBorder(
                        borderColor = MaterialTheme.colorScheme.primary,
                        borderWidth = 2.dp,
                        enabled = true,
                        selected = true
                    )
                } else {
                    FilterChipDefaults.filterChipBorder(enabled = true, selected = false)
                }
            )
        }
    }
}

/**
 * Sub-Category Filter (Second level)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubCategoryFilter(
    subCategories: List<String>,
    selectedSubCategory: String,
    onSubCategorySelected: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    ) {
        items(subCategories) { subCategory ->
            val displayText = if (subCategory == stringResource(R.string.category_all)) {
                stringResource(R.string.category_all)
            } else subCategory

            FilterChip(
                selected = subCategory == selectedSubCategory,
                onClick = { onSubCategorySelected(subCategory) },
                label = {
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                shape = RoundedCornerShape(10.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            )
        }
    }
}