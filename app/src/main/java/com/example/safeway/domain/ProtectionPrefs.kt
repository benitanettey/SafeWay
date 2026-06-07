package com.example.safeway.domain

import android.content.Context
import android.util.Log

object ProtectionPrefs {

    private const val PREF_NAME = "protection_prefs"
    private const val KEY_ENABLED = "protection_enabled"
    private const val KEY_DEVICE_NAME = "paired_device_name"
    private const val KEY_TRIPLE_ACTION = "gesture_triple_action"
    private const val KEY_DOUBLE_ACTION = "gesture_double_action"
    private const val KEY_TRIGGER_HISTORY = "trigger_history"
    private const val KEY_SLOW_DOUBLE_ACTION = "gesture_slow_double_action"
    private const val KEY_PENDING_VOICE_NOTE_PATH = "pending_voice_note_path"
    private const val KEY_PENDING_VOICE_NOTE_DURATION = "pending_voice_note_duration"

    fun isEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getPairedDeviceName(context: Context): String? {
        val name = prefs(context).getString(KEY_DEVICE_NAME, null)
        return if (name.isNullOrBlank()) null else name
    }

    fun setPairedDeviceName(context: Context, name: String?) {
        prefs(context).edit().putString(KEY_DEVICE_NAME, name).apply()
    }

    fun getTriplePressAction(context: Context): EmergencyAction {
        val ordinal = prefs(context).getInt(KEY_TRIPLE_ACTION, EmergencyAction.SOS_ALERT.ordinal)
        return EmergencyAction.entries.getOrElse(ordinal) { EmergencyAction.SOS_ALERT }
    }

    fun setTriplePressAction(context: Context, action: EmergencyAction) {
        prefs(context).edit().putInt(KEY_TRIPLE_ACTION, action.ordinal).apply()
    }

    fun getDoublePressAction(context: Context): EmergencyAction {
        val ordinal = prefs(context).getInt(KEY_DOUBLE_ACTION, EmergencyAction.START_RECORDING.ordinal)
        return EmergencyAction.entries.getOrElse(ordinal) { EmergencyAction.START_RECORDING }
    }

    fun setDoublePressAction(context: Context, action: EmergencyAction) {
        prefs(context).edit().putInt(KEY_DOUBLE_ACTION, action.ordinal).apply()
    }

    fun getSlowDoublePressAction(context: Context): EmergencyAction {
        val ordinal = prefs(context).getInt(KEY_SLOW_DOUBLE_ACTION, EmergencyAction.SOS_ALERT.ordinal)
        return EmergencyAction.entries.getOrElse(ordinal) { EmergencyAction.SOS_ALERT }
    }

    fun setSlowDoublePressAction(context: Context, action: EmergencyAction) {
        prefs(context).edit().putInt(KEY_SLOW_DOUBLE_ACTION, action.ordinal).apply()
    }

    fun addTriggerEvent(context: Context, event: TriggerEvent) {
        Log.d("ShieldBT.Prefs", "addTriggerEvent: ${event.gestureType} -> ${event.action}")
        val history = getTriggerHistory(context).toMutableList()
        history.add(0, event)
        if (history.size > 50) {
            history.removeAt(history.lastIndex)
        }
        val json = history.joinToString("||") { it.serialize() }
        prefs(context).edit().putString(KEY_TRIGGER_HISTORY, json).apply()
    }

    fun getTriggerHistory(context: Context): List<TriggerEvent> {
        val json = prefs(context).getString(KEY_TRIGGER_HISTORY, "") ?: ""
        if (json.isBlank()) return emptyList()
        return json.split("||").mapNotNull { TriggerEvent.deserialize(it) }
    }

    fun clearHistory(context: Context) {
        prefs(context).edit().remove(KEY_TRIGGER_HISTORY).apply()
    }

    // --- Pending voice note (background recording draft) ---

    fun savePendingVoiceNote(context: Context, path: String, durationSec: Int) {
        Log.d("ShieldBT.Prefs", "savePendingVoiceNote: path=$path, durationSec=$durationSec")
        prefs(context).edit()
            .putString(KEY_PENDING_VOICE_NOTE_PATH, path)
            .putInt(KEY_PENDING_VOICE_NOTE_DURATION, durationSec)
            .apply()
    }

    fun getPendingVoiceNotePath(context: Context): String? {
        return prefs(context).getString(KEY_PENDING_VOICE_NOTE_PATH, null)
    }

    fun getPendingVoiceNoteDuration(context: Context): Int {
        return prefs(context).getInt(KEY_PENDING_VOICE_NOTE_DURATION, 0)
    }

    fun clearPendingVoiceNote(context: Context) {
        Log.d("ShieldBT.Prefs", "clearPendingVoiceNote")
        prefs(context).edit()
            .remove(KEY_PENDING_VOICE_NOTE_PATH)
            .remove(KEY_PENDING_VOICE_NOTE_DURATION)
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
