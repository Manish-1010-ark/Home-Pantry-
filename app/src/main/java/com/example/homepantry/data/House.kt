// file: data/House.kt
package com.example.homepantry.data

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(InternalSerializationApi::class)
@Serializable
data class House(
    val id: Long,
    val pin: String,
    val house_name: String
)