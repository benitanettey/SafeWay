package com.example.safeway

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.View
import android.widget.LinearLayout
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.example.safeway.data.AppDatabase
import com.example.safeway.data.Incident
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import android.graphics.Paint
import android.graphics.pdf.PdfDocument

class RecordsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var btnExportCsv: Button
    private lateinit var btnExportPdf: Button
    private lateinit var btnExportEncrypted: Button
    private lateinit var recordsRecycler: RecyclerView
    private lateinit var btnLoadMore: Button
    private lateinit var tvEmptyRecords: TextView
    private lateinit var tvAlertBanner: TextView
    private lateinit var etSearchRecords: EditText
    private lateinit var spFilterSeverity: Spinner
    private lateinit var spFilterVoice: Spinner
    private lateinit var spSortRecords: Spinner
    private lateinit var database: AppDatabase
    private var currentIncidents: List<Incident> = emptyList()
    private var visibleIncidents: List<Incident> = emptyList()
    private var pagedVisibleIncidents: List<Incident> = emptyList()
    private lateinit var adapter: RecordsAdapter
    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingPath: String? = null
    private var currentlyPlayingIncidentId: Int? = null
    private var isPlaybackRunning: Boolean = false
    private var pausedPositionMs: Int = 0
    private val playbackUiHandler = Handler(Looper.getMainLooper())

    // Detail dialog state
    private var detailDialog: Dialog? = null
    private var waveformPlaybackSeed = 0
    private var isDetailDialogPlaying = false

    private val playbackUiRunnable = object : Runnable {
        override fun run() {
            if (!isPlaybackRunning) return

            val player = mediaPlayer
            val incidentId = currentlyPlayingIncidentId
            if (player != null && incidentId != null) {
                val durationMs = player.duration.coerceAtLeast(1)
                val positionMs = player.currentPosition.coerceAtLeast(0)
                val progress = ((positionMs * 100f) / durationMs).toInt().coerceIn(0, 100)
                val label = formatDurationFromMillis(positionMs)
                val durationLabel = formatDurationFromMillis(durationMs)

                val severity = currentIncidents.find { it.id == incidentId }?.severity ?: "Medium"
                val bars = generateWaveformBars(progress, severity)

                adapter.setPlaybackState(incidentId, true, progress, label, durationLabel)
                adapter.updatePlaybackWaveform(bars)

                // Update detail dialog waveform if showing
                if (detailDialog?.isShowing == true && isDetailDialogPlaying) {
                    updateDetailDialogWaveform(bars)
                    updateDetailDialogPlayButton(true)
                }

                waveformPlaybackSeed++
                playbackUiHandler.postDelayed(this, 200)
            }
        }
    }

    private fun generateWaveformBars(progressSeed: Int, severity: String): List<Int> {
        val bars = mutableListOf<Int>()
        val barCount = 35
        val baseHeight = when {
            severity.equals("Low", true) -> 10
            severity.equals("Crisis", true) -> 18
            else -> 14
        }
        for (i in 0 until barCount) {
            val pos = (i.toFloat() / barCount) * 100f
            val wave = (Math.sin((pos + progressSeed * 4) * 0.08) * 0.5 + 0.5)
            val wave2 = (Math.sin((pos + progressSeed * 2) * 0.15) * 0.3)
            val noise = (Math.sin((i * 137.0 + progressSeed * 73.0)) * 0.15 + 0.15)
            val height = ((wave + wave2 + noise) * baseHeight + 4f).toInt().coerceIn(4, 32)
            bars.add(height)
        }
        return bars
    }

    private fun updateDetailDialogWaveform(bars: List<Int>) {
        val dialog = detailDialog ?: return
        val waveformContainer = dialog.findViewById<LinearLayout>(R.id.ll_detail_waveform) ?: return
        waveformContainer.removeAllViews()
        val density = resources.displayMetrics.density
        for (heightDp in bars) {
            val bar = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (4 * density).toInt(),
                    (heightDp * density).toInt()
                ).apply {
                    setMargins(2, 0, 2, 0)
                    gravity = android.view.Gravity.BOTTOM
                }
                setBackgroundColor(getColor(R.color.highlight_accent))
                alpha = 0.5f + (heightDp.toFloat() / 32f) * 0.5f
            }
            waveformContainer.addView(bar)
        }
    }

    private fun updateDetailDialogPlayButton(isPlaying: Boolean) {
        val dialog = detailDialog ?: return
        val btnPlay = dialog.findViewById<Button>(R.id.btn_detail_play) ?: return
        val tvTime = dialog.findViewById<TextView>(R.id.tv_detail_playback_time) ?: return
        btnPlay.text = if (isPlaying) getString(R.string.pause) else getString(R.string.play)
        if (isPlaying && mediaPlayer != null) {
            tvTime.text = "${formatDurationFromMillis(mediaPlayer!!.currentPosition)} / ${formatDurationFromMillis(mediaPlayer!!.duration)}"
        }
    }

    private val pageSize = 20
    private var currentPage = 1

    private enum class SeverityFilter { ALL, LOW, MEDIUM, HIGH, CRISIS }
    private enum class VoiceFilter { ALL, WITH_NOTE, WITHOUT_NOTE }
    private enum class SortOrder { NEWEST, OLDEST }

    private var selectedSeverityFilter = SeverityFilter.ALL
    private var selectedVoiceFilter = VoiceFilter.ALL
    private var selectedSortOrder = SortOrder.NEWEST

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_records)
        database = AppDatabase.getDatabase(this)

        BottomNavHelper.setup(this, NavTab.RECORDS)
        initializeViews()
        setupRecycler()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        loadIncidents()
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btn_back_records)
        btnExportCsv = findViewById(R.id.btn_export_csv)
        btnExportPdf = findViewById(R.id.btn_export_pdf)
        btnExportEncrypted = findViewById(R.id.btn_export_encrypted)
        recordsRecycler = findViewById(R.id.rv_records)
        btnLoadMore = findViewById(R.id.btn_load_more)
        tvEmptyRecords = findViewById(R.id.tv_empty_records)
        tvAlertBanner = findViewById(R.id.tv_alert_banner)
        etSearchRecords = findViewById(R.id.et_search_records)
        spFilterSeverity = findViewById(R.id.sp_filter_severity)
        spFilterVoice = findViewById(R.id.sp_filter_voice)
        spSortRecords = findViewById(R.id.sp_sort_records)
    }

    private fun setupRecycler() {
        adapter = RecordsAdapter(
            onCardClick = { showRecordDetailDialog(it) },
            onDetailsClick = { showRecordDetailDialog(it) },
            onVoicePlayClick = { toggleVoicePlayback(it) }
        )
        recordsRecycler.layoutManager = LinearLayoutManager(this)
        recordsRecycler.adapter = adapter
        adapter.setPlaybackState(null, false, 0, null, null)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.slide_out_left)
        }

        btnExportCsv.setOnClickListener { exportAndShareRecords(ExportType.CSV) }
        btnExportPdf.setOnClickListener { exportAndShareRecords(ExportType.PDF) }
        btnExportEncrypted.setOnClickListener { exportAndShareRecords(ExportType.ENCRYPTED) }

        btnLoadMore.setOnClickListener {
            currentPage++
            renderCurrentPage()
        }

        setupFilterControls()

        etSearchRecords.addTextChangedListener {
            applyFiltersAndRender()
        }
    }

    private fun setupFilterControls() {
        setupSpinner(
            spinner = spFilterSeverity,
            entries = listOf(
                getString(R.string.filter_all_severity),
                getString(R.string.filter_severity_low),
                getString(R.string.filter_severity_medium),
                getString(R.string.filter_severity_high),
                getString(R.string.filter_severity_crisis)
            )
        ) { position ->
            selectedSeverityFilter = when (position) {
                1 -> SeverityFilter.LOW
                2 -> SeverityFilter.MEDIUM
                3 -> SeverityFilter.HIGH
                4 -> SeverityFilter.CRISIS
                else -> SeverityFilter.ALL
            }
            applyFiltersAndRender()
        }

        setupSpinner(
            spinner = spFilterVoice,
            entries = listOf(
                getString(R.string.filter_voice_all),
                getString(R.string.filter_voice_with),
                getString(R.string.filter_voice_without)
            )
        ) { position ->
            selectedVoiceFilter = when (position) {
                1 -> VoiceFilter.WITH_NOTE
                2 -> VoiceFilter.WITHOUT_NOTE
                else -> VoiceFilter.ALL
            }
            applyFiltersAndRender()
        }

        setupSpinner(
            spinner = spSortRecords,
            entries = listOf(
                getString(R.string.sort_newest_first),
                getString(R.string.sort_oldest_first)
            )
        ) { position ->
            selectedSortOrder = if (position == 1) SortOrder.OLDEST else SortOrder.NEWEST
            applyFiltersAndRender()
        }
    }

    private fun setupSpinner(spinner: Spinner, entries: List<String>, onSelect: (Int) -> Unit) {
        val adapter = ArrayAdapter(this, R.layout.spinner_item, entries).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                onSelect(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // No-op
            }
        }
    }

    private fun loadIncidents() {
        lifecycleScope.launch {
            val encrypted = database.incidentDao().getAllIncidents()
            currentIncidents = encrypted.map { decryptIncident(it) }
            applyFiltersAndRender()
        }
    }

    private fun decryptIncident(incident: Incident): Incident {
        return incident.copy(
            type = EncryptionManager.decrypt(incident.type),
            description = EncryptionManager.decrypt(incident.description),
            severity = EncryptionManager.decrypt(incident.severity),
            location = EncryptionManager.decrypt(incident.location),
            who = EncryptionManager.decrypt(incident.who)
        )
    }

    private fun encryptIncident(incident: Incident): Incident {
        return incident.copy(
            type = EncryptionManager.encrypt(incident.type),
            description = EncryptionManager.encrypt(incident.description),
            severity = EncryptionManager.encrypt(incident.severity),
            location = EncryptionManager.encrypt(incident.location),
            who = EncryptionManager.encrypt(incident.who)
        )
    }

    private fun applyFiltersAndRender() {
        val query = etSearchRecords.text?.toString()?.trim().orEmpty()

        val filtered = currentIncidents
            .asSequence()
            .filter { incident ->
                if (query.isBlank()) true else {
                    incident.type.contains(query, ignoreCase = true) ||
                        incident.description.contains(query, ignoreCase = true) ||
                        incident.location.contains(query, ignoreCase = true) ||
                        incident.who.contains(query, ignoreCase = true) ||
                        incident.severity.contains(query, ignoreCase = true)
                }
            }
            .filter { incident ->
                when (selectedSeverityFilter) {
                    SeverityFilter.ALL -> true
                    SeverityFilter.LOW -> incident.severity.equals("Low", ignoreCase = true)
                    SeverityFilter.MEDIUM -> incident.severity.equals("Medium", ignoreCase = true)
                    SeverityFilter.HIGH -> incident.severity.equals("High", ignoreCase = true)
                    SeverityFilter.CRISIS -> incident.severity.equals("Crisis", ignoreCase = true)
                }
            }
            .filter { incident ->
                when (selectedVoiceFilter) {
                    VoiceFilter.ALL -> true
                    VoiceFilter.WITH_NOTE -> incident.hasVoiceNote
                    VoiceFilter.WITHOUT_NOTE -> !incident.hasVoiceNote
                }
            }
            .toList()
            .let { incidents ->
                when (selectedSortOrder) {
                    SortOrder.NEWEST -> incidents.sortedByDescending { it.createdAtMillis }
                    SortOrder.OLDEST -> incidents.sortedBy { it.createdAtMillis }
                }
            }

        visibleIncidents = filtered
        currentPage = 1

        val hasActiveFilter = query.isNotBlank() ||
            selectedSeverityFilter != SeverityFilter.ALL ||
            selectedVoiceFilter != VoiceFilter.ALL

        renderIncidents(filtered, hasActiveFilter)
    }

    private fun renderIncidents(incidents: List<Incident>, hasActiveFilter: Boolean) {
        tvEmptyRecords.visibility = if (incidents.isEmpty()) View.VISIBLE else View.GONE
        tvEmptyRecords.text = if (hasActiveFilter) {
            getString(R.string.no_records_match_filters)
        } else {
            getString(R.string.no_records_yet)
        }

        if (incidents.isNotEmpty()) {
            val latestTime = formatTimestamp(incidents.maxOf { it.createdAtMillis })
            tvAlertBanner.text = getString(R.string.recent_alert_message, latestTime, incidents.size)
        } else {
            tvAlertBanner.text = getString(R.string.private_records_title)
        }

        renderCurrentPage()
    }

    private fun renderCurrentPage() {
        val targetCount = (currentPage * pageSize).coerceAtMost(visibleIncidents.size)
        pagedVisibleIncidents = visibleIncidents.take(targetCount)
        adapter.submitItems(pagedVisibleIncidents)
        btnLoadMore.visibility = if (visibleIncidents.size > pagedVisibleIncidents.size) View.VISIBLE else View.GONE
    }

    private fun showRecordDetailDialog(incident: Incident) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_record_detail)
        dialog.window?.setBackgroundDrawableResource(R.color.primary_background)
        detailDialog = dialog
        isDetailDialogPlaying = isPlaybackRunning && currentlyPlayingIncidentId == incident.id

        val etType = dialog.findViewById<EditText>(R.id.et_detail_type)
        val etDescription = dialog.findViewById<EditText>(R.id.et_detail_description)
        val etSeverity = dialog.findViewById<EditText>(R.id.et_detail_severity)
        val etLocation = dialog.findViewById<EditText>(R.id.et_detail_location)
        val etWho = dialog.findViewById<EditText>(R.id.et_detail_who)
        val btnPhoto = dialog.findViewById<Button>(R.id.btn_detail_photo)
        val btnVideo = dialog.findViewById<Button>(R.id.btn_detail_video)
        val btnPlay = dialog.findViewById<Button>(R.id.btn_detail_play)
        val tvPlaybackTime = dialog.findViewById<TextView>(R.id.tv_detail_playback_time)
        val waveformContainer = dialog.findViewById<LinearLayout>(R.id.ll_detail_waveform)
        val btnSave = dialog.findViewById<Button>(R.id.btn_detail_save)
        val btnDelete = dialog.findViewById<Button>(R.id.btn_detail_delete)

        etType.setText(incident.type)
        etDescription.setText(incident.description)
        etSeverity.setText(incident.severity)
        etLocation.setText(incident.location)
        etWho.setText(incident.who)

        val hasPhoto = !incident.photoPath.isNullOrBlank() && File(incident.photoPath).exists()
        val hasVideo = !incident.videoPath.isNullOrBlank() && File(incident.videoPath).exists()
        val canPlay = incident.hasVoiceNote && !incident.voiceNotePath.isNullOrBlank() && File(incident.voiceNotePath).exists()

        btnPhoto.isEnabled = hasPhoto
        btnVideo.isEnabled = hasVideo

        if (canPlay) {
            btnPlay.visibility = View.VISIBLE
            tvPlaybackTime.visibility = View.VISIBLE
            btnPlay.text = if (isDetailDialogPlaying) getString(R.string.pause) else getString(R.string.play)
            if (isDetailDialogPlaying && mediaPlayer != null) {
                tvPlaybackTime.text = "${formatDurationFromMillis(mediaPlayer!!.currentPosition)} / ${formatDurationFromMillis(mediaPlayer!!.duration)}"
                if (waveformContainer != null) {
                    waveformContainer.visibility = View.VISIBLE
                    val seed = (incident.id ?: 0) * 100 + waveformPlaybackSeed
                    val bars = generateWaveformBars(seed, incident.severity)
                    updateDetailDialogWaveform(bars)
                }
            } else {
                tvPlaybackTime.text = formatDurationFromMillis(incident.voiceDurationSec * 1000)
            }
        } else {
            btnPlay.visibility = View.GONE
            tvPlaybackTime.visibility = View.GONE
        }

        btnPlay.setOnClickListener {
            if (canPlay) {
                toggleVoicePlayback(incident)
                isDetailDialogPlaying = isPlaybackRunning && currentlyPlayingIncidentId == incident.id
                btnPlay.text = if (isDetailDialogPlaying) getString(R.string.pause) else getString(R.string.play)
                if (isDetailDialogPlaying) {
                    waveformContainer?.visibility = View.VISIBLE
                } else {
                    waveformContainer?.visibility = View.GONE
                }
            }
        }

        btnPhoto.setOnClickListener {
            openMediaFromPath(incident.photoPath, "image/*")
        }

        btnVideo.setOnClickListener {
            openMediaFromPath(incident.videoPath, "video/*")
        }

        btnSave.setOnClickListener {
            val updated = incident.copy(
                type = EncryptionManager.encrypt(etType.text.toString().trim().ifEmpty { EncryptionManager.decrypt(incident.type) }),
                description = EncryptionManager.encrypt(etDescription.text.toString().trim().ifEmpty { EncryptionManager.decrypt(incident.description) }),
                severity = EncryptionManager.encrypt(etSeverity.text.toString().trim().ifEmpty { EncryptionManager.decrypt(incident.severity) }),
                location = EncryptionManager.encrypt(etLocation.text.toString().trim().ifEmpty { EncryptionManager.decrypt(incident.location) }),
                who = EncryptionManager.encrypt(etWho.text.toString().trim().ifEmpty { EncryptionManager.decrypt(incident.who) })
            )

            lifecycleScope.launch {
                database.incidentDao().updateIncident(updated)
                Toast.makeText(this@RecordsActivity, getString(R.string.record_updated), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                loadIncidents()
            }
        }

        btnDelete.setOnClickListener {
            lifecycleScope.launch {
                database.incidentDao().deleteIncident(incident)
                incident.voiceNotePath?.let { path ->
                    File(path).takeIf { it.exists() }?.delete()
                }
                incident.photoPath?.let { path ->
                    File(path).takeIf { it.exists() }?.delete()
                }
                incident.videoPath?.let { path ->
                    File(path).takeIf { it.exists() }?.delete()
                }
                Toast.makeText(this@RecordsActivity, getString(R.string.record_deleted), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                loadIncidents()
            }
        }

        dialog.setOnDismissListener {
            detailDialog = null
            isDetailDialogPlaying = false
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun toggleVoicePlayback(incident: Incident) {
        val path = incident.voiceNotePath
        if (!incident.hasVoiceNote || path.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.no_voice_note_to_preview), Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, getString(R.string.no_voice_note_to_preview), Toast.LENGTH_SHORT).show()
            return
        }

        if (currentlyPlayingPath == path) {
            val player = mediaPlayer
            if (player != null && player.isPlaying) {
                pausedPositionMs = player.currentPosition
                player.pause()
                isPlaybackRunning = false
                val durationMs = player.duration.coerceAtLeast(1)
                val progress = ((pausedPositionMs * 100f) / durationMs).toInt().coerceIn(0, 100)
                val label = formatDurationFromMillis(pausedPositionMs)
                val totalDuration = mediaPlayer?.duration ?: 0
                adapter.setPlaybackState(currentlyPlayingIncidentId, false, progress, label, formatDurationFromMillis(totalDuration))
                stopPlaybackUiUpdates()
                Toast.makeText(this, getString(R.string.preview_paused), Toast.LENGTH_SHORT).show()
            } else if (player != null) {
                player.seekTo(pausedPositionMs)
                player.start()
                isPlaybackRunning = true
                startPlaybackUiUpdates()
                Toast.makeText(this, getString(R.string.preview_playing), Toast.LENGTH_SHORT).show()
            } else {
                stopVoicePlaybackIfNeeded()
            }
            return
        }

        try {
            stopVoicePlaybackIfNeeded()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener {
                    stopVoicePlaybackIfNeeded()
                }
                prepare()
                start()
            }
            currentlyPlayingPath = path
            currentlyPlayingIncidentId = incident.id
            pausedPositionMs = 0
            isPlaybackRunning = true
            startPlaybackUiUpdates()
            Toast.makeText(this, getString(R.string.preview_playing), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            stopVoicePlaybackIfNeeded()
            Toast.makeText(this, getString(R.string.preview_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun startPlaybackUiUpdates() {
        playbackUiHandler.removeCallbacks(playbackUiRunnable)
        playbackUiHandler.post(playbackUiRunnable)
    }

    private fun stopPlaybackUiUpdates() {
        playbackUiHandler.removeCallbacks(playbackUiRunnable)
    }

    private fun stopVoicePlaybackIfNeeded() {
        mediaPlayer?.run {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        currentlyPlayingPath = null
        currentlyPlayingIncidentId = null
        pausedPositionMs = 0
        isPlaybackRunning = false
        waveformPlaybackSeed = 0
        isDetailDialogPlaying = false
        stopPlaybackUiUpdates()
        adapter.setPlaybackState(null, false, 0, null, null)
    }

    private enum class ExportType { CSV, PDF, ENCRYPTED }

    private fun openMediaFromPath(path: String?, mimeType: String) {
        if (path.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.unable_to_open_media), Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, getString(R.string.unable_to_open_media), Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.unable_to_open_media), Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportAndShareRecords(type: ExportType) {
        if (visibleIncidents.isEmpty()) {
            Toast.makeText(this, getString(R.string.nothing_to_export), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    when (type) {
                        ExportType.CSV -> createCsvExportFile(visibleIncidents)
                        ExportType.PDF -> createPdfExportFile(visibleIncidents)
                        ExportType.ENCRYPTED -> createEncryptedExportFile(visibleIncidents)
                    }
                }

                shareExportFile(file, type)

                Toast.makeText(
                    this@RecordsActivity,
                    getString(R.string.records_exported, visibleIncidents.size),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (_: Exception) {
                Toast.makeText(this@RecordsActivity, getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createCsvExportFile(records: List<Incident>): File {
        val exportDir = ensureExportDir()
        val file = File(exportDir, "records_${System.currentTimeMillis()}.csv")
        val header = "id,type,severity,timestamp,description,location,who,hasVoiceNote,voiceDurationSec,voiceNotePath,photoPath,videoPath\n"
        val rows = records.joinToString("\n") { record ->
            listOf(
                record.id.toString(),
                csv(record.type),
                csv(record.severity),
                csv(formatTimestamp(record.createdAtMillis)),
                csv(record.description),
                csv(record.location),
                csv(record.who),
                record.hasVoiceNote.toString(),
                record.voiceDurationSec.toString(),
                csv(record.voiceNotePath ?: ""),
                csv(record.photoPath ?: ""),
                csv(record.videoPath ?: "")
            ).joinToString(",")
        }
        file.writeText(header + rows)
        return file
    }

    private fun createPdfExportFile(records: List<Incident>): File {
        val exportDir = ensureExportDir()
        val file = File(exportDir, "records_${System.currentTimeMillis()}.pdf")

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint().apply { textSize = 11f }

        var y = 36f
        canvas.drawText("SafeWay Private Records", 24f, y, paint)
        y += 22f

        records.forEachIndexed { index, record ->
            if (y > 780f) return@forEachIndexed
            canvas.drawText("${index + 1}. ${record.type} (${record.severity})", 24f, y, paint)
            y += 16f
            canvas.drawText("Time: ${formatTimestamp(record.createdAtMillis)}", 24f, y, paint)
            y += 16f
            canvas.drawText("Location: ${record.location}", 24f, y, paint)
            y += 16f
            canvas.drawText("Who: ${record.who}", 24f, y, paint)
            y += 16f
            canvas.drawText("Voice: ${if (record.hasVoiceNote) "Yes (${record.voiceDurationSec}s)" else "No"}", 24f, y, paint)
            y += 16f
            canvas.drawText("Photo: ${if (!record.photoPath.isNullOrBlank()) "Attached" else "None"}", 24f, y, paint)
            y += 16f
            canvas.drawText("Video: ${if (!record.videoPath.isNullOrBlank()) "Attached" else "None"}", 24f, y, paint)
            y += 16f
            canvas.drawText("Details: ${record.description.take(70)}", 24f, y, paint)
            y += 22f
        }

        document.finishPage(page)
        FileOutputStream(file).use { output ->
            document.writeTo(output)
        }
        document.close()
        return file
    }

    private fun createEncryptedExportFile(records: List<Incident>): File {
        val exportDir = ensureExportDir()
        val file = File(exportDir, "records_${System.currentTimeMillis()}.enc")

        val plainText = records.joinToString("\n---\n") { record ->
            """
            ID: ${record.id}
            Type: ${record.type}
            Severity: ${record.severity}
            Timestamp: ${formatTimestamp(record.createdAtMillis)}
            Description: ${record.description}
            Location: ${record.location}
            Who: ${record.who}
            Voice: ${record.hasVoiceNote}
            VoiceDurationSec: ${record.voiceDurationSec}
            VoicePath: ${record.voiceNotePath ?: ""}
            PhotoPath: ${record.photoPath ?: ""}
            VideoPath: ${record.videoPath ?: ""}
            """.trimIndent()
        }.toByteArray(Charsets.UTF_8)

        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }

        val keySpec = PBEKeySpec("SafeWayExportSecret".toCharArray(), salt, 10000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(keySpec).encoded
        val key = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plainText)

        FileOutputStream(file).use { output ->
            output.write(salt)
            output.write(iv)
            output.write(encrypted)
        }

        return file
    }

    private fun shareExportFile(file: File, type: ExportType) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val mimeType = when (type) {
            ExportType.CSV -> "text/csv"
            ExportType.PDF -> "application/pdf"
            ExportType.ENCRYPTED -> "application/octet-stream"
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            this.type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_export)))
    }

    private fun ensureExportDir(): File {
        val base = getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: filesDir
        val exportDir = File(base, "SafeWay/exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        return exportDir
    }

    private fun csv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun formatTimestamp(millis: Long): String {
        return SimpleDateFormat("MMM dd • HH:mm", Locale.getDefault()).format(Date(millis))
    }

    private fun formatDurationFromMillis(millis: Int): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val min = totalSeconds / 60
        val sec = totalSeconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", min, sec)
    }

    override fun onDestroy() {
        super.onDestroy()
        detailDialog?.dismiss()
        detailDialog = null
        stopPlaybackUiUpdates()
        stopVoicePlaybackIfNeeded()
    }
}

