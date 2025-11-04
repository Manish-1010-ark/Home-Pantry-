package com.example.homepantry.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.homepantry.data.InventoryRepository
import com.example.homepantry.data.Item
import com.example.homepantry.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import javax.inject.Inject

sealed class LoginState {
    object Checking : LoginState()
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val houseId: Long, val houseName: String) : LoginState()
    data class Error(val message: String) : LoginState()
}

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    object Success : SyncState()
    data class Error(val message: String) : SyncState()
}

sealed class OperationState {
    object Idle : OperationState()
    data class Loading(val operation: String) : OperationState()
    data class Success(val message: String, val operation: String) : OperationState()
    data class Error(val message: String, val operation: String) : OperationState()
}

// ✅ NEW: Duplicate check state
sealed class DuplicateCheckState {
    object Idle : DuplicateCheckState()
    data class Found(val existingItem: Item, val newItemData: Item) : DuplicateCheckState()
}

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val TAG = "InventoryViewModel"

    // ✅ OPTIONAL: Use a constant for internal "All" state
    // This isn't displayed to users, but makes code clearer
    companion object {
        private const val ALL_CATEGORIES_INTERNAL = "All"
    }

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Checking)
    val loginState = _loginState.asStateFlow()

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items = _items.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState = _syncState.asStateFlow()

    private val _operationState = MutableStateFlow<OperationState>(OperationState.Idle)
    val operationState = _operationState.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>()
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    // ✅ NEW: Duplicate check state flow
    private val _duplicateCheckState = MutableStateFlow<DuplicateCheckState>(DuplicateCheckState.Idle)
    val duplicateCheckState = _duplicateCheckState.asStateFlow()

    // Add these after _snackbarMessages
    private val _searchTerm = MutableStateFlow("")
    val searchTerm = _searchTerm.asStateFlow()

    private val _selectedMainCategory = MutableStateFlow(ALL_CATEGORIES_INTERNAL)
    val selectedMainCategory = _selectedMainCategory.asStateFlow()

    private val _selectedSubCategory = MutableStateFlow(ALL_CATEGORIES_INTERNAL)
    val selectedSubCategory = _selectedSubCategory.asStateFlow()

    private var hasCheckedSession = false

    val appLanguage = prefsRepository.appLanguage


    init {
        checkSavedSession()

        viewModelScope.launch {
            repository.syncState.collect { state ->
                _syncState.value = state
            }
        }
    }

    fun updateSearchTerm(term: String) {
        _searchTerm.value = term
    }

    fun updateSelectedMainCategory(category: String) {
        _selectedMainCategory.value = category
    }

    fun updateSelectedSubCategory(subCategory: String) {
        _selectedSubCategory.value = subCategory
    }

    private fun checkSavedSession() {
        if (hasCheckedSession) return
        hasCheckedSession = true

        viewModelScope.launch {
            prefsRepository.houseInfoFlow.firstOrNull()?.let { (houseId, pin, houseName) ->
                if (houseId != null && pin != null && houseName != null) {
                    Log.d(TAG, "Found saved session for house: $houseName (ID: $houseId)")
                    _loginState.value = LoginState.Success(houseId, houseName)
                    startCollectingItems(houseId)
                } else {
                    Log.d(TAG, "No saved session found.")
                    _loginState.value = LoginState.Idle
                }
            } ?: run {
                _loginState.value = LoginState.Idle
            }
        }
    }

    // In InventoryViewModel.kt, replace the setAppLanguage function:

    fun setAppLanguage(language: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                prefsRepository.saveAppLanguage(language)
                // Wait for DataStore to complete the write
                kotlinx.coroutines.delay(200)  // Small delay to ensure persistence
                Log.d(TAG, "Language preference saved: $language")
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting app language", e)
            }
        }
    }

    private fun startCollectingItems(houseId: Long) {
        viewModelScope.launch {
            try {
                repository.getItems(houseId)
                    .catch { e ->
                        Log.e(TAG, "Error collecting items", e)
                        _items.value = emptyList()
                    }
                    .collect { itemList ->
                        Log.d(TAG, "Received ${itemList.size} items from repository")
                        _items.value = itemList
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error in items collection", e)
            }
        }
    }

    private suspend fun showSnackbar(message: String) {
        _snackbarMessages.emit(message)
    }

    fun login(pin: String) {
        if (pin.isBlank()) {
            _loginState.value = LoginState.Error("PIN cannot be empty.")
            return
        }
        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            val house = repository.getHouseForPin(pin)
            if (house != null) {
                prefsRepository.saveHouseInfo(house.id, house.pin, house.house_name)
                _loginState.value = LoginState.Success(house.id, house.house_name)
                startCollectingItems(house.id)
            } else {
                _loginState.value = LoginState.Error("Invalid PIN. Please try again.")
            }
        }
    }

    fun logout() {
        _operationState.value = OperationState.Loading("logout")
        viewModelScope.launch {
            try {
                repository.cleanup()
                prefsRepository.clearHouseInfo()
                _loginState.value = LoginState.Idle
                _items.value = emptyList()
                hasCheckedSession = false
                _operationState.value = OperationState.Success("Logged out successfully", "logout")
                showSnackbar("Logged out successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error during logout", e)
                _operationState.value = OperationState.Error("Logout failed. Please try again.", "logout")
                showSnackbar("Logout failed. Please try again.")
            }
        }
    }

    /**
     * Validates and normalizes category format.
     * Accepts:
     * - "Main:Sub" format (e.g., "Food & Groceries:Spices & Seasonings")
     * - Plain text (converts to "Miscellaneous:Plain Text")
     * Returns null if invalid
     */
    private fun validateAndNormalizeCategory(categoryRaw: String): String? {
        if (categoryRaw.isBlank()) {
            return "Miscellaneous:Uncategorized"
        }

        // Check if it's in "Main:Sub" format
        return if (categoryRaw.contains(":")) {
            val parts = categoryRaw.split(":", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                // Valid hierarchical format
                "${parts[0].trim()}:${parts[1].trim()}"
            } else {
                // Invalid format with colon but missing parts
                Log.w(TAG, "Invalid category format: '$categoryRaw' - missing main or sub category")
                null
            }
        } else {
            // Plain text - convert to hierarchical format
            "Miscellaneous:${categoryRaw.trim()}"
        }
    }

    /**
     * Enhanced Excel import with hierarchical category support (Main:Sub format),
     * aggregation, upsert logic, and detailed logging
     */
    fun importItemsFromExcel(uri: Uri, context: Context) {
        Log.d(TAG, "=== STARTING HIERARCHICAL EXCEL IMPORT ===")
        _operationState.value = OperationState.Loading("import_items")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val houseId = getCurrentHouseId() ?: run {
                    Log.e(TAG, "Import failed: User not logged in")
                    _operationState.value = OperationState.Error("Import failed: Not logged in", "import_items")
                    showSnackbar("Import failed: Not logged in")
                    return@launch
                }

                // Step 1: Fetch existing items from database
                val existingItems = items.value
                Log.d(TAG, "Step 1: Fetched ${existingItems.size} existing items from database")

                // Step 2: Parse Excel file with hierarchical categories
                Log.d(TAG, "Step 2: Parsing Excel file with hierarchical categories...")
                val aggregatedItemsMap = mutableMapOf<String, AggregatedItem>()
                var totalRowsParsed = 0
                var skippedRowCount = 0
                var categoryValidationWarnings = 0

                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val workbook = XSSFWorkbook(stream)
                    val sheet = workbook.getSheetAt(0)

                    // Validate header row (optional but helpful)
                    val headerRow = sheet.getRow(0)
                    if (headerRow != null) {
                        val expectedHeaders = listOf("Name", "Category", "Quantity", "Unit", "Location", "Notes", "NameHindi")
                        val actualHeaders = (0..6).mapNotNull {
                            headerRow.getCell(it)?.stringCellValue?.trim()
                        }

                        if (actualHeaders.size >= 4) { // At least Name, Category, Quantity, Unit
                            Log.d(TAG, "Header validation: Found ${actualHeaders.size} columns")
                            if (actualHeaders != expectedHeaders) {
                                Log.w(TAG, "Header columns may not match expected format. Expected: $expectedHeaders, Got: $actualHeaders")
                            }
                        }
                    }

                    for (rowIndex in 1..sheet.lastRowNum) {
                        val row = sheet.getRow(rowIndex) ?: continue
                        totalRowsParsed++

                        try {
                            // Column 0: Name
                            val name = row.getCell(0)?.stringCellValue?.trim() ?: ""
                            if (name.isBlank()) {
                                Log.w(TAG, "Row ${rowIndex + 1}: Skipped - Empty item name")
                                skippedRowCount++
                                continue
                            }

                            // Column 1: Category (format: "Main:Sub" or plain text)
                            val categoryRaw = row.getCell(1)?.stringCellValue?.trim() ?: ""
                            val category = validateAndNormalizeCategory(categoryRaw)
                            if (category == null) {
                                Log.w(TAG, "Row ${rowIndex + 1}: Skipped - Invalid category format '$categoryRaw' for item '$name'")
                                skippedRowCount++
                                categoryValidationWarnings++
                                continue
                            }

                            // Log if category was auto-converted
                            if (!categoryRaw.contains(":") && categoryRaw.isNotBlank()) {
                                Log.d(TAG, "Row ${rowIndex + 1}: Auto-converted category '$categoryRaw' → '$category'")
                            }

                            // Column 2: Quantity
                            val quantity = row.getCell(2)?.let {
                                when (it.cellType) {
                                    CellType.NUMERIC -> it.numericCellValue
                                    CellType.STRING -> it.stringCellValue.toDoubleOrNull()
                                    else -> null
                                }
                            }

                            if (quantity == null || quantity < 0) {
                                Log.w(TAG, "Row ${rowIndex + 1}: Skipped - Invalid quantity for item '$name' (must be 0 or positive)")
                                skippedRowCount++
                                continue
                            }

                            // Column 3: Unit
                            val unit = row.getCell(3)?.stringCellValue?.trim() ?: "piece"
                            if (unit.isBlank()) {
                                Log.w(TAG, "Row ${rowIndex + 1}: Warning - Empty unit for '$name', defaulting to 'piece'")
                            }

                            // Column 4: Location (optional)
                            val location = row.getCell(4)?.stringCellValue?.trim()?.takeIf { it.isNotBlank() }

                            // Column 5: Notes (optional)
                            val notes = row.getCell(5)?.stringCellValue?.trim()?.takeIf { it.isNotBlank() }

                            // Column 6: NameHindi (optional)
                            val nameHindi = row.getCell(6)?.stringCellValue?.trim()?.takeIf { it.isNotBlank() }

                            // Create unique key: name + unit + location (all case-insensitive)
                            val itemKey = "${name.lowercase()}|${unit.lowercase()}|${location?.lowercase() ?: ""}"

                            if (aggregatedItemsMap.containsKey(itemKey)) {
                                // Item with same name, unit, AND location - aggregate quantities
                                val existing = aggregatedItemsMap[itemKey]!!
                                aggregatedItemsMap[itemKey] = existing.copy(
                                    quantity = existing.quantity + quantity,
                                    // Keep latest values for other fields
                                    category = category,
                                    notes = notes ?: existing.notes,
                                    nameHindi = nameHindi ?: existing.nameHindi
                                )
                                Log.d(TAG, "Row ${rowIndex + 1}: Aggregated '$name' [${category}] (${unit}, ${location ?: "no location"}) - New total: ${existing.quantity + quantity} $unit")
                            } else {
                                // First occurrence or different unit/location - create new entry
                                aggregatedItemsMap[itemKey] = AggregatedItem(
                                    name = name,
                                    category = category,
                                    quantity = quantity,
                                    unit = unit,
                                    location = location,
                                    notes = notes,
                                    nameHindi = nameHindi
                                )
                                Log.d(TAG, "Row ${rowIndex + 1}: Parsed '$name' [${category}] - Quantity: $quantity $unit, Location: ${location ?: "none"}")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Row ${rowIndex + 1}: Skipped due to parsing error - ${e.message}", e)
                            skippedRowCount++
                        }
                    }
                }

                Log.i(TAG, "Step 2 Complete: Parsed $totalRowsParsed rows → ${aggregatedItemsMap.size} unique items, $skippedRowCount skipped")
                if (categoryValidationWarnings > 0) {
                    Log.w(TAG, "Category validation warnings: $categoryValidationWarnings items had invalid category format")
                }

                // Step 3: Perform upsert operations (update existing or insert new)
                Log.d(TAG, "Step 3: Performing upsert operations...")
                val itemsToInsert = mutableListOf<Item>()
                val itemsToUpdate = mutableListOf<Item>()

                aggregatedItemsMap.forEach { (itemKey, aggregatedItem) ->
                    // Check if item exists in database with same name, unit, and location
                    val existingItem = existingItems.find {
                        it.name.trim().lowercase() == aggregatedItem.name.lowercase() &&
                                it.unit.trim().lowercase() == aggregatedItem.unit.lowercase() &&
                                (it.location?.trim()?.lowercase() ?: "") == (aggregatedItem.location?.lowercase() ?: "")
                    }

                    if (existingItem != null) {
                        // UPSERT-UPDATE: Item exists in database
                        val updatedItem = existingItem.copy(
                            quantity = existingItem.quantity + aggregatedItem.quantity,
                            category = aggregatedItem.category, // Update to new hierarchical category
                            notes = aggregatedItem.notes,
                            nameHindi = aggregatedItem.nameHindi
                        )
                        itemsToUpdate.add(updatedItem)
                        Log.d(TAG, "UPSERT-UPDATE: '${aggregatedItem.name}' [${aggregatedItem.category}] - Old: ${existingItem.quantity}, Added: ${aggregatedItem.quantity}, New: ${updatedItem.quantity}")
                    } else {
                        // UPSERT-INSERT: New item or different unit/location combination
                        val newItem = Item(
                            name = aggregatedItem.name,
                            category = aggregatedItem.category,
                            quantity = aggregatedItem.quantity,
                            unit = aggregatedItem.unit,
                            location = aggregatedItem.location,
                            notes = aggregatedItem.notes,
                            nameHindi = aggregatedItem.nameHindi,
                            house_id = houseId
                        )
                        itemsToInsert.add(newItem)
                        Log.d(TAG, "UPSERT-INSERT: '${aggregatedItem.name}' [${aggregatedItem.category}] - Qty: ${aggregatedItem.quantity} ${aggregatedItem.unit}")
                    }
                }

                // Step 4: Execute database operations
                Log.d(TAG, "Step 4: Executing database operations...")
                if (itemsToInsert.isNotEmpty()) {
                    Log.d(TAG, "Inserting ${itemsToInsert.size} new items...")
                    repository.addItem(itemsToInsert)
                    Log.i(TAG, "✓ Successfully inserted ${itemsToInsert.size} new items")
                }

                if (itemsToUpdate.isNotEmpty()) {
                    Log.d(TAG, "Updating ${itemsToUpdate.size} existing items...")
                    itemsToUpdate.forEach { repository.updateItem(it) }
                    Log.i(TAG, "✓ Successfully updated ${itemsToUpdate.size} existing items")
                }

                // Step 5: Generate summary and provide feedback
                val summaryMessage = buildString {
                    if (itemsToInsert.isEmpty() && itemsToUpdate.isEmpty()) {
                        append("No valid items to import")
                    } else {
                        val parts = mutableListOf<String>()
                        if (itemsToInsert.isNotEmpty()) {
                            parts.add("${itemsToInsert.size} added")
                        }
                        if (itemsToUpdate.isNotEmpty()) {
                            parts.add("${itemsToUpdate.size} updated")
                        }
                        if (skippedRowCount > 0) {
                            parts.add("$skippedRowCount skipped")
                        }
                        append(parts.joinToString(", "))
                    }
                }

                Log.i(TAG, "=== HIERARCHICAL IMPORT COMPLETE === Summary: $summaryMessage")

                if (itemsToInsert.isEmpty() && itemsToUpdate.isEmpty()) {
                    _operationState.value = OperationState.Error("No valid items found to import", "import_items")
                    showSnackbar("No valid items found in file")
                } else {
                    _operationState.value = OperationState.Success(summaryMessage, "import_items")
                    showSnackbar("Import successful: $summaryMessage")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Fatal error during Excel import", e)
                _operationState.value = OperationState.Error("Failed to read file. Please check format.", "import_items")
                showSnackbar("Failed to read file. Please check the format.")
            }
        }
    }

    // ✅ NEW: Initiate add item with fuzzy duplicate check
    fun initiateAddItem(newItem: Item) {
        Log.d(TAG, "Initiating add item with fuzzy duplicate check for: ${newItem.name}")

        val newNameNormalized = normalizeItemName(newItem.name)

        // Check for duplicate using fuzzy matching
        val existingItem = items.value.find { existingItem ->
            val existingNameNormalized = normalizeItemName(existingItem.name)

            // Check 1: Exact match after normalization
            if (existingNameNormalized == newNameNormalized) {
                Log.d(TAG, "Exact match found: '${existingItem.name}' matches '${newItem.name}'")
                return@find true
            }

            // Check 2: One name contains the other (after normalization)
            if (existingNameNormalized.contains(newNameNormalized) ||
                newNameNormalized.contains(existingNameNormalized)) {
                Log.d(TAG, "Containment match found: '${existingItem.name}' ~ '${newItem.name}'")
                return@find true
            }

            // Check 3: Check if core words match (ignore parentheses content and common words)
            val existingCoreWords = extractCoreWords(existingNameNormalized)
            val newCoreWords = extractCoreWords(newNameNormalized)

            // If any significant word matches, consider it a potential duplicate
            val hasCommonWord = existingCoreWords.any { it in newCoreWords }
            if (hasCommonWord && (existingCoreWords.size <= 2 || newCoreWords.size <= 2)) {
                Log.d(TAG, "Core word match found: '${existingItem.name}' ~ '${newItem.name}' (words: $existingCoreWords vs $newCoreWords)")
                return@find true
            }

            false
        }

        if (existingItem != null) {
            Log.d(TAG, "Potential duplicate found: ${existingItem.name} (ID: ${existingItem.id})")
            _duplicateCheckState.value = DuplicateCheckState.Found(existingItem, newItem)
        } else {
            Log.d(TAG, "No duplicate found, proceeding with add")
            addItem(newItem)
        }
    }

    /**
     * Normalize item name for fuzzy matching:
     * - Convert to lowercase
     * - Remove extra spaces
     * - Remove special characters except parentheses
     */
    private fun normalizeItemName(name: String): String {
        return name.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9\\s()]"), " ") // Keep letters, numbers, spaces, and parentheses
            .replace(Regex("\\s+"), " ") // Normalize multiple spaces to single space
            .trim()
    }

    /**
     * Extract core words from item name (ignore content in parentheses and common filler words)
     */
    private fun extractCoreWords(normalizedName: String): Set<String> {
        // Remove content in parentheses
        val withoutParentheses = normalizedName.replace(Regex("\\([^)]*\\)"), "").trim()

        // Common words to ignore
        val ignoreWords = setOf("the", "a", "an", "of", "and", "or", "in", "on", "with", "for")

        // Extract meaningful words (length > 2 and not in ignore list)
        return withoutParentheses.split(Regex("\\s+"))
            .filter { it.length > 2 && it !in ignoreWords }
            .toSet()
    }

    // ✅ NEW: Confirm adding to existing item
    fun confirmDuplicateAdd(existingItemId: Long, quantityToAdd: Double) {
        Log.d(TAG, "Confirming duplicate add: itemId=$existingItemId, quantity=$quantityToAdd")
        _operationState.value = OperationState.Loading("add_item")

        viewModelScope.launch {
            try {
                val existingItem = items.value.find { it.id == existingItemId }
                if (existingItem != null) {
                    val updatedItem = existingItem.copy(
                        quantity = existingItem.quantity + quantityToAdd
                    )
                    Log.d(TAG, "Updating item quantity: ${existingItem.quantity} + $quantityToAdd = ${updatedItem.quantity}")
                    repository.updateItem(updatedItem)
                    _duplicateCheckState.value = DuplicateCheckState.Idle
                    _operationState.value = OperationState.Success("Quantity added to existing item", "add_item")
                    showSnackbar("Quantity added to existing item")
                } else {
                    throw Exception("Existing item not found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding to existing item", e)
                _operationState.value = OperationState.Error("Failed to add quantity", "add_item")
                showSnackbar("Failed to add quantity")
            }
        }
    }

    // ✅ NEW: Confirm creating new duplicate item
    fun confirmCreateNewDuplicate(newItem: Item) {
        Log.d(TAG, "Confirming create new duplicate: ${newItem.name}")
        _duplicateCheckState.value = DuplicateCheckState.Idle
        addItem(newItem)
    }

    // ✅ NEW: Dismiss duplicate dialog
    fun dismissDuplicateDialog() {
        Log.d(TAG, "Dismissing duplicate dialog")
        _duplicateCheckState.value = DuplicateCheckState.Idle
        _operationState.value = OperationState.Idle
    }

    /**
     * Check if an item with the given name already exists (case-insensitive)
     * Returns the existing item if found, null otherwise
     */
    fun checkDuplicateItem(name: String): Item? {
        val normalizedName = name.trim().lowercase()
        return items.value.find { it.name.trim().lowercase() == normalizedName }
    }

    /**
     * Add a new item or update existing if confirmed by user
     */
    fun addItem(item: Item, isUpdateExisting: Boolean = false, existingItemId: Long? = null) {
        _operationState.value = OperationState.Loading("add_item")
        viewModelScope.launch {
            try {
                if (isUpdateExisting && existingItemId != null) {
                    // Update existing item by adding quantities
                    val existingItem = items.value.find { it.id == existingItemId }
                    if (existingItem != null) {
                        val updatedItem = existingItem.copy(
                            quantity = existingItem.quantity + item.quantity
                        )
                        Log.d(TAG, "Adding to existing item: '${item.name}' - Old: ${existingItem.quantity}, Adding: ${item.quantity}, New: ${updatedItem.quantity}")
                        repository.updateItem(updatedItem)
                        Log.d(TAG, "Successfully added quantity to existing item")
                        _operationState.value = OperationState.Success("Quantity added to existing item", "add_item")
                        showSnackbar("Quantity added to existing item")
                    } else {
                        throw Exception("Existing item not found")
                    }
                } else {
                    // Insert new item
                    Log.d(TAG, "Adding new item: ${item.name}")
                    repository.addItem(mutableListOf(item))
                    Log.d(TAG, "Item added successfully")
                    _operationState.value = OperationState.Success("Item added successfully", "add_item")
                    showSnackbar("Item added successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding item", e)
                _operationState.value = OperationState.Error("Failed to add item", "add_item")
                showSnackbar("Failed to add item")
            }
        }
    }

    fun updateItem(item: Item) {
        _operationState.value = OperationState.Loading("update_item")
        viewModelScope.launch {
            try {
                Log.d(TAG, "Updating item: ${item.name}")
                repository.updateItem(item)
                Log.d(TAG, "Item updated successfully")
                _operationState.value = OperationState.Success("Item updated successfully", "update_item")
                showSnackbar("Item updated successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating item", e)
                _operationState.value = OperationState.Error("Failed to update item", "update_item")
                showSnackbar("Failed to update item")
            }
        }
    }

    fun deleteItem(itemId: Long) {
        _operationState.value = OperationState.Loading("delete_item")
        viewModelScope.launch {
            try {
                Log.d(TAG, "Deleting item with id: $itemId")
                repository.deleteItem(itemId)
                Log.d(TAG, "Item deleted successfully")
                _operationState.value = OperationState.Success("Item deleted successfully", "delete_item")
                showSnackbar("Item deleted successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting item", e)
                _operationState.value = OperationState.Error("Failed to delete item", "delete_item")
                showSnackbar("Failed to delete item")
            }
        }
    }

    fun clearOperationState() {
        _operationState.value = OperationState.Idle
    }

    fun getCurrentHouseId(): Long? {
        return if (loginState.value is LoginState.Success) {
            (loginState.value as LoginState.Success).houseId
        } else {
            null
        }
    }

    fun getCurrentHouseName(): String {
        return if (loginState.value is LoginState.Success) {
            (loginState.value as LoginState.Success).houseName
        } else {
            "My House"
        }
    }

    fun getItemById(id: Long?): Item? {
        if (id == null) return null
        return items.value.find { it.id == id }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared")
    }
}

/**
 * Helper data class for aggregating items during Excel import
 */
private data class AggregatedItem(
    val name: String,
    val category: String,
    val quantity: Double,
    val unit: String,
    val location: String?,
    val notes: String?,
    val nameHindi: String?
)