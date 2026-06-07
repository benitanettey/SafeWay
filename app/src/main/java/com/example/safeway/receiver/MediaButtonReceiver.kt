package com.example.safeway.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.KeyEvent

class MediaButtonReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "BroadcastReceiver.onReceive: action=${intent.action}")

        if (intent.action != Intent.ACTION_MEDIA_BUTTON) {
            Log.d(TAG, "ignored: not ACTION_MEDIA_BUTTON")
            return
        }

        val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        }

        if (keyEvent == null) {
            Log.d(TAG, "no KeyEvent in intent")
            return
        }

        Log.d(TAG, "received key: action=${keyEvent.action}, code=${keyEvent.keyCode}")

        val code = keyEvent.keyCode
        if (code != KeyEvent.KEYCODE_HEADSETHOOK &&
            code != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE &&
            code != KeyEvent.KEYCODE_MEDIA_NEXT &&
            code != KeyEvent.KEYCODE_MEDIA_PREVIOUS &&
            code != KeyEvent.KEYCODE_MEDIA_PLAY &&
            code != KeyEvent.KEYCODE_MEDIA_PAUSE
        ) {
            Log.d(TAG, "ignored code=$code (not HEADHOOK/PLAY_PAUSE/NEXT/PREVIOUS)")
            return
        }

        Log.d(TAG, "forwarding to service: action=${keyEvent.action}, code=$code")

        val serviceIntent = Intent(context, com.example.safeway.service.ProtectionForegroundService::class.java).apply {
            action = ACTION_FORWARD_KEY
            putExtra(EXTRA_KEY_EVENT_ACTION, keyEvent.action)
            putExtra(EXTRA_KEY_CODE, code)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    companion object {
        private const val TAG = "ShieldBT.Broadcast"

        const val ACTION_FORWARD_KEY = "com.example.safeway.FORWARD_KEY"
        const val EXTRA_KEY_EVENT_ACTION = "key_action"
        const val EXTRA_KEY_CODE = "key_code"
    }
}
