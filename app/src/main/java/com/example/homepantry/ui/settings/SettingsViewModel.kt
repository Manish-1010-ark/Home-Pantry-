package com.example.homepantry.ui.settings

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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import javax.inject.Inject

// New data structures for conflict resolution
data class ImportConflict(
    val excelItem: Item,
    val databaseItem: Item
)

data class ImportReport(
    val newItemsAdded: List<String> = emptyList(),
    val itemsMerged: List<String> = emptyList(),
    val itemsReplaced: List<String> = emptyList(),
    val itemsSkipped: List<String> = emptyList(),
    val itemsFailed: List<String> = emptyList()
)

enum class ConflictAction { MERGE, REPLACE, SKIP, MERGE_ALL, REPLACE_ALL, SKIP_ALL }

// New ImportState with conflict resolution support
sealed class ImportState {
    object Idle : ImportState()
    data class Loading(val progress: String = "Starting import...") : ImportState()
    data class Conflict(
        val conflicts: List<ImportConflict>,
        val newItems: List<Item>,
        val totalConflicts: Int,
        val currentConflictIndex: Int = 0
    ) : ImportState()

    data class SuccessReport(val report: ImportReport) : ImportState()
    data class Error(val message: String) : ImportState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val TAG = "SettingsViewModel"

    val appLanguage = prefsRepository.appLanguage
    val appTheme = prefsRepository.appTheme

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState = _importState.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>()
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    // Cache of items for import operations
    private val _itemsCache = MutableStateFlow<List<Item>>(emptyList())

    // New state variables for conflict resolution
    private val _newItems = MutableStateFlow<List<Item>>(emptyList())
    private val _conflictItems = MutableStateFlow<List<ImportConflict>>(emptyList())
    private val _totalConflicts = MutableStateFlow(0) // Store original total
    private val _report = MutableStateFlow(ImportReport())

    fun updateItemsCache(items: List<Item>) {
        _itemsCache.value = items
    }

    fun setAppTheme(theme: String) {
        viewModelScope.launch {
            prefsRepository.saveAppTheme(theme)
        }
    }

    fun setAppLanguage(language: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                prefsRepository.saveAppLanguage(language)
                kotlinx.coroutines.delay(200)
                Log.d(TAG, "Language preference saved: $language")
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting app language", e)
            }
        }
    }

    private fun validateAndNormalizeCategory(categoryRaw: String): String? {
        if (categoryRaw.isBlank()) {
            return "Miscellaneous:Uncategorized"
        }

        return if (categoryRaw.contains(":")) {
            val parts = categoryRaw.split(":", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                "${parts[0].trim()}:${parts[1].trim()}"
            } else {
                Log.w(TAG, "Invalid category format: '$categoryRaw'")
                null
            }
        } else {
            "Miscellaneous:${categoryRaw.trim()}"
        }
    }

    /**
     * IMPROVED: Now with progress tracking and better error handling
     */
    fun importItemsFromExcel(uri: Uri, context: Context, houseId: Long) {
        Log.d(TAG, "=== STARTING CONFLICT-SAFE EXCEL IMPORT ===")
        _importState.value = ImportState.Loading("Preparing import...")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Step 1: Fetch existing items from cache
                val existingItems = _itemsCache.value
                Log.d(TAG, "Step 1: Fetched ${existingItems.size} existing items from cache")
                _importState.value = ImportState.Loading("Reading Excel file...")

                // Step 2: Parse Excel file
                Log.d(TAG, "Step 2: Parsing Excel file...")
                val aggregatedItemsMap = mutableMapOf<String, AggregatedItem>()
                var totalRowsParsed = 0
                var skippedRowCount = 0
                val skippedRows = mutableListOf<String>()

                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val workbook = XSSFWorkbook(stream)
                    val sheet = workbook.getSheetAt(0)
                    val totalRows = sheet.lastRowNum

                    for (rowIndex in 1..totalRows) {
                        val row = sheet.getRow(rowIndex) ?: continue
                        totalRowsParsed++

                        // Update progress every 10 rows
                        if (totalRowsParsed % 10 == 0) {
                            _importState.value =
                                ImportState.Loading("Processing row $totalRowsParsed of $totalRows...")
                        }

                        try {
                            val name = row.getCell(0)?.stringCellValue?.trim() ?: ""
                            if (name.isBlank()) {
                                skippedRowCount++
                                skippedRows.add("Row ${rowIndex + 1}: Empty name")
                                continue
                            }

                            val categoryRaw = row.getCell(1)?.stringCellValue?.trim() ?: ""
                            val category = validateAndNormalizeCategory(categoryRaw)
                            if (category == null) {
                                skippedRowCount++
                                skippedRows.add("Row ${rowIndex + 1}: Invalid category")
                                continue
                            }

                            val quantity = row.getCell(2)?.let {
                                when (it.cellType) {
                                    CellType.NUMERIC -> it.numericCellValue
                                    CellType.STRING -> it.stringCellValue.toDoubleOrNull()
                                    else -> null
                                }
                            }

                            if (quantity == null || quantity < 0) {
                                skippedRowCount++
                                skippedRows.add("Row ${rowIndex + 1}: Invalid quantity")
                                continue
                            }

                            // Warn about unusually large quantities
                            if (quantity > 10000) {
                                Log.w(
                                    TAG,
                                    "Row ${rowIndex + 1}: Unusually large quantity ($quantity) for '$name'"
                                )
                            }

                            val unit = row.getCell(3)?.stringCellValue?.trim() ?: "piece"
                            val location =
                                row.getCell(4)?.stringCellValue?.trim()?.takeIf { it.isNotBlank() }
                            val notes =
                                row.getCell(5)?.stringCellValue?.trim()?.takeIf { it.isNotBlank() }
                            val nameHindi =
                                row.getCell(6)?.stringCellValue?.trim()?.takeIf { it.isNotBlank() }

                            val itemKey =
                                "${name.lowercase()}|${unit.lowercase()}|${location?.lowercase() ?: ""}"

                            if (aggregatedItemsMap.containsKey(itemKey)) {
                                val existing = aggregatedItemsMap[itemKey]!!
                                aggregatedItemsMap[itemKey] = existing.copy(
                                    quantity = existing.quantity + quantity,
                                    category = category,
                                    notes = notes ?: existing.notes,
                                    nameHindi = nameHindi ?: existing.nameHindi
                                )
                                Log.d(
                                    TAG,
                                    "Aggregated duplicate: '$name' (${existing.quantity} + $quantity)"
                                )
                            } else {
                                aggregatedItemsMap[itemKey] = AggregatedItem(
                                    name = name,
                                    category = category,
                                    quantity = quantity,
                                    unit = unit,
                                    location = location,
                                    notes = notes,
                                    nameHindi = nameHindi
                                )
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Row ${rowIndex + 1}: Skipped - ${e.message}")
                            skippedRowCount++
                            skippedRows.add("Row ${rowIndex + 1}: ${e.message}")
                        }
                    }
                }

                Log.i(
                    TAG,
                    "Step 2 Complete: Parsed $totalRowsParsed rows → ${aggregatedItemsMap.size} items (skipped $skippedRowCount)"
                )
                if (skippedRows.isNotEmpty()) {
                    Log.w(
                        TAG,
                        "Skipped rows: ${
                            skippedRows.take(5).joinToString(", ")
                        }${if (skippedRows.size > 5) "..." else ""}"
                    )
                }

                _importState.value = ImportState.Loading("Checking for conflicts...")

                // Step 3: Separate into conflicts and new items
                Log.d(TAG, "Step 3: Identifying conflicts...")
                val tempNewItems = mutableListOf<Item>()
                val tempConflicts = mutableListOf<ImportConflict>()

                aggregatedItemsMap.forEach { (_, aggregatedItem) ->
                    // Only check name for conflicts
                    val existingItem = existingItems.find {
                        it.name.trim().lowercase() == aggregatedItem.name.lowercase()
                    }

                    if (existingItem != null) {
                        // CONFLICT DETECTED
                        val excelItem = Item(
                            name = aggregatedItem.name,
                            category = aggregatedItem.category,
                            quantity = aggregatedItem.quantity,
                            unit = aggregatedItem.unit,
                            location = aggregatedItem.location,
                            notes = aggregatedItem.notes,
                            nameHindi = aggregatedItem.nameHindi,
                            house_id = houseId
                        )
                        tempConflicts.add(ImportConflict(excelItem, existingItem))
                        Log.d(TAG, "CONFLICT: '${aggregatedItem.name}' exists in database")
                    } else {
                        // NEW ITEM
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
                        tempNewItems.add(newItem)
                        Log.d(TAG, "NEW: '${aggregatedItem.name}'")
                    }
                }

                Log.i(TAG, "Found ${tempConflicts.size} conflicts, ${tempNewItems.size} new items")

                // Step 4: Decide next state
                if (tempConflicts.isEmpty()) {
                    // No conflicts - process immediately
                    Log.d(TAG, "No conflicts detected, processing items directly")
                    processNewItems(tempNewItems)
                } else {
                    // CONFLICTS EXIST - PAUSE FOR USER RESOLUTION
                    Log.d(TAG, "Conflicts detected, entering conflict resolution mode")
                    _newItems.value = tempNewItems
                    _conflictItems.value = tempConflicts
                    _totalConflicts.value = tempConflicts.size // Store original total
                    _report.value = ImportReport() // Reset report
                    _importState.value = ImportState.Conflict(
                        conflicts = tempConflicts,
                        newItems = tempNewItems,
                        totalConflicts = tempConflicts.size,
                        currentConflictIndex = 0
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Fatal error during Excel import", e)
                _importState.value = ImportState.Error("Failed to read file: ${e.message}")
                showSnackbar("Import failed: ${e.message}")
            }
        }
    }

    /**
     * IMPROVED: Resolve conflicts with bulk actions support
     */
    fun resolveConflict(action: ConflictAction) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (action) {
                    ConflictAction.MERGE_ALL -> {
                        // Apply MERGE to all remaining conflicts
                        _conflictItems.value.forEach { conflict ->
                            applyMerge(conflict.excelItem, conflict.databaseItem)
                        }
                        _conflictItems.value = emptyList()
                        processNewItems(_newItems.value)
                    }

                    ConflictAction.REPLACE_ALL -> {
                        // Apply REPLACE to all remaining conflicts
                        _conflictItems.value.forEach { conflict ->
                            applyReplace(conflict.excelItem, conflict.databaseItem)
                        }
                        _conflictItems.value = emptyList()
                        processNewItems(_newItems.value)
                    }

                    ConflictAction.SKIP_ALL -> {
                        // Apply SKIP to all remaining conflicts
                        _conflictItems.value.forEach { conflict ->
                            applySkip(conflict.databaseItem)
                        }
                        _conflictItems.value = emptyList()
                        processNewItems(_newItems.value)
                    }

                    else -> {
                        // Handle single conflict
                        val currentConflict = _conflictItems.value.firstOrNull() ?: return@launch
                        val (excelItem, dbItem) = currentConflict

                        when (action) {
                            ConflictAction.MERGE -> applyMerge(excelItem, dbItem)
                            ConflictAction.REPLACE -> applyReplace(excelItem, dbItem)
                            ConflictAction.SKIP -> applySkip(dbItem)
                            else -> {}
                        }

                        // Remove the resolved conflict
                        val remainingConflicts = _conflictItems.value.drop(1)
                        _conflictItems.value = remainingConflicts

                        // Check if all conflicts are resolved
                        if (remainingConflicts.isEmpty()) {
                            Log.d(TAG, "All conflicts resolved, processing new items")
                            processNewItems(_newItems.value)
                        } else {
                            // Show next conflict - FIXED INDEX CALCULATION
                            val currentIndex = _totalConflicts.value - remainingConflicts.size
                            _importState.value = ImportState.Conflict(
                                conflicts = remainingConflicts,
                                newItems = _newItems.value,
                                totalConflicts = _totalConflicts.value,
                                currentConflictIndex = currentIndex
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving conflict", e)
                _importState.value = ImportState.Error("Failed to resolve conflict: ${e.message}")
            }
        }
    }

    private suspend fun applyMerge(excelItem: Item, dbItem: Item) {
        val mergedItem = dbItem.copy(
            quantity = dbItem.quantity + excelItem.quantity,
            category = excelItem.category,
            unit = excelItem.unit, // Update unit to Excel version
            notes = excelItem.notes ?: dbItem.notes,
            nameHindi = excelItem.nameHindi ?: dbItem.nameHindi
        )
        repository.updateItem(mergedItem)
        _report.value = _report.value.copy(
            itemsMerged = _report.value.itemsMerged + dbItem.name
        )
        Log.d(
            TAG,
            "MERGED: '${dbItem.name}' - Old qty: ${dbItem.quantity}, Added: ${excelItem.quantity}, New: ${mergedItem.quantity}"
        )
    }

    private suspend fun applyReplace(excelItem: Item, dbItem: Item) {
        val replacedItem = excelItem.copy(id = dbItem.id, house_id = dbItem.house_id)
        repository.updateItem(replacedItem)
        _report.value = _report.value.copy(
            itemsReplaced = _report.value.itemsReplaced + dbItem.name
        )
        Log.d(TAG, "REPLACED: '${dbItem.name}' with Excel data")
    }

    private fun applySkip(dbItem: Item) {
        _report.value = _report.value.copy(
            itemsSkipped = _report.value.itemsSkipped + dbItem.name
        )
        Log.d(TAG, "SKIPPED: '${dbItem.name}' - keeping database version")
    }

    /**
     * Process all new items (no conflicts)
     */
    private suspend fun processNewItems(newItems: List<Item>) {
        try {
            if (newItems.isNotEmpty()) {
                repository.addItem(newItems.toMutableList())
                _report.value = _report.value.copy(
                    newItemsAdded = newItems.map { it.name }
                )
                Log.i(TAG, "✓ Successfully added ${newItems.size} new items")
            }

            // Set final success state
            _importState.value = ImportState.SuccessReport(_report.value)
            Log.i(TAG, "=== IMPORT COMPLETE ===")

            // Show summary snackbar
            val summary = buildSummaryMessage(_report.value)
            showSnackbar(summary)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing new items", e)
            _importState.value = ImportState.Error("Failed to save items: ${e.message}")
        }
    }

    private fun buildSummaryMessage(report: ImportReport): String {
        val parts = mutableListOf<String>()
        if (report.newItemsAdded.isNotEmpty()) parts.add("${report.newItemsAdded.size} added")
        if (report.itemsMerged.isNotEmpty()) parts.add("${report.itemsMerged.size} merged")
        if (report.itemsReplaced.isNotEmpty()) parts.add("${report.itemsReplaced.size} replaced")
        if (report.itemsSkipped.isNotEmpty()) parts.add("${report.itemsSkipped.size} skipped")
        if (report.itemsFailed.isNotEmpty()) parts.add("${report.itemsFailed.size} failed")
        return if (parts.isEmpty()) "No changes made" else parts.joinToString(", ")
    }

    /**
     * Cancel the import process
     */
    suspend fun cancelImport() {
        Log.d(TAG, "Import cancelled by user")
        _importState.value = ImportState.Idle
        _newItems.value = emptyList()
        _conflictItems.value = emptyList()
        _totalConflicts.value = 0
        _report.value = ImportReport()
        showSnackbar("Import cancelled")
    }

    fun clearImportState() {
        _importState.value = ImportState.Idle
        _newItems.value = emptyList()
        _conflictItems.value = emptyList()
        _totalConflicts.value = 0
        _report.value = ImportReport()
    }

    private suspend fun showSnackbar(message: String) {
        _snackbarMessages.emit(message)
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "SettingsViewModel cleared")
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