package com.example.homepantry.ui.itemform

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.homepantry.data.InventoryRepository
import com.example.homepantry.data.Item
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class OperationState {
    object Idle : OperationState()
    data class Loading(val operation: String) : OperationState()
    data class Success(val message: String, val operation: String) : OperationState()
    data class Error(val message: String, val operation: String) : OperationState()
}

sealed class DuplicateCheckState {
    object Idle : DuplicateCheckState()
    data class Found(val existingItem: Item, val newItemData: Item) : DuplicateCheckState()
}

@HiltViewModel
class ItemFormViewModel @Inject constructor(
    private val repository: InventoryRepository
) : ViewModel() {

    private val TAG = "ItemFormViewModel"

    private val _operationState = MutableStateFlow<OperationState>(OperationState.Idle)
    val operationState = _operationState.asStateFlow()

    private val _duplicateCheckState =
        MutableStateFlow<DuplicateCheckState>(DuplicateCheckState.Idle)
    val duplicateCheckState = _duplicateCheckState.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>()
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    // Cache of items for duplicate checking - should be updated from outside
    private val _itemsCache = MutableStateFlow<List<Item>>(emptyList())

    /**
     * Update the cached items list for duplicate checking
     * This should be called from the UI layer whenever items change
     */
    fun updateItemsCache(items: List<Item>) {
        _itemsCache.value = items
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
     * Initiate add item with fuzzy duplicate check
     */
    fun initiateAddItem(newItem: Item) {
        Log.d(TAG, "Initiating add item with fuzzy duplicate check for: ${newItem.name}")

        val newNameNormalized = normalizeItemName(newItem.name)

        // Check for duplicate using fuzzy matching
        val existingItem = _itemsCache.value.find { existingItem ->
            val existingNameNormalized = normalizeItemName(existingItem.name)

            // Check 1: Exact match after normalization
            if (existingNameNormalized == newNameNormalized) {
                Log.d(TAG, "Exact match found: '${existingItem.name}' matches '${newItem.name}'")
                return@find true
            }

            // Check 2: One name contains the other (after normalization)
            if (existingNameNormalized.contains(newNameNormalized) ||
                newNameNormalized.contains(existingNameNormalized)
            ) {
                Log.d(TAG, "Containment match found: '${existingItem.name}' ~ '${newItem.name}'")
                return@find true
            }

            // Check 3: Check if core words match (ignore parentheses content and common words)
            val existingCoreWords = extractCoreWords(existingNameNormalized)
            val newCoreWords = extractCoreWords(newNameNormalized)

            // If any significant word matches, consider it a potential duplicate
            val hasCommonWord = existingCoreWords.any { it in newCoreWords }
            if (hasCommonWord && (existingCoreWords.size <= 2 || newCoreWords.size <= 2)) {
                Log.d(
                    TAG,
                    "Core word match found: '${existingItem.name}' ~ '${newItem.name}' (words: $existingCoreWords vs $newCoreWords)"
                )
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

    /**
     * Confirm adding to existing item
     */
    fun confirmDuplicateAdd(existingItemId: Long, quantityToAdd: Double) {
        Log.d(TAG, "Confirming duplicate add: itemId=$existingItemId, quantity=$quantityToAdd")
        _operationState.value = OperationState.Loading("add_item")

        viewModelScope.launch {
            try {
                val existingItem = _itemsCache.value.find { it.id == existingItemId }
                if (existingItem != null) {
                    val updatedItem = existingItem.copy(
                        quantity = existingItem.quantity + quantityToAdd
                    )
                    Log.d(
                        TAG,
                        "Updating item quantity: ${existingItem.quantity} + $quantityToAdd = ${updatedItem.quantity}"
                    )
                    repository.updateItem(updatedItem)
                    _duplicateCheckState.value = DuplicateCheckState.Idle
                    _operationState.value =
                        OperationState.Success("Quantity added to existing item", "add_item")
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

    /**
     * Confirm creating new duplicate item
     */
    fun confirmCreateNewDuplicate(newItem: Item) {
        Log.d(TAG, "Confirming create new duplicate: ${newItem.name}")
        _duplicateCheckState.value = DuplicateCheckState.Idle
        addItem(newItem)
    }

    /**
     * Dismiss duplicate dialog
     */
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
        return _itemsCache.value.find { it.name.trim().lowercase() == normalizedName }
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
                    val existingItem = _itemsCache.value.find { it.id == existingItemId }
                    if (existingItem != null) {
                        val updatedItem = existingItem.copy(
                            quantity = existingItem.quantity + item.quantity
                        )
                        Log.d(
                            TAG,
                            "Adding to existing item: '${item.name}' - Old: ${existingItem.quantity}, Adding: ${item.quantity}, New: ${updatedItem.quantity}"
                        )
                        repository.updateItem(updatedItem)
                        Log.d(TAG, "Successfully added quantity to existing item")
                        _operationState.value =
                            OperationState.Success("Quantity added to existing item", "add_item")
                        showSnackbar("Quantity added to existing item")
                    } else {
                        throw Exception("Existing item not found")
                    }
                } else {
                    // Insert new item
                    Log.d(TAG, "Adding new item: ${item.name}")
                    repository.addItem(mutableListOf(item))
                    Log.d(TAG, "Item added successfully")
                    _operationState.value =
                        OperationState.Success("Item added successfully", "add_item")
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
                _operationState.value =
                    OperationState.Success("Item updated successfully", "update_item")
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
                _operationState.value =
                    OperationState.Success("Item deleted successfully", "delete_item")
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

    fun getItemById(id: Long?): Item? {
        if (id == null) return null
        return _itemsCache.value.find { it.id == id }
    }

    private suspend fun showSnackbar(message: String) {
        _snackbarMessages.emit(message)
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ItemFormViewModel cleared")
    }
}