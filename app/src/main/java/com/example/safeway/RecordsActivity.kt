package com.example.safeway

import android.app.AlertDialog
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
                adapter.setPlaybackState(incidentId, true, progress, label)
                playbackUiHandler.postDelayed(this, 250)
            }
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
        adapter.setPlaybackState(null, false, 0, null)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
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
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, entries).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
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
            currentIncidents = database.incidentDao().getAllIncidents()
            applyFiltersAndRender()
        }
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
        val view = layoutInflater.inflate(R.layout.dialog_record_detail, null)
        val etType = view.findViewById<EditText>(R.id.et_detail_type)
        val etDescription = view.findViewById<EditText>(R.id.et_detail_description)
        val etSeverity = view.findViewById<EditText>(R.id.et_detail_severity)
        val etLocation = view.findViewById<EditText>(R.id.et_detail_location)
        val etWho = view.findViewById<EditText>(R.id.et_detail_who)
        val btnPhoto = view.findViewById<Button>(R.id.btn_detail_photo)
        val btnVideo = view.findViewById<Button>(R.id.btn_detail_video)
        val btnPlay = view.findViewById<Button>(R.id.btn_detail_play)
        val btnSave = view.findViewById<Button>(R.id.btn_detail_save)
        val btnDelete = view.findViewById<Button>(R.id.btn_detail_delete)

        etType.setText(incident.type)
        etDescription.setText(incident.description)
        etSeverity.setText(incident.severity)
        etLocation.setText(incident.location)
        etWho.setText(incident.who)

        btnPhoto.isEnabled = !incident.photoPath.isNullOrBlank() && File(incident.photoPath).exists()
        btnVideo.isEnabled = !incident.videoPath.isNullOrBlank() && File(incident.videoPath).exists()
        btnPlay.isEnabled = incident.hasVoiceNote && !incident.voiceNotePath.isNullOrBlank() && File(incident.voiceNotePath).exists()

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.record_details))
            .setView(view)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        btnPhoto.setOnClickListener {
            openMediaFromPath(incident.photoPath, "image/*")
        }

        btnVideo.setOnClickListener {
            openMediaFromPath(incident.videoPath, "video/*")
        }

        btnPlay.text = if (isPlaybackRunning && currentlyPlayingIncidentId == incident.id) {
            getString(R.string.pause)
        } else {
            getString(R.string.play)
        }

        btnPlay.setOnClickListener {
            toggleVoicePlayback(incident)
            btnPlay.text = if (isPlaybackRunning && currentlyPlayingIncidentId == incident.id) {
                getString(R.string.pause)
            } else {
                getString(R.string.play)
            }
        }

        btnSave.setOnClickListener {
            val updated = incident.copy(
                type = etType.text.toString().trim().ifEmpty { incident.type },
                description = etDescription.text.toString().trim().ifEmpty { incident.description },
                severity = etSeverity.text.toString().trim().ifEmpty { incident.severity },
                location = etLocation.text.toString().trim().ifEmpty { incident.location },
                who = etWho.text.toString().trim().ifEmpty { incident.who }
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
            stopVoicePlaybackIfNeeded()
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.96f).toInt(),
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
                adapter.setPlaybackState(currentlyPlayingIncidentId, false, progress, label)
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
        stopPlaybackUiUpdates()
        adapter.setPlaybackState(null, false, 0, null)
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
        stopPlaybackUiUpdates()
        stopVoicePlaybackIfNeeded()
    }
}

