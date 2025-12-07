// file: data/Item.kt
package com.example.homepantry.data

import com.google.firebase.firestore.PropertyName

data class Item(
    @get:PropertyName("id")
    @set:PropertyName("id")
    var id: Long? = 0,

    @get:PropertyName("name")
    @set:PropertyName("name")
    var name: String = "",

    @get:PropertyName("nameHindi")
    @set:PropertyName("nameHindi")
    var nameHindi: String? = null,

    @get:PropertyName("category")
    @set:PropertyName("category")
    var category: String = "",

    @get:PropertyName("quantity")
    @set:PropertyName("quantity")
    var quantity: Double = 0.0,

    @get:PropertyName("unit")
    @set:PropertyName("unit")
    var unit: String = "",

    @get:PropertyName("location")
    @set:PropertyName("location")
    var location: String? = null,

    @get:PropertyName("notes")
    @set:PropertyName("notes")
    var notes: String? = null,

    @get:PropertyName("house_id")
    @set:PropertyName("house_id")
    var house_id: Long = 0,

    @get:PropertyName("created_at")
    @set:PropertyName("created_at")
    var created_at: String = ""
) {
    // No-argument constructor required by Firestore
    constructor() : this(
        id = 0,
        name = "",
        nameHindi = null,
        category = "",
        quantity = 0.0,
        unit = "",
        location = null,
        notes = null,
        house_id = 0,
        created_at = ""
    )
}