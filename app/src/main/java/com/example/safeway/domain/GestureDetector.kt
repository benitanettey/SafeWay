package com.example.safeway.domain

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent

enum class GestureType {
    TRIPLE_PRESS,
    DOUBLE_PRESS,
    SLOW_DOUBLE_PRESS
}

enum class EmergencyAction(val displayName: String) {
    SOS_ALERT("Send SOS"),
    START_RECORDING("Start Recording"),
    SHARE_LOCATION("Share Location")
}

class GestureDetector(
    private val onGesture: (GestureType) -> Unit,
    /** Called when a single tap arms the slow-double timer.
     *  Use for haptic feedback so the user knows the system is listening. */
    private val onSlowDoubleArmed: (() -> Unit)? = null
) {

    private val pressTimestamps = mutableListOf<Long>()
    private var doublePressPending = false
    private var slowDoublePending = false
    private var slowDoubleFirstPressTime: Long = 0L
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "ShieldBT.Gesture"
        private const val TRIPLE_PRESS_WINDOW_MS = 2000L
        private const val DOUBLE_PRESS_WINDOW_MS = 800L
        private const val DOUBLE_PRESS_DEBOUNCE_MS = 400L
        /** Window for two single clicks spaced ~3s apart.
         *  Min gap (1s) is well beyond the 400ms double-press debounce.
         *  Max gap (10s) gives the user reasonable margin to count to 3
         *  without rushing. Previously 1.5-5s, widened based on user
         *  testing where clicks arrived 10-14s apart. */
        private const val SLOW_DOUBLE_MIN_GAP_MS = 1000L
        private const val SLOW_DOUBLE_WINDOW_MS = 10000L
    }

    /**
     * Handles AVRCP commands from Bluetooth devices that send single keycodes for multi-tap.
     * AirPods send KEYCODE_MEDIA_NEXT for double tap, KEYCODE_MEDIA_PREVIOUS for triple tap.
     */
    fun onAvrcpCommand(keyCode: Int) {
        // Clear any in-progress slow-double detection.
        // AVRCP and key-event paths operate independently for the same
        // physical action — a double-tap reported as MEDIA_NEXT also
        // kills the slow-double timer from a prior single tap.
        slowDoublePending = false
        handler.removeCallbacks(slowDoubleRunnable)

        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                Log.d(TAG, "AVRCP MEDIA_NEXT → DOUBLE_PRESS")
                pressTimestamps.clear()
                handler.removeCallbacksAndMessages(null)
                onGesture(GestureType.DOUBLE_PRESS)
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                Log.d(TAG, "AVRCP MEDIA_PREVIOUS → TRIPLE_PRESS")
                pressTimestamps.clear()
                handler.removeCallbacksAndMessages(null)
                onGesture(GestureType.TRIPLE_PRESS)
            }
            else -> Log.d(TAG, "AVRCP unhandled keyCode=$keyCode")
        }
    }

    fun onKeyEvent(keyEvent: KeyEvent) {
        Log.d(TAG, "onKeyEvent: action=${keyEvent.action}, code=${keyEvent.keyCode}")
        if (keyEvent.action == KeyEvent.ACTION_DOWN) {
            Log.d(TAG, "press count before: ${pressTimestamps.size}")
            onKeyDown()
            Log.d(TAG, "press count after: ${pressTimestamps.size}")
        }
    }

    private fun onKeyDown() {
        val now = SystemClock.elapsedRealtime()
        pressTimestamps.add(now)

        // Check slow-double: second click arrives after the regular double-tap window.
        // This catches the "click, wait 3s, click" pattern that works on AirPods.
        if (slowDoublePending) {
            val gap = now - slowDoubleFirstPressTime
            if (gap >= SLOW_DOUBLE_MIN_GAP_MS && gap <= SLOW_DOUBLE_WINDOW_MS) {
                slowDoublePending = false
                handler.removeCallbacks(slowDoubleRunnable)
                Log.d(TAG, "SLOW_DOUBLE_PRESS detected (${gap}ms gap)")
                pressTimestamps.clear()
                handler.removeCallbacksAndMessages(null)
                onGesture(GestureType.SLOW_DOUBLE_PRESS)
                return
            } else {
                // Click arrived too fast or too slow — cancel slow-double state
                slowDoublePending = false
                handler.removeCallbacks(slowDoubleRunnable)
            }
        }

        cleanupOldPresses(now)

        // Cancel any pending double-press debounce — a new press arrived
        if (doublePressPending) {
            handler.removeCallbacksAndMessages(null)
            doublePressPending = false
        }

        // Check triple press
        if (pressTimestamps.size >= 3) {
            Log.d(TAG, "TRIPLE_PRESS detected (${pressTimestamps.size} presses in window)")
            pressTimestamps.clear()
            handler.removeCallbacksAndMessages(null)
            onGesture(GestureType.TRIPLE_PRESS)
            return
        }

        // Check double press — debounce to give triple-press a chance
        if (pressTimestamps.size >= 2) {
            Log.d(TAG, "DOUBLE_PRESS candidate — debouncing ${DOUBLE_PRESS_DEBOUNCE_MS}ms")
            doublePressPending = true
            handler.postDelayed({
                if (doublePressPending) {
                    doublePressPending = false
                    Log.d(TAG, "DOUBLE_PRESS confirmed after debounce")
                    pressTimestamps.clear()
                    onGesture(GestureType.DOUBLE_PRESS)
                }
            }, DOUBLE_PRESS_DEBOUNCE_MS)
            return
        }

        // Exactly one press — arm slow-double timer.
        // If a second click arrives in 1-10s, it's a SLOW_DOUBLE_PRESS.
        if (pressTimestamps.size == 1) {
            slowDoublePending = true
            slowDoubleFirstPressTime = pressTimestamps[0]
            handler.removeCallbacks(slowDoubleRunnable)
            handler.postDelayed(slowDoubleRunnable, SLOW_DOUBLE_WINDOW_MS)
            onSlowDoubleArmed?.invoke()
        }
    }

    private val slowDoubleRunnable = Runnable {
        if (slowDoublePending) {
            Log.d(TAG, "slow-double window expired — no second click")
            slowDoublePending = false
        }
    }

    private fun cleanupOldPresses(now: Long) {
        val before = pressTimestamps.size
        pressTimestamps.removeAll { now - it > TRIPLE_PRESS_WINDOW_MS }
        if (before != pressTimestamps.size) {
            Log.d(TAG, "cleaned ${before - pressTimestamps.size} stale press(es)")
        }
        handler.postDelayed({
            val cutoff = SystemClock.elapsedRealtime() - TRIPLE_PRESS_WINDOW_MS
            val pruned = pressTimestamps.size
            pressTimestamps.removeAll { it < cutoff }
            if (pressTimestamps.size != pruned) {
                Log.d(TAG, "pruned ${pruned - pressTimestamps.size} expired press(es)")
            }
        }, TRIPLE_PRESS_WINDOW_MS)
    }

    fun reset() {
        Log.d(TAG, "reset")
        pressTimestamps.clear()
        doublePressPending = false
        slowDoublePending = false
        handler.removeCallbacksAndMessages(null)
    }
}
