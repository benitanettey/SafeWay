package com.example.safeway

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.safeway.overlay.OverlayPrefs
import com.example.safeway.overlay.ShieldOverlayService

class HomeActivity : AppCompatActivity() {

    private var shieldEnabled = false
    private var pendingOverlayPermissionRequest = false
    private lateinit var overlaySwitch: SwitchCompat
    private var isUpdatingSwitchState = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        shieldEnabled = OverlayPrefs.isEnabled(this)

        setupQuickActions()
        setupBottomNavigation()
        setupHotlines()
        setupShieldToggle()
    }

    override fun onResume() {
        super.onResume()
        if (pendingOverlayPermissionRequest) {
            pendingOverlayPermissionRequest = false
            if (canDrawOverlays()) {
                enableOverlay()
            } else {
                disableOverlay(showToast = true)
                Toast.makeText(this, getString(R.string.overlay_permission_denied), Toast.LENGTH_SHORT).show()
            }
        } else {
            val enabled = OverlayPrefs.isEnabled(this)
            updateShieldToggleUi(enabled)
            if (enabled && canDrawOverlays()) {
                startOverlayService()
            }
        }
    }

    private fun setupQuickActions() {
        val btnEmergency = findViewById<LinearLayout>(R.id.btn_emergency)
        val btnLogIncident = findViewById<LinearLayout>(R.id.btn_log_incident)
        val btnMyCircle = findViewById<LinearLayout>(R.id.btn_my_circle)
        val btnRecords = findViewById<LinearLayout>(R.id.btn_records)
        val btnResourcesCenter = findViewById<LinearLayout>(R.id.btn_resources_center)

        btnEmergency.setOnClickListener {
            startActivity(Intent(this, EmergencyAlertActivity::class.java))
        }

        btnLogIncident.setOnClickListener {
            startActivity(Intent(this, LogIncidentActivity::class.java))
        }

        btnMyCircle.setOnClickListener {
            startActivity(Intent(this, SupportCircleActivity::class.java))
        }

        btnRecords.setOnClickListener {
            startActivity(Intent(this, RecordsActivity::class.java))
        }

        btnResourcesCenter.setOnClickListener {
            startActivity(Intent(this, ResourcesActivity::class.java))
        }
    }

    private fun setupBottomNavigation() {
        val navHome = findViewById<LinearLayout>(R.id.nav_home)
        val navLog = findViewById<LinearLayout>(R.id.nav_log)
        val navCircle = findViewById<LinearLayout>(R.id.nav_circle)
        val navRecords = findViewById<LinearLayout>(R.id.nav_records)

        navHome.setOnClickListener {
            // Already on home
        }

        navLog.setOnClickListener {
            startActivity(Intent(this, LogIncidentActivity::class.java))
        }

        navCircle.setOnClickListener {
            startActivity(Intent(this, SupportCircleActivity::class.java))
        }

        navRecords.setOnClickListener {
            startActivity(Intent(this, RecordsActivity::class.java))
        }
    }

    private fun setupHotlines() {
        val btnPolice = findViewById<LinearLayout>(R.id.btn_police_hotline)
        val btnHospital = findViewById<LinearLayout>(R.id.btn_hospital_hotline)
        val btnAmnesty = findViewById<LinearLayout>(R.id.btn_amnesty)
        val btnCounselor = findViewById<LinearLayout>(R.id.btn_counselor)

        btnPolice.setOnClickListener {
            callNumber("999")
        }

        btnHospital.setOnClickListener {
            callNumber("112")
        }

        btnAmnesty.setOnClickListener {
            callNumber("0800123456")
        }

        btnCounselor.setOnClickListener {
            callNumber("0724999999")
        }
    }

    private fun setupShieldToggle() {
        overlaySwitch = findViewById(R.id.switch_overlay_bubble)
        updateShieldToggleUi(shieldEnabled)

        overlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSwitchState) return@setOnCheckedChangeListener

            if (shieldEnabled) {
                disableOverlay(showToast = true)
            } else {
                if (canDrawOverlays()) {
                    enableOverlay()
                } else {
                    updateShieldToggleUi(false)
                    requestOverlayPermission()
                }
            }
        }
    }

    private fun enableOverlay() {
        OverlayPrefs.setEnabled(this, true)
        shieldEnabled = true
        updateShieldToggleUi(true)
        startOverlayService()
        Toast.makeText(this, getString(R.string.overlay_enabled_message), Toast.LENGTH_SHORT).show()
    }

    private fun disableOverlay(showToast: Boolean) {
        OverlayPrefs.setEnabled(this, false)
        shieldEnabled = false
        updateShieldToggleUi(false)
        stopService(Intent(this, ShieldOverlayService::class.java))
        if (showToast) {
            Toast.makeText(this, getString(R.string.overlay_disabled_message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateShieldToggleUi(enabled: Boolean) {
        shieldEnabled = enabled
        isUpdatingSwitchState = true
        overlaySwitch.isChecked = enabled
        isUpdatingSwitchState = false
    }

    private fun requestOverlayPermission() {
        pendingOverlayPermissionRequest = true
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun startOverlayService() {
        val serviceIntent = Intent(this, ShieldOverlayService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun callNumber(number: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:$number")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to call $number", Toast.LENGTH_SHORT).show()
        }
    }
}

