package com.example.safeway

import android.Manifest
import android.annotation.SuppressLint
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
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
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
    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null
    private var pendingSendAfterPermissions = false

    companion object {
        private const val SMS_PERMISSION_REQUEST_CODE = 1001
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_alert)

        database = AppDatabase.getDatabase(this)

        BottomNavHelper.setup(this, NavTab.HOME)
        initializeViews()
        setupListeners()
        loadContacts()
        refreshLocation()
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
            overridePendingTransition(R.anim.fade_in, R.anim.slide_out_left)
        }

        btnSendSOS.setOnClickListener {
            pendingSendAfterPermissions = true
            if (!hasSmsPermission()) {
                requestSmsPermission()
                return@setOnClickListener
            }
            if (!hasLocationPermission()) {
                requestLocationPermission()
                return@setOnClickListener
            }

            prepareAndSendEmergencyAlert()
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
        val locationText = formattedCoordinates()
        val mapsLink = buildMapsLink()
        val locationSection = if (locationText != null && mapsLink != null) {
            "Location: $locationText. Map: $mapsLink."
        } else {
            "Location unavailable."
        }

        return "SHIELD ALERT: Thomas needs help. $locationSection Time: $timestamp. Automated safety alert."
    }

    private fun prepareAndSendEmergencyAlert() {
        refreshLocation {
            sendEmergencyAlert()
        }
    }

    private fun formattedCoordinates(): String? {
        val latitude = currentLatitude ?: return null
        val longitude = currentLongitude ?: return null
        return String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
    }

    private fun buildMapsLink(): String? {
        val latitude = currentLatitude ?: return null
        val longitude = currentLongitude ?: return null
        return String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", latitude, longitude)
    }

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun applyLocation(latitude: Double, longitude: Double) {
        currentLatitude = latitude
        currentLongitude = longitude
        val formatted = String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
        tvLocationName.text = getString(R.string.current_gps_location)
        tvLocationCoords.text = getString(R.string.live_location_format, formatted)
        updateSmsPreview()
    }

    private fun setLocationUnavailable() {
        currentLatitude = null
        currentLongitude = null
        tvLocationName.text = getString(R.string.location_unavailable_title)
        tvLocationCoords.text = getString(R.string.location_unavailable)
        updateSmsPreview()
    }

    @SuppressLint("MissingPermission")
    private fun refreshLocation(onResult: (() -> Unit)? = null) {
        if (!hasLocationPermission()) {
            setLocationUnavailable()
            onResult?.invoke()
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val cancellationToken = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationToken.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    applyLocation(location.latitude, location.longitude)
                    onResult?.invoke()
                } else {
                    fetchLastKnownLocation(fusedLocationClient, onResult)
                }
            }
            .addOnFailureListener {
                fetchLastKnownLocation(fusedLocationClient, onResult)
            }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLastKnownLocation(
        fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient,
        onResult: (() -> Unit)?
    ) {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    applyLocation(location.latitude, location.longitude)
                } else {
                    setLocationUnavailable()
                }
                onResult?.invoke()
            }
            .addOnFailureListener {
                setLocationUnavailable()
                onResult?.invoke()
            }
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
                if (hasLocationPermission()) {
                    prepareAndSendEmergencyAlert()
                    pendingSendAfterPermissions = false
                } else {
                    requestLocationPermission()
                }
            } else {
                pendingSendAfterPermissions = false
                Toast.makeText(
                    this,
                    "SMS permission is required to send an emergency alert",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (!pendingSendAfterPermissions) {
                refreshLocation()
                return
            }

            if (hasLocationPermission()) {
                prepareAndSendEmergencyAlert()
                pendingSendAfterPermissions = false
            } else {
                pendingSendAfterPermissions = false
                Toast.makeText(
                    this,
                    getString(R.string.location_permission_required_sms),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}



