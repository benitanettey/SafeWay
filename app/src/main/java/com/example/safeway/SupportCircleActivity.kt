package com.example.safeway

import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class SupportCircleActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var btnAddContact: LinearLayout

    // Mock contacts data
    private val mockContacts = listOf(
        MockContact("James Mutua", "JM", "Brother", "+254 712 xxx xxx", true, true),
        MockContact("Sarah Njeri", "SN", "Friend", "+254 722 xxx xxx", true, true),
        MockContact("Dr. P. Kamau", "DK", "Counselor", "+254 733 xxx xxx", true, true)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_support_circle)

        initializeViews()
        setupListeners()
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btn_back_circle)
        btnAddContact = findViewById(R.id.btn_add_contact)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnAddContact.setOnClickListener {
            // Navigate to add contact screen
            android.widget.Toast.makeText(this, "Add contact feature coming soon", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    data class MockContact(
        val name: String,
        val initials: String,
        val relationship: String,
        val phone: String,
        val smsAlerts: Boolean,
        val includeGPS: Boolean
    )
}

