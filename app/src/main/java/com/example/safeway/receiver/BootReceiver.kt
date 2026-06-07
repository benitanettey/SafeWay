package com.example.safeway.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.safeway.domain.ProtectionPrefs
import com.example.safeway.service.ProtectionForegroundService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val enabled = ProtectionPrefs.isEnabled(context)
        Log.d(TAG, "BOOT_COMPLETED — protection was enabled=$enabled")

        if (enabled) {
            ProtectionForegroundService.start(context)
        }
    }

    companion object {
        private const val TAG = "ShieldBT.Boot"
    }
}
