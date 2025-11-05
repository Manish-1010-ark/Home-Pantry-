package com.example.homepantry.ui.inventory

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.homepantry.data.InventoryRepository
import com.example.homepantry.data.Item
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    object Success : SyncState()
    data class Error(val message: String) : SyncState()
}

enum class SortOrder {
    ALPHABETICAL_ASC,
    ALPHABETICAL_DESC,
    QUANTITY_ASC,
    QUANTITY_DESC,
    CATEGORY_ASC,
    DATE_ADDED
}

@HiltViewModel
class InventoryListViewModel @Inject constructor(
    private val repository: InventoryRepository
) : ViewModel() {

    private val TAG = "InventoryListViewModel"

    companion object {
        private const val ALL_CATEGORIES_INTERNAL = "All"
    }

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items = _items.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState = _syncState.asStateFlow()

    private val _searchTerm = MutableStateFlow("")
    val searchTerm = _searchTerm.asStateFlow()

    private val _selectedMainCategory = MutableStateFlow(ALL_CATEGORIES_INTERNAL)
    val selectedMainCategory = _selectedMainCategory.asStateFlow()

    private val _selectedSubCategory = MutableStateFlow(ALL_CATEGORIES_INTERNAL)
    val selectedSubCategory = _selectedSubCategory.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.ALPHABETICAL_ASC)
    val sortOrder = _sortOrder.asStateFlow()

    init {
        viewModelScope.launch {
            repository.syncState.collect { state ->
                _syncState.value = state
            }
        }
    }

    fun startCollectingItems(houseId: Long) {
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

    fun updateSearchTerm(term: String) {
        _searchTerm.value = term
    }

    fun updateSelectedMainCategory(category: String) {
        _selectedMainCategory.value = category

        // Reset sub-category if the currently selected sub-category doesn't belong to the new main category
        if (category != ALL_CATEGORIES_INTERNAL) {
            // Get all items with parsed categories
            val itemsWithParsedCategories = _items.value.mapNotNull { item ->
                val hierarchy = parseCategory(item.category)
                if (hierarchy != null) {
                    item to hierarchy
                } else {
                    null
                }
            }

            // Get valid sub-categories for the selected main category
            val validSubCategories = itemsWithParsedCategories
                .filter { it.second.mainCategory == category }
                .map { it.second.subCategory }
                .distinct()

            // If current sub-category is not valid for this main category, reset it
            if (_selectedSubCategory.value != ALL_CATEGORIES_INTERNAL &&
                !validSubCategories.contains(_selectedSubCategory.value)
            ) {
                _selectedSubCategory.value = ALL_CATEGORIES_INTERNAL
                Log.d(
                    TAG,
                    "Reset sub-category to 'All' because it doesn't belong to main category: $category"
                )
            }
        } else {
            // If "All" main categories is selected, reset sub-category
            _selectedSubCategory.value = ALL_CATEGORIES_INTERNAL
        }
    }

    fun updateSelectedSubCategory(subCategory: String) {
        _selectedSubCategory.value = subCategory
    }

    fun updateSortOrder(order: SortOrder) {
        _sortOrder.value = order
        Log.d(TAG, "Sort order updated to: $order")
    }

    fun clearAllFilters() {
        _searchTerm.value = ""
        _selectedMainCategory.value = ALL_CATEGORIES_INTERNAL
        _selectedSubCategory.value = ALL_CATEGORIES_INTERNAL
        _sortOrder.value = SortOrder.ALPHABETICAL_ASC
        Log.d(TAG, "All filters and sort order cleared")
    }

    fun getItemById(id: Long?): Item? {
        if (id == null) return null
        return items.value.find { it.id == id }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "InventoryListViewModel cleared")
    }
}