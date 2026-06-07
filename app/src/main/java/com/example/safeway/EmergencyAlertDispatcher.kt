package com.example.safeway

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
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

        // Fetch last known location instantly as fallback
        var lastKnownLat: Double? = null
        var lastKnownLng: Double? = null

        fusedLocationClient.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    lastKnownLat = loc.latitude
                    lastKnownLng = loc.longitude
                }
            }

        // Also try fresh GPS fix with a 5-second timeout.
        // If GPS locks fast, we use the fresh fix. If not, we fall back
        // to the cached lastLocation above — SOS never waits more than 5s.
        val cancellationToken = CancellationTokenSource()
        val handler = Handler(Looper.getMainLooper())
        var resolved = false

        handler.postDelayed({
            if (!resolved) {
                resolved = true
                cancellationToken.cancel()
                onLocationReady(lastKnownLat, lastKnownLng)
            }
        }, 5000)

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationToken.token)
            .addOnSuccessListener { location ->
                if (resolved) return@addOnSuccessListener
                resolved = true
                handler.removeCallbacksAndMessages(null)
                onLocationReady(
                    location?.latitude ?: lastKnownLat,
                    location?.longitude ?: lastKnownLng
                )
            }
            .addOnFailureListener {
                if (resolved) return@addOnFailureListener
                resolved = true
                handler.removeCallbacksAndMessages(null)
                onLocationReady(lastKnownLat, lastKnownLng)
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

