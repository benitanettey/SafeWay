package com.example.safeway.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.safeway.EmergencyAlertDispatcher
import com.example.safeway.HomeActivity
import com.example.safeway.LogIncidentActivity
import com.example.safeway.R
import kotlin.math.abs

class ShieldOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var mainBubble: View
    private lateinit var emergencyBubble: View
    private lateinit var logBubble: View
    private lateinit var dismissOverlay: View

    private lateinit var mainParams: WindowManager.LayoutParams
    private lateinit var emergencyParams: WindowManager.LayoutParams
    private lateinit var logParams: WindowManager.LayoutParams
    private lateinit var dismissParams: WindowManager.LayoutParams

    private var expanded = false
    private var moving = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createBubbles()
        addMainBubble()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        removeViewSafely(dismissOverlay)
        removeViewSafely(logBubble)
        removeViewSafely(emergencyBubble)
        removeViewSafely(mainBubble)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createBubbles() {
        mainBubble = buildBubbleView(sizeDp = 58, iconRes = R.drawable.ic_shield)
        emergencyBubble = buildBubbleView(sizeDp = 46, iconRes = R.drawable.ic_alert)
        logBubble = buildBubbleView(sizeDp = 46, iconRes = R.drawable.ic_log)

        mainParams = createLayoutParams().apply {
            val defaultX = OverlayPrefs.bubbleX(this@ShieldOverlayService) ?: 0
            val defaultY = OverlayPrefs.bubbleY(this@ShieldOverlayService) ?: 320
            x = defaultX
            y = defaultY
        }
        emergencyParams = createLayoutParams()
        logParams = createLayoutParams()
        dismissOverlay = View(this).apply {
            setBackgroundColor(0x00000000)
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN && expanded) {
                    collapseMenu()
                    return@setOnTouchListener true
                }
                false
            }
        }
        dismissParams = createDismissLayoutParams()

        wireMainBubbleTouch()
        wireActionBubbles()
    }

    private fun buildBubbleView(sizeDp: Int, iconRes: Int): View {
        val sizePx = sizeDp.dpToPx()
        val circle = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(this@ShieldOverlayService, R.color.card_background))
                setStroke(2.dpToPx(), ContextCompat.getColor(this@ShieldOverlayService, R.color.highlight_accent))
            }
            elevation = 10f
        }

        val icon = ImageView(this).apply {
            val iconSize = (sizePx * 0.5f).toInt()
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
            setImageResource(iconRes)
            setColorFilter(ContextCompat.getColor(this@ShieldOverlayService, R.color.highlight_accent))
        }
        circle.addView(icon)
        return circle
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun wireMainBubbleTouch() {
        mainBubble.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        moving = false
                        startX = mainParams.x
                        startY = mainParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()
                        if (abs(deltaX) > 5 || abs(deltaY) > 5) {
                            moving = true
                        }
                        mainParams.x = startX + deltaX
                        mainParams.y = startY + deltaY
                        windowManager.updateViewLayout(mainBubble, mainParams)
                        if (expanded) {
                            updateExpandedBubblePositions()
                        }
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (!moving) {
                            v.performClick()
                            toggleExpanded()
                        }
                        OverlayPrefs.saveBubblePosition(this@ShieldOverlayService, mainParams.x, mainParams.y)
                        return true
                    }

                    MotionEvent.ACTION_OUTSIDE -> {
                        if (expanded) {
                            collapseMenu()
                        }
                        return false
                    }
                }
                return false
            }
        })
    }

    private fun wireActionBubbles() {
        emergencyBubble.setOnClickListener {
            EmergencyAlertDispatcher.sendNow(applicationContext) { _, message ->
                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            }
            collapseMenu()
        }

        logBubble.setOnClickListener {
            collapseMenu()
            val intent = Intent(applicationContext, LogIncidentActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }

    private fun toggleExpanded() {
        if (expanded) collapseMenu() else expandMenu()
    }

    private fun expandMenu() {
        if (expanded) return
        expanded = true

        if (!isViewAttached(dismissOverlay)) {
            windowManager.addView(dismissOverlay, dismissParams)
        }

        emergencyParams.x = mainParams.x
        emergencyParams.y = mainParams.y
        logParams.x = mainParams.x
        logParams.y = mainParams.y

        if (!isViewAttached(emergencyBubble)) {
            windowManager.addView(emergencyBubble, emergencyParams)
        }
        if (!isViewAttached(logBubble)) {
            windowManager.addView(logBubble, logParams)
        }

        emergencyBubble.alpha = 0f
        emergencyBubble.scaleX = 0.5f
        emergencyBubble.scaleY = 0.5f

        logBubble.alpha = 0f
        logBubble.scaleX = 0.5f
        logBubble.scaleY = 0.5f

        animateBubbleWindows(expand = true)
    }

    private fun animateBubbleWindows(expand: Boolean) {
        val start = if (expand) 0f else 1f
        val end = if (expand) 1f else 0f
        val duration = if (expand) 180L else 140L

        val startEmergencyX = if (expand) mainParams.x else emergencyParams.x
        val startEmergencyY = if (expand) mainParams.y else emergencyParams.y
        val startLogX = if (expand) mainParams.x else logParams.x
        val startLogY = if (expand) mainParams.y else logParams.y

        val targetEmergencyX = mainParams.x - ACTION_OFFSET_DP.dpToPx()
        val targetEmergencyY = mainParams.y - ACTION_OFFSET_DP.dpToPx()
        val targetLogX = mainParams.x + ACTION_OFFSET_DP.dpToPx()
        val targetLogY = mainParams.y - ACTION_OFFSET_DP.dpToPx()

        val endEmergencyX = if (expand) targetEmergencyX else mainParams.x
        val endEmergencyY = if (expand) targetEmergencyY else mainParams.y
        val endLogX = if (expand) targetLogX else mainParams.x
        val endLogY = if (expand) targetLogY else mainParams.y

        ValueAnimator.ofFloat(start, end).apply {
            this.duration = duration
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float

                emergencyParams.x = lerp(startEmergencyX, endEmergencyX, t)
                emergencyParams.y = lerp(startEmergencyY, endEmergencyY, t)
                logParams.x = lerp(startLogX, endLogX, t)
                logParams.y = lerp(startLogY, endLogY, t)

                if (isViewAttached(emergencyBubble)) {
                    windowManager.updateViewLayout(emergencyBubble, emergencyParams)
                }
                if (isViewAttached(logBubble)) {
                    windowManager.updateViewLayout(logBubble, logParams)
                }

                emergencyBubble.alpha = 0.5f + (0.5f * t)
                emergencyBubble.scaleX = 0.5f + (0.5f * t)
                emergencyBubble.scaleY = 0.5f + (0.5f * t)

                logBubble.alpha = 0.5f + (0.5f * t)
                logBubble.scaleX = 0.5f + (0.5f * t)
                logBubble.scaleY = 0.5f + (0.5f * t)
            }
            doOnEnd {
                if (!expand) {
                    removeViewSafely(emergencyBubble)
                    removeViewSafely(logBubble)
                    removeViewSafely(dismissOverlay)
                }
            }
            start()
        }
    }

    private fun ValueAnimator.doOnEnd(block: () -> Unit) {
        addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) = Unit
            override fun onAnimationEnd(animation: android.animation.Animator) = block()
            override fun onAnimationCancel(animation: android.animation.Animator) = block()
            override fun onAnimationRepeat(animation: android.animation.Animator) = Unit
        })
    }

    private fun lerp(start: Int, end: Int, t: Float): Int {
        return (start + ((end - start) * t)).toInt()
    }

    private fun updateExpandedBubblePositions() {
        if (!expanded) return
        emergencyParams.x = mainParams.x - ACTION_OFFSET_DP.dpToPx()
        emergencyParams.y = mainParams.y - ACTION_OFFSET_DP.dpToPx()
        logParams.x = mainParams.x + ACTION_OFFSET_DP.dpToPx()
        logParams.y = mainParams.y - ACTION_OFFSET_DP.dpToPx()

        if (isViewAttached(emergencyBubble)) {
            windowManager.updateViewLayout(emergencyBubble, emergencyParams)
        }
        if (isViewAttached(logBubble)) {
            windowManager.updateViewLayout(logBubble, logParams)
        }
    }

    private fun collapseMenu() {
        if (!expanded) return
        expanded = false

        animateBubbleWindows(expand = false)
    }

    private fun createDismissLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun isViewAttached(view: View): Boolean {
        return view.windowToken != null
    }

    private fun addMainBubble() {
        windowManager.addView(mainBubble, mainParams)
    }

    private fun removeViewSafely(view: View) {
        try {
            if (view.windowToken != null) {
                windowManager.removeView(view)
            }
        } catch (_: Exception) {
            // View is already detached.
        }
    }

    private fun buildNotification(): Notification {
        createNotificationChannel()

        val homeIntent = Intent(this, HomeActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            homeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.overlay_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val CHANNEL_ID = "shield_overlay_channel"
        private const val NOTIFICATION_ID = 7001
        private const val ACTION_OFFSET_DP = 74
    }
}



