package com.example.safeway.overlay

import android.content.Context

object OverlayPrefs {
    private const val PREF_NAME = "overlay_prefs"
    private const val KEY_ENABLED = "overlay_enabled"
    private const val KEY_BUBBLE_X = "bubble_x"
    private const val KEY_BUBBLE_Y = "bubble_y"

    fun isEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun bubbleX(context: Context): Int? {
        return if (prefs(context).contains(KEY_BUBBLE_X)) prefs(context).getInt(KEY_BUBBLE_X, 0) else null
    }

    fun bubbleY(context: Context): Int? {
        return if (prefs(context).contains(KEY_BUBBLE_Y)) prefs(context).getInt(KEY_BUBBLE_Y, 0) else null
    }

    fun saveBubblePosition(context: Context, x: Int, y: Int) {
        prefs(context).edit().putInt(KEY_BUBBLE_X, x).putInt(KEY_BUBBLE_Y, y).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}

