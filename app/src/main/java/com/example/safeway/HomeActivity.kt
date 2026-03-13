package com.example.safeway

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class HomeActivity : AppCompatActivity() {

    private var shieldEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        setupQuickActions()
        setupBottomNavigation()
        setupHotlines()
        setupShieldToggle()
    }

    private fun setupQuickActions() {
        val btnEmergency = findViewById<LinearLayout>(R.id.btn_emergency)
        val btnLogIncident = findViewById<LinearLayout>(R.id.btn_log_incident)
        val btnMyCircle = findViewById<LinearLayout>(R.id.btn_my_circle)
        val btnRecords = findViewById<LinearLayout>(R.id.btn_records)

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
        val shieldToggle = findViewById<ImageView>(R.id.shield_toggle)

        shieldToggle.setOnClickListener {
            shieldEnabled = !shieldEnabled
            if (shieldEnabled) {
                shieldToggle.setImageResource(R.drawable.ic_toggle_on)
                Toast.makeText(this, "Shield protection activated", Toast.LENGTH_SHORT).show()
            } else {
                shieldToggle.setImageResource(R.drawable.ic_toggle_off)
                Toast.makeText(this, "Shield protection paused", Toast.LENGTH_SHORT).show()
            }
        }
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

