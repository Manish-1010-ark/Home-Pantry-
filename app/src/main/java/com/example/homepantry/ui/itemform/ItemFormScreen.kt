package com.example.homepantry.ui.itemform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.homepantry.R
import com.example.homepantry.data.Item
import com.example.homepantry.ui.auth.AuthViewModel
import com.example.homepantry.ui.inventory.InventoryListViewModel

@Composable
fun ItemFormScreen(
    navController: NavController,
    itemId: Long?,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    // ViewModels
    val formViewModel: ItemFormViewModel = hiltViewModel()
    val authViewModel: AuthViewModel = hiltViewModel()

    // Scope InventoryListViewModel to navigation graph to get items list
    val viewModelStoreOwner = remember(navController.currentBackStackEntry) {
        navController.getBackStackEntry("inventory_list")
    }
    val inventoryViewModel: InventoryListViewModel = hiltViewModel(viewModelStoreOwner)

    val isEditing = itemId != null

    // Collect items and update cache for duplicate checking
    val items by inventoryViewModel.items.collectAsState()

    // Critical: Update ItemFormViewModel's cache whenever items change
    LaunchedEffect(items) {
        formViewModel.updateItemsCache(items)
    }

    val itemToEdit = remember(itemId, items) {
        if (itemId != null) {
            items.find { it.id == itemId }
        } else {
            null
        }
    }

    // Dynamic category options from existing items
    val existingCategories = remember(items) {
        items.map { it.category }.distinct().sorted()
    }

    // Form state
    var name by remember(itemToEdit) { mutableStateOf(itemToEdit?.name ?: "") }
    val defaultCategoryFallback = stringResource(R.string.category_groceries)
    var category by remember(itemToEdit, defaultCategoryFallback) {
        mutableStateOf(itemToEdit?.category ?: defaultCategoryFallback)
    }
    var quantity by remember(itemToEdit) {
        mutableStateOf(itemToEdit?.quantity?.toString() ?: "1.0")
    }
    var unit by remember(itemToEdit) { mutableStateOf(itemToEdit?.unit ?: "packet") }
    var location by remember(itemToEdit) { mutableStateOf(itemToEdit?.location ?: "") }
    var notes by remember(itemToEdit) { mutableStateOf(itemToEdit?.notes ?: "") }
    var nameHindi by remember(itemToEdit) { mutableStateOf(itemToEdit?.nameHindi ?: "") }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    val operationState by formViewModel.operationState.collectAsState()
    val duplicateCheckState by formViewModel.duplicateCheckState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Default category options
    val defaultCategories = listOf(
        stringResource(R.string.category_groceries),
        stringResource(R.string.category_spices),
        stringResource(R.string.category_cleaning),
        stringResource(R.string.category_miscellaneous)
    )

    // Combine default and existing categories
    val categoryOptions = remember(existingCategories) {
        (defaultCategories + existingCategories).distinct().sorted()
    }

    var expandedCategory by remember { mutableStateOf(false) }

    // Unit Options
    val unitOptions = listOf(
        "packet", "kg", "g", "liter", "ml", "pieces", "bottles",
        "cans", "boxes", "bag", "jar", "tube", "bottle"
    )
    var expandedUnit by remember { mutableStateOf(false) }

    // Handle operation state changes
    LaunchedEffect(operationState) {
        when (operationState) {
            is OperationState.Success -> {
                val operation = (operationState as OperationState.Success).operation
                if (operation == "delete_item" || operation == "add_item" || operation == "update_item") {
                    onSave()
                }
            }

            else -> {}
        }
    }

    // Handle snackbar messages
    LaunchedEffect(Unit) {
        formViewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    // Dialogs
    if (showDeleteDialog) {
        DeleteDialog(
            onConfirm = {
                itemId?.let { formViewModel.deleteItem(it) }
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false },
            operationState = operationState
        )
    }

    if (showNewCategoryDialog) {
        NewCategoryDialog(
            categoryName = newCategoryName,
            onCategoryNameChange = { newCategoryName = it },
            onConfirm = {
                if (newCategoryName.isNotBlank()) {
                    category = newCategoryName.trim()
                    showNewCategoryDialog = false
                    newCategoryName = ""
                }
            },
            onDismiss = {
                showNewCategoryDialog = false
                newCategoryName = ""
            }
        )
    }

    if (duplicateCheckState is DuplicateCheckState.Found) {
        DuplicateItemDialog(
            duplicateCheckState = duplicateCheckState as DuplicateCheckState.Found,
            operationState = operationState,
            onAddToExisting = { existingItemId, quantityToAdd ->
                formViewModel.confirmDuplicateAdd(existingItemId, quantityToAdd)
            },
            onCreateNew = { newItem ->
                formViewModel.confirmCreateNewDuplicate(newItem)
            },
            onDismiss = {
                formViewModel.dismissDuplicateDialog()
            }
        )
    }

    val isFormLoading = operationState is OperationState.Loading &&
            ((operationState as OperationState.Loading).operation == "add_item" ||
                    (operationState as OperationState.Loading).operation == "update_item")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                ItemFormTopBar(
                    isEditing = isEditing,
                    itemToEdit = itemToEdit,
                    onDeleteClick = { showDeleteDialog = true },
                    operationState = operationState
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Main Details Card
                MainDetailsCard(
                    name = name,
                    onNameChange = { name = it },
                    nameHindi = nameHindi,
                    onNameHindiChange = { nameHindi = it },
                    category = category,
                    expandedCategory = expandedCategory,
                    onExpandedCategoryChange = { if (!isFormLoading) expandedCategory = it },
                    categoryOptions = categoryOptions,
                    onCategorySelect = { category = it; expandedCategory = false },
                    onNewCategoryClick = {
                        expandedCategory = false
                        showNewCategoryDialog = true
                    },
                    isFormLoading = isFormLoading
                )

                // Stock & Location Card
                StockAndLocationCard(
                    quantity = quantity,
                    onQuantityChange = { quantity = it },
                    unit = unit,
                    expandedUnit = expandedUnit,
                    onExpandedUnitChange = { if (!isFormLoading) expandedUnit = it },
                    unitOptions = unitOptions,
                    onUnitSelect = { unit = it; expandedUnit = false },
                    location = location,
                    onLocationChange = { location = it },
                    isFormLoading = isFormLoading
                )

                // Notes Card
                NotesCard(
                    notes = notes,
                    onNotesChange = { notes = it },
                    isFormLoading = isFormLoading
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Required fields hint
                Text(
                    text = stringResource(R.string.required_fields),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Save Button
                Button(
                    onClick = {
                        val houseId = authViewModel.getCurrentHouseId() ?: return@Button

                        if (isEditing) {
                            // Editing existing item - no duplicate check needed
                            val finalItem = Item(
                                id = itemId,
                                name = name,
                                nameHindi = nameHindi.ifBlank { null },
                                category = category,
                                quantity = quantity.toDoubleOrNull() ?: 0.0,
                                unit = unit,
                                location = location.ifBlank { null },
                                notes = notes.ifBlank { null },
                                house_id = houseId
                            )
                            formViewModel.updateItem(finalItem)
                        } else {
                            // Adding new item - trigger duplicate check
                            val newItem = Item(
                                name = name,
                                nameHindi = nameHindi.ifBlank { null },
                                category = category,
                                quantity = quantity.toDoubleOrNull() ?: 0.0,
                                unit = unit,
                                location = location.ifBlank { null },
                                notes = notes.ifBlank { null },
                                house_id = houseId
                            )
                            formViewModel.initiateAddItem(newItem)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = name.isNotBlank() &&
                            category.isNotBlank() &&
                            quantity.toDoubleOrNull() != null &&
                            !isFormLoading
                ) {
                    if (isFormLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (isEditing)
                                stringResource(R.string.button_save_changes)
                            else
                                stringResource(R.string.button_add_item),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                // Cancel Button
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isFormLoading
                ) {
                    Text(stringResource(R.string.button_cancel))
                }
            }
        }
    }
}