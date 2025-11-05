package com.example.homepantry.data

import android.util.Log
import com.example.homepantry.data.local.ItemDao
import com.example.homepantry.data.local.toEntity
import com.example.homepantry.data.local.toItem
import com.example.homepantry.ui.inventory.SyncState
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepository @Inject constructor(
    private val postgrest: Postgrest,
    private val realtime: Realtime,
    private val itemDao: ItemDao
) {
    private val TAG = "InventoryRepository"
    private var currentChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null
    private var currentScope: CoroutineScope? = null
    private var isSyncing = false

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    suspend fun getHouseForPin(pin: String): House? {
        return try {
            val house = postgrest.from("houses").select {
                filter {
                    eq("pin", pin)
                }
            }.decodeSingleOrNull<House>()
            house
        } catch (e: Exception) {
            Log.e(TAG, "An exception occurred during getHouseForPin", e)
            null
        }
    }

    /**
     * Fetches the house name from Supabase given a house ID
     */
    suspend fun getHouseName(houseId: Long): String? {
        return try {
            Log.d(TAG, "Fetching house name for houseId: $houseId")

            val response = postgrest.from("houses")
                .select {
                    filter {
                        eq("id", houseId.toString())
                    }
                }

            val house = response.decodeSingleOrNull<House>()
            val houseName = house?.house_name

            Log.d(TAG, "Fetched house name: $houseName")
            houseName
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching house name", e)
            null
        }
    }

    /**
     * Returns a Flow of items from the local Room database.
     * This is the single source of truth for the UI.
     */
    fun getItems(houseId: Long): Flow<List<Item>> {
        Log.d(TAG, "Getting items flow from Room for houseId: $houseId")

        // Start background sync and realtime subscription
        startBackgroundSync(houseId)

        // Return the Room database flow, mapping entities to domain models
        return itemDao.getItemsForHouse(houseId)
            .map { entities ->
                entities.map { it.toItem() }
            }
            .catch { e ->
                Log.e(TAG, "Error in Room flow", e)
                emit(emptyList())
            }
    }

    /**
     * Performs initial sync and sets up realtime updates.
     * This runs in the background and updates the Room database.
     */
    private fun startBackgroundSync(houseId: Long) {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        currentScope = scope

        scope.launch {
            try {
                // Perform initial sync from Supabase to Room
                syncFromRemote(houseId)

                // Set up realtime subscription
                setupRealtimeSubscription(houseId)
            } catch (e: Exception) {
                Log.e(TAG, "Error in background sync setup", e)
            }
        }
    }

    /**
     * Syncs data from Supabase to the local Room database.
     * This is called on app start and when realtime changes are detected.
     */
    private suspend fun syncFromRemote(houseId: Long) {
        if (isSyncing) {
            Log.d(TAG, "Sync already in progress, skipping")
            return
        }

        isSyncing = true
        _syncState.value = SyncState.Syncing

        try {
            Log.d(TAG, "Starting sync from Supabase for houseId: $houseId")

            val remoteItems = withContext(Dispatchers.IO) {
                postgrest.from("items")
                    .select {
                        filter {
                            eq("house_id", houseId.toString())
                        }
                    }
                    .decodeList<Item>()
            }

            Log.d(TAG, "Fetched ${remoteItems.size} items from Supabase")

            val localItems = itemDao.getAllItemsForHouseSync(houseId)
            val remoteEntities = remoteItems.map { it.toEntity() }
            val remoteItemIds = remoteEntities.map { it.id }.toSet()

            val itemsToDelete = localItems.filter { it.id !in remoteItemIds }

            if (remoteEntities.isNotEmpty()) {
                itemDao.insertItems(remoteEntities)
                Log.d(TAG, "Inserted/Updated ${remoteEntities.size} items in Room")
            }

            itemsToDelete.forEach { entity ->
                itemDao.deleteItem(entity)
                Log.d(TAG, "Deleted item ${entity.id} from Room (not found in remote)")
            }

            Log.d(TAG, "Sync completed successfully")
            _syncState.value = SyncState.Success

        } catch (e: Exception) {
            Log.e(TAG, "Error syncing from remote", e)
            _syncState.value = SyncState.Error(
                if (e.message?.contains("timeout", ignoreCase = true) == true) {
                    "Working offline"
                } else {
                    "Sync failed: ${e.message}"
                }
            )
        } finally {
            isSyncing = false
            // Reset to idle after a delay
            kotlinx.coroutines.delay(3000)
            _syncState.value = SyncState.Idle
        }
    }

    /**
     * Sets up a realtime subscription to listen for changes in Supabase.
     * When changes are detected, it triggers a sync.
     */
    private suspend fun setupRealtimeSubscription(houseId: Long) {
        // Cleanup any existing channel first
        currentChannel?.let { oldChannel ->
            try {
                oldChannel.unsubscribe()
                realtime.removeChannel(oldChannel)
                Log.d(TAG, "Cleaned up old channel")
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning up old channel", e)
            }
        }

        val channelId = "items-channel-${houseId}"
        val channel = realtime.channel(channelId)
        currentChannel = channel

        try {
            Log.d(TAG, "Subscribing to realtime channel: $channelId")
            channel.subscribe()
            Log.d(TAG, "Successfully subscribed to channel")

            // Listen for realtime changes
            currentScope?.launch {
                try {
                    channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "items"
                        filter = "house_id=eq.$houseId"
                    }.collect { action ->
                        Log.d(TAG, "Realtime change detected: ${action.javaClass.simpleName}")
                        when (action) {
                            is PostgresAction.Insert -> {
                                Log.d(TAG, "Item inserted remotely")
                                syncFromRemote(houseId)
                            }

                            is PostgresAction.Update -> {
                                Log.d(TAG, "Item updated remotely")
                                syncFromRemote(houseId)
                            }

                            is PostgresAction.Delete -> {
                                Log.d(TAG, "Item deleted remotely")
                                syncFromRemote(houseId)
                            }

                            else -> Log.d(TAG, "Other action: $action")
                        }
                    }
                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        Log.e(TAG, "Error in realtime flow", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing to channel", e)
        }
    }

    suspend fun cleanup() {
        try {
            Log.d(TAG, "Manual cleanup initiated")
            currentChannel?.let { channel ->
                try {
                    channel.unsubscribe()
                    realtime.removeChannel(channel)
                    Log.d(TAG, "Manual channel cleanup successful")
                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        Log.e(TAG, "Error during manual cleanup", e)
                    }
                }
            }
            currentChannel = null
            currentScope = null
        } catch (e: Exception) {
            Log.e(TAG, "Error in cleanup", e)
        }
    }

    /**
     * CRITICAL FIX: Adds items to Supabase first (to get server-generated IDs), then to Room.
     * This ensures proper ID synchronization between local and remote databases.
     */
    suspend fun addItem(items: MutableList<Item>) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Adding ${items.size} items")

                // Step 1: Insert into Supabase FIRST to get server-generated IDs
                // The insert() method automatically returns the inserted items with IDs
                val insertedItems = postgrest.from("items")
                    .insert(items) {
                        select()  // This tells Supabase to return the inserted data
                    }
                    .decodeList<Item>()

                Log.d(
                    TAG,
                    "Items added to Supabase successfully with IDs: ${insertedItems.map { it.id }}"
                )

                // Step 2: Insert into Room with the server-generated IDs
                val entities = insertedItems.map { it.toEntity() }
                itemDao.insertItems(entities)
                Log.d(TAG, "Items added to Room successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Error adding items", e)
                throw e
            }
        }
    }

    /**
     * Updates an item in both Room (local) and Supabase (remote).
     * Room is updated first for instant UI feedback.
     */
    suspend fun updateItem(item: Item) {
        try {
            Log.d(TAG, "Updating item with id: ${item.id}")

            // Step 1: Update in Room first for instant UI feedback
            val entity = item.toEntity()
            itemDao.updateItem(entity)
            Log.d(TAG, "Item updated in Room")

            // Step 2: Update in Supabase
            withContext(Dispatchers.IO) {
                postgrest.from("items")
                    .update({
                        set("name", item.name)
                        set("nameHindi", item.nameHindi)
                        set("category", item.category)
                        set("quantity", item.quantity)
                        set("unit", item.unit)
                        set("location", item.location)
                        set("notes", item.notes)
                    }) {
                        filter {
                            eq("id", item.id.toString())
                        }
                    }
            }
            Log.d(TAG, "Item updated in Supabase successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating item", e)
            throw e
        }
    }

    /**
     * Deletes an item from both Room (local) and Supabase (remote).
     * Room is updated first for instant UI feedback.
     */
    suspend fun deleteItem(itemId: Long) {
        try {
            Log.d(TAG, "Deleting item with id: $itemId")

            // Step 1: Delete from Room first for instant UI feedback
            itemDao.deleteItemById(itemId)
            Log.d(TAG, "Item deleted from Room")

            // Step 2: Delete from Supabase
            withContext(Dispatchers.IO) {
                postgrest.from("items")
                    .delete {
                        filter {
                            eq("id", itemId.toString())
                        }
                    }
            }
            Log.d(TAG, "Item deleted from Supabase successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error deleting item", e)
            throw e
        }
    }

    /**
     * Manually triggers a sync from Supabase to Room.
     * Can be called by the UI on pull-to-refresh.
     */
    suspend fun refreshItems(houseId: Long) {
        Log.d(TAG, "Manual refresh triggered for houseId: $houseId")
        syncFromRemote(houseId)
    }
}