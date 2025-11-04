package com.example.homepantry.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Query("SELECT * FROM items WHERE house_id = :houseId ORDER BY name ASC")
    fun getItemsForHouse(houseId: Long): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :itemId")
    suspend fun getItemById(itemId: Long): ItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemEntity): Long

    @Update
    suspend fun updateItem(item: ItemEntity)

    @Delete
    suspend fun deleteItem(item: ItemEntity)

    @Query("DELETE FROM items WHERE id = :itemId")
    suspend fun deleteItemById(itemId: Long)

    @Query("DELETE FROM items WHERE house_id = :houseId")
    suspend fun deleteAllItemsForHouse(houseId: Long)

    @Query("SELECT COUNT(*) FROM items WHERE house_id = :houseId")
    suspend fun getItemCount(houseId: Long): Int

    // For sync operations - get all items for a house as a list (not Flow)
    @Query("SELECT * FROM items WHERE house_id = :houseId")
    suspend fun getAllItemsForHouseSync(houseId: Long): List<ItemEntity>
}