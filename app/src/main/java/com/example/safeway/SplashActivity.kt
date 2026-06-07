package com.example.safeway

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.safeway.service.ProtectionForegroundService

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Restart protection service if process was killed
        ProtectionForegroundService.ensureRunning(this)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, HomeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            overridePendingTransition(R.anim.fade_in, R.anim.fade_in)
        }, 1500)
    }
}
