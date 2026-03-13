package com.example.safeway

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip

class EmergencyAlertActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var btnSendSOS: FrameLayout
    private lateinit var llContactsList: LinearLayout
    private lateinit var tvSMSPreview: TextView

    // Mock data for contacts
    private val mockContacts = listOf(
        MockContact("James Mutua", "JM", "Brother", "+254 712 xxx xxx"),
        MockContact("Sarah Njeri", "SN", "Friend", "+254 722 xxx xxx"),
        MockContact("Dr. P. Kamau", "DK", "Counselor", "+254 733 xxx xxx")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_alert)

        initializeViews()
        setupListeners()
        populateContacts()
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btn_back_sos)
        btnSendSOS = findViewById(R.id.btn_send_sos)
        llContactsList = findViewById(R.id.ll_contacts_list)
        tvSMSPreview = findViewById(R.id.tv_sms_preview)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnSendSOS.setOnClickListener {
            sendEmergencyAlert()
        }
    }

    private fun populateContacts() {
        llContactsList.removeAllViews()
        for (contact in mockContacts) {
            val contactChip = Chip(this).apply {
                text = "${contact.initials} ${contact.name}"
                isClickable = true
            }
            llContactsList.addView(contactChip)
        }
    }

    private fun sendEmergencyAlert() {
        // Mock implementation
        val timestamp = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        tvSMSPreview.text = "SHIELD ALERT: Thomas needs help. Location: -1.2921, 36.8219. Time: $timestamp. Automated safety alert."

        // Show success (in real app, would send SMS)
        android.widget.Toast.makeText(
            this,
            "${mockContacts.size} SMS messages sent successfully",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    data class MockContact(
        val name: String,
        val initials: String,
        val relationship: String,
        val phone: String
    )
}

