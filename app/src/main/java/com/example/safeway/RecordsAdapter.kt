package com.example.safeway

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
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

    fun submitItems(newItems: List<Incident>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun setPlaybackState(
        incidentId: Int?,
        isPlaying: Boolean,
        progressPercent: Int = 0,
        elapsedLabel: String? = null
    ) {
        activePlaybackIncidentId = incidentId
        isPlaybackRunning = isPlaying
        playbackProgressPercent = progressPercent.coerceIn(0, 100)
        playbackElapsedLabel = elapsedLabel
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_record_incident, parent, false)
        return RecordViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        val incident = items[position]
        val isActivePlayback = activePlaybackIncidentId == incident.id
        holder.bind(
            incident = incident,
            isPlaying = isPlaybackRunning && isActivePlayback,
            isActivePlayback = isActivePlayback,
            playbackProgressPercent = if (isActivePlayback) playbackProgressPercent else 0,
            playbackElapsedLabel = if (isActivePlayback) playbackElapsedLabel else null,
            onCardClick = onCardClick,
            onDetailsClick = onDetailsClick,
            onVoicePlayClick = onVoicePlayClick
        )
    }

    override fun getItemCount(): Int = items.size

    class RecordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title = itemView.findViewById<TextView>(R.id.tv_record_title)
        private val time = itemView.findViewById<TextView>(R.id.tv_record_time)
        private val description = itemView.findViewById<TextView>(R.id.tv_record_description)
        private val chipSeverity = itemView.findViewById<Chip>(R.id.chip_severity)
        private val chipGps = itemView.findViewById<Chip>(R.id.chip_gps)
        private val chipVoice = itemView.findViewById<Chip>(R.id.chip_voice)
        private val chipPhoto = itemView.findViewById<Chip>(R.id.chip_photo)
        private val chipVideo = itemView.findViewById<Chip>(R.id.chip_video)
        private val progressRing = itemView.findViewById<CircularProgressIndicator>(R.id.progress_playback_ring)
        private val btnPlay = itemView.findViewById<ImageButton>(R.id.btn_item_play)
        private val btnDetails = itemView.findViewById<Button>(R.id.btn_item_details)

        fun bind(
            incident: Incident,
            isPlaying: Boolean,
            isActivePlayback: Boolean,
            playbackProgressPercent: Int,
            playbackElapsedLabel: String?,
            onCardClick: (Incident) -> Unit,
            onDetailsClick: (Incident) -> Unit,
            onVoicePlayClick: (Incident) -> Unit
        ) {
            title.text = incident.type
            val defaultTime = SimpleDateFormat("MMM dd • HH:mm", Locale.getDefault()).format(Date(incident.createdAtMillis))
            time.text = if (isActivePlayback && !playbackElapsedLabel.isNullOrBlank()) {
                playbackElapsedLabel
            } else {
                defaultTime
            }
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
            btnPlay.visibility = if (canPlay) View.VISIBLE else View.GONE

            if (canPlay) {
                val iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                val descriptionRes = if (isPlaying) R.string.pause_voice_note_preview else R.string.play_voice_note_preview
                btnPlay.setImageDrawable(AppCompatResources.getDrawable(itemView.context, iconRes))
                btnPlay.contentDescription = itemView.context.getString(descriptionRes)
            }

            progressRing.visibility = if (isActivePlayback) View.VISIBLE else View.GONE
            progressRing.isIndeterminate = false
            progressRing.progress = playbackProgressPercent

            itemView.setOnClickListener { onCardClick(incident) }
            btnDetails.setOnClickListener { onDetailsClick(incident) }
            btnPlay.setOnClickListener { onVoicePlayClick(incident) }
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




