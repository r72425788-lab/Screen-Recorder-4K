package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AudioSourceType
import com.example.data.RecorderSettings
import com.example.data.RecordingEntity
import com.example.data.RecordingRepository
import com.example.data.VideoBitrate
import com.example.data.VideoCodecType
import com.example.data.VideoFrameRate
import com.example.data.VideoOrientation
import com.example.data.VideoResolution
import com.example.detector.DeviceCapabilityDetector
import com.example.detector.DeviceMetrics
import com.example.detector.SmartOptimizationResult
import com.example.service.RecordServiceState
import com.example.service.ScreenRecordService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.min

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: RecordingRepository

    val recordingsList: StateFlow<List<RecordingEntity>>

    val serviceState: StateFlow<RecordServiceState> = ScreenRecordService.serviceState

    private val _settings = MutableStateFlow(RecorderSettings())
    val settings: StateFlow<RecorderSettings> = _settings.asStateFlow()

    private val _deviceMetrics = MutableStateFlow<DeviceMetrics?>(null)
    val deviceMetrics: StateFlow<DeviceMetrics?> = _deviceMetrics.asStateFlow()

    private val _optimizationResult = MutableStateFlow<SmartOptimizationResult?>(null)
    val optimizationResult: StateFlow<SmartOptimizationResult?> = _optimizationResult.asStateFlow()

    init {
        val dao = AppDatabase.getDatabase(application).recordingDao()
        repository = RecordingRepository(dao)

        recordingsList = repository.allRecordings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Automatically clean any legacy duplicate entries if file paths match
        viewModelScope.launch {
            repository.allRecordings.collect { list ->
                val seenPaths = mutableSetOf<String>()
                list.forEach { rec ->
                    if (seenPaths.contains(rec.filePath)) {
                        repository.delete(rec.id)
                    } else {
                        seenPaths.add(rec.filePath)
                    }
                }
            }
        }

        refreshDeviceMetrics()
    }

    fun refreshDeviceMetrics() {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val metrics = DeviceCapabilityDetector.detectMetrics(context)
            _deviceMetrics.value = metrics
            updateOptimizationAnalysis(_settings.value, metrics)
        }
    }

    private fun updateOptimizationAnalysis(settings: RecorderSettings, metrics: DeviceMetrics?) {
        if (metrics == null) return
        val result = DeviceCapabilityDetector.analyzeAndOptimize(settings, metrics)
        _optimizationResult.value = result
    }

    fun updateResolution(resolution: VideoResolution) {
        _settings.value = _settings.value.copy(resolution = resolution)
        updateOptimizationAnalysis(_settings.value, _deviceMetrics.value)
    }

    fun updateFrameRate(frameRate: VideoFrameRate) {
        _settings.value = _settings.value.copy(frameRate = frameRate)
        updateOptimizationAnalysis(_settings.value, _deviceMetrics.value)
    }

    fun updateBitrate(bitrate: VideoBitrate) {
        _settings.value = _settings.value.copy(bitrate = bitrate)
        updateOptimizationAnalysis(_settings.value, _deviceMetrics.value)
    }

    fun updateAudioSource(audioSource: AudioSourceType) {
        _settings.value = _settings.value.copy(audioSource = audioSource)
        updateOptimizationAnalysis(_settings.value, _deviceMetrics.value)
    }

    fun updateOrientation(orientation: VideoOrientation) {
        _settings.value = _settings.value.copy(orientation = orientation)
    }

    fun updateCodec(codec: VideoCodecType) {
        _settings.value = _settings.value.copy(codec = codec)
    }

    fun updateCountdown(seconds: Int) {
        _settings.value = _settings.value.copy(countdownSeconds = seconds)
    }

    fun applySmartRecommendation() {
        val opt = _optimizationResult.value ?: return
        _settings.value = _settings.value.copy(
            resolution = opt.recommendedResolution,
            frameRate = opt.recommendedFps,
            bitrate = VideoBitrate.AUTO
        )
        updateOptimizationAnalysis(_settings.value, _deviceMetrics.value)
    }

    fun startRecording(resultCode: Int, resultData: Intent) {
        val context = getApplication<Application>().applicationContext
        val currentSettings = _settings.value
        val metrics = _deviceMetrics.value

        var width = currentSettings.resolution.width
        var height = currentSettings.resolution.height

        // Check orientation override or screen native aspect ratio
        if (metrics != null) {
            val isScreenLandscape = metrics.screenWidthPx > metrics.screenHeightPx
            when (currentSettings.orientation) {
                VideoOrientation.PORTRAIT -> {
                    val w = min(width, height)
                    val h = max(width, height)
                    width = w
                    height = h
                }
                VideoOrientation.LANDSCAPE -> {
                    val w = max(width, height)
                    val h = min(width, height)
                    width = w
                    height = h
                }
                VideoOrientation.AUTO -> {
                    if (isScreenLandscape) {
                        width = max(width, height)
                        height = min(width, height)
                    } else {
                        width = min(width, height)
                        height = max(width, height)
                    }
                }
            }
        }

        val calculatedBitrate = _optimizationResult.value?.calculatedBitrateMbps ?: 16

        val serviceIntent = Intent(context, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_START
            putExtra(ScreenRecordService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenRecordService.EXTRA_RESULT_DATA, resultData)
            putExtra(ScreenRecordService.EXTRA_WIDTH, width)
            putExtra(ScreenRecordService.EXTRA_HEIGHT, height)
            putExtra(ScreenRecordService.EXTRA_FPS, currentSettings.frameRate.fps)
            putExtra(ScreenRecordService.EXTRA_BITRATE_MBPS, calculatedBitrate)
            putExtra(ScreenRecordService.EXTRA_AUDIO_SOURCE, currentSettings.audioSource.name)
            putExtra(ScreenRecordService.EXTRA_CODEC, currentSettings.codec.mimeType)
            putExtra(ScreenRecordService.EXTRA_COUNTDOWN, currentSettings.countdownSeconds)
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to start ScreenRecordService", e)
        }
    }

    fun stopRecording() {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun pauseRecording() {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun resumeRecording() {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun deleteRecording(recording: RecordingEntity) {
        viewModelScope.launch {
            try {
                val file = File(recording.filePath)
                if (file.exists()) file.delete()
                repository.delete(recording.id)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to delete recording", e)
            }
        }
    }

    fun renameRecording(id: Long, newTitle: String) {
        viewModelScope.launch {
            repository.updateTitle(id, newTitle)
        }
    }
}
