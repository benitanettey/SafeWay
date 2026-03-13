package com.example.safeway

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip

class LogIncidentActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var btnRecord: FrameLayout
    private lateinit var tvRecordLabel: TextView
    private lateinit var tvRecordStatus: TextView
    private lateinit var tvRecordTime: TextView
    private lateinit var llWaveform: LinearLayout
    private lateinit var etDescription: EditText
    private lateinit var etWho: EditText
    private lateinit var btnSave: Button

    // Recording state
    private var isRecording = false
    private var recordingSeconds = 0
    private val recordingHandler = Handler(Looper.getMainLooper())

    // Mock incident types and severity chips
    private val incidentTypeChips by lazy {
        listOf(
            findViewById<Chip>(R.id.chip_type_physical),
            findViewById<Chip>(R.id.chip_type_verbal),
            findViewById<Chip>(R.id.chip_type_financial),
            findViewById<Chip>(R.id.chip_type_sexual),
            findViewById<Chip>(R.id.chip_type_neglect)
        )
    }

    private val severityChips by lazy {
        listOf(
            findViewById<Chip>(R.id.chip_sev_low),
            findViewById<Chip>(R.id.chip_sev_medium),
            findViewById<Chip>(R.id.chip_sev_high),
            findViewById<Chip>(R.id.chip_sev_crisis)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_incident)

        initializeViews()
        setupListeners()
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btn_back_log)
        btnRecord = findViewById(R.id.btn_record)
        tvRecordLabel = findViewById(R.id.tv_record_label)
        tvRecordStatus = findViewById(R.id.tv_record_status)
        tvRecordTime = findViewById(R.id.tv_record_time)
        llWaveform = findViewById(R.id.ll_waveform)
        etDescription = findViewById(R.id.et_description)
        etWho = findViewById(R.id.et_who)
        btnSave = findViewById(R.id.btn_save_incident)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            if (isRecording) {
                stopRecording()
            }
            finish()
        }

        btnRecord.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        btnSave.setOnClickListener {
            saveIncident()
        }
    }

    private fun startRecording() {
        isRecording = true
        recordingSeconds = 0
        tvRecordLabel.text = "Recording..."
        tvRecordStatus.text = "Tap to stop"
        btnRecord.background = getDrawable(R.drawable.record_button_recording_bg)

        // Simulate recording with visual waveform
        simulateWaveform()
    }

    private fun stopRecording() {
        isRecording = false
        tvRecordLabel.text = "Voice note saved"
        tvRecordStatus.text = "Encrypted • ${String.format("%d:%02d", recordingSeconds / 60, recordingSeconds % 60)}"
        btnRecord.background = getDrawable(R.drawable.record_button_bg)
        recordingHandler.removeCallbacksAndMessages(null)
    }

    private fun simulateWaveform() {
        recordingHandler.post(object : Runnable {
            override fun run() {
                if (isRecording) {
                    recordingSeconds++
                    tvRecordTime.text = String.format("%d:%02d", recordingSeconds / 60, recordingSeconds % 60)

                    // Add a waveform bar
                    val bar = android.view.View(this@LogIncidentActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            3.dpToPx(),
                            (4..20).random().dpToPx()
                        ).apply {
                            setMargins(2, 0, 2, 0)
                        }
                        setBackgroundColor(getColor(R.color.highlight_accent))
                    }
                    llWaveform.addView(bar)

                    // Limit to 50 bars
                    if (llWaveform.childCount > 50) {
                        llWaveform.removeViewAt(0)
                    }

                    recordingHandler.postDelayed(this, 1000)
                }
            }
        })
    }

    private fun saveIncident() {
        val description = etDescription.text.toString()
        val who = etWho.text.toString()
        val selectedType = incidentTypeChips.firstOrNull { it.isChecked }?.text.toString()
        val selectedSeverity = severityChips.firstOrNull { it.isChecked }?.text.toString()

        if (description.isEmpty()) {
            android.widget.Toast.makeText(this, "Please describe the incident", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // Mock save implementation
        val incident = MockIncident(
            title = selectedType.takeIf { it.isNotEmpty() } ?: "Incident",
            description = description,
            severity = selectedSeverity.takeIf { it.isNotEmpty() } ?: "Medium",
            location = "Nairobi, Kenya • -1.2921, 36.8219",
            who = who.takeIf { it.isNotEmpty() } ?: "Unknown",
            hasVoiceNote = recordingSeconds > 0,
            timestamp = java.text.SimpleDateFormat("MMM dd • HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        )

        // Show success
        btnSave.text = "Saved to private journal"
        btnSave.isEnabled = false

        // Reset after 2 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            btnSave.text = getString(R.string.save_to_journal)
            btnSave.isEnabled = true
            finish()
        }, 2000)
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    data class MockIncident(
        val title: String,
        val description: String,
        val severity: String,
        val location: String,
        val who: String,
        val hasVoiceNote: Boolean,
        val timestamp: String
    )
}

