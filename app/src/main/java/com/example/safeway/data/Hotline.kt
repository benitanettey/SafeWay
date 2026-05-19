package com.example.safeway.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hotlines")
data class Hotline(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String
)
