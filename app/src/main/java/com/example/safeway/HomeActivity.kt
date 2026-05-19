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
import android.app.AlertDialog
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.safeway.data.AppDatabase
import com.example.safeway.data.Hotline
import com.example.safeway.overlay.OverlayPrefs
import com.example.safeway.overlay.ShieldOverlayService
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private var shieldEnabled = false
    private var pendingOverlayPermissionRequest = false
    private lateinit var overlaySwitch: SwitchCompat
    private var isUpdatingSwitchState = false
    private lateinit var database: AppDatabase
    private lateinit var hotlineGrid: GridLayout
    private lateinit var btnAddHotline: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        shieldEnabled = OverlayPrefs.isEnabled(this)
        database = AppDatabase.getDatabase(this)

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

        hotlineGrid = findViewById(R.id.hotline_grid)
        btnAddHotline = findViewById(R.id.btn_add_hotline)

        btnAddHotline.setOnClickListener { showAddHotlineDialog() }

        loadCustomHotlines()
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

    private fun loadCustomHotlines() {
        lifecycleScope.launch {
            val hotlines = database.hotlineDao().getAllHotlines()
            // Remove previously added custom hotline cards (keep the 4 defaults)
            while (hotlineGrid.childCount > 4) {
                hotlineGrid.removeViewAt(hotlineGrid.childCount - 1)
            }
            for ((i, hotline) in hotlines.withIndex()) {
                val row = 2 + (i / 2)
                val col = i % 2
                hotlineGrid.addView(createCustomHotlineCard(hotline, row, col))
            }
        }
    }

    private fun createCustomHotlineCard(hotline: Hotline, row: Int, col: Int): View {
        val params = GridLayout.LayoutParams().apply {
            width = 0
            height = dpToPx(90)
            rowSpec = GridLayout.spec(row, GridLayout.FILL, 1f)
            columnSpec = GridLayout.spec(col, GridLayout.FILL, 1f)
            setMargins(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
        }

        val frame = FrameLayout(this).apply {
            layoutParams = params
        }

        // Card content — matches the default hotline card style
        val card = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.hotline_card_bg)
            setOnClickListener { callNumber(hotline.phone) }
        }

        card.addView(ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(26), dpToPx(26))
            setImageResource(android.R.drawable.ic_menu_call)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(ContextCompat.getColor(this@HomeActivity, R.color.highlight_accent))
        })

        card.addView(TextView(this).apply {
            text = hotline.name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpToPx(6) }
        })

        frame.addView(card)

        // Delete X — visible on long-press
        val deleteBtn = ImageView(this).apply {
            visibility = View.GONE
            val size = dpToPx(22)
            layoutParams = FrameLayout.LayoutParams(size, size).also {
                it.gravity = Gravity.TOP or Gravity.END
                it.topMargin = dpToPx(4)
                it.rightMargin = dpToPx(4)
            }
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(ContextCompat.getColor(this@HomeActivity, R.color.emergency_red))
            isClickable = true
            setOnClickListener { confirmDeleteHotline(hotline) }
        }
        frame.addView(deleteBtn)

        // Long-press toggles the delete X
        frame.setOnLongClickListener {
            deleteBtn.visibility = if (deleteBtn.visibility == View.GONE) View.VISIBLE else View.GONE
            true
        }

        return frame
    }

    private fun showAddHotlineDialog() {
        val nameInput = EditText(this).apply {
            hint = getString(R.string.hotline_name_hint)
        }
        val phoneInput = EditText(this).apply {
            hint = getString(R.string.hotline_phone_hint)
            inputType = InputType.TYPE_CLASS_PHONE
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(4))
            addView(nameInput)
            addView(phoneInput.apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dpToPx(8) }
            })
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.add_hotline_title))
            .setView(container)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = nameInput.text.toString().trim()
                val phone = phoneInput.text.toString().trim()
                if (name.isEmpty() || phone.isEmpty()) {
                    Toast.makeText(
                        this,
                        if (name.isEmpty()) R.string.hotline_name_required else R.string.hotline_phone_required,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    database.hotlineDao().insertHotline(Hotline(name = name, phone = phone))
                    loadCustomHotlines()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun confirmDeleteHotline(hotline: Hotline) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_hotline))
            .setMessage(getString(R.string.delete_hotline_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                lifecycleScope.launch {
                    database.hotlineDao().deleteHotline(hotline)
                    loadCustomHotlines()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}

