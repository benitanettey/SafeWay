package com.example.safeway

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ToggleButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip

class HomeActivity : AppCompatActivity() {

    private lateinit var btnEmergencyAlert: FrameLayout
    private lateinit var btnLogIncident: FrameLayout
    private lateinit var btnMyCircle: FrameLayout
    private lateinit var btnRecords: FrameLayout
    private lateinit var toggleShield: ToggleButton
    private lateinit var tvShieldDescription: TextView
    private lateinit var chipStatus: Chip
    private lateinit var bottomNavigation: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        initializeViews()
        setupClickListeners()
        setupBottomNavigation()
    }

    private fun initializeViews() {
        btnEmergencyAlert = findViewById(R.id.btn_emergency_alert)
        btnLogIncident = findViewById(R.id.btn_log_incident)
        btnMyCircle = findViewById(R.id.btn_my_circle)
        btnRecords = findViewById(R.id.btn_records)
        toggleShield = findViewById(R.id.toggle_shield)
        tvShieldDescription = findViewById(R.id.tv_shield_description)
        chipStatus = findViewById(R.id.chip_status)
        bottomNavigation = findViewById(R.id.bottom_navigation)
    }

    private fun setupClickListeners() {
        btnEmergencyAlert.setOnClickListener {
            navigateToEmergencyAlert()
        }

        btnLogIncident.setOnClickListener {
            navigateToLogIncident()
        }

        btnMyCircle.setOnClickListener {
            navigateToMyCircle()
        }

        btnRecords.setOnClickListener {
            navigateToRecords()
        }

        toggleShield.setOnCheckedChangeListener { _, isChecked ->
            handleShieldToggle(isChecked)
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigation.setOnNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    // Already on home
                    true
                }
                R.id.nav_log -> {
                    navigateToLogIncident()
                    true
                }
                R.id.nav_circle -> {
                    navigateToMyCircle()
                    true
                }
                R.id.nav_records -> {
                    navigateToRecords()
                    true
                }
                else -> false
            }
        }
    }

    private fun navigateToEmergencyAlert() {
        val intent = Intent(this, EmergencyAlertActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToLogIncident() {
        val intent = Intent(this, LogIncidentActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToMyCircle() {
        val intent = Intent(this, SupportCircleActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToRecords() {
        val intent = Intent(this, RecordsActivity::class.java)
        startActivity(intent)
    }

    private fun handleShieldToggle(isChecked: Boolean) {
        if (isChecked) {
            // Shield is active
            tvShieldDescription.text = getString(R.string.shield_description)
            chipStatus.text = getString(R.string.shield_active)
        } else {
            // Shield is paused
            tvShieldDescription.text = getString(R.string.shield_paused)
            chipStatus.text = getString(R.string.shield_paused)
        }
    }
}

