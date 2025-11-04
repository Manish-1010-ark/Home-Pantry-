package com.example.homepantry.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.example.homepantry.data.Item

@Entity(
    tableName = "items",
    indices = [Index(value = ["house_id"])]
)
data class ItemEntity(
    @PrimaryKey
    val id: Long,
    val name: String,
    val nameHindi: String?,
    val category: String,
    val quantity: Double,
    val unit: String,
    val location: String?,
    val notes: String?,
    val house_id: Long,
    val lastSyncedAt: Long = System.currentTimeMillis()
)

// Extension functions for conversion
fun ItemEntity.toItem(): Item {
    return Item(
        id = id,
        name = name,
        nameHindi = nameHindi,
        category = category,
        quantity = quantity,
        unit = unit,
        location = location,
        notes = notes,
        house_id = house_id
    )
}

fun Item.toEntity(): ItemEntity {
    return ItemEntity(
        id = id ?: 0,
        name = name,
        nameHindi = nameHindi,
        category = category,
        quantity = quantity,
        unit = unit,
        location = location,
        notes = notes,
        house_id = house_id,
        lastSyncedAt = System.currentTimeMillis()
    )
}