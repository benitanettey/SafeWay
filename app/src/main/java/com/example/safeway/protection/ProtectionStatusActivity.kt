package com.example.safeway.protection

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.safeway.R
import com.example.safeway.domain.EmergencyAction
import com.example.safeway.domain.ProtectionPrefs
import com.example.safeway.service.ProtectionAccessibilityService
import com.example.safeway.service.ProtectionForegroundService

class ProtectionStatusActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var btnToggleProtection: Button
    private lateinit var tvStatusIndicator: TextView
    private lateinit var tvDeviceStatus: TextView
    private lateinit var tvDoubleActionLabel: TextView
    private lateinit var tvSlowDoubleActionLabel: TextView
    private lateinit var configDoublePress: LinearLayout
    private lateinit var configSlowDoublePress: LinearLayout
    private lateinit var historyContainer: LinearLayout
    private lateinit var historyScroll: androidx.core.widget.NestedScrollView
    private lateinit var tvEmptyHistory: TextView
    private lateinit var btnClearHistory: Button
    private lateinit var btnRefreshDevice: Button

    // Accessibility views
    private lateinit var accessibilityCard: LinearLayout
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var btnEnableAccessibility: Button

    private var protectionEnabled = false
    private val btPollHandler = Handler(Looper.getMainLooper())
    private val btPollIntervalMs = 2000L
    private var isPolling = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_protection_status)

        Log.d(TAG, "=== ProtectionStatusActivity opened ===")
        Log.d(TAG, "protection enabled: ${ProtectionPrefs.isEnabled(this)}")

        initializeViews()
        setupListeners()
        refreshState()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume — refreshing state")
        refreshState()
        renderHistory()
        startBtPolling()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause — stopping BT polling")
        stopBtPolling()
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btn_back_protection)
        btnToggleProtection = findViewById(R.id.btn_toggle_protection)
        tvStatusIndicator = findViewById(R.id.tv_protection_status)
        tvDeviceStatus = findViewById(R.id.tv_device_status)
        tvDoubleActionLabel = findViewById(R.id.tv_double_action)
        tvSlowDoubleActionLabel = findViewById(R.id.tv_slow_double_action)
        configDoublePress = findViewById(R.id.config_double_press)
        configSlowDoublePress = findViewById(R.id.config_slow_double_press)
        historyContainer = findViewById(R.id.history_container)
        historyScroll = findViewById(R.id.history_scroll)
        tvEmptyHistory = findViewById(R.id.tv_empty_history)
        btnClearHistory = findViewById(R.id.btn_clear_history)
        btnRefreshDevice = findViewById(R.id.btn_refresh_device)

        accessibilityCard = findViewById(R.id.accessibility_card)
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status)
        btnEnableAccessibility = findViewById(R.id.btn_enable_accessibility)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.slide_out_left)
        }

        btnToggleProtection.setOnClickListener {
            if (protectionEnabled) {
                disableProtection()
            } else {
                enableProtection()
            }
        }

        configDoublePress.setOnClickListener {
            showActionPicker(getString(R.string.double_press)) { action ->
                ProtectionPrefs.setDoublePressAction(this, action)
                refreshState()
            }
        }

        configSlowDoublePress.setOnClickListener {
            showActionPicker(getString(R.string.slow_double_press)) { action ->
                ProtectionPrefs.setSlowDoublePressAction(this, action)
                refreshState()
            }
        }

        btnClearHistory.setOnClickListener {
            ProtectionPrefs.clearHistory(this)
            renderHistory()
            Toast.makeText(this, getString(R.string.history_cleared), Toast.LENGTH_SHORT).show()
        }

        btnRefreshDevice.setOnClickListener {
            refreshDeviceStatus()
        }

        btnEnableAccessibility.setOnClickListener {
            ProtectionAccessibilityService.openSettings(this)
        }
    }

    private fun enableProtection() {
        Log.d(TAG, "enableProtection clicked")
        doEnableProtection()
    }

    private fun doEnableProtection() {
        ProtectionPrefs.setEnabled(this, true)
        ProtectionForegroundService.start(this)
        updateUi(true)
        Log.d(TAG, "protection enabled")
        Toast.makeText(this, getString(R.string.bt_protection_enabled), Toast.LENGTH_SHORT).show()
    }

    private fun disableProtection() {
        Log.d(TAG, "protection disabled")
        ProtectionPrefs.setEnabled(this, false)
        ProtectionForegroundService.stop(this)
        updateUi(false)
        Toast.makeText(this, getString(R.string.bt_protection_disabled), Toast.LENGTH_SHORT).show()
    }

    private fun refreshState() {
        protectionEnabled = ProtectionPrefs.isEnabled(this)
        updateUi(protectionEnabled)

        // Auto-restart the service if prefs say it should be running but the
        // process was killed since the last session (service doesn't persist
        // across process death, but SharedPreferences does).
        if (protectionEnabled) {
            Log.d(TAG, "protection pref is enabled — ensuring service is running")
            ProtectionForegroundService.start(this)
        }

        renderHistory()
        refreshDeviceStatus()
        refreshAccessibilityStatus()

        tvDoubleActionLabel.text = ProtectionPrefs.getDoublePressAction(this).displayName
        tvSlowDoubleActionLabel.text = ProtectionPrefs.getSlowDoublePressAction(this).displayName
    }

    private fun updateUi(enabled: Boolean) {
        protectionEnabled = enabled
        if (enabled) {
            tvStatusIndicator.text = getString(R.string.protection_active)
            tvStatusIndicator.setTextColor(
                ContextCompat.getColor(this, R.color.highlight_accent)
            )
            btnToggleProtection.text = getString(R.string.deactivate_protection)
            btnToggleProtection.setBackgroundResource(R.drawable.button_secondary_bg)
        } else {
            tvStatusIndicator.text = getString(R.string.protection_inactive)
            tvStatusIndicator.setTextColor(
                ContextCompat.getColor(this, R.color.neutral_muted)
            )
            btnToggleProtection.text = getString(R.string.activate_protection)
            btnToggleProtection.setBackgroundResource(R.drawable.button_primary_bg)
        }
    }

    private fun refreshDeviceStatus() {
        val pairedName = ProtectionPrefs.getPairedDeviceName(this)
        val connected = ProtectionForegroundService.isConnectedToDevice(this)

        Log.d(TAG, "device status check — connected=$connected, pairedName=$pairedName")

        tvDeviceStatus.text = when {
            connected && pairedName != null -> getString(R.string.device_connected, pairedName)
            connected -> getString(R.string.device_connected, getString(R.string.unknown_device))
            else -> getString(R.string.no_device_connected)
        }
    }

    // --- Accessibility Service Status ---

    private fun refreshAccessibilityStatus() {
        val enabled = ProtectionAccessibilityService.isEnabled(this)
        accessibilityCard.visibility = View.VISIBLE

        if (enabled) {
            tvAccessibilityStatus.text = getString(R.string.accessibility_service_enabled)
            tvAccessibilityStatus.setTextColor(
                ContextCompat.getColor(this, R.color.status_success)
            )
            btnEnableAccessibility.visibility = View.GONE
        } else {
            tvAccessibilityStatus.text = getString(R.string.accessibility_not_enabled)
            tvAccessibilityStatus.setTextColor(
                ContextCompat.getColor(this, R.color.emergency_red)
            )
            btnEnableAccessibility.visibility = View.VISIBLE
        }
    }

    // --- BT Polling ---

    private val btPollRunnable = object : Runnable {
        override fun run() {
            if (!isPolling) return
            refreshDeviceStatus()
            btPollHandler.postDelayed(this, btPollIntervalMs)
        }
    }

    private fun startBtPolling() {
        if (isPolling) return
        isPolling = true
        btPollHandler.post(btPollRunnable)
    }

    private fun stopBtPolling() {
        isPolling = false
        btPollHandler.removeCallbacks(btPollRunnable)
    }

    // --- History ---

    private fun renderHistory() {
        val history = ProtectionPrefs.getTriggerHistory(this)
        historyContainer.removeAllViews()

        if (history.isEmpty()) {
            tvEmptyHistory.visibility = View.VISIBLE
            historyScroll.visibility = View.GONE
            btnClearHistory.visibility = View.GONE
            return
        }

        tvEmptyHistory.visibility = View.GONE
        historyScroll.visibility = View.VISIBLE
        btnClearHistory.visibility = View.VISIBLE

        history.take(20).forEach { event ->
            val row = TextView(this).apply {
                val gestureLabel = when (event.gestureType) {
                    com.example.safeway.domain.GestureType.TRIPLE_PRESS -> "Triple Press"
                    com.example.safeway.domain.GestureType.DOUBLE_PRESS -> "Double Press"
                    com.example.safeway.domain.GestureType.SLOW_DOUBLE_PRESS -> "Slow Double"
                }
                text = "${event.formattedTime()} • $gestureLabel → ${event.action.displayName}"
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@ProtectionStatusActivity, R.color.text_secondary))
                setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
            }
            historyContainer.addView(row)
        }
    }

    private fun showActionPicker(title: String, onSelected: (EmergencyAction) -> Unit) {
        val actions = EmergencyAction.entries.map { it.displayName }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(actions) { _, which ->
                val action = EmergencyAction.entries[which]
                onSelected(action)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "ShieldBT.UI"
    }
}
