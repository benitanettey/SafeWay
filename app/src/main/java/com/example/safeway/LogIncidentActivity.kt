package com.example.safeway

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.safeway.data.AppDatabase
import com.example.safeway.data.Incident
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.Locale

class LogIncidentActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var btnRecord: FrameLayout
    private lateinit var btnPreviewRecording: ImageButton
    private lateinit var btnDeleteRecording: ImageButton
    private lateinit var tvRecordLabel: TextView
    private lateinit var tvRecordStatus: TextView
    private lateinit var tvRecordTime: TextView
    private lateinit var llWaveform: LinearLayout
    private lateinit var etDescription: EditText
    private lateinit var etLocation: EditText
    private lateinit var etWho: EditText
    private lateinit var btnSave: Button
    private lateinit var database: AppDatabase

    private var isRecording = false
    private var recordingSeconds = 0
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var voiceNotePath: String? = null
    private var isPlayingPreview = false
    private val recordingHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val RECORD_AUDIO_PERMISSION_REQUEST_CODE = 2001
    }

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
        database = AppDatabase.getDatabase(this)

        initializeViews()
        setupListeners()
        setupChipSelection(incidentTypeChips)
        setupChipSelection(severityChips)
        updateVoiceActionButtons()
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btn_back_log)
        btnRecord = findViewById(R.id.btn_record)
        btnPreviewRecording = findViewById(R.id.btn_preview_recording)
        btnDeleteRecording = findViewById(R.id.btn_delete_recording)
        tvRecordLabel = findViewById(R.id.tv_record_label)
        tvRecordStatus = findViewById(R.id.tv_record_status)
        tvRecordTime = findViewById(R.id.tv_record_time)
        llWaveform = findViewById(R.id.ll_waveform)
        etDescription = findViewById(R.id.et_description)
        etLocation = findViewById(R.id.et_location)
        etWho = findViewById(R.id.et_who)
        btnSave = findViewById(R.id.btn_save_incident)

        etDescription.gravity = Gravity.TOP or Gravity.START
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            if (isRecording) {
                stopRecording(showToast = false)
            }
            finish()
        }

        btnRecord.setOnClickListener {
            if (isRecording) {
                stopRecording(showToast = true)
            } else {
                startRecording()
            }
        }

        btnPreviewRecording.setOnClickListener {
            if (isPlayingPreview) {
                stopPreview()
            } else {
                startPreview()
            }
        }

        btnDeleteRecording.setOnClickListener {
            deleteCurrentVoiceNote()
        }

        btnSave.setOnClickListener {
            saveIncident()
        }
    }

    private fun startRecording() {
        if (!hasRecordAudioPermission()) {
            requestRecordAudioPermission()
            return
        }

        if (isPlayingPreview) {
            stopPreview()
        }

        voiceNotePath?.let { deleteAudioFile(it) }
        voiceNotePath = null
        llWaveform.removeAllViews()

        try {
            val outputFile = createAudioOutputFile()
            setupAndStartRecorder(outputFile)

            isRecording = true
            recordingSeconds = 0
            tvRecordLabel.text = getString(R.string.recording)
            tvRecordStatus.text = getString(R.string.tap_to_stop)
            tvRecordTime.text = getString(R.string.record_time_default)
            btnRecord.background = AppCompatResources.getDrawable(this, R.drawable.record_button_recording_bg)
            updateVoiceActionButtons()
            simulateWaveform()

            Toast.makeText(this, getString(R.string.recording_started), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            releaseRecorder()
            isRecording = false
            updateVoiceActionButtons()
            Toast.makeText(this, getString(R.string.recording_start_failed), Toast.LENGTH_SHORT).show()
        }
    }

    @Throws(IOException::class)
    private fun setupAndStartRecorder(outputFile: File) {
        voiceNotePath = outputFile.absolutePath
        mediaRecorder = createRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
    }

    private fun stopRecording(showToast: Boolean) {
        if (!isRecording) return

        var validRecording = true
        try {
            mediaRecorder?.stop()
        } catch (_: RuntimeException) {
            validRecording = false
            voiceNotePath?.let { deleteAudioFile(it) }
            voiceNotePath = null
            recordingSeconds = 0
        } finally {
            releaseRecorder()
            isRecording = false
            recordingHandler.removeCallbacksAndMessages(null)
            btnRecord.background = AppCompatResources.getDrawable(this, R.drawable.record_button_bg)
        }

        if (validRecording && !voiceNotePath.isNullOrBlank()) {
            val duration = formatDuration(recordingSeconds)
            tvRecordLabel.text = getString(R.string.voice_note_saved)
            tvRecordStatus.text = getString(R.string.encrypted_duration, duration)
            if (showToast) {
                Toast.makeText(this, getString(R.string.voice_note_recorded, duration), Toast.LENGTH_SHORT).show()
            }
        } else {
            resetVoiceUi()
            if (showToast) {
                Toast.makeText(this, getString(R.string.recording_invalid_retry), Toast.LENGTH_SHORT).show()
            }
        }

        updateVoiceActionButtons()
    }

    private fun releaseRecorder() {
        mediaRecorder?.reset()
        mediaRecorder?.release()
        mediaRecorder = null
    }

    private fun createRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    private fun createAudioOutputFile(): File {
        val baseDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: filesDir
        val voiceDir = File(baseDir, "SafeWay/voice_notes")
        if (!voiceDir.exists()) {
            voiceDir.mkdirs()
        }
        val fileName = "voice_note_${System.currentTimeMillis()}.m4a"
        return File(voiceDir, fileName)
    }

    private fun deleteCurrentVoiceNote() {
        if (isRecording) {
            stopRecording(showToast = false)
        }

        if (isPlayingPreview) {
            stopPreview()
        }

        val deleted = voiceNotePath?.let { deleteAudioFile(it) } ?: false
        voiceNotePath = null
        recordingSeconds = 0
        resetVoiceUi()
        updateVoiceActionButtons()

        val messageRes = if (deleted) R.string.voice_note_deleted else R.string.no_voice_note_to_delete
        Toast.makeText(this, getString(messageRes), Toast.LENGTH_SHORT).show()
    }

    private fun resetVoiceUi() {
        if (isPlayingPreview) {
            stopPreview()
        }
        tvRecordLabel.text = getString(R.string.voice_note)
        tvRecordStatus.text = getString(R.string.tap_to_record)
        tvRecordTime.text = getString(R.string.record_time_default)
        btnRecord.background = AppCompatResources.getDrawable(this, R.drawable.record_button_bg)
        llWaveform.removeAllViews()
        updatePreviewButtonUi(false)
    }

    private fun updateVoiceActionButtons() {
        val hasVoiceNote = !voiceNotePath.isNullOrBlank()
        btnPreviewRecording.visibility = if (hasVoiceNote && !isRecording) View.VISIBLE else View.GONE
        btnDeleteRecording.visibility = if (isRecording || hasVoiceNote) View.VISIBLE else View.GONE
        if (!hasVoiceNote && isPlayingPreview) {
            stopPreview()
        }
    }

    private fun startPreview() {
        if (isRecording) {
            Toast.makeText(this, getString(R.string.stop_recording_before_preview), Toast.LENGTH_SHORT).show()
            return
        }

        val path = voiceNotePath
        if (path.isNullOrBlank() || !File(path).exists()) {
            voiceNotePath = null
            updateVoiceActionButtons()
            Toast.makeText(this, getString(R.string.no_voice_note_to_preview), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            stopPreview()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener {
                    stopPreview()
                }
                prepare()
                start()
            }
            isPlayingPreview = true
            updatePreviewButtonUi(true)
            tvRecordStatus.text = getString(R.string.preview_playing)
        } catch (_: Exception) {
            stopPreview()
            Toast.makeText(this, getString(R.string.preview_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPreview() {
        mediaPlayer?.run {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        isPlayingPreview = false
        updatePreviewButtonUi(false)
        if (!voiceNotePath.isNullOrBlank()) {
            tvRecordStatus.text = getString(R.string.tap_preview_or_record)
        }
    }

    private fun updatePreviewButtonUi(isPlaying: Boolean) {
        val iconRes = if (isPlaying) R.drawable.ic_stop else R.drawable.ic_play
        val descriptionRes = if (isPlaying) R.string.stop_voice_note_preview else R.string.play_voice_note_preview
        btnPreviewRecording.setImageDrawable(AppCompatResources.getDrawable(this, iconRes))
        btnPreviewRecording.contentDescription = getString(descriptionRes)
    }

    private fun setupChipSelection(chips: List<Chip>) {
        chips.forEach { chip ->
            chip.isCheckedIconVisible = false
            applyChipStyle(chip, chip.isChecked)

            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    chips.filter { it != chip }.forEach { other ->
                        other.isChecked = false
                        applyChipStyle(other, false)
                    }
                }
                applyChipStyle(chip, isChecked)
            }
        }
    }

    private fun applyChipStyle(chip: Chip, isChecked: Boolean) {
        if (isChecked) {
            chip.chipBackgroundColor = ContextCompat.getColorStateList(this, R.color.primary_accent)
            chip.chipStrokeColor = ContextCompat.getColorStateList(this, R.color.primary_accent)
            chip.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        } else {
            chip.chipBackgroundColor = ContextCompat.getColorStateList(this, R.color.card_background)
            chip.chipStrokeColor = ContextCompat.getColorStateList(this, R.color.border_dark)
            chip.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun simulateWaveform() {
        recordingHandler.post(object : Runnable {
            override fun run() {
                if (isRecording) {
                    recordingSeconds++
                    tvRecordTime.text = formatDuration(recordingSeconds)

                    val bar = View(this@LogIncidentActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(3.dpToPx(), (4..20).random().dpToPx()).apply {
                            setMargins(2, 0, 2, 0)
                        }
                        setBackgroundColor(getColor(R.color.highlight_accent))
                    }
                    llWaveform.addView(bar)

                    if (llWaveform.childCount > 50) {
                        llWaveform.removeViewAt(0)
                    }

                    recordingHandler.postDelayed(this, 1000)
                }
            }
        })
    }

    private fun saveIncident() {
        val description = etDescription.text.toString().trim()
        val who = etWho.text.toString().trim()
        val location = etLocation.text.toString().trim()
        val selectedType = incidentTypeChips.firstOrNull { it.isChecked }?.text?.toString().orEmpty()
        val selectedSeverity = severityChips.firstOrNull { it.isChecked }?.text?.toString().orEmpty()

        if (description.isEmpty()) {
            Toast.makeText(this, getString(R.string.please_describe_incident), Toast.LENGTH_SHORT).show()
            return
        }
        if (location.isEmpty()) {
            Toast.makeText(this, getString(R.string.please_enter_location), Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedType.isEmpty()) {
            Toast.makeText(this, getString(R.string.please_select_incident_type), Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedSeverity.isEmpty()) {
            Toast.makeText(this, getString(R.string.please_select_severity), Toast.LENGTH_SHORT).show()
            return
        }

        if (isRecording) {
            stopRecording(showToast = true)
        }

        if (isPlayingPreview) {
            stopPreview()
        }

        val incident = Incident(
            type = selectedType,
            description = description,
            severity = selectedSeverity,
            location = location,
            who = who.ifEmpty { getString(R.string.who_unknown) },
            hasVoiceNote = !voiceNotePath.isNullOrBlank(),
            voiceNotePath = voiceNotePath,
            voiceDurationSec = recordingSeconds,
            createdAtMillis = System.currentTimeMillis()
        )

        btnSave.text = getString(R.string.saved_to_journal)
        btnSave.isEnabled = false

        lifecycleScope.launch {
            try {
                database.incidentDao().insertIncident(incident)
                val successMessage = if (incident.hasVoiceNote) {
                    getString(R.string.incident_saved_with_voice_note)
                } else {
                    getString(R.string.incident_saved_success)
                }
                Toast.makeText(this@LogIncidentActivity, successMessage, Toast.LENGTH_SHORT).show()
                finish()
            } catch (_: Exception) {
                btnSave.text = getString(R.string.save_to_journal)
                btnSave.isEnabled = true
                Toast.makeText(this@LogIncidentActivity, getString(R.string.incident_save_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRecordAudioPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            RECORD_AUDIO_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            } else {
                Toast.makeText(this, getString(R.string.recording_permission_required), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recordingHandler.removeCallbacksAndMessages(null)
        if (isRecording) {
            stopRecording(showToast = false)
        }
        stopPreview()
        releaseRecorder()
    }

    private fun formatDuration(totalSeconds: Int): String {
        return String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    private fun deleteAudioFile(path: String): Boolean {
        return try {
            val file = File(path)
            file.exists() && file.delete()
        } catch (_: Exception) {
            false
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}

