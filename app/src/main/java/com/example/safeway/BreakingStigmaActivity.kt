package com.example.safeway

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class BreakingStigmaActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_breaking_stigma)

        findViewById<ImageButton>(R.id.btn_back_breaking_stigma).setOnClickListener { finish() }
    }
}

