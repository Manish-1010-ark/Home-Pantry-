package com.example.homepantry.data

import android.util.Log
import com.example.homepantry.ui.inventory.SyncState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class InventoryRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val TAG = "InventoryRepository"
    private var listenerRegistration: ListenerRegistration? = null
    private val repositoryScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    /**
     * Fetches a house from Firestore by PIN
     * Simplified and more reliable version
     */
    suspend fun getHouseForPin(pin: String): House? {
        return try {
            val trimmedPin = pin.trim()
            Log.d(TAG, "=== START: Fetching house with PIN ===")
            Log.d(TAG, "PIN: ${trimmedPin.take(2)}*** (length: ${trimmedPin.length})")

            // Query Firestore for house with matching PIN
            val querySnapshot = firestore.collection("houses")
                .whereEqualTo("pin", trimmedPin)
                .get()
                .await()

            Log.d(TAG, "Query completed. Documents found: ${querySnapshot.size()}")

            if (querySnapshot.isEmpty) {
                Log.w(TAG, "No documents found with PIN: ${trimmedPin.take(2)}***")

                // Debug: List all houses to help diagnose
                try {
                    Log.d(TAG, "Fetching ALL houses for debugging...")
                    val allHouses = firestore.collection("houses")
                        .get()
                        .await()

                    Log.d(TAG, "Total houses in collection: ${allHouses.size()}")

                    allHouses.documents.forEachIndexed { index, doc ->
                        val docPin = doc.getString("pin") ?: "null"
                        val docId = doc.getLong("id") ?: 0L
                        val docName = doc.getString("house_name") ?: "null"

                        Log.d(TAG, "House #$index:")
                        Log.d(TAG, "  - Firestore Doc ID: ${doc.id}")
                        Log.d(TAG, "  - id field: $docId")
                        Log.d(
                            TAG,
                            "  - pin field: '${docPin.take(2)}***' (length: ${docPin.length})"
                        )
                        Log.d(TAG, "  - house_name field: '$docName'")
                        Log.d(TAG, "  - PIN match: ${docPin == trimmedPin}")
                    }

                    // Try manual search as fallback
                    val manualMatch = allHouses.documents.firstOrNull { doc ->
                        val docPin = doc.getString("pin")
                        docPin == trimmedPin
                    }

                    if (manualMatch != null) {
                        Log.d(TAG, "✓ Found house via manual search!")
                        val house = House(
                            id = manualMatch.getLong("id") ?: 0L,
                            pin = manualMatch.getString("pin") ?: "",
                            house_name = manualMatch.getString("house_name") ?: ""
                        )
                        Log.d(TAG, "Manual search result: $house")
                        Log.d(TAG, "=== END: House found via fallback ===")
                        return house
                    }

                } catch (debugException: Exception) {
                    Log.e(TAG, "Debug listing failed: ${debugException.message}", debugException)
                }

                Log.e(TAG, "=== END: No house found ===")
                return null
            }

            // Document found via query
            val document = querySnapshot.documents[0]
            Log.d(TAG, "✓ Document found via query")
            Log.d(TAG, "Firestore Doc ID: ${document.id}")
            Log.d(TAG, "Document exists: ${document.exists()}")
            Log.d(TAG, "Document data: ${document.data}")

            // Try to deserialize using Firestore toObject
            try {
                val house = document.toObject(House::class.java)

                if (house != null) {
                    Log.d(TAG, "✓ Successfully deserialized via toObject()")
                    Log.d(TAG, "House: $house")
                    Log.d(TAG, "House valid: ${house.isValid()}")

                    if (house.isValid()) {
                        Log.d(TAG, "=== END: House found and valid ===")
                        return house
                    } else {
                        Log.e(TAG, "House data incomplete: $house")
                    }
                } else {
                    Log.e(TAG, "toObject() returned null")
                }
            } catch (deserializeException: Exception) {
                Log.e(
                    TAG,
                    "Deserialization error: ${deserializeException.message}",
                    deserializeException
                )
            }

            // Fallback: Manual construction from document fields
            Log.d(TAG, "Attempting manual construction from document fields...")
            val manualHouse = House(
                id = document.getLong("id") ?: 0L,
                pin = document.getString("pin") ?: "",
                house_name = document.getString("house_name") ?: ""
            )

            Log.d(TAG, "Manually constructed house: $manualHouse")
            Log.d(TAG, "Manual house valid: ${manualHouse.isValid()}")

            if (manualHouse.isValid()) {
                Log.d(TAG, "=== END: House found via manual construction ===")
                return manualHouse
            } else {
                Log.e(TAG, "Manual construction resulted in invalid house")
                Log.d(TAG, "=== END: Invalid house data ===")
                return null
            }

        } catch (e: Exception) {
            Log.e(TAG, "=== ERROR in getHouseForPin ===")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Stack trace:", e)
            Log.d(TAG, "=== END: Exception occurred ===")
            throw e // Re-throw to be caught by AuthViewModel
        }
    }

    /**
     * Fetches the house name from Firestore given a house ID
     */
    suspend fun getHouseName(houseId: Long): String? {
        return try {
            Log.d(TAG, "Fetching house name for houseId: $houseId")

            val querySnapshot = firestore.collection("houses")
                .whereEqualTo("id", houseId)
                .limit(1)
                .get()
                .await()

            if (querySnapshot.isEmpty) {
                Log.d(TAG, "No house found with ID: $houseId")
                null
            } else {
                val house = querySnapshot.documents[0].toObject(House::class.java)
                Log.d(TAG, "Fetched house name: ${house?.house_name}")
                house?.house_name
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching house name", e)
            null
        }
    }

    /**
     * Returns a Flow of items from Firestore with real-time updates.
     * Uses Firestore's snapshot listener for real-time synchronization.
     */
    fun getItems(houseId: Long): Flow<List<Item>> = callbackFlow {
        Log.d(TAG, "Setting up real-time listener for houseId: $houseId")
        _syncState.value = SyncState.Syncing

        // Set up Firestore snapshot listener for real-time updates
        listenerRegistration = firestore.collection("items")
            .whereEqualTo("house_id", houseId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to items", error)
                    _syncState.value = SyncState.Error("Error: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    try {
                        val items = snapshot.documents.mapNotNull { doc ->
                            try {
                                doc.toObject(Item::class.java)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing item document: ${doc.id}", e)
                                null
                            }
                        }

                        Log.d(TAG, "Received ${items.size} items from Firestore")
                        _syncState.value = SyncState.Success
                        trySend(items)

                        // Reset to idle after a delay
                        repositoryScope.launch {
                            delay(3000)
                            _syncState.value = SyncState.Idle
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing snapshot", e)
                        _syncState.value = SyncState.Error("Processing error: ${e.message}")
                        trySend(emptyList())
                    }
                } else {
                    Log.d(TAG, "Snapshot is null")
                    trySend(emptyList())
                }
            }

        // Clean up listener when flow is closed
        awaitClose {
            Log.d(TAG, "Closing items flow, removing listener")
            listenerRegistration?.remove()
            listenerRegistration = null
        }
    }

    /**
     * Adds multiple items to Firestore using a batch write.
     * Generates unique Long IDs for each item.
     */
    suspend fun addItem(items: MutableList<Item>) {
        try {
            Log.d(TAG, "Adding ${items.size} items to Firestore")
            _syncState.value = SyncState.Syncing

            val batch = firestore.batch()
            val baseTimestamp = System.currentTimeMillis()

            items.forEachIndexed { index, item ->
                // Generate unique Long ID
                val newId = if (item.id == null || item.id == 0L) {
                    baseTimestamp + index + Random.nextLong(1000)
                } else {
                    item.id!!
                }

                // Update the item with the generated ID
                item.id = newId

                // Create a new document reference with auto-generated Firestore ID
                val docRef = firestore.collection("items").document()

                // Add to batch using the item object directly
                batch.set(docRef, item)

                Log.d(TAG, "Prepared item ${item.name} with ID: $newId")
            }

            // Commit the batch
            batch.commit().await()
            Log.d(TAG, "Successfully added ${items.size} items to Firestore")
            _syncState.value = SyncState.Success

        } catch (e: Exception) {
            Log.e(TAG, "Error adding items to Firestore", e)
            _syncState.value = SyncState.Error("Failed to add items: ${e.message}")
            throw e
        } finally {
            // Reset to idle after a delay
            repositoryScope.launch {
                delay(3000)
                _syncState.value = SyncState.Idle
            }
        }
    }

    /**
     * Updates an item in Firestore.
     * Queries for the document where id == item.id, then updates it.
     */
    suspend fun updateItem(item: Item) {
        try {
            Log.d(TAG, "Updating item with id: ${item.id}")
            _syncState.value = SyncState.Syncing

            // Find the document with this ID
            val querySnapshot = firestore.collection("items")
                .whereEqualTo("id", item.id)
                .limit(1)
                .get()
                .await()

            if (querySnapshot.isEmpty) {
                Log.e(TAG, "No document found with id: ${item.id}")
                throw Exception("Item not found")
            }

            // Get the document reference
            val docRef = querySnapshot.documents[0].reference

            // Update the document using the item object directly
            docRef.set(item).await()
            Log.d(TAG, "Successfully updated item with id: ${item.id}")
            _syncState.value = SyncState.Success

        } catch (e: Exception) {
            Log.e(TAG, "Error updating item", e)
            _syncState.value = SyncState.Error("Failed to update: ${e.message}")
            throw e
        } finally {
            // Reset to idle after a delay
            repositoryScope.launch {
                delay(3000)
                _syncState.value = SyncState.Idle
            }
        }
    }

    /**
     * Deletes an item from Firestore.
     * Queries for the document where id == itemId, then deletes it.
     */
    suspend fun deleteItem(itemId: Long) {
        try {
            Log.d(TAG, "Deleting item with id: $itemId")
            _syncState.value = SyncState.Syncing

            // Find the document with this ID
            val querySnapshot = firestore.collection("items")
                .whereEqualTo("id", itemId)
                .limit(1)
                .get()
                .await()

            if (querySnapshot.isEmpty) {
                Log.e(TAG, "No document found with id: $itemId")
                throw Exception("Item not found")
            }

            // Get the document reference and delete it
            val docRef = querySnapshot.documents[0].reference
            docRef.delete().await()

            Log.d(TAG, "Successfully deleted item with id: $itemId")
            _syncState.value = SyncState.Success

        } catch (e: Exception) {
            Log.e(TAG, "Error deleting item", e)
            _syncState.value = SyncState.Error("Failed to delete: ${e.message}")
            throw e
        } finally {
            // Reset to idle after a delay
            repositoryScope.launch {
                delay(3000)
                _syncState.value = SyncState.Idle
            }
        }
    }

    /**
     * Manually triggers a refresh.
     * With Firestore's real-time listener, this is essentially a no-op,
     * but we keep it for API compatibility.
     */
    suspend fun refreshItems(houseId: Long) {
        Log.d(TAG, "Manual refresh requested (using real-time listener)")
        _syncState.value = SyncState.Syncing

        // The real-time listener handles updates automatically
        delay(1000)
        _syncState.value = SyncState.Success

        delay(2000)
        _syncState.value = SyncState.Idle
    }

    /**
     * Cleanup method to remove listeners
     */
    suspend fun cleanup() {
        try {
            Log.d(TAG, "Cleaning up Firestore listeners")
            listenerRegistration?.remove()
            listenerRegistration = null
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}