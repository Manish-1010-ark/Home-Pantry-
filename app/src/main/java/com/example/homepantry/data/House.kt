package com.example.homepantry.data

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

/**
 * House data class for Firestore synchronization
 *
 * Firestore Requirements:
 * - Must have a no-argument constructor
 * - Properties must have default values
 * - Field names must match Firestore using @PropertyName
 */
@IgnoreExtraProperties
data class House(
    @get:PropertyName("id")
    @set:PropertyName("id")
    var id: Long = 0L,

    @get:PropertyName("pin")
    @set:PropertyName("pin")
    var pin: String = "",

    @get:PropertyName("house_name")
    @set:PropertyName("house_name")
    var house_name: String = ""
) {
    /**
     * Explicit no-argument constructor required by Firestore
     * This allows Firestore to instantiate the object and populate fields
     */
    constructor() : this(
        id = 0L,
        pin = "",
        house_name = ""
    )

    /**
     * Validation helper to ensure house data is complete
     */
    fun isValid(): Boolean {
        return id > 0L && pin.isNotBlank() && house_name.isNotBlank()
    }

    override fun toString(): String {
        return "House(id=$id, pin='${pin.take(2)}***', house_name='$house_name')"
    }
}