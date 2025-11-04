// file: data/Item.kt
package com.example.homepantry.data

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(InternalSerializationApi::class)
@Serializable
data class Item(
    val id: Long? = 0,

    var name: String,
    var nameHindi: String? = null,
    var category: String,
    var quantity: Double,
    var unit: String,
    var location: String?,
    var notes: String?,

    val house_id: Long
)