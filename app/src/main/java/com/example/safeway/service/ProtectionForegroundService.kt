package com.example.safeway.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaMetadata
import android.media.MediaRecorder
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.safeway.EmergencyAlertDispatcher
import com.example.safeway.HomeActivity
import com.example.safeway.R
import com.example.safeway.domain.EmergencyAction
import java.io.File
import com.example.safeway.domain.GestureDetector
import com.example.safeway.domain.GestureType
import com.example.safeway.domain.ProtectionPrefs
import com.example.safeway.domain.TriggerEvent
import com.example.safeway.receiver.MediaButtonReceiver

class ProtectionForegroundService : Service() {

    private lateinit var gestureDetector: GestureDetector
    private lateinit var audioManager: AudioManager
    private var mediaSession: MediaSession? = null
    private var audioFocusRequest: android.media.AudioFocusRequest? = null
    private var isConnectedToBtDevice = false
    private var connectedDeviceName: String? = null

    /** Cooldown timestamp (SystemClock.elapsedRealtime) until which phantom
     *  gestures triggered by BT disconnect are suppressed. */
    private var phantomGestureCooldownUntil: Long = 0L

    /** Cooldown timestamp (SystemClock.elapsedRealtime) for suppressing ALL
     *  gestures after one executes. Prevents AVRCP feedback loop where
     *  restoring PLAYING state causes the earbud to re-send button events. */
    private var gestureCooldownUntil: Long = 0L

    /** Cooldown timestamp (SystemClock.elapsedRealtime) for suppressing code=354
     *  events during BT reconnect handshake. Transsion/Infinix BT stack fires
     *  phantom code=354 events when the headset reconnects. */
    private var transsionReconnectCooldownUntil: Long = 0L

    /** Last time (SystemClock.elapsedRealtime) a trusted event arrived: a standard
     *  media key (HEADSETHOOK, MEDIA_PLAY_PAUSE, etc.) or AVRCP command (MEDIA_NEXT,
     *  MEDIA_PREVIOUS). On Transsion, real earbud taps fire BOTH a standard media key
     *  AND code=354 simultaneously; phantom events fire only code=354. We check this
     *  to distinguish real from phantom. */
    private var lastTrustedEventTime: Long = 0L

    /** Debounce: the last gesture type that was handled and the time it was handled.
     *  Prevents the same gesture from being processed twice when both the AVRCP
     *  and AccessibilityService (code=354) paths fire for the same physical tap. */
    private var lastHandledGestureType: GestureType? = null
    private var lastHandledGestureTime: Long = 0L

    /** Dynamically registered receiver – works where manifest receivers don't (Android 14+ OEMs). */
    private var dynamicMediaButtonReceiver: BroadcastReceiver? = null

    /** Silent audio track: makes the system treat us as actively playing audio (needed for AVRCP routing). */
    private var silentAudioTrack: AudioTrack? = null
    /** Set true when we deliberately pause silent audio for a media-check probe.
     *  Prevents writeSilence() from auto-restarting during the probe. */
    @Volatile
    private var silentAudioPausedForCheck = false
    /** Runs silent audio writes on a dedicated background thread so they're
     *  immune to main-thread UI jank. Without this, Handler posts get delayed
     *  by layout/inflation on the main looper, the audio buffer drains, and
     *  the AudioTrack gets disabled by the system (restartIfDisabled). */
    private var silentAudioThread: HandlerThread? = null
    private var silentAudioHandler: Handler? = null

    // --- Background recording (triggered by double-tap) ---
    private var backgroundRecorder: MediaRecorder? = null
    private var backgroundRecordingPath: String? = null
    private var backgroundRecordingStartMs: Long = 0L

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(devices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "AudioDeviceCallback: devices added")
            updateConnectionState()
        }

        override fun onAudioDevicesRemoved(devices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "AudioDeviceCallback: devices removed")
            updateConnectionState()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "=== ProtectionForegroundService created ===")

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        gestureDetector = GestureDetector(
            onGesture = { gestureType -> handleGesture(gestureType) },
            onSlowDoubleArmed = {
                vibrateShort()
                runCatching {
                    Toast.makeText(this, "Tap again within 10s for SOS", Toast.LENGTH_SHORT).show()
                }
                // Grab GAIN audio focus during the 10s slow-double window so
                // the second earbud press routes to us. Restored from Round 7
                // approach — was removed in Round 8 because silent audio was
                // unstable (main-thread underruns broke AVRCP on Transsion).
                // Now that silent audio runs on a dedicated HandlerThread with
                // Process.THREAD_PRIORITY_AUDIO (Round 9), GAIN-level focus
                // works correctly without disrupting AVRCP routing.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    requestSlowDoubleAudioFocus()
                }

                // Schedule diagnostic state dump when the slow-double window expires
                // (10s from now). Helps debug why the second press doesn't arrive.
                focusTimerHandler.postDelayed({
                    dumpSlowDoubleDiagnostics()
                }, 10_000L)
            }
        )

        setupMediaSession()
        startSilentAudio() // makes system treat us as actively playing — critical for AVRCP routing
        registerDynamicReceiver()
        registerComponentReceiver()

        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        updateConnectionState()
    }

    private var isForeground = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}, flags=$flags, startId=$startId")

        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "ACTION_START received")
                if (!isForeground) {
                    startForeground(NOTIFICATION_ID, buildNotification())
                    isForeground = true
                }
            }

            ACTION_STOP -> {
                Log.d(TAG, "ACTION_STOP received — stopping service")
                stopSelf()
                return START_NOT_STICKY
            }

            MediaButtonReceiver.ACTION_FORWARD_KEY -> {
                Log.d(TAG, "ACTION_FORWARD_KEY received")
                forwardKeyEvent(intent)
            }

            "com.example.safeway.ACCESSIBILITY_KEY" -> {
                val action = intent.getIntExtra("key_action", -1)
                val code = intent.getIntExtra("key_code", -1)
                Log.d(TAG, "ACCESSIBILITY_KEY: action=$action, code=$code")
                processKeyEvent(android.view.KeyEvent(action, code))
            }
        }

        if (!isForeground) {
            Log.d(TAG, "startForeground fallback (null intent or unknown action)")
            startForeground(NOTIFICATION_ID, buildNotification())
            isForeground = true
        }

        updateConnectionState()
        updateOngoingNotification()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "onDestroy — releasing resources")
        stopSilentAudio()
        releaseBackgroundRecorder()
        // Abandon temporary audio focus if held
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusTimerHandler.removeCallbacksAndMessages(null)
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        }
        mediaSession?.release()
        unregisterDynamicReceiver()
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        audioManager.unregisterMediaButtonEventReceiver(
            ComponentName(this, MediaButtonReceiver::class.java)
        )
        gestureDetector.reset()
        super.onDestroy()
    }

    private fun releaseBackgroundRecorder() {
        isBackgroundRecording = false
        try {
            backgroundRecorder?.stop()
        } catch (_: Exception) { }
        try {
            backgroundRecorder?.reset()
            backgroundRecorder?.release()
        } catch (_: Exception) { }
        backgroundRecorder = null
    }

    // ------------------------------------------------------------------
    // Routing path 1: Dynamically registered BroadcastReceiver
    // ------------------------------------------------------------------

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerDynamicReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d(TAG, "dynamicReceiver: action=${intent.action}")
                if (intent.action != Intent.ACTION_MEDIA_BUTTON) return
                val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                }
                if (keyEvent != null) {
                    Log.d(TAG, "dynamicReceiver keyEvent: action=${keyEvent.action}, code=${keyEvent.keyCode}")
                    processKeyEvent(keyEvent)
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_MEDIA_BUTTON).apply {
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        dynamicMediaButtonReceiver = receiver
        Log.d(TAG, "dynamicReceiver registered")
    }

    private fun unregisterDynamicReceiver() {
        dynamicMediaButtonReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) { }
            dynamicMediaButtonReceiver = null
        }
    }

    // ------------------------------------------------------------------
    // Routing path 2: Direct AudioManager component registration
    // ------------------------------------------------------------------

    private fun registerComponentReceiver() {
        val cn = ComponentName(this, MediaButtonReceiver::class.java)
        audioManager.registerMediaButtonEventReceiver(cn)
    }

    // ------------------------------------------------------------------
    // Silent audio: makes the system consider us "actively playing"
    // Needed because Android only routes AVRCP to sessions that produce audio.
    // ------------------------------------------------------------------

    private fun startSilentAudio() {
        Log.d(TAG, "silentAudio: starting...")
        try {
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            if (minBufferSize <= 0) {
                Log.w(TAG, "silentAudio: getMinBufferSize returned $minBufferSize, using 4096")
            }
            val oneSecBytes = sampleRate * 2 // 16-bit mono = 2 bytes per sample
            val bufSize = minBufferSize.coerceAtLeast(oneSecBytes)

            val attr = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val fmt = AudioFormat.Builder()
                .setEncoding(audioFormat)
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .build()
            val track = AudioTrack.Builder()
                .setAudioAttributes(attr)
                .setAudioFormat(fmt)
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            silentAudioTrack = track

            // Pre-fill the entire buffer with silence BEFORE play() so there's
            // never an initial underrun — the AudioTrack starts with data ready.
            val prefill = ByteArray(oneSecBytes)
            var total = 0
            while (total < prefill.size) {
                val written = track.write(prefill, total, prefill.size - total)
                if (written < 0) break
                total += written
            }
            Log.d(TAG, "silentAudio: pre-filled $total bytes")

            track.play()
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                Log.w(TAG, "silentAudio: track didn't start, state=${track.playState}")
            }

            // Start the background thread AFTER pre-fill and play() so the
            // write loop picks up from a stable state.
            val thread = HandlerThread("silent-audio-writer", Process.THREAD_PRIORITY_AUDIO)
            thread.start()
            silentAudioThread = thread
            silentAudioHandler = Handler(thread.looper)
            writeSilence(track)

            Log.d(TAG, "silentAudio: playing (bufferSize=$bufSize, min=$minBufferSize)")
        } catch (e: Exception) {
            Log.e(TAG, "silentAudio: failed to start", e)
        }
    }

    /** Writes silence chunks on the dedicated audio thread at a rate that
     *  outpaces the drain. Each write is 4410 bytes (50ms at 44100Hz/16-bit/mono),
     *  posted every 50ms — 2x the drain rate. If a write fails (e.g. the track
     *  was disabled), we attempt a single restart. */
    private fun writeSilence(track: AudioTrack) {
        val bytesPerMs = 44100 * 2 / 1000  // 88 bytes/ms at 44100Hz/16-bit/mono
        val chunkSize = bytesPerMs * 50     // 4410 bytes = 50ms
        val silentBuf = ByteArray(chunkSize)
        val handler = silentAudioHandler ?: return
        handler.post(object : Runnable {
            override fun run() {
                if (silentAudioTrack == null) return
                if (silentAudioPausedForCheck) {
                    handler.postDelayed(this, 50)
                    return
                }
                try {
                    // Restart if the track entered a non-playing state (underrun)
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        Log.w(TAG, "silentAudio: track not playing — restarting")
                        track.play()
                    }
                    val result = track.write(silentBuf, 0, silentBuf.size)
                    if (result < 0) {
                        // write() returned an error (e.g. ERROR_DEAD_OBJECT from
                        // a disabled track). Restart before the next attempt.
                        Log.w(TAG, "silentAudio: write returned $result — restarting")
                        track.play()
                    }
                } catch (_: Exception) {
                    // track may have been released in stopSilentAudio()
                }
                handler.postDelayed(this, 50)
            }
        })
    }

    private fun stopSilentAudio() {
        silentAudioHandler?.removeCallbacksAndMessages(null)
        silentAudioHandler = null
        silentAudioThread?.let { thread ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                thread.quitSafely()
            } else {
                thread.quit()
            }
            silentAudioThread = null
        }
        silentAudioTrack?.let { track ->
            try {
                track.stop()
                track.release()
            } catch (_: Exception) { }
            silentAudioTrack = null
            Log.d(TAG, "silentAudio: stopped")
        }
    }

    private var focusReAcquireAttempts = 0
    private var focusStableSinceMs: Long = 0L
    private val focusReAcquireHandler = Handler(Looper.getMainLooper())
    private val focusTimerHandler = Handler(Looper.getMainLooper())
    /** Handler for diagnostic and AVRCP state toggle operations. */
    private val serviceHandler = Handler(Looper.getMainLooper())
    private var focusHeldTemporarily = false
    /** Transient audio focus request for slow-double window — doesn't
     *  block other apps (YouTube, Spotify) from taking focus. Built
     *  in setupMediaSession() on API 26+. */
    private var transientAudioFocusRequest: android.media.AudioFocusRequest? = null

    @RequiresApi(Build.VERSION_CODES.O)
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // If we're in the temporary post-gesture window, don't re-acquire
                // focus — the user is actively using another app (YouTube, Spotify,
                // etc.) and we shouldn't fight for it. Subsequent earbud events
                // will still arrive via the AccessibilityService code=354 path.
                if (focusHeldTemporarily) {
                    Log.d(TAG, "audio focus lost during temporary window — not re-acquiring")
                    return@OnAudioFocusChangeListener
                }

                focusReAcquireAttempts++
                Log.d(TAG, "Audio focus lost: $focusChange (attempt #$focusReAcquireAttempts)")

                // Don't re-acquire if we've already tried 3 times — avoid infinite tug-of-war
                if (focusReAcquireAttempts >= 3) {
                    Log.d(TAG, "focus re-acquire limit reached — giving up")
                    return@OnAudioFocusChangeListener
                }

                // Exponential backoff: 500ms, 1s, 2s
                val delayMs = 500L * (1 shl (focusReAcquireAttempts - 1))
                focusReAcquireHandler.postDelayed({
                    audioFocusRequest?.let {
                        val result = audioManager.requestAudioFocus(it)
                        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                            Log.d(TAG, "focus re-acquired after attempt #$focusReAcquireAttempts")
                        }
                    }
                }, delayMs)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                focusStableSinceMs = SystemClock.elapsedRealtime()
                // Reset counter — we've held focus for a stable period
                if (focusReAcquireAttempts > 0 && focusReAcquireAttempts < 3) {
                    Log.d(TAG, "Audio focus regained — resetting attempt counter")
                    focusReAcquireAttempts = 0
                }
            }
        }
    }

    /** Requests audio focus temporarily for FOCUS_HOLD_DURATION_MS after a gesture.
     *  This lets us capture subsequent AVRCP events without holding focus permanently
     *  and interfering with other media apps. */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestTemporaryAudioFocus() {
        focusTimerHandler.removeCallbacksAndMessages(null)
        focusReAcquireAttempts = 0
        audioFocusRequest?.let {
            val result = audioManager.requestAudioFocus(it)
            focusHeldTemporarily = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            if (focusHeldTemporarily) {
                Log.d(TAG, "temporary audio focus granted (${FOCUS_HOLD_DURATION_MS}ms window)")
            }
        }
        // Auto-release focus after the window expires
        focusTimerHandler.postDelayed({
            if (focusHeldTemporarily) {
                Log.d(TAG, "temporary audio focus window expired — abandoning")
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                focusHeldTemporarily = false
                focusReAcquireAttempts = 0
            }
        }, FOCUS_HOLD_DURATION_MS)
    }

    /** Requests AUDIOFOCUS_GAIN_TRANSIENT during the 10s slow-double window so
     *  the second earbud button press routes to us. Uses TRANSIENT (not GAIN) so
     *  other apps (YouTube, Instagram, Spotify) can take focus back when the user
     *  switches to them — the AVRCP PAUSED state (setAvrcpStateToPaused) is what
     *  actually resets the earbud firmware state machine; audio focus is only needed
     *  for routing priority. Auto-releases after 12s. Only for API 26+. */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestSlowDoubleAudioFocus() {
        focusTimerHandler.removeCallbacksAndMessages(null)
        focusReAcquireAttempts = 0
        val req = transientAudioFocusRequest
        val result = req?.let { audioManager.requestAudioFocus(it) }
        focusHeldTemporarily = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        Log.d(TAG, "slow-double audio focus request: result=$result (AUDIOFOCUS_GAIN_TRANSIENT)")

        // Auto-release after the slow-double window. Use SLOW_DOUBLE_FOCUS_HOLD_MS
        // (12s) rather than FOCUS_HOLD_DURATION_MS (30s) since the slow-double
        // window is at most 10s — we don't need to hold focus after the window.
        focusTimerHandler.postDelayed({
            // Restore PLAYING state since the slow-double window expired
            // without a second press arriving.
            restoreAvrcpStateToPlaying()
            if (focusHeldTemporarily) {
                val req = transientAudioFocusRequest
                req?.let { audioManager.abandonAudioFocusRequest(it) }
                focusHeldTemporarily = false
                focusReAcquireAttempts = 0
                Log.d(TAG, "slow-double audio focus released (AUDIOFOCUS_GAIN_TRANSIENT)")
            }
        }, SLOW_DOUBLE_FOCUS_HOLD_MS)
    }

    private fun setupMediaSession() {
        Log.d(TAG, "setupMediaSession")

        // Create the AudioFocusRequest but do NOT request focus yet — we only
        // grab focus temporarily when a gesture is detected. This prevents SafeWay
        // from interfering with other media apps (YouTube, Spotify) while protection
        // is active in the background.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            audioFocusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .setAcceptsDelayedFocusGain(true)
                .build()
            // Transient request for the slow-double window — doesn't hold
            // exclusive focus so other apps (YouTube, Spotify) aren't affected.
            transientAudioFocusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .build()
            // Not requesting focus here — requestTemporaryAudioFocus() does it on-demand
        } else {
            @Suppress("DEPRECATION")
            // Pre-O: no temporary focus mechanism, keep existing behavior
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }

        mediaSession = MediaSession(this, TAG).apply {
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    Log.d(TAG, "MediaSession.onMediaButtonEvent received")
                    val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonIntent.getParcelableExtra(
                            Intent.EXTRA_KEY_EVENT,
                            KeyEvent::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }

                    if (keyEvent != null) {
                        Log.d(TAG, "MediaSession keyEvent: action=${keyEvent.action}, code=${keyEvent.keyCode}")
                        processKeyEvent(keyEvent)
                    } else {
                        Log.d(TAG, "MediaSession: keyEvent was null in intent")
                    }
                    return true
                }

                override fun onSkipToNext() {
                    Log.d(TAG, "onSkipToNext (AVRCP next — double tap)")
                    lastTrustedEventTime = SystemClock.elapsedRealtime()
                    gestureDetector.onAvrcpCommand(KeyEvent.KEYCODE_MEDIA_NEXT)
                }

                override fun onSkipToPrevious() {
                    Log.d(TAG, "onSkipToPrevious (AVRCP previous — triple tap)")
                    lastTrustedEventTime = SystemClock.elapsedRealtime()
                    gestureDetector.onAvrcpCommand(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                }

                override fun onPlay() {
                    Log.d(TAG, "onPlay received")
                }

                override fun onPause() {
                    Log.d(TAG, "onPause received")
                }
            })

            setPlaybackState(
                PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, 0, 0f)
                    .setActions(
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    )
                    .build()
            )

            // Set metadata so the system treats us as a real media session
            setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "Shield Protection")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "SafeWay")
                    .build()
            )

            // Flags: tell the system we handle media buttons (deprecated but still needed on many OEMs)
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            // Explicit MediaButtonReceiver PendingIntent
            val mi = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                component = ComponentName(this@ProtectionForegroundService, MediaButtonReceiver::class.java)
            }
            val pi = PendingIntent.getBroadcast(
                this@ProtectionForegroundService, 0, mi,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setMediaButtonReceiver(pi)

            isActive = true
            Log.d(TAG, "MediaSession active: $isActive")
        }
    }

    // ------------------------------------------------------------------
    // Key event processing
    // ------------------------------------------------------------------

    private fun forwardKeyEvent(intent: Intent) {
        val action = intent.getIntExtra(MediaButtonReceiver.EXTRA_KEY_EVENT_ACTION, -1)
        val code = intent.getIntExtra(MediaButtonReceiver.EXTRA_KEY_CODE, -1)
        Log.d(TAG, "forwardKeyEvent: action=$action, code=$code")
        processKeyEvent(KeyEvent(action, code))
    }

    private fun processKeyEvent(keyEvent: KeyEvent) {
        Log.d(TAG, "processKeyEvent: action=${keyEvent.action}, code=${keyEvent.keyCode}, device=${keyEvent.device?.name}, source=0x${Integer.toHexString(keyEvent.source)}")

        // Also log the unhandled code unconditionally so we can detect ANY
        // keycode that arrives during the slow-double window, even ones we
        // don't recognize or filter out.
        if (keyEvent.action == KeyEvent.ACTION_DOWN) {
            Log.d(TAG, "UNCONDITIONAL KEY DOWN: code=${keyEvent.keyCode} (0x${Integer.toHexString(keyEvent.keyCode)}), time=${SystemClock.elapsedRealtime()}")
        }

        // Suppress phantom key events after BT disconnect
        if (SystemClock.elapsedRealtime() < phantomGestureCooldownUntil) {
            Log.d(TAG, "phantom gesture cooldown active — ignoring key code=${keyEvent.keyCode}")
            return
        }

        if (keyEvent.action != KeyEvent.ACTION_DOWN) return

        when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                lastTrustedEventTime = SystemClock.elapsedRealtime()
                // Only treat as a gesture tap if an audio device is actually connected.
                // Many devices generate spurious media button events during normal
                // system UI interactions (navigation gestures, USB events, etc.).
                if (!isConnectedToBtDevice && !isWiredHeadsetConnected()) {
                    Log.d(TAG, "ignoring code=${keyEvent.keyCode} — no audio device connected")
                    return
                }
                Log.d(TAG, "KEY_EVENT → gesture detector (code=${keyEvent.keyCode})")
                gestureDetector.onKeyEvent(keyEvent)

                // Set AVRCP state to PAUSED and keep it there until the second
                // press arrives or the window expires. Harmonics earbuds implement
                // a play/pause toggle in firmware: they send MEDIA_PAUSE (127) and
                // expect the phone to pause. By staying PAUSED, the earbud's
                // internal state stays in sync and it will accept the next press
                // as a fresh MEDIA_PLAY or MEDIA_PAUSE event.
                setAvrcpStateToPaused()
            }
            KEYCODE_TRANSSION_BT_BUTTON -> {
                // Vendor key 354 can be triggered by system UI navigation gestures
                // on some OEMs (Transsion/Infinix) — ignore unless BT device is connected
                if (!isConnectedToBtDevice) {
                    Log.d(TAG, "ignoring code=354 — no BT device connected")
                    return
                }
                // Suppress code=354 burst during BT reconnect handshake
                if (SystemClock.elapsedRealtime() < transsionReconnectCooldownUntil) {
                    Log.d(TAG, "ignoring code=354 — reconnect cooldown active")
                    return
                }
                // On Transsion, real earbud taps fire BOTH a standard media key (AVRCP)
                // AND code=354 simultaneously. Phantom events fire only code=354.
                // If no trusted event arrived recently, this is phantom — reset the
                // gesture detector to clear any accumulated state (e.g., slow-double
                // timer that would trigger SOS on the next phantom event).
                if (SystemClock.elapsedRealtime() - lastTrustedEventTime > CODE354_TRUST_WINDOW_MS) {
                    Log.d(TAG, "code=354 phantom — no recent trusted event, resetting detector")
                    gestureDetector.reset()
                    return
                }
                Log.d(TAG, "code=354 → gesture detector (BT device connected)")
                gestureDetector.onKeyEvent(keyEvent)
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                lastTrustedEventTime = SystemClock.elapsedRealtime()
                Log.d(TAG, "MEDIA_NEXT → AVRCP double tap")
                gestureDetector.onAvrcpCommand(KeyEvent.KEYCODE_MEDIA_NEXT)
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                lastTrustedEventTime = SystemClock.elapsedRealtime()
                Log.d(TAG, "MEDIA_PREVIOUS → AVRCP triple tap")
                gestureDetector.onAvrcpCommand(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
            else -> Log.d(TAG, "processKeyEvent: unhandled code=${keyEvent.keyCode}, hex=0x${Integer.toHexString(keyEvent.keyCode)}")
        }
    }

    // ------------------------------------------------------------------
    // BT connection state
    // ------------------------------------------------------------------

    private fun updateConnectionState() {
        val btDevice = findConnectedBtDevice()
        val wasConnected = isConnectedToBtDevice
        isConnectedToBtDevice = btDevice != null
        connectedDeviceName = btDevice

        if (wasConnected != isConnectedToBtDevice) {
            Log.d(TAG, "BT connection changed: connected=$isConnectedToBtDevice, device=$connectedDeviceName")
            if (wasConnected && !isConnectedToBtDevice) {
                // BT disconnected — clear pending gesture state and suppress phantom events
                gestureDetector.reset()
                phantomGestureCooldownUntil = SystemClock.elapsedRealtime() + PHANTOM_COOLDOWN_MS
                Log.d(TAG, "phantom gesture cooldown activated: ${PHANTOM_COOLDOWN_MS}ms")
            } else if (!wasConnected && isConnectedToBtDevice) {
                // BT reconnected — suppress code=354 burst during handshake
                transsionReconnectCooldownUntil = SystemClock.elapsedRealtime() + PHANTOM_COOLDOWN_MS
                Log.d(TAG, "transsion reconnect cooldown activated: ${PHANTOM_COOLDOWN_MS}ms")
            }
        }

        ProtectionPrefs.setPairedDeviceName(this, connectedDeviceName)
        updateOngoingNotification()
    }

    /** Returns true if a non-SafeWay app is actively playing audio (Spotify, YouTube,
     *  browser video, gallery video, etc.).
     *
     *  Strategy by API level:
     *  - API 21-30: Enumerate active MediaSessions via getActiveSessions(null).
     *    RELIABLE — other apps' sessions are visible.
     *  - API 31+:   getActiveSessions(null) throws. Try with our own ComponentName.
     *    If that fails (no NotificationListener), fall back to getActivePlaybackConfigurations
     *    with a robust isMusicActive() check: longer pause (300ms), retry once to
     *    filter out OEM false-positives (Transsion/Infinix BT SCO quirk).
     *  - API 24-28 fallback: getActivePlaybackConfigurations returns ALL apps' configs.
     *    mediaCount > 1 means another app is playing.
     *
     *  On all API levels, if detection is inconclusive, returns false (don't suppress). */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun isOtherAppPlayingAudio(): Boolean {
        // ── Approach 1: MediaSession enumeration (API 21+ for all devices) ──
        try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val controllers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // API 31+: null throws. Use our component — works on some OEMs even
                // without NotificationListenerService, others return empty.
                msm.getActiveSessions(ComponentName(this, javaClass))
            } else {
                @Suppress("DEPRECATION")
                msm.getActiveSessions(null)
            }
            if (controllers != null) {
                for (controller in controllers) {
                    if (controller.packageName != packageName) {
                        val state = controller.playbackState
                        if (state != null && state.state == PlaybackState.STATE_PLAYING) {
                            Log.d(TAG, "isOtherAppPlayingAudio: detected ${controller.packageName}")
                            return true
                        }
                    }
                }
                // Enumeration worked and found no other playing sessions — reliable result
                return false
            }
        } catch (e: Exception) {
            Log.d(TAG, "isOtherAppPlayingAudio: MediaSession exception (${e.message})")
            // Fall through to next approach
        }

        // ── Approach 2: getActivePlaybackConfigurations (API 24-28) ──
        // On pre-Q (API 28-), configs include ALL apps. On Q+ only our own.
        try {
            val configs = audioManager.getActivePlaybackConfigurations()
            if (configs.isEmpty()) return false

            var mediaCount = 0
            for (config in configs) {
                if (config.audioAttributes.usage == android.media.AudioAttributes.USAGE_MEDIA ||
                    config.audioAttributes.usage == android.media.AudioAttributes.USAGE_GAME) {
                    mediaCount++
                }
            }
            if (mediaCount == 0) return false

            // API 24-28: configs include all apps — mediaCount > 1 = another app playing
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                return mediaCount > 1
            }

            // API 29+: only our configs visible (mediaCount >= 1 = our silent audio).
            // Brief pause/check/resume via isMusicActive() with OEM quirk handling.
            // We already know mediaCount >= 1 (our silent audio is playing).
            silentAudioTrack?.let { track ->
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) return false
                silentAudioPausedForCheck = true
                track.pause()
                Thread.sleep(300)  // longer sleep for reliable isMusicActive() update
                val stillActive = audioManager.isMusicActive()
                track.play()
                silentAudioPausedForCheck = false

                if (!stillActive) return false

                // isMusicActive() returned true even after our audio paused.
                // On some OEMs (Transsion/Infinix) this false-positives due to
                // BT SCO connection. Verify with a second pause to confirm.
                // If both checks return true, another app is genuinely playing.
                silentAudioPausedForCheck = true
                track.pause()
                Thread.sleep(500)  // longer second check
                val stillActive2 = audioManager.isMusicActive()
                track.play()
                silentAudioPausedForCheck = false

                if (!stillActive2) {
                    Log.d(TAG, "isOtherAppPlayingAudio: isMusicActive false-positive " +
                            "(first check passed, second didn't)")
                }
                return stillActive2
            }
        } catch (_: Exception) { }
        return false
    }

    /** Returns true if a wired headset or USB audio device is plugged in. */
    private fun isWiredHeadsetConnected(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any { device ->
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }
    }

    private fun findConnectedBtDevice(): String? {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (device in devices) {
            if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    device.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            ) {
                val name = device.productName?.toString() ?: ""
                if (name.isNotBlank()) {
                    Log.d(TAG, "Found BT device: type=${device.type}, name=$name")
                    return name
                }
            }
        }
        Log.d(TAG, "No BT audio device found (${devices.size} devices total)")
        return null
    }

    // ------------------------------------------------------------------
    // Gesture handling / action execution
    // ------------------------------------------------------------------

    private fun handleGesture(gestureType: GestureType) {
        val now = SystemClock.elapsedRealtime()

        // Same-type debounce: ignore the exact same gesture within 1s.
        // Catches dual-path scenarios (AccessibilityService + MediaSession)
        // that fire identical gesture types from the same physical tap.
        if (gestureType == lastHandledGestureType && now - lastHandledGestureTime < 1000) {
            Log.d(TAG, "same-type debounce: ignoring duplicate $gestureType")
            return
        }
        lastHandledGestureType = gestureType
        lastHandledGestureTime = now

        // Suppress phantom gestures during BT disconnect cooldown period
        if (now < phantomGestureCooldownUntil) {
            Log.d(TAG, "phantom cooldown active — suppressing $gestureType")
            return
        }

        // Global gesture cooldown: suppress all gestures for GESTURE_COOLDOWN_MS
        // after any gesture executes. Prevents AVRCP feedback loop where
        // restoring PLAYING state causes the earbud to re-send button events.
        if (now < gestureCooldownUntil) {
            Log.d(TAG, "gesture cooldown active — suppressing $gestureType (${gestureCooldownUntil - now}ms left)")
            return
        }

        // Suppress fast gestures (double/triple press) when another app is actively
        // playing audio/video. These can be triggered accidentally by media playback
        // controls or system navigation. SLOW_DOUBLE_PRESS requires the user to click,
        // wait 3-10s, and click again — deliberate action that should never be suppressed.
        // Skip the check if we hold temporary audio focus from a recent gesture —
        // AUDIOFOCUS_GAIN pauses any other media app, so isOtherAppPlayingAudio()
        // would be unreliable (notification sounds, BT state, or system audio on
        // Transsion can cause false positive isMusicActive() results).
        if (gestureType != GestureType.SLOW_DOUBLE_PRESS &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !focusHeldTemporarily && isOtherAppPlayingAudio()) {
            Log.d(TAG, "another app is playing audio — suppressing $gestureType")
            return
        }

        val action = when (gestureType) {
            GestureType.TRIPLE_PRESS -> ProtectionPrefs.getTriplePressAction(this)
            GestureType.DOUBLE_PRESS -> ProtectionPrefs.getDoublePressAction(this)
            GestureType.SLOW_DOUBLE_PRESS -> ProtectionPrefs.getSlowDoublePressAction(this)
        }
        Log.d(TAG, "GESTURE TRIGGERED: $gestureType → $action")

        // Set cooldown BEFORE restoring AVRCP state or grabbing focus —
        // those operations can cause earbuds to re-send button events,
        // creating a phantom feedback loop.
        gestureCooldownUntil = SystemClock.elapsedRealtime() + GESTURE_COOLDOWN_MS

        // Restore AVRCP state to PLAYING — gesture was detected, no longer
        // need the paused state for earbud state machine synchronization.
        restoreAvrcpStateToPlaying()

        // Grab temporary audio focus so subsequent AVRCP events route to us
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requestTemporaryAudioFocus()
        }

        // Wake screen so the user sees something happened
        wakeScreen()

        // Auditory feedback — distinct tones for each gesture type
        playFeedbackTone(gestureType)

        // Visible feedback — shows a toast so the user knows the gesture was
        // detected and what action it triggered. For START_RECORDING (which
        // toggles), reflect the actual state rather than the static action name.
        runCatching {
            val displayText = if (action == EmergencyAction.START_RECORDING) {
                if (backgroundRecorder != null) "Stop Recording" else "Start Recording"
            } else {
                action.displayName
            }
            Toast.makeText(this, "$gestureType → $displayText", Toast.LENGTH_SHORT).show()
        }

        ProtectionPrefs.addTriggerEvent(this, TriggerEvent(gestureType, action))
        vibrateFeedback()
        updateNotificationWithAction(action)
        executeAction(action)
    }

    private fun updateNotificationWithAction(triggeredAction: EmergencyAction) {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(triggeredAction))
        } catch (_: Exception) { }
    }

    private fun vibrateFeedback() {
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (vibrator.hasVibrator()) {
                Log.d(TAG, "vibrateFeedback: buzzing")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0, 100, 100, 100, 100, 100), intArrayOf(0, 255, 0, 255, 0, 255), -1)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 100, 100, 100, 100, 100), -1)
                }
            } else {
                Log.d(TAG, "vibrateFeedback: no vibrator available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "vibrateFeedback failed", e)
        }
    }

    /** Short vibration to confirm a single tap armed the slow-double timer. */
    private fun vibrateShort() {
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (vibrator.hasVibrator()) {
                Log.d(TAG, "vibrateShort: slow-double armed")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50)
                }
            }
        } catch (_: Exception) { }
    }

    private fun executeAction(action: EmergencyAction) {
        Log.d(TAG, "executeAction: $action")
        when (action) {
            EmergencyAction.SOS_ALERT -> {
                Log.d(TAG, "SOS_ALERT: calling EmergencyAlertDispatcher")

                // Persistent notification + repeating tone so the user knows
                // SOS is in flight even if the phone is locked/across the room
                showSosAlertNotification("Sending SOS...")
                playSosLoopTone()

                EmergencyAlertDispatcher.sendNow(this) { _, message ->
                    Log.d(TAG, "SOS result: $message")
                    showSosAlertNotification("Done: $message")
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(this@ProtectionForegroundService, message, Toast.LENGTH_LONG).show()
                    }
                    // Auto-dismiss the alert notification after 10s
                    Handler(Looper.getMainLooper()).postDelayed({
                        dismissSosAlertNotification()
                    }, 10_000)
                }
            }

            EmergencyAction.START_RECORDING -> {
                if (backgroundRecorder != null) {
                    Log.d(TAG, "START_RECORDING: stopping background recording")
                    stopBackgroundRecording()
                } else {
                    Log.d(TAG, "START_RECORDING: starting background recording")
                    startBackgroundRecording()
                }
            }

            EmergencyAction.SHARE_LOCATION -> {
                Log.d(TAG, "SHARE_LOCATION: calling EmergencyAlertDispatcher")
                Toast.makeText(this, "Sharing location...", Toast.LENGTH_SHORT).show()
                EmergencyAlertDispatcher.sendNow(this) { _, message ->
                    Log.d(TAG, "Location share result: $message")
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(this@ProtectionForegroundService, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Background recording (double-tap toggles on/off)
    // ------------------------------------------------------------------

    private fun startBackgroundRecording() {
        Log.d(TAG, "startBackgroundRecording")
        isBackgroundRecording = true

        val dir = File(getExternalFilesDir(null), "SafeWay/voice_notes")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "bg_recording_${System.currentTimeMillis()}.m4a")

        try {
            @Suppress("DEPRECATION")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            backgroundRecorder = recorder
            backgroundRecordingPath = file.absolutePath
            backgroundRecordingStartMs = System.currentTimeMillis()

            Log.d(TAG, "background recording started: ${file.absolutePath}")

            // Update notification to show recording state
            updateOngoingNotification()
            runCatching {
                Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "startBackgroundRecording failed", e)
            isBackgroundRecording = false
            backgroundRecorder = null
            backgroundRecordingPath = null
        }
    }

    private fun stopBackgroundRecording() {
        Log.d(TAG, "stopBackgroundRecording")
        isBackgroundRecording = false
        val recorder = backgroundRecorder ?: return
        val path = backgroundRecordingPath ?: return

        val durationSec = ((System.currentTimeMillis() - backgroundRecordingStartMs) / 1000).toInt()

        try {
            recorder.stop()
            Log.d(TAG, "background recording stopped, duration=${durationSec}s")
        } catch (e: Exception) {
            Log.e(TAG, "stopBackgroundRecording error", e)
            // File may be corrupt — delete it
            try { File(path).delete() } catch (_: Exception) {}
            backgroundRecorder = null
            backgroundRecordingPath = null
            updateOngoingNotification()
            return
        } finally {
            recorder.reset()
            recorder.release()
            backgroundRecorder = null
        }

        // Save as pending draft so LogIncidentActivity can pick it up
        ProtectionPrefs.savePendingVoiceNote(this, path, durationSec.coerceAtLeast(1))
        backgroundRecordingPath = null
        backgroundRecordingStartMs = 0L

        Log.d(TAG, "pending voice note saved: path=$path, duration=$durationSec")
        updateOngoingNotification()

        runCatching {
            Toast.makeText(this, "Recording saved — open Log Incident to finish", Toast.LENGTH_LONG).show()
        }
    }

    /** Wakes the screen briefly so the user knows a gesture was registered. */
    private fun wakeScreen() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "$TAG:wake"
            )
            // Acquire with a 2-second timeout — the system auto-releases after
            // the timeout. Do NOT call release() here; doing so would cancel the
            // timeout immediately and the screen would flash but not stay on.
            wl.acquire(2000)
        } catch (_: Exception) { }
    }

    /** Plays a short tone through the notification audio channel.
     *  Ascending beep for recording start, descending for stop,
     *  urgent tone for SOS. Runs on a background thread to avoid
     *  blocking the main thread. */
    private fun playFeedbackTone(gestureType: GestureType) {
        Thread {
            try {
                val sampleRate = 22050
                val durationSec = 0.25
                val numSamples = (sampleRate * durationSec).toInt()
                val samples = ShortArray(numSamples)

                val (startFreq, endFreq) = when (gestureType) {
                    GestureType.DOUBLE_PRESS -> 800.0 to 1200.0  // ascending
                    GestureType.TRIPLE_PRESS -> 600.0 to 900.0   // ascending, lower
                    GestureType.SLOW_DOUBLE_PRESS -> 500.0 to 1500.0  // urgent rising sweep
                }

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val freq = startFreq + (endFreq - startFreq) * (t / durationSec)
                    val sample = (Math.sin(2.0 * Math.PI * freq * t) * Short.MAX_VALUE * 0.4).toInt()
                    samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(numSamples * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(samples, 0, numSamples)
                track.play()
                Thread.sleep((durationSec * 1000).toLong() + 150)
                track.release()
            } catch (_: Exception) { }
        }.apply { isDaemon = true }.start()
    }

    /** Plays a repeating two-tone siren pattern for SOS alerts.
     *  Longer and more noticeable than the short confirmation beep —
     *  intended to be heard from across the room. Repeats 3 times. */
    private fun playSosLoopTone() {
        Thread {
            try {
                val sampleRate = 22050
                val toneDurationSec = 0.5  // 500ms per tone
                val numSamples = (sampleRate * toneDurationSec).toInt()
                val repeats = 3

                for (r in 0 until repeats) {
                    for (freq in listOf(880.0, 1100.0)) {  // alternating two-tone siren
                        val samples = ShortArray(numSamples)
                        for (i in 0 until numSamples) {
                            val t = i.toDouble() / sampleRate
                            val sample =
                                (Math.sin(2.0 * Math.PI * freq * t) * Short.MAX_VALUE * 0.5).toInt()
                            samples[i] = sample.coerceIn(
                                Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()
                            ).toShort()
                        }
                        val track = AudioTrack.Builder()
                            .setAudioAttributes(
                                android.media.AudioAttributes.Builder()
                                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build()
                            )
                            .setAudioFormat(
                                AudioFormat.Builder()
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setSampleRate(sampleRate)
                                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                    .build()
                            )
                            .setBufferSizeInBytes(numSamples * 2)
                            .setTransferMode(AudioTrack.MODE_STATIC)
                            .build()
                        track.write(samples, 0, numSamples)
                        track.play()
                        Thread.sleep((toneDurationSec * 1000).toLong())
                        track.release()
                    }
                    // Pause between repeats
                    if (r < repeats - 1) Thread.sleep(200)
                }
            } catch (_: Exception) { }
        }.apply { isDaemon = true }.start()
    }

    /** Logs key diagnostic state at the end of a slow-double window when
     *  the second press didn't arrive. Helps distinguish between:
     *  - Earbuds not sending the second press (no log at all)
     *  - System routing it elsewhere (no processKeyEvent call)
     *  - Our code filtering it (processKeyEvent called but ignored) */
    private fun dumpSlowDoubleDiagnostics() {
        val btConnected = findConnectedBtDevice()
        val mediaSessionActive = mediaSession?.isActive ?: false
        val trackState = silentAudioTrack?.playState ?: -1
        val totalDevices = try {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).size
        } catch (_: Exception) { -1 }

        Log.d(TAG, "=== SLOW-DOUBLE DIAGNOSTIC ===")
        Log.d(TAG, "BT connected: ${btConnected != null}, device=$btConnected")
        Log.d(TAG, "isConnectedToBtDevice flag: $isConnectedToBtDevice")
        Log.d(TAG, "MediaSession active: $mediaSessionActive")
        Log.d(TAG, "Silent audio track state: $trackState (3=PLAYING)")
        Log.d(TAG, "Audio output devices count: $totalDevices")
        Log.d(TAG, "focusHeldTemporarily: $focusHeldTemporarily")
        Log.d(TAG, "focusReAcquireAttempts: $focusReAcquireAttempts")
        Log.d(TAG, "silentAudioPausedForCheck: $silentAudioPausedForCheck")
        Log.d(TAG, "Dynamic receiver registered: ${dynamicMediaButtonReceiver != null}")

        // Log all active audio playback configurations for debugging
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val configs = audioManager.getActivePlaybackConfigurations()
                Log.d(TAG, "Active playback configs count: ${configs.size}")
                for ((i, config) in configs.withIndex()) {
                    val usage = config.audioAttributes.usage
                    Log.d(TAG, "  config[$i]: usage=$usage")
                }
            } catch (_: Exception) { }
        }
        Log.d(TAG, "=== END DIAGNOSTIC ===")
    }

    /** Sets AVRCP PlaybackState to PAUSED after the first earbud press.
     *  Harmonics earbuds implement a play/pause toggle in firmware: they send
     *  MEDIA_PAUSE for the first press and expect the phone to pause. By keeping
     *  the state at PAUSED (instead of toggling back), the earbud's internal
     *  state machine stays in "paused" and accepts the next press as a new
     *  MEDIA_PLAY/MEDIA_PAUSE event. The PLAYING state is restored when a
     *  gesture is detected or the slow-double window expires. */
    private fun setAvrcpStateToPaused() {
        val session = mediaSession ?: return
        val pausedState = PlaybackState.Builder()
            .setState(PlaybackState.STATE_PAUSED, 0, 0f)
            .setActions(
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS
            )
            .build()
        session.setPlaybackState(pausedState)
        Log.d(TAG, "AVRCP state → PAUSED (earbud state machine reset — staying paused)")
    }

    /** Restores AVRCP PlaybackState to PLAYING. Called when a gesture is
     *  detected or the slow-double window expires. */
    private fun restoreAvrcpStateToPlaying() {
        val session = mediaSession ?: return
        val playingState = PlaybackState.Builder()
            .setState(PlaybackState.STATE_PLAYING, 0, 0f)
            .setActions(
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS
            )
            .build()
        session.setPlaybackState(playingState)
        Log.d(TAG, "AVRCP state → PLAYING (restored)")
    }

    // ------------------------------------------------------------------
    // Foreground notification
    // ------------------------------------------------------------------

    private fun buildNotification(triggeredAction: EmergencyAction? = null): Notification {
        createChannel()

        val homeIntent = Intent(this, HomeActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, homeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ProtectionForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = when {
            backgroundRecorder != null -> "Recording…"
            triggeredAction != null -> "Last trigger: ${triggeredAction.displayName}"
            else -> getString(R.string.notification_protection_text)
        }

        val priority = if (backgroundRecorder != null) {
            NotificationCompat.PRIORITY_HIGH
        } else {
            NotificationCompat.PRIORITY_LOW
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setPriority(priority)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.stop),
                stopIntent
            )
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    /** Shows a high-priority heads-up notification for SOS dispatch.
     *  Uses a dedicated channel with sound & vibration so it cuts through
     *  even when the phone is locked or across the room. */
    private fun showSosAlertNotification(status: String) {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(this, SOS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alert)
                .setContentTitle("SOS Alert")
                .setContentText(status)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .build()
            nm.notify(SOS_NOTIFICATION_ID, notification)
        } catch (_: Exception) { }
    }

    private fun dismissSosAlertNotification() {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(SOS_NOTIFICATION_ID)
        } catch (_: Exception) { }
    }

    private fun updateOngoingNotification() {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification())
        } catch (_: Exception) {
            // Notification update failed — service continues regardless.
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val protectionChannel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.bt_protection_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(protectionChannel)

        // SOS alert channel — IMPORTANCE_HIGH so it heads-up displays with sound
        // even when the phone is locked or across the room.
        val sosChannel = NotificationChannel(
            SOS_CHANNEL_ID,
            "SOS Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            setSound(
                android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setBypassDnd(true)
            }
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(sosChannel)
    }

    companion object {
        /** True while a background recording is actively in progress.
         *  Queried by other components (e.g., LogIncidentActivity) to show
         *  recording status. */
        @Volatile
        var isBackgroundRecording: Boolean = false

        private const val CHANNEL_ID = "shield_bt_protection"
        private const val NOTIFICATION_ID = 8001
        private const val SOS_CHANNEL_ID = "shield_sos_alerts"
        private const val SOS_NOTIFICATION_ID = 8002
        private const val TAG = "ShieldBT.Service"
        private const val PHANTOM_COOLDOWN_MS = 3000L
        /** After any gesture executes, suppress all subsequent gestures for this
         *  duration. Prevents AVRCP feedback loop: restoring PlaybackState to
         *  PLAYING and grabbing audio focus can cause earbuds to re-send button
         *  events, creating phantom triggers. */
        private const val GESTURE_COOLDOWN_MS = 3000L
        /** If code=354 arrives without a standard media key event within this
         *  window, it's treated as a phantom event and ignored. Real taps on
         *  Transsion fire BOTH paths simultaneously. */
        private const val CODE354_TRUST_WINDOW_MS = 3000L
        private const val FOCUS_HOLD_DURATION_MS = 30_000L
        /** Duration to hold transient audio focus during the slow-double press
         *  window. Must be >= SLOW_DOUBLE_WINDOW_MS (10s) so the second click
         *  is captured. We add 2s buffer for safety. */
        private const val SLOW_DOUBLE_FOCUS_HOLD_MS = 12_000L

        /** Vendor-specific key code used by Transsion/Infinix BT stack (observed
         *  on Infinix Smart 9 with Harmonics earbuds via SCO). Must match the
         *  constant in ProtectionAccessibilityService. */
        const val KEYCODE_TRANSSION_BT_BUTTON = 354

        const val ACTION_START = "com.example.safeway.PROTECTION_START"
        const val ACTION_STOP = "com.example.safeway.PROTECTION_STOP"

        fun start(context: Context) {
            Log.d(TAG, "companion start()")
            val intent = Intent(context, ProtectionForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Starts the service if protection is enabled. Safe to call repeatedly —
         *  onStartCommand handles duplicate starts. Use in activities to recover
         *  from process death where START_STICKY was suppressed by the OEM. */
        fun ensureRunning(context: Context) {
            if (ProtectionPrefs.isEnabled(context)) {
                Log.d(TAG, "ensureRunning: protection enabled, starting service")
                start(context)
            } else {
                Log.d(TAG, "ensureRunning: protection not enabled, skipping")
            }
        }

        fun stop(context: Context) {
            Log.d(TAG, "companion stop()")
            context.stopService(Intent(context, ProtectionForegroundService::class.java))
        }

        fun isConnectedToDevice(context: Context): Boolean {
            val am = context.getSystemService(AUDIO_SERVICE) as AudioManager
            val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val btDevices = devices.filter { d ->
                d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        d.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            }
            btDevices.forEach { d ->
                Log.d(TAG, "isConnectedToDevice: found type=${d.type}, name=${d.productName}")
            }
            if (btDevices.isEmpty()) {
                Log.d(TAG, "isConnectedToDevice: no BT audio devices (${devices.size} total audio outputs)")
            }
            return btDevices.isNotEmpty()
        }
    }
}
