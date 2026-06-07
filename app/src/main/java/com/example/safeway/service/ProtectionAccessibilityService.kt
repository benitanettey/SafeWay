package com.example.safeway.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.example.safeway.domain.ProtectionPrefs

/**
 * AccessibilityService that monitors foreground app changes and attempts
 * to capture media button key events.
 *
 * The service uses a standard config (no restricted flags in XML) so it can
 * be enabled on Transsion/OEM devices that block canRequestFilterKeyEvents
 * for sideloaded apps. Key event filtering is requested programmatically in
 * onServiceConnected(), which works on some devices even when the XML flag
 * is absent.
 *
 * If key event filtering is not granted, the service still provides
 * connectivity monitoring as a fallback.
 *
 * The user must enable this in Settings > Accessibility > SafeWay.
 */
class ProtectionAccessibilityService : AccessibilityService() {

    private var keyFilteringGranted = false
    private var protectionWasActive = false

    companion object {
        private const val TAG = "ShieldBT.Accessibility"

        /** Vendor-specific key code sent by Transsion/Infinix BT stack for
         *  earbud button presses (observed on Infinix Smart 9 with
         *  "Harmonics twins mini" earbuds over SCO connection). */
        private const val KEYCODE_TRANSSION_BT_BUTTON = 354

        fun isEnabled(context: Context): Boolean {
            try {
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                return enabledServices.contains(
                    context.packageName + "/" + ProtectionAccessibilityService::class.java.canonicalName
                )
            } catch (_: Exception) {
                return false
            }
        }

        fun openSettings(context: Context) {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "AccessibilityService connected")

        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            info.notificationTimeout = 100

            // Attempt to request key event filtering programmatically.
            // Some OEMs check the XML at install time, others only check at
            // registration time, and setting the flag here may be sufficient.
            runCatching {
                info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                serviceInfo = info
                keyFilteringGranted = true
                Log.d(TAG, "key event filtering requested programmatically")
            }.onFailure {
                Log.w(TAG, "could not set key event filtering flag: ${it.message}")
            }

            serviceInfo = info
        } catch (e: Exception) {
            Log.e(TAG, "failed to configure service info", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Currently unused — kept as a placeholder for future monitoring.
        // When key filtering is not available, we could monitor package
        // changes to detect when media apps are active, but for now the
        // MediaSession + BroadcastReceiver paths handle that independently.
    }

    override fun onInterrupt() {
        Log.d(TAG, "AccessibilityService interrupted")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        Log.d(TAG, "onKeyEvent: action=${event.action}, code=${event.keyCode}")

        if (!keyFilteringGranted) {
            Log.d(TAG, "key filtering not granted — passing through")
            return false
        }

        // Only handle media-button key events (standard + vendor-specific)
        if (event.keyCode != KeyEvent.KEYCODE_HEADSETHOOK &&
            event.keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE &&
            event.keyCode != KeyEvent.KEYCODE_MEDIA_NEXT &&
            event.keyCode != KeyEvent.KEYCODE_MEDIA_PREVIOUS &&
            event.keyCode != KeyEvent.KEYCODE_MEDIA_PLAY &&
            event.keyCode != KeyEvent.KEYCODE_MEDIA_PAUSE &&
            event.keyCode != KEYCODE_TRANSSION_BT_BUTTON
        ) {
            Log.d(TAG, "onKeyEvent: unhandled code=${event.keyCode}, hex=0x${Integer.toHexString(event.keyCode)}")
            return false
        }

        // Only intercept when protection is active
        if (!ProtectionPrefs.isEnabled(this)) {
            Log.d(TAG, "protection not active — passing through")
            return false
        }

        // Vendor key 354 can be triggered by system navigation on Transsion/Infinix
        // — ignore unless a BT audio device is actually connected
        if (event.keyCode == KEYCODE_TRANSSION_BT_BUTTON) {
            val audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
            val btConnected = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).any { device ->
                device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
            if (!btConnected) {
                Log.d(TAG, "code=354 with no BT device — ignoring (likely system UI event)")
                return false
            }
        }

        Log.d(TAG, "intercepting key event and forwarding to service")

        val intent = Intent(this, ProtectionForegroundService::class.java).apply {
            action = "com.example.safeway.ACCESSIBILITY_KEY"
            putExtra("key_action", event.action)
            putExtra("key_code", event.keyCode)
        }
        runCatching { startForegroundService(intent) }

        return true
    }

    override fun onDestroy() {
        Log.d(TAG, "AccessibilityService destroyed")
        super.onDestroy()
    }
}
