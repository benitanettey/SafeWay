package com.example.safeway

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class RecordsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var btnExport: Button

    // Mock records data
    private val mockRecords = listOf(
        MockRecord(
            title = "Physical altercation",
            description = "Was struck on the shoulder at home. Denied it happened before to family members.",
            severity = "High",
            timestamp = "Mar 11 • 22:40",
            hasGPS = true,
            hasVoiceNote = true
        ),
        MockRecord(
            title = "Verbal / emotional",
            description = "Sustained verbal threats in front of children. Threatened to file false reports.",
            severity = "Medium",
            timestamp = "Mar 8 • 18:22",
            hasGPS = true,
            hasVoiceNote = false
        ),
        MockRecord(
            title = "Financial control",
            description = "All bank accounts restricted without consent for third consecutive week.",
            severity = "Medium",
            timestamp = "Mar 2 • 10:05",
            hasGPS = true,
            hasVoiceNote = true
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_records)

        initializeViews()
        setupListeners()
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btn_back_records)
        btnExport = findViewById(R.id.btn_export_report)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnExport.setOnClickListener {
            exportRecords()
        }
    }

    private fun exportRecords() {
        // Mock export implementation
        val exportData = mockRecords.joinToString("\n---\n") { record ->
            """
                Title: ${record.title}
                Severity: ${record.severity}
                Timestamp: ${record.timestamp}
                Description: ${record.description}
                GPS: ${if (record.hasGPS) "Yes" else "No"}
                Voice Note: ${if (record.hasVoiceNote) "Yes" else "No"}
            """.trimIndent()
        }

        android.widget.Toast.makeText(
            this,
            "Exported ${mockRecords.size} records (encrypted)",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    data class MockRecord(
        val title: String,
        val description: String,
        val severity: String,
        val timestamp: String,
        val hasGPS: Boolean,
        val hasVoiceNote: Boolean
    )
}

