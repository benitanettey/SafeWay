package com.example.safeway

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.safeway.data.Incident
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordsAdapter(
    private val onCardClick: (Incident) -> Unit,
    private val onDetailsClick: (Incident) -> Unit,
    private val onVoicePlayClick: (Incident) -> Unit
) : RecyclerView.Adapter<RecordsAdapter.RecordViewHolder>() {

    private val items = mutableListOf<Incident>()
    private var activePlaybackIncidentId: Int? = null
    private var isPlaybackRunning: Boolean = false
    private var playbackProgressPercent: Int = 0
    private var playbackElapsedLabel: String? = null
    private var playbackDurationLabel: String? = null
    private var activeViewHolder: RecordViewHolder? = null

    fun submitItems(newItems: List<Incident>) {
        items.clear()
        items.addAll(newItems)
        activeViewHolder = null
        notifyDataSetChanged()
    }

    /**
     * Updates playback state. Only triggers adapter rebind on start/stop transitions,
     * NOT during active playback — during playback, views are updated directly.
     */
    fun setPlaybackState(
        incidentId: Int?,
        isPlaying: Boolean,
        progressPercent: Int = 0,
        elapsedLabel: String? = null,
        durationLabel: String? = null
    ) {
        val wasPlaying = isPlaybackRunning && activePlaybackIncidentId != null
        val nowPlaying = isPlaying && incidentId != null
        val transitioning = wasPlaying != nowPlaying

        activePlaybackIncidentId = incidentId
        isPlaybackRunning = isPlaying
        playbackProgressPercent = progressPercent.coerceIn(0, 100)
        playbackElapsedLabel = elapsedLabel
        playbackDurationLabel = durationLabel

        if (transitioning) {
            // Playback started or stopped — need to show/hide waveform across items
            activeViewHolder = null
            notifyDataSetChanged()
        } else if (isPlaying && incidentId != null) {
            // Active playback tick — update the active ViewHolder directly, no rebind
            activeViewHolder?.updatePlaybackUi(
                progressPercent = playbackProgressPercent,
                elapsedLabel = playbackElapsedLabel,
                durationLabel = playbackDurationLabel,
                waveformVisible = true
            )
        }
    }

    fun getActivePlaybackId(): Int? = activePlaybackIncidentId
    fun isPlaying(): Boolean = isPlaybackRunning

    /**
     * Directly updates waveform bars on the active playback ViewHolder without rebinding.
     */
    fun updatePlaybackWaveform(bars: List<Int>) {
        activeViewHolder?.setWaveformBars(bars)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_record_incident, parent, false)
        return RecordViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        val incident = items[position]
        val isActivePlayback = activePlaybackIncidentId == incident.id

        // Track holder for direct playback updates
        if (isActivePlayback && isPlaybackRunning) {
            activeViewHolder = holder
        }

        holder.bind(
            incident = incident,
            isPlaying = isPlaybackRunning && isActivePlayback,
            isActivePlayback = isActivePlayback,
            playbackProgressPercent = if (isActivePlayback) playbackProgressPercent else 0,
            playbackElapsedLabel = if (isActivePlayback) playbackElapsedLabel else null,
            playbackDurationLabel = if (isActivePlayback) playbackDurationLabel else null,
            onCardClick = onCardClick,
            onDetailsClick = onDetailsClick,
            onVoicePlayClick = onVoicePlayClick
        )
    }

    override fun onViewRecycled(holder: RecordViewHolder) {
        super.onViewRecycled(holder)
        if (holder == activeViewHolder) {
            activeViewHolder = null
        }
    }

    override fun getItemCount(): Int = items.size

    inner class RecordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title = itemView.findViewById<TextView>(R.id.tv_record_title)
        private val time = itemView.findViewById<TextView>(R.id.tv_record_time)
        private val description = itemView.findViewById<TextView>(R.id.tv_record_description)
        private val chipSeverity = itemView.findViewById<Chip>(R.id.chip_severity)
        private val chipGps = itemView.findViewById<Chip>(R.id.chip_gps)
        private val chipVoice = itemView.findViewById<Chip>(R.id.chip_voice)
        private val chipPhoto = itemView.findViewById<Chip>(R.id.chip_photo)
        private val chipVideo = itemView.findViewById<Chip>(R.id.chip_video)
        val llWaveform = itemView.findViewById<LinearLayout>(R.id.ll_item_waveform)
        private val containerPlay = itemView.findViewById<FrameLayout>(R.id.container_item_play)
        private val progressRing = itemView.findViewById<CircularProgressIndicator>(R.id.progress_playback_ring)
        private val btnPlay = itemView.findViewById<ImageButton>(R.id.btn_item_play)
        val tvPlaybackLabel = itemView.findViewById<TextView>(R.id.tv_item_playback_label)
        private val btnDetails = itemView.findViewById<Button>(R.id.btn_item_details)

        fun bind(
            incident: Incident,
            isPlaying: Boolean,
            isActivePlayback: Boolean,
            playbackProgressPercent: Int,
            playbackElapsedLabel: String?,
            playbackDurationLabel: String?,
            onCardClick: (Incident) -> Unit,
            onDetailsClick: (Incident) -> Unit,
            onVoicePlayClick: (Incident) -> Unit
        ) {
            title.text = incident.type
            val defaultTime = SimpleDateFormat("MMM dd • HH:mm", Locale.getDefault()).format(Date(incident.createdAtMillis))
            time.text = defaultTime
            description.text = incident.description

            setupChip(chipSeverity, incident.severity, isSeverity = true)
            setupChip(chipGps, itemView.context.getString(R.string.gps_logged), isSeverity = false)
            setupChip(
                chipVoice,
                if (incident.hasVoiceNote) "Voice note" else "Timestamped",
                isSeverity = false,
                highlighted = incident.hasVoiceNote
            )

            val hasPhoto = !incident.photoPath.isNullOrBlank() && File(incident.photoPath).exists()
            val hasVideo = !incident.videoPath.isNullOrBlank() && File(incident.videoPath).exists()

            setupChip(
                chipPhoto,
                if (hasPhoto) itemView.context.getString(R.string.photo_attached) else itemView.context.getString(R.string.no_photo_attached),
                isSeverity = false,
                highlighted = hasPhoto
            )

            setupChip(
                chipVideo,
                if (hasVideo) itemView.context.getString(R.string.video_attached) else itemView.context.getString(R.string.no_video_attached),
                isSeverity = false,
                highlighted = hasVideo
            )

            val canPlay = incident.hasVoiceNote && !incident.voiceNotePath.isNullOrBlank() && File(incident.voiceNotePath).exists()
            containerPlay.visibility = if (canPlay) View.VISIBLE else View.GONE

            if (canPlay) {
                val iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                val descriptionRes = if (isPlaying) R.string.pause_voice_note_preview else R.string.play_voice_note_preview
                btnPlay.setImageDrawable(AppCompatResources.getDrawable(itemView.context, iconRes))
                btnPlay.contentDescription = itemView.context.getString(descriptionRes)
            }

            llWaveform.visibility = if (isActivePlayback) View.VISIBLE else View.GONE
            progressRing.visibility = if (isActivePlayback && isPlaying) View.VISIBLE else View.GONE
            progressRing.isIndeterminate = false
            progressRing.progress = playbackProgressPercent

            if (isActivePlayback && playbackElapsedLabel != null) {
                tvPlaybackLabel.visibility = View.VISIBLE
                val label = if (playbackDurationLabel != null) {
                    "${playbackElapsedLabel} / ${playbackDurationLabel}"
                } else {
                    playbackElapsedLabel
                }
                tvPlaybackLabel.text = label
            } else {
                tvPlaybackLabel.visibility = View.GONE
            }

            itemView.setOnClickListener { onCardClick(incident) }
            btnDetails.setOnClickListener { onDetailsClick(incident) }
            btnPlay.setOnClickListener { onVoicePlayClick(incident) }
        }

        /**
         * Sets waveform bars directly on this holder without triggering a rebind.
         */
        fun setWaveformBars(barHeights: List<Int>) {
            llWaveform.removeAllViews()
            val context = itemView.context
            val density = context.resources.displayMetrics.density
            for (heightDp in barHeights) {
                val bar = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        (4 * density).toInt(),
                        (heightDp * density).toInt()
                    ).apply {
                        setMargins(2, 0, 2, 0)
                        gravity = android.view.Gravity.BOTTOM
                    }
                    setBackgroundColor(ContextCompat.getColor(context, R.color.highlight_accent))
                    alpha = 0.5f + (heightDp.toFloat() / 28f) * 0.5f
                }
                llWaveform.addView(bar)
            }
        }

        /**
         * Direct UI update during active playback — no rebind, no flicker.
         */
        fun updatePlaybackUi(
            progressPercent: Int,
            elapsedLabel: String?,
            durationLabel: String?,
            waveformVisible: Boolean
        ) {
            llWaveform.visibility = if (waveformVisible) View.VISIBLE else View.GONE
            progressRing.visibility = if (waveformVisible) View.VISIBLE else View.GONE
            progressRing.isIndeterminate = false
            progressRing.progress = progressPercent

            if (elapsedLabel != null) {
                tvPlaybackLabel.visibility = View.VISIBLE
                tvPlaybackLabel.text = if (durationLabel != null) {
                    "$elapsedLabel / $durationLabel"
                } else {
                    elapsedLabel
                }
            } else {
                tvPlaybackLabel.visibility = View.GONE
            }
        }

        private fun setupChip(chip: Chip, text: String, isSeverity: Boolean, highlighted: Boolean = false) {
            val context = chip.context
            chip.text = text
            chip.isClickable = false
            chip.isCheckable = false
            chip.chipStrokeWidth = if (highlighted) 0f else 1f

            when {
                highlighted -> {
                    chip.chipBackgroundColor = ContextCompat.getColorStateList(context, R.color.primary_accent)
                    chip.chipStrokeColor = ContextCompat.getColorStateList(context, R.color.primary_accent)
                    chip.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                }

                isSeverity && (text.equals("High", true) || text.equals("Crisis", true)) -> {
                    chip.chipBackgroundColor = ColorStateList.valueOf(0xFF2A1418.toInt())
                    chip.chipStrokeColor = ColorStateList.valueOf(0xFF4A1A1A.toInt())
                    chip.setTextColor(ContextCompat.getColor(context, R.color.emergency_red))
                }

                else -> {
                    chip.chipBackgroundColor = ContextCompat.getColorStateList(context, R.color.card_background)
                    chip.chipStrokeColor = ContextCompat.getColorStateList(context, R.color.border_dark)
                    chip.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                }
            }
        }
    }
}
