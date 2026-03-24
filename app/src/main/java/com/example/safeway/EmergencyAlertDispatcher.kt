package com.example.safeway

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.safeway.data.AppDatabase
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object EmergencyAlertDispatcher {

    fun sendNow(context: Context, onResult: (Boolean, String) -> Unit) {
        if (!hasSmsPermission(context)) {
            onResult(false, "SMS permission is missing")
            return
        }

        fetchLocation(context) { latitude, longitude ->
            val message = buildAlertMessage(latitude, longitude)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val database = AppDatabase.getDatabase(context)
                    val contacts = database.contactDao().getContactsWithSmsAlerts()
                    if (contacts.isEmpty()) {
                        onResult(false, "No contacts with SMS alerts enabled")
                        return@launch
                    }

                    val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.getSystemService(SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getDefault()
                    }

                    var successCount = 0
                    contacts.forEach { contact ->
                        try {
                            smsManager.sendTextMessage(contact.phone, null, message, null, null)
                            successCount++
                        } catch (_: Exception) {
                            // Keep sending to other contacts.
                        }
                    }

                    val status = if (successCount > 0) {
                        "Emergency alert sent to $successCount contact(s)"
                    } else {
                        "Failed to send emergency alert"
                    }
                    onResult(successCount > 0, status)
                } catch (e: Exception) {
                    onResult(false, "Failed to send SMS: ${e.message ?: "Unknown error"}")
                }
            }
        }
    }

    private fun hasSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasLocationPermission(context: Context): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation(context: Context, onLocationReady: (Double?, Double?) -> Unit) {
        if (!hasLocationPermission(context)) {
            onLocationReady(null, null)
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val cancellationToken = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationToken.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    onLocationReady(location.latitude, location.longitude)
                } else {
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { lastKnown ->
                            onLocationReady(lastKnown?.latitude, lastKnown?.longitude)
                        }
                        .addOnFailureListener {
                            onLocationReady(null, null)
                        }
                }
            }
            .addOnFailureListener {
                onLocationReady(null, null)
            }
    }

    private fun buildAlertMessage(latitude: Double?, longitude: Double?): String {
        val timestamp = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date())
        val locationSection = if (latitude != null && longitude != null) {
            val coords = String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
            val map = String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", latitude, longitude)
            "Location: $coords. Map: $map."
        } else {
            "Location unavailable."
        }

        return "SHIELD ALERT: Thomas needs help. $locationSection Time: $timestamp. Automated safety alert."
    }
}

