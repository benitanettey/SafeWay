package com.example.safeway

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class UnderstandAbuseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_understand_abuse)

        findViewById<ImageButton>(R.id.btn_back_understand_abuse).setOnClickListener { finish() }

        val logButtons = listOf(
            R.id.btn_log_physical,
            R.id.btn_log_verbal,
            R.id.btn_log_financial,
            R.id.btn_log_sexual,
            R.id.btn_log_psychological
        )

        logButtons.forEach { id ->
            findViewById<Button>(id).setOnClickListener {
                startActivity(Intent(this, LogIncidentActivity::class.java))
            }
        }
    }
}

