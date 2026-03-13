package com.example.safeway.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "incidents")
data class Incident(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val type: String,
    val description: String,
    val severity: String,
    val location: String,
    val who: String,
    val hasVoiceNote: Boolean,
    val voiceNotePath: String?,
    val voiceDurationSec: Int,
    val photoPath: String? = null,
    val videoPath: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)


