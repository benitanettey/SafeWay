package com.example.safeway

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.safeway.data.AppDatabase
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EmergencyAlertActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var btnSendSOS: FrameLayout
    private lateinit var llContactsList: LinearLayout
    private lateinit var tvSMSPreview: TextView
    private lateinit var tvLocationName: TextView
    private lateinit var tvLocationCoords: TextView
    private lateinit var database: AppDatabase

    companion object {
        private const val SMS_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_alert)

        database = AppDatabase.getDatabase(this)

        initializeViews()
        setupListeners()
        loadContacts()
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btn_back_sos)
        btnSendSOS = findViewById(R.id.btn_send_sos)
        llContactsList = findViewById(R.id.ll_contacts_list)
        tvSMSPreview = findViewById(R.id.tv_sms_preview)
        tvLocationName = findViewById(R.id.tv_location_name)
        tvLocationCoords = findViewById(R.id.tv_location_coords)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnSendSOS.setOnClickListener {
            if (hasSmsPermission()) {
                sendEmergencyAlert()
            } else {
                requestSmsPermission()
            }
        }
    }

    private fun loadContacts() {
        lifecycleScope.launch {
            try {
                val contacts = database.contactDao().getContactsWithSmsAlerts()
                displayContacts(contacts)
                updateSmsPreview()
            } catch (e: Exception) {
                Toast.makeText(this@EmergencyAlertActivity, "Error loading contacts", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayContacts(contacts: List<com.example.safeway.data.Contact>) {
        llContactsList.removeAllViews()

        if (contacts.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "No contacts with SMS alerts enabled"
                setTextColor(ContextCompat.getColor(this@EmergencyAlertActivity, R.color.neutral_muted))
                textSize = 12f
                setPadding(16, 16, 16, 16)
            }
            llContactsList.addView(emptyText)
            return
        }

        contacts.forEach { contact ->
            val chip = Chip(this).apply {
                text = contact.name
                isClickable = false
                isCheckable = false
                chipBackgroundColor = ContextCompat.getColorStateList(
                    this@EmergencyAlertActivity,
                    R.color.card_background
                )
                setTextColor(ContextCompat.getColor(this@EmergencyAlertActivity, R.color.text_primary))
                chipStrokeWidth = 1f
                chipStrokeColor = ContextCompat.getColorStateList(
                    this@EmergencyAlertActivity,
                    R.color.border_dark
                )
                chipCornerRadius = 28f
                textSize = 12f
                setEnsureMinTouchTargetSize(false)
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = 8
                bottomMargin = 8
            }

            chip.layoutParams = params
            llContactsList.addView(chip)
        }
    }

    private fun updateSmsPreview() {
        tvSMSPreview.text = buildAlertMessage()
    }

    private fun buildAlertMessage(): String {
        val timestamp = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date())
        val locationText = tvLocationCoords.text.toString().replace(" • live", "")

        return "SHIELD ALERT: Thomas needs help. Location: $locationText. Time: $timestamp. Automated safety alert."
    }

    private fun sendEmergencyAlert() {
        lifecycleScope.launch {
            try {
                val contacts = database.contactDao().getContactsWithSmsAlerts()

                if (contacts.isEmpty()) {
                    Toast.makeText(
                        this@EmergencyAlertActivity,
                        "No contacts with SMS alerts enabled",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val message = buildAlertMessage()
                tvSMSPreview.text = message

                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                var successCount = 0
                contacts.forEach { contact ->
                    try {
                        smsManager.sendTextMessage(
                            contact.phone,
                            null,
                            message,
                            null,
                            null
                        )
                        successCount++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                Toast.makeText(
                    this@EmergencyAlertActivity,
                    "Emergency alert sent to $successCount contact(s)",
                    Toast.LENGTH_LONG
                ).show()

            } catch (e: Exception) {
                Toast.makeText(
                    this@EmergencyAlertActivity,
                    "Failed to send SMS: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestSmsPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.SEND_SMS),
            SMS_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sendEmergencyAlert()
            } else {
                Toast.makeText(
                    this,
                    "SMS permission is required to send an emergency alert",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

