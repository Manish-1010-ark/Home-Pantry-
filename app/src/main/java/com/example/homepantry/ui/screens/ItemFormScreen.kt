package com.example.homepantry.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.homepantry.R
import com.example.homepantry.data.Item
import com.example.homepantry.ui.InventoryViewModel
import com.example.homepantry.ui.OperationState
import com.example.homepantry.ui.DuplicateCheckState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemFormScreen(
    navController: NavController,
    itemId: Long?,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    // FIX: Scope ViewModel to navigation graph to preserve state across navigation
    val viewModelStoreOwner = remember(navController.currentBackStackEntry) {
        navController.getBackStackEntry("inventory_list")
    }
    val viewModel: InventoryViewModel = hiltViewModel(viewModelStoreOwner)

    val isEditing = itemId != null

    // Collect items to get the current item data and categories
    val items by viewModel.items.collectAsState()
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

    var name by remember(itemToEdit) { mutableStateOf(itemToEdit?.name ?: "") }
    val defaultCategoryFallback = stringResource(R.string.category_groceries)
    var category by remember(itemToEdit, defaultCategoryFallback) {
        mutableStateOf(itemToEdit?.category ?: defaultCategoryFallback)
    }
    var quantity by remember(itemToEdit) { mutableStateOf(itemToEdit?.quantity?.toString() ?: "1.0") }
    var unit by remember(itemToEdit) { mutableStateOf(itemToEdit?.unit ?: "packet") }
    var location by remember(itemToEdit) { mutableStateOf(itemToEdit?.location ?: "") }
    var notes by remember(itemToEdit) { mutableStateOf(itemToEdit?.notes ?: "") }
    var nameHindi by remember(itemToEdit) { mutableStateOf(itemToEdit?.nameHindi ?: "") }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    val operationState by viewModel.operationState.collectAsState()
    val duplicateCheckState by viewModel.duplicateCheckState.collectAsState()
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
    val unitOptions = listOf("packet", "kg", "g", "liter", "ml", "pieces", "bottles", "cans", "boxes", "bag", "jar", "tube", "bottle")
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
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.dialog_delete_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.dialog_delete_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                val isDeleteLoading = operationState is OperationState.Loading &&
                        (operationState as OperationState.Loading).operation == "delete_item"

                Button(
                    onClick = {
                        itemId?.let { viewModel.deleteItem(it) }
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    enabled = !isDeleteLoading
                ) {
                    if (isDeleteLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onError,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.button_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = operationState !is OperationState.Loading
                ) {
                    Text(stringResource(R.string.button_cancel))
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // New Category Dialog
    if (showNewCategoryDialog) {
        AlertDialog(
            onDismissRequest = {
                showNewCategoryDialog = false
                newCategoryName = ""
            },
            title = {
                Text(
                    text = stringResource(R.string.dialog_new_category_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text(stringResource(R.string.dialog_new_category_label)) },
                    placeholder = { Text(stringResource(R.string.dialog_new_category_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCategoryName.isNotBlank()) {
                            category = newCategoryName.trim()
                            showNewCategoryDialog = false
                            newCategoryName = ""
                        }
                    },
                    enabled = newCategoryName.isNotBlank()
                ) {
                    Text(stringResource(R.string.button_create))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNewCategoryDialog = false
                        newCategoryName = ""
                    }
                ) {
                    Text(stringResource(R.string.button_cancel))
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ✅ FIXED: Duplicate item confirmation dialog - now connected to ViewModel state
    if (duplicateCheckState is DuplicateCheckState.Found) {
        val state = duplicateCheckState as DuplicateCheckState.Found
        val existingItem = state.existingItem
        val newItemData = state.newItemData

        AlertDialog(
            onDismissRequest = { viewModel.dismissDuplicateDialog() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = stringResource(R.string.dialog_duplicate_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dialog_duplicate_message, existingItem.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.dialog_duplicate_current),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = existingItem.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "${existingItem.quantity} ${existingItem.unit}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            existingItem.location?.let { loc ->
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
                                        text = loc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.dialog_duplicate_adding),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${newItemData.quantity} ${newItemData.unit}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.dialog_duplicate_new_total),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${existingItem.quantity + newItemData.quantity} ${existingItem.unit}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val isLoading = operationState is OperationState.Loading &&
                            (operationState as OperationState.Loading).operation == "add_item"

                    Button(
                        onClick = {
                            existingItem.id?.let { id ->
                                viewModel.confirmDuplicateAdd(id, newItemData.quantity)
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.dialog_duplicate_add_existing))
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.confirmCreateNewDuplicate(newItemData)
                        },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.dialog_duplicate_create_new))
                    }

                    TextButton(
                        onClick = {
                            viewModel.dismissDuplicateDialog()
                        },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.button_cancel))
                    }
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                val currentLanguage by viewModel.appLanguage.collectAsState("en")
                TopAppBar(
                    title = {
                        val titleText = if (isEditing) {
                            if (currentLanguage == "hi" && !itemToEdit?.nameHindi.isNullOrBlank()) {
                                "${stringResource(R.string.item_form_edit_title)}: ${itemToEdit?.nameHindi}"
                            } else {
                                "${stringResource(R.string.item_form_edit_title)}: ${itemToEdit?.name}"
                            }
                        } else {
                            stringResource(R.string.item_form_add_title)
                        }

                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        if (isEditing) {
                            IconButton(
                                onClick = { showDeleteDialog = true },
                                enabled = operationState !is OperationState.Loading
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.cd_delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
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
                val isFormLoading = operationState is OperationState.Loading &&
                        ((operationState as OperationState.Loading).operation == "add_item" ||
                                (operationState as OperationState.Loading).operation == "update_item")

                // CARD 1: Main Details
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Main Details",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Name Field
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.field_name)) },
                            placeholder = { Text(stringResource(R.string.field_name_hint)) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Edit, contentDescription = null)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            enabled = !isFormLoading
                        )

                        // Hindi Name Field (Optional)
                        OutlinedTextField(
                            value = nameHindi,
                            onValueChange = { nameHindi = it },
                            label = { Text(stringResource(R.string.field_name_hindi)) },
                            placeholder = { Text(stringResource(R.string.field_name_hindi_hint)) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Language, contentDescription = null)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            enabled = !isFormLoading
                        )

                        // Category Dropdown
                        ExposedDropdownMenuBox(
                            expanded = expandedCategory,
                            onExpandedChange = {
                                if (!isFormLoading) {
                                    expandedCategory = !expandedCategory
                                }
                            }
                        ) {
                            OutlinedTextField(
                                value = category,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.field_category)) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Category, contentDescription = null)
                                },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isFormLoading
                            )
                            ExposedDropdownMenu(
                                expanded = expandedCategory,
                                onDismissRequest = { expandedCategory = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Add,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                stringResource(R.string.create_new_category),
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    },
                                    onClick = {
                                        expandedCategory = false
                                        showNewCategoryDialog = true
                                    }
                                )

                                if (categoryOptions.isNotEmpty()) {
                                    HorizontalDivider()
                                }

                                categoryOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            category = option
                                            expandedCategory = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // CARD 2: Stock & Location
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Stock & Location",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Quantity and Unit Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Quantity Field
                            OutlinedTextField(
                                value = quantity,
                                onValueChange = { quantity = it },
                                label = { Text(stringResource(R.string.field_quantity)) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Numbers, contentDescription = null)
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                enabled = !isFormLoading
                            )

                            // Unit Dropdown
                            ExposedDropdownMenuBox(
                                expanded = expandedUnit,
                                onExpandedChange = {
                                    if (!isFormLoading) {
                                        expandedUnit = !expandedUnit
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = unit,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.field_unit)) },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedUnit)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    enabled = !isFormLoading
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedUnit,
                                    onDismissRequest = { expandedUnit = false }
                                ) {
                                    unitOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option) },
                                            onClick = {
                                                unit = option
                                                expandedUnit = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Location Field
                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = { Text(stringResource(R.string.field_location)) },
                            placeholder = { Text(stringResource(R.string.field_location_hint)) },
                            leadingIcon = {
                                Icon(Icons.Outlined.LocationOn, contentDescription = null)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            enabled = !isFormLoading
                        )
                    }
                }

                // CARD 3: Notes
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Additional Notes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Notes Field
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text(stringResource(R.string.field_notes)) },
                            placeholder = { Text(stringResource(R.string.field_notes_hint)) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Notes, contentDescription = null)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            minLines = 3,
                            maxLines = 5,
                            enabled = !isFormLoading
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Required fields hint
                Text(
                    text = stringResource(R.string.required_fields),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ✅ FIXED: Save Button now uses initiateAddItem for new items
                Button(
                    onClick = {
                        val houseId = viewModel.getCurrentHouseId() ?: return@Button

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
                            viewModel.updateItem(finalItem)
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
                            viewModel.initiateAddItem(newItem)
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
                            text = if (isEditing) stringResource(R.string.button_save_changes) else stringResource(R.string.button_add_item),
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