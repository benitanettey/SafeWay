package com.example.safeway

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.safeway.data.AppDatabase
import com.example.safeway.data.Incident
import com.example.safeway.domain.ProtectionPrefs
import com.example.safeway.service.ProtectionForegroundService
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
    private lateinit var btnTakePhoto: Button
    private lateinit var btnTakeVideo: Button
    private lateinit var btnPreviewVideo: Button
    private lateinit var btnDeletePhoto: Button
    private lateinit var btnDeleteVideo: Button
    private lateinit var tvPhotoStatus: TextView
    private lateinit var tvVideoStatus: TextView
    private lateinit var ivPhotoPreview: ImageView
    private lateinit var btnSave: Button
    private lateinit var database: AppDatabase

    private var isRecording = false
    private var recordingSeconds = 0
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var voiceNotePath: String? = null
    private var photoPath: String? = null
    private var videoPath: String? = null
    private var pendingPhotoPath: String? = null
    private var pendingVideoPath: String? = null
    private var isPlayingPreview = false
    private var pendingVoiceNoteLoaded = false
    /** Tracks whether background recording was active when we last polled,
     *  so we can detect the transition and auto-load the result. */
    private var wasBackgroundRecording = false
    private val recordingHandler = Handler(Looper.getMainLooper())
    private val playbackWaveformHandler = Handler(Looper.getMainLooper())
    private val autoRefreshHandler = Handler(Looper.getMainLooper())

    private enum class PendingCaptureAction {
        NONE,
        PHOTO,
        VIDEO
    }

    private var pendingCaptureAction: PendingCaptureAction = PendingCaptureAction.NONE

    companion object {
        private const val RECORD_AUDIO_PERMISSION_REQUEST_CODE = 2001
        private const val CAMERA_PERMISSION_REQUEST_CODE = 2002
        const val EXTRA_START_RECORDING = "extra_start_recording"

        private const val DRAFT_PREFS = "incident_draft"
        private const val KEY_DRAFT_DESC = "draft_description"
        private const val KEY_DRAFT_LOC = "draft_location"
        private const val KEY_DRAFT_WHO = "draft_who"
        private const val KEY_DRAFT_TYPE = "draft_type_index"
        private const val KEY_DRAFT_SEV = "draft_severity_index"
        private const val KEY_DRAFT_PHOTO = "draft_photo_path"
        private const val KEY_DRAFT_VIDEO = "draft_video_path"
    }

    private val takePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && !pendingPhotoPath.isNullOrBlank()) {
                photoPath = pendingPhotoPath
                Toast.makeText(this, getString(R.string.photo_attached), Toast.LENGTH_SHORT).show()
            } else {
                pendingPhotoPath?.let { deleteFileSafely(it) }
                Toast.makeText(this, getString(R.string.photo_capture_failed), Toast.LENGTH_SHORT).show()
            }
            pendingPhotoPath = null
            updateEvidenceStatus()
        }

    private val captureVideoLauncher =
        registerForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
            if (success && !pendingVideoPath.isNullOrBlank()) {
                videoPath = pendingVideoPath
                Toast.makeText(this, getString(R.string.video_attached), Toast.LENGTH_SHORT).show()
            } else {
                pendingVideoPath?.let { deleteFileSafely(it) }
                Toast.makeText(this, getString(R.string.video_capture_failed), Toast.LENGTH_SHORT).show()
            }
            pendingVideoPath = null
            updateEvidenceStatus()
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

        BottomNavHelper.setup(this, NavTab.LOG)
        initializeViews()
        setupListeners()
        setupChipSelection(incidentTypeChips)
        setupChipSelection(severityChips)
        updateVoiceActionButtons()
        updateEvidenceStatus()

        if (intent.getBooleanExtra(EXTRA_START_RECORDING, false)) {
            startRecording()
        }

        // Load pending voice note from background recording (earbud double-tap)
        loadPendingVoiceNote()

        // Restore draft form fields from a previous session
        loadDraft()

        // Show indicator if background recording is currently in progress
        if (ProtectionForegroundService.isBackgroundRecording) {
            tvRecordLabel.text = "Background Recording Active"
            tvRecordStatus.text = "Background recording active — double-tap to stop"
        }
    }

    override fun onResume() {
        super.onResume()
        wasBackgroundRecording = ProtectionForegroundService.isBackgroundRecording
        startAutoRefresh()
    }

    override fun onPause() {
        super.onPause()
        stopAutoRefresh()
        saveDraft()
    }

    // ------------------------------------------------------------------
    // Auto-refresh: polls for background recording completion
    // ------------------------------------------------------------------

    private val autoRefreshRunnable = object : Runnable {
        override fun run() {
            val stillRecording = ProtectionForegroundService.isBackgroundRecording
            val justStopped = wasBackgroundRecording && !stillRecording

            // Check for a new pending voice note (recording just stopped or appeared)
            val pendingPath = ProtectionPrefs.getPendingVoiceNotePath(this@LogIncidentActivity)
            if (pendingPath != null && pendingPath != voiceNotePath && !pendingVoiceNoteLoaded) {
                loadPendingVoiceNote()
            }

            // Update the "Background Recording Active" indicator
            if (stillRecording) {
                tvRecordLabel.text = "Background Recording Active"
                tvRecordStatus.text = "Background recording active — double-tap to stop"
            } else if (justStopped && voiceNotePath == null) {
                // Recording stopped but nothing loaded yet — check one more time
                loadPendingVoiceNote()
            }

            wasBackgroundRecording = stillRecording
            autoRefreshHandler.postDelayed(this, 1000)
        }
    }

    private fun startAutoRefresh() {
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable)
        autoRefreshHandler.post(autoRefreshRunnable)
    }

    private fun stopAutoRefresh() {
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable)
    }

    private fun loadPendingVoiceNote() {
        if (pendingVoiceNoteLoaded) return
        val pendingPath = ProtectionPrefs.getPendingVoiceNotePath(this) ?: return
        if (!File(pendingPath).exists()) {
            ProtectionPrefs.clearPendingVoiceNote(this)
            return
        }
        pendingVoiceNoteLoaded = true
        val pendingDuration = ProtectionPrefs.getPendingVoiceNoteDuration(this)
        // Don't clear prefs here — defer to save or delete so the draft
        // survives if the user exits without saving.

        // Delete any previous unsaved recording
        voiceNotePath?.let { deleteAudioFile(it) }
        voiceNotePath = pendingPath
        recordingSeconds = pendingDuration

        tvRecordLabel.text = getString(R.string.voice_note_saved)
        tvRecordStatus.text = getString(R.string.encrypted_duration, formatDuration(recordingSeconds))
        updateVoiceActionButtons()
        Toast.makeText(this, getString(R.string.voice_note_recorded, formatDuration(recordingSeconds)), Toast.LENGTH_SHORT).show()
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
        btnTakePhoto = findViewById(R.id.btn_take_photo)
        btnTakeVideo = findViewById(R.id.btn_take_video)
        btnPreviewVideo = findViewById(R.id.btn_preview_video)
        btnDeletePhoto = findViewById(R.id.btn_delete_photo)
        btnDeleteVideo = findViewById(R.id.btn_delete_video)
        tvPhotoStatus = findViewById(R.id.tv_photo_status)
        tvVideoStatus = findViewById(R.id.tv_video_status)
        ivPhotoPreview = findViewById(R.id.iv_photo_preview)
        btnSave = findViewById(R.id.btn_save_incident)

        etDescription.gravity = Gravity.TOP or Gravity.START
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            if (isRecording) {
                stopRecording(showToast = false)
            }
            if (hasUnsavedData()) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Discard draft?")
                    .setMessage("You have unsaved data. Discard it?")
                    .setPositiveButton("Discard") { _, _ ->
                        clearDraft()
                        finish()
                        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_left)
                    }
                    .setNegativeButton("Keep editing", null)
                    .show()
            } else {
                finish()
                overridePendingTransition(R.anim.fade_in, R.anim.slide_out_left)
            }
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

        btnTakePhoto.setOnClickListener {
            if (hasCameraPermission()) {
                launchPhotoCapture()
            } else {
                pendingCaptureAction = PendingCaptureAction.PHOTO
                requestCameraPermission()
            }
        }

        btnTakeVideo.setOnClickListener {
            if (hasCameraPermission()) {
                launchVideoCapture()
            } else {
                pendingCaptureAction = PendingCaptureAction.VIDEO
                requestCameraPermission()
            }
        }

        btnDeletePhoto.setOnClickListener {
            deletePhotoEvidence()
        }

        btnDeleteVideo.setOnClickListener {
            deleteVideoEvidence()
        }

        ivPhotoPreview.setOnClickListener {
            openPhotoPreview()
        }

        btnPreviewVideo.setOnClickListener {
            openVideoPreview()
        }

        btnSave.setOnClickListener {
            saveIncident()
        }
    }

    private fun launchPhotoCapture() {
        try {
            photoPath?.let { deleteFileSafely(it) }
            val outputFile = createMediaOutputFile(Environment.DIRECTORY_PICTURES, "photos", "jpg")
            val photoUri = toFileProviderUri(outputFile)
            pendingPhotoPath = outputFile.absolutePath
            takePhotoLauncher.launch(photoUri)
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.photo_capture_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchVideoCapture() {
        try {
            videoPath?.let { deleteFileSafely(it) }
            val outputFile = createMediaOutputFile(Environment.DIRECTORY_MOVIES, "videos", "mp4")
            val videoUri = toFileProviderUri(outputFile)
            pendingVideoPath = outputFile.absolutePath
            captureVideoLauncher.launch(videoUri)
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.video_capture_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun createMediaOutputFile(directoryType: String, folderName: String, extension: String): File {
        val baseDir = getExternalFilesDir(directoryType) ?: filesDir
        val targetDir = File(baseDir, "SafeWay/$folderName")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val prefix = if (extension.equals("jpg", ignoreCase = true)) "photo" else "video"
        return File(targetDir, "${prefix}_${System.currentTimeMillis()}.$extension")
    }

    private fun toFileProviderUri(file: File): Uri {
        return FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
    }

    private fun updateEvidenceStatus() {
        val photoExists = !photoPath.isNullOrBlank() && File(photoPath!!).exists()
        val videoExists = !videoPath.isNullOrBlank() && File(videoPath!!).exists()

        if (!photoExists) {
            photoPath = null
        }
        if (!videoExists) {
            videoPath = null
        }

        tvPhotoStatus.text = if (photoExists) {
            getString(R.string.photo_attached)
        } else {
            getString(R.string.no_photo_attached)
        }

        tvVideoStatus.text = if (videoExists) {
            getString(R.string.video_attached)
        } else {
            getString(R.string.no_video_attached)
        }

        if (photoExists) {
            ivPhotoPreview.visibility = View.VISIBLE
            ivPhotoPreview.setImageURI(Uri.fromFile(File(photoPath!!)))
            btnDeletePhoto.visibility = View.VISIBLE
        } else {
            ivPhotoPreview.visibility = View.GONE
            ivPhotoPreview.setImageDrawable(null)
            btnDeletePhoto.visibility = View.GONE
        }

        btnPreviewVideo.visibility = if (videoExists) View.VISIBLE else View.GONE
        btnDeleteVideo.visibility = if (videoExists) View.VISIBLE else View.GONE
    }

    private fun deletePhotoEvidence() {
        val path = photoPath
        if (!path.isNullOrBlank()) {
            deleteFileSafely(path)
        }
        photoPath = null
        updateEvidenceStatus()
        Toast.makeText(this, getString(R.string.photo_deleted), Toast.LENGTH_SHORT).show()
    }

    private fun deleteVideoEvidence() {
        val path = videoPath
        if (!path.isNullOrBlank()) {
            deleteFileSafely(path)
        }
        videoPath = null
        updateEvidenceStatus()
        Toast.makeText(this, getString(R.string.video_deleted), Toast.LENGTH_SHORT).show()
    }

    private fun openPhotoPreview() {
        val path = photoPath ?: return
        val file = File(path)
        if (!file.exists()) {
            photoPath = null
            updateEvidenceStatus()
            return
        }

        val uri = toFileProviderUri(file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.unable_to_open_media), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openVideoPreview() {
        val path = videoPath ?: return
        val file = File(path)
        if (!file.exists()) {
            videoPath = null
            updateEvidenceStatus()
            return
        }

        val uri = toFileProviderUri(file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.unable_to_open_media), Toast.LENGTH_SHORT).show()
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
        if (pendingVoiceNoteLoaded) {
            ProtectionPrefs.clearPendingVoiceNote(this)
            pendingVoiceNoteLoaded = false
        }
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
            waveformPlaybackSeed = 0
            tvRecordTime.text = getString(R.string.record_time_default)
            playbackWaveformHandler.post(playbackWaveformRunnable)
            playbackWaveformHandler.post(previewTimerRunnable)
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
        playbackWaveformHandler.removeCallbacks(playbackWaveformRunnable)
        playbackWaveformHandler.removeCallbacks(previewTimerRunnable)
        waveformPlaybackSeed = 0
        updatePreviewButtonUi(false)
        tvRecordTime.text = getString(R.string.record_time_default)
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
                if (!isRecording) return

                recordingSeconds++
                tvRecordTime.text = formatDuration(recordingSeconds)

                val amp = getAmplitude()
                // Normalize amplitude (typically 0-32767) to bar height (4-28dp)
                val normalizedAmp = if (amp > 0) {
                    (amp.toFloat() / 32767f * 24f + 4f).toInt().coerceIn(4, 28)
                } else {
                    (4..12).random()
                }

                val bar = View(this@LogIncidentActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(4.dpToPx(), normalizedAmp.dpToPx()).apply {
                        setMargins(2, 0, 2, 0)
                        gravity = Gravity.BOTTOM
                    }
                    setBackgroundColor(getColor(R.color.highlight_accent))
                    alpha = 0.7f + (normalizedAmp.toFloat() / 28f) * 0.3f
                }
                llWaveform.addView(bar)

                if (llWaveform.childCount > 80) {
                    llWaveform.removeViewAt(0)
                }

                recordingHandler.postDelayed(this, 200)
            }
        })
    }

    private var waveformPlaybackSeed = 0

    private val previewTimerRunnable = object : Runnable {
        override fun run() {
            if (!isPlayingPreview) return
            val player = mediaPlayer ?: return
            if (!player.isPlaying) return
            val elapsedMs = player.currentPosition.coerceAtLeast(0)
            tvRecordTime.text = formatDuration(elapsedMs / 1000)
            playbackWaveformHandler.postDelayed(this, 200)
        }
    }

    private val playbackWaveformRunnable = object : Runnable {
        override fun run() {
            if (!isPlayingPreview) return
            val player = mediaPlayer ?: return
            if (!player.isPlaying) return

            val progress = player.currentPosition.coerceAtLeast(0)
            val duration = player.duration.coerceAtLeast(1)
            val bars = generatePlaybackWaveform(waveformPlaybackSeed, duration)

            llWaveform.removeAllViews()
            val density = this@LogIncidentActivity.resources.displayMetrics.density
            for (heightDp in bars) {
                val bar = View(this@LogIncidentActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        (4 * density).toInt(),
                        (heightDp * density).toInt()
                    ).apply {
                        setMargins(2, 0, 2, 0)
                        gravity = Gravity.BOTTOM
                    }
                    setBackgroundColor(getColor(R.color.highlight_accent))
                    alpha = 0.5f + (heightDp.toFloat() / 32f) * 0.5f
                }
                llWaveform.addView(bar)
            }

            waveformPlaybackSeed++
            playbackWaveformHandler.postDelayed(this, 200)
        }
    }

    private fun generatePlaybackWaveform(seed: Int, totalDurationMs: Int): List<Int> {
        val bars = mutableListOf<Int>()
        val barCount = 35
        for (i in 0 until barCount) {
            val pos = (i.toFloat() / barCount) * 100f
            val wave = (Math.sin((pos + seed * 4) * 0.08) * 0.5 + 0.5)
            val wave2 = (Math.sin((pos + seed * 2) * 0.15) * 0.3)
            val noise = (Math.sin((i * 137.0 + seed * 73.0)) * 0.15 + 0.15)
            val height = ((wave + wave2 + noise) * 12f + 4f).toInt().coerceIn(4, 32)
            bars.add(height)
        }
        return bars
    }

    // ------------------------------------------------------------------
    // Draft persistence — saves/restores form state across sessions
    // ------------------------------------------------------------------

    private fun saveDraft() {
        val prefs = getSharedPreferences(DRAFT_PREFS, MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_DRAFT_DESC, etDescription.text.toString())
            .putString(KEY_DRAFT_LOC, etLocation.text.toString())
            .putString(KEY_DRAFT_WHO, etWho.text.toString())
            .putInt(KEY_DRAFT_TYPE, incidentTypeChips.indexOfFirst { it.isChecked })
            .putInt(KEY_DRAFT_SEV, severityChips.indexOfFirst { it.isChecked })
            .putString(KEY_DRAFT_PHOTO, photoPath)
            .putString(KEY_DRAFT_VIDEO, videoPath)
            .apply()
    }

    private fun loadDraft() {
        // Don't restore if this is a fresh activity with no saved draft
        val prefs = getSharedPreferences(DRAFT_PREFS, MODE_PRIVATE)
        if (!prefs.contains(KEY_DRAFT_DESC) && !prefs.contains(KEY_DRAFT_PHOTO)) return

        // Text fields
        prefs.getString(KEY_DRAFT_DESC, "")?.let { etDescription.setText(it) }
        prefs.getString(KEY_DRAFT_LOC, "")?.let { etLocation.setText(it) }
        prefs.getString(KEY_DRAFT_WHO, "")?.let { etWho.setText(it) }

        // Chip selections
        val typeIdx = prefs.getInt(KEY_DRAFT_TYPE, -1)
        if (typeIdx in incidentTypeChips.indices) {
            incidentTypeChips[typeIdx].isChecked = true
        }
        val sevIdx = prefs.getInt(KEY_DRAFT_SEV, -1)
        if (sevIdx in severityChips.indices) {
            severityChips[sevIdx].isChecked = true
        }

        // Photo and video — only restore if file still exists
        val savedPhoto = prefs.getString(KEY_DRAFT_PHOTO, null)
        if (!savedPhoto.isNullOrBlank() && File(savedPhoto).exists()) {
            photoPath = savedPhoto
        }
        val savedVideo = prefs.getString(KEY_DRAFT_VIDEO, null)
        if (!savedVideo.isNullOrBlank() && File(savedVideo).exists()) {
            videoPath = savedVideo
        }
        updateEvidenceStatus()
    }

    private fun clearDraft() {
        getSharedPreferences(DRAFT_PREFS, MODE_PRIVATE).edit().clear().apply()
    }

    private fun hasUnsavedData(): Boolean {
        if (etDescription.text.isNotBlank() || etLocation.text.isNotBlank() || etWho.text.isNotBlank()) return true
        if (incidentTypeChips.any { it.isChecked } || severityChips.any { it.isChecked }) return true
        if (!voiceNotePath.isNullOrBlank() || !photoPath.isNullOrBlank() || !videoPath.isNullOrBlank()) return true
        return false
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
            type = EncryptionManager.encrypt(selectedType),
            description = EncryptionManager.encrypt(description),
            severity = EncryptionManager.encrypt(selectedSeverity),
            location = EncryptionManager.encrypt(location),
            who = EncryptionManager.encrypt(who.ifEmpty { getString(R.string.who_unknown) }),
            hasVoiceNote = !voiceNotePath.isNullOrBlank(),
            voiceNotePath = voiceNotePath,
            voiceDurationSec = recordingSeconds,
            photoPath = photoPath,
            videoPath = videoPath,
            createdAtMillis = System.currentTimeMillis()
        )

        btnSave.text = getString(R.string.saved_to_journal)
        btnSave.isEnabled = false

        lifecycleScope.launch {
            try {
                database.incidentDao().insertIncident(incident)
                if (pendingVoiceNoteLoaded) {
                    ProtectionPrefs.clearPendingVoiceNote(this@LogIncidentActivity)
                    pendingVoiceNoteLoaded = false
                }
                clearDraft()
                val successMessage = if (incident.hasVoiceNote) {
                    getString(R.string.incident_saved_with_voice_note)
                } else {
                    getString(R.string.incident_saved_success)
                }
                Toast.makeText(this@LogIncidentActivity, successMessage, Toast.LENGTH_SHORT).show()
                val destination = ResourceRecommendationEngine.primaryDestinationFor(selectedType)
                openSuggestedResource(destination)
                finish()
            } catch (_: Exception) {
                btnSave.text = getString(R.string.save_to_journal)
                btnSave.isEnabled = true
                Toast.makeText(this@LogIncidentActivity, getString(R.string.incident_save_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openSuggestedResource(destination: ResourceDestination) {
        val targetActivity = when (destination) {
            ResourceDestination.UNDERSTAND_ABUSE -> UnderstandAbuseActivity::class.java
            ResourceDestination.EVIDENCE_GUIDE -> EvidenceGuideActivity::class.java
            ResourceDestination.FIND_HELP_NEARBY -> FindHelpNearYouActivity::class.java
            ResourceDestination.BREAK_STIGMA -> BreakingStigmaActivity::class.java
        }
        startActivity(Intent(this, targetActivity))
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

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
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
        } else if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                when (pendingCaptureAction) {
                    PendingCaptureAction.PHOTO -> launchPhotoCapture()
                    PendingCaptureAction.VIDEO -> launchVideoCapture()
                    PendingCaptureAction.NONE -> Unit
                }
            } else {
                Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_LONG).show()
            }
            pendingCaptureAction = PendingCaptureAction.NONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoRefresh()
        recordingHandler.removeCallbacksAndMessages(null)
        playbackWaveformHandler.removeCallbacks(playbackWaveformRunnable)
        playbackWaveformHandler.removeCallbacks(previewTimerRunnable)
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

    private fun deleteFileSafely(path: String): Boolean {
        return try {
            val file = File(path)
            file.exists() && file.delete()
        } catch (_: Exception) {
            false
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun getAmplitude(): Int {
        return try {
            mediaRecorder?.maxAmplitude?.coerceAtLeast(0) ?: 0
        } catch (_: Exception) {
            0
        }
    }
}

