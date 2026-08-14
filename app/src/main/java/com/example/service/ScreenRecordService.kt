package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.AudioSourceType
import com.example.data.RecordingEntity
import com.example.data.RecordingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class RecordServiceState {
    object Idle : RecordServiceState()
    data class Countdown(val secondsRemaining: Int) : RecordServiceState()
    data class Recording(val durationMs: Long, val isPaused: Boolean = false) : RecordServiceState()
    data class Completed(val fileUri: String, val durationMs: Long) : RecordServiceState()
    data class Error(val message: String) : RecordServiceState()
}

class ScreenRecordService : Service() {

    companion object {
        private const val TAG = "ScreenRecordService"
        private const val CHANNEL_ID = "screen_record_service_channel"
        private const val NOTIFICATION_ID = 8881

        const val ACTION_START = "com.example.action.START_RECORDING"
        const val ACTION_STOP = "com.example.action.STOP_RECORDING"
        const val ACTION_PAUSE = "com.example.action.PAUSE_RECORDING"
        const val ACTION_RESUME = "com.example.action.RESUME_RECORDING"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_WIDTH = "extra_width"
        const val EXTRA_HEIGHT = "extra_height"
        const val EXTRA_FPS = "extra_fps"
        const val EXTRA_BITRATE_MBPS = "extra_bitrate_mbps"
        const val EXTRA_AUDIO_SOURCE = "extra_audio_source"
        const val EXTRA_CODEC = "extra_codec"
        const val EXTRA_COUNTDOWN = "extra_countdown"

        private val _serviceState = MutableStateFlow<RecordServiceState>(RecordServiceState.Idle)
        val serviceState: StateFlow<RecordServiceState> = _serviceState.asStateFlow()
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null

    private var recordWidth = 1080
    private var recordHeight = 1920
    private var recordFps = 60
    private var recordBitrateMbps = 16
    private var audioSourceType = AudioSourceType.MIC.name
    private var videoCodec = "video/avc"

    private var outputFile: File? = null
    private var recordingStartTimeMs = 0L
    private var totalPausedDurationMs = 0L
    private var pauseStartTimeMs = 0L
    private var isPaused = false
    private var isRecording = false
    private var isStopping = false

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand action: $action")

        when (action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                
                recordWidth = intent.getIntExtra(EXTRA_WIDTH, 1080)
                recordHeight = intent.getIntExtra(EXTRA_HEIGHT, 1920)
                recordFps = intent.getIntExtra(EXTRA_FPS, 60)
                recordBitrateMbps = intent.getIntExtra(EXTRA_BITRATE_MBPS, 16)
                audioSourceType = intent.getStringExtra(EXTRA_AUDIO_SOURCE) ?: AudioSourceType.MIC.name
                videoCodec = intent.getStringExtra(EXTRA_CODEC) ?: "video/avc"
                val countdown = intent.getIntExtra(EXTRA_COUNTDOWN, 3)

                if (resultData != null) {
                    startForegroundWithNotification("Preparing to record...")
                    if (countdown > 0) {
                        runCountdownThenStart(countdown, resultCode, resultData)
                    } else {
                        initAndStartRecording(resultCode, resultData)
                    }
                } else {
                    _serviceState.value = RecordServiceState.Error("Media projection permission required")
                    stopSelf()
                }
            }

            ACTION_STOP -> {
                stopRecording()
            }

            ACTION_PAUSE -> {
                pauseRecording()
            }

            ACTION_RESUME -> {
                resumeRecording()
            }
        }

        return START_NOT_STICKY
    }

    private fun runCountdownThenStart(seconds: Int, resultCode: Int, resultData: Intent) {
        var current = seconds
        _serviceState.value = RecordServiceState.Countdown(current)

        val countdownRunnable = object : Runnable {
            override fun run() {
                if (current > 1) {
                    current--
                    _serviceState.value = RecordServiceState.Countdown(current)
                    updateNotification("Starting in $current seconds...")
                    handler.postDelayed(this, 1000)
                } else {
                    initAndStartRecording(resultCode, resultData)
                }
            }
        }
        handler.postDelayed(countdownRunnable, 1000)
    }

    private fun initAndStartRecording(resultCode: Int, resultData: Intent) {
        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

            if (mediaProjection == null) {
                _serviceState.value = RecordServiceState.Error("Failed to obtain Media Projection")
                stopSelf()
                return
            }

            // Register callback to stop on projection stop
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection stopped by system")
                    stopRecording()
                }
            }, handler)

            setupMediaRecorder()

            val metrics = DisplayMetrics()
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            val dpi = metrics.densityDpi

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenRecorder4K",
                recordWidth,
                recordHeight,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder?.surface,
                null,
                handler
            )

            mediaRecorder?.start()
            recordingStartTimeMs = System.currentTimeMillis()
            isPaused = false
            isRecording = true
            isStopping = false
            totalPausedDurationMs = 0L

            _serviceState.value = RecordServiceState.Recording(0L, false)
            updateNotification("Recording Screen • 00:00")
            FloatingBubbleManager.showBubble(this)
            startTimer()

        } catch (e: Exception) {
            Log.e(TAG, "Error starting screen recording", e)
            _serviceState.value = RecordServiceState.Error("Recording failed: ${e.localizedMessage}")
            isRecording = false
            cleanup()
            stopSelf()
        }
    }

    @Suppress("DEPRECATION")
    private fun setupMediaRecorder() {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }

        // Configure audio
        var hasMic = audioSourceType == AudioSourceType.MIC.name || audioSourceType == AudioSourceType.INTERNAL_AND_MIC.name
        if (hasMic) {
            try {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            } catch (e: Exception) {
                Log.w(TAG, "Audio source MIC unavailable, falling back to muted video", e)
                hasMic = false
            }
        }

        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

        // Create output file in app external movies directory
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "REC_${timeStamp}_${recordWidth}p.mp4"
        val moviesDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        val recorderDir = File(moviesDir, "ScreenRecordings")
        if (!recorderDir.exists()) recorderDir.mkdirs()

        outputFile = File(recorderDir, fileName)
        recorder.setOutputFile(outputFile?.absolutePath)

        // Video configuration
        recorder.setVideoSize(recordWidth, recordHeight)
        val isHevc = videoCodec.contains("hevc", ignoreCase = true)
        recorder.setVideoEncoder(if (isHevc) MediaRecorder.VideoEncoder.HEVC else MediaRecorder.VideoEncoder.H264)

        if (hasMic) {
            try {
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(128000)
                recorder.setAudioSamplingRate(44100)
            } catch (e: Exception) {
                Log.w(TAG, "Audio encoder configuration failed", e)
            }
        }

        recorder.setVideoFrameRate(recordFps)
        recorder.setVideoEncodingBitRate(recordBitrateMbps * 1024 * 1024)

        recorder.prepare()
        this.mediaRecorder = recorder
    }

    private fun startTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = object : Runnable {
            override fun run() {
                if (_serviceState.value is RecordServiceState.Recording && !isPaused) {
                    val currentMs = System.currentTimeMillis() - recordingStartTimeMs - totalPausedDurationMs
                    _serviceState.value = RecordServiceState.Recording(currentMs, false)
                    
                    val seconds = (currentMs / 1000) % 60
                    val minutes = (currentMs / (1000 * 60)) % 60
                    val hours = (currentMs / (1000 * 60 * 60))
                    val timeStr = if (hours > 0) {
                        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
                    } else {
                        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                    }
                    
                    updateNotification("Recording • $timeStr")
                    FloatingBubbleManager.updateTimer(timeStr, false)
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(timerRunnable!!)
    }

    private fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isPaused) {
            try {
                mediaRecorder?.pause()
                isPaused = true
                pauseStartTimeMs = System.currentTimeMillis()
                val currentMs = pauseStartTimeMs - recordingStartTimeMs - totalPausedDurationMs
                _serviceState.value = RecordServiceState.Recording(currentMs, true)
                updateNotification("Recording Paused")
            } catch (e: Exception) {
                Log.e(TAG, "Pause failed", e)
            }
        }
    }

    private fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isPaused) {
            try {
                mediaRecorder?.resume()
                totalPausedDurationMs += (System.currentTimeMillis() - pauseStartTimeMs)
                isPaused = false
                val currentMs = System.currentTimeMillis() - recordingStartTimeMs - totalPausedDurationMs
                _serviceState.value = RecordServiceState.Recording(currentMs, false)
                startTimer()
            } catch (e: Exception) {
                Log.e(TAG, "Resume failed", e)
            }
        }
    }

    private fun stopRecording() {
        if (isStopping || !isRecording) {
            Log.d(TAG, "stopRecording ignored because isStopping=$isStopping, isRecording=$isRecording")
            return
        }
        isStopping = true
        isRecording = false

        timerRunnable?.let { handler.removeCallbacks(it) }
        val durationMs = if (recordingStartTimeMs > 0) System.currentTimeMillis() - recordingStartTimeMs - totalPausedDurationMs else 0L

        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "MediaRecorder stop failed", e)
        }

        cleanup()

        val file = outputFile
        if (file != null && file.exists() && file.length() > 0) {
            val fileUri = saveToMediaStore(file)
            val finalUriStr = fileUri?.toString() ?: Uri.fromFile(file).toString()

            // Save to Room DB exactly once
            serviceScope.launch {
                try {
                    val repository = RecordingRepository(AppDatabase.getDatabase(applicationContext).recordingDao())
                    val titleName = file.nameWithoutExtension
                    val entity = RecordingEntity(
                        title = titleName,
                        fileUri = finalUriStr,
                        filePath = file.absolutePath,
                        durationMs = durationMs,
                        width = recordWidth,
                        height = recordHeight,
                        fps = recordFps,
                        bitrateMbps = recordBitrateMbps,
                        sizeBytes = file.length(),
                        audioSource = audioSourceType
                    )
                    repository.insert(entity)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to insert recording into DB", e)
                }
            }

            _serviceState.value = RecordServiceState.Completed(finalUriStr, durationMs)
        } else {
            _serviceState.value = RecordServiceState.Error("Recorded video file is empty or corrupted")
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun saveToMediaStore(file: File): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.SIZE, file.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ScreenRecordings")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        return try {
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    file.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                }
            }
            // Trigger MediaScanner scan
            android.media.MediaScannerConnection.scanFile(
                applicationContext,
                arrayOf(file.absolutePath),
                arrayOf("video/mp4"),
                null
            )
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting into MediaStore", e)
            null
        }
    }

    private fun cleanup() {
        try {
            FloatingBubbleManager.hideBubble()
            virtualDisplay?.release()
            virtualDisplay = null
            mediaProjection?.stop()
            mediaProjection = null
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup exception", e)
        }
    }

    private fun startForegroundWithNotification(contentText: String) {
        val notification = buildNotification(contentText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (audioSourceType == AudioSourceType.MIC.name || audioSourceType == AudioSourceType.INTERNAL_AND_MIC.name) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
            }
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(contentText: String) {
        val notification = buildNotification(contentText)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(contentText: String): Notification {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ScreenRecordService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(this, ScreenRecordService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val pausePendingIntent = PendingIntent.getService(
            this, 2, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Recorder 4K")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) "Resume" else "Pause",
                pausePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Recorder Service",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notification channel for active screen recording"
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }
}
