package com.example.safeway

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class ResourcesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_resources)

        findViewById<ImageButton>(R.id.btn_back_resources).setOnClickListener { finish() }

        findViewById<LinearLayout>(R.id.card_understand_abuse).setOnClickListener {
            startActivity(Intent(this, UnderstandAbuseActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.card_evidence_guide).setOnClickListener {
            startActivity(Intent(this, EvidenceGuideActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.card_find_help_nearby).setOnClickListener {
            startActivity(Intent(this, FindHelpNearYouActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.card_break_stigma).setOnClickListener {
            startActivity(Intent(this, BreakingStigmaActivity::class.java))
        }
    }
}

