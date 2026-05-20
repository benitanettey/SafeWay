package com.example.safeway.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TriggerEvent(
    val gestureType: GestureType,
    val action: EmergencyAction,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    fun formattedTime(): String {
        return SimpleDateFormat("MMM dd • HH:mm:ss", Locale.getDefault())
            .format(Date(timestampMillis))
    }

    fun serialize(): String {
        return "${gestureType.ordinal},${action.ordinal},$timestampMillis"
    }

    companion object {
        fun deserialize(raw: String): TriggerEvent? {
            val parts = raw.split(",")
            if (parts.size != 3) return null
            val gesture = GestureType.entries.getOrNull(parts[0].toIntOrNull() ?: return null)
            val action = EmergencyAction.entries.getOrNull(parts[1].toIntOrNull() ?: return null)
            val timestamp = parts[2].toLongOrNull() ?: return null
            if (gesture == null || action == null) return null
            return TriggerEvent(gesture, action, timestamp)
        }
    }
}
