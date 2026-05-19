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

        BottomNavHelper.setup(this, NavTab.HOME)

        findViewById<ImageButton>(R.id.btn_back_evidence_guide).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.slide_out_left)
        }

        findViewById<Button>(R.id.btn_start_recording_evidence).setOnClickListener {
            startActivity(Intent(this, LogIncidentActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_in)
        }
    }
}

