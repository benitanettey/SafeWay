package com.example.safeway

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class EvidenceGuideActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_evidence_guide)

        findViewById<ImageButton>(R.id.btn_back_evidence_guide).setOnClickListener { finish() }

        findViewById<Button>(R.id.btn_start_recording_evidence).setOnClickListener {
            startActivity(Intent(this, LogIncidentActivity::class.java))
        }
    }
}

