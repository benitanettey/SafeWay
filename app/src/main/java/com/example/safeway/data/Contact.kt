package com.example.safeway.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val phone: String,
    val relationship: String,
    val smsAlerts: Boolean = true,
    val includeGPS: Boolean = true
)

