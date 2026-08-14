package com.example.detector

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import com.example.data.RecorderSettings
import com.example.data.VideoBitrate
import com.example.data.VideoFrameRate
import com.example.data.VideoResolution
import kotlin.math.max
import kotlin.math.min

data class DeviceMetrics(
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val nativeWidth: Int,
    val nativeHeight: Int,
    val refreshRateHz: Float,
    val totalRamGb: Float,
    val availableStorageGb: Float,
    val supports4KEncoder: Boolean,
    val supportsHevcEncoder: Boolean
)

enum class DetectionSeverity {
    OPTIMAL,
    WARNING,
    HIGH_LOAD,
    ALERT
}

data class SmartOptimizationResult(
    val severity: DetectionSeverity,
    val title: String,
    val message: String,
    val recommendationText: String,
    val calculatedBitrateMbps: Int,
    val recommendedResolution: VideoResolution,
    val recommendedFps: VideoFrameRate,
    val isUpscaling: Boolean
)

object DeviceCapabilityDetector {

    fun detectMetrics(context: Context): DeviceMetrics {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        
        var widthPx = 1080
        var heightPx = 1920
        var refreshRate = 60f

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            widthPx = bounds.width()
            heightPx = bounds.height()
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val defaultDisplay = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
            defaultDisplay?.mode?.refreshRate?.let {
                refreshRate = it
            }
        } else {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(displayMetrics)
            widthPx = displayMetrics.widthPixels
            heightPx = displayMetrics.heightPixels
            @Suppress("DEPRECATION")
            refreshRate = wm.defaultDisplay.refreshRate
        }

        val nativeLong = max(widthPx, heightPx)
        val nativeShort = min(widthPx, heightPx)

        // Memory check
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalRamGb = memoryInfo.totalMem / (1024f * 1024f * 1024f)

        // Storage check
        var storageFreeGb = 10f
        try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
            storageFreeGb = bytesAvailable / (1024f * 1024f * 1024f)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Codec capability check
        var supports4k = false
        var supportsHevc = false

        try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            for (info in codecList.codecInfos) {
                if (!info.isEncoder) continue
                for (type in info.supportedTypes) {
                    if (type.equals("video/hevc", ignoreCase = true)) {
                        supportsHevc = true
                    }
                    if (type.equals("video/avc", ignoreCase = true) || type.equals("video/hevc", ignoreCase = true)) {
                        try {
                            val caps = info.getCapabilitiesForType(type)
                            val videoCaps = caps.videoCapabilities
                            if (videoCaps != null && videoCaps.areSizeAndRateSupported(3840, 2160, 30.0)) {
                                supports4k = true
                            }
                        } catch (ignored: Exception) { }
                    }
                }
            }
        } catch (e: Exception) {
            supports4k = true
            supportsHevc = true
        }

        return DeviceMetrics(
            screenWidthPx = widthPx,
            screenHeightPx = heightPx,
            nativeWidth = nativeShort,
            nativeHeight = nativeLong,
            refreshRateHz = refreshRate,
            totalRamGb = totalRamGb,
            availableStorageGb = storageFreeGb,
            supports4KEncoder = supports4k,
            supportsHevcEncoder = supportsHevc
        )
    }

    fun analyzeAndOptimize(settings: RecorderSettings, metrics: DeviceMetrics): SmartOptimizationResult {
        val selectedResLong = max(settings.resolution.width, settings.resolution.height)
        val nativeLong = max(metrics.nativeWidth, metrics.nativeHeight)

        val isUpscaling = selectedResLong > nativeLong
        val is4KSelected = settings.resolution == VideoResolution.RES_4K
        val isHighFps = settings.frameRate.fps >= 90
        val fpsExceedsRefresh = settings.frameRate.fps > (metrics.refreshRateHz.toInt() + 5)

        // Low RAM device handling (< 3.5 GB)
        val isLowRamDevice = metrics.totalRamGb < 3.5f

        // Native display classification
        val recommendedRes = when {
            isLowRamDevice -> VideoResolution.RES_720P
            nativeLong >= 3840 -> VideoResolution.RES_4K
            nativeLong >= 2560 -> VideoResolution.RES_2K
            nativeLong >= 1800 -> VideoResolution.RES_1080P
            else -> VideoResolution.RES_720P
        }

        val recommendedFps = if (isLowRamDevice) {
            VideoFrameRate.FPS_30
        } else if (metrics.refreshRateHz >= 115) {
            VideoFrameRate.FPS_120
        } else if (metrics.refreshRateHz >= 85) {
            VideoFrameRate.FPS_90
        } else {
            VideoFrameRate.FPS_60
        }

        // Calculated Bitrate calculation
        val targetBitrate = if (settings.bitrate == VideoBitrate.AUTO) {
            when (settings.resolution) {
                VideoResolution.RES_4K -> if (settings.frameRate.fps >= 60) 36 else 24
                VideoResolution.RES_2K -> if (settings.frameRate.fps >= 60) 24 else 16
                VideoResolution.RES_1080P -> if (settings.frameRate.fps >= 60) 16 else 12
                VideoResolution.RES_720P -> 8
                VideoResolution.RES_480P -> 4
            }
        } else {
            settings.bitrate.mbps
        }

        // Warning and Suggestion Logic
        return when {
            metrics.availableStorageGb < 2.0f -> {
                SmartOptimizationResult(
                    severity = DetectionSeverity.ALERT,
                    title = "Low Storage Warning",
                    message = "Only ${String.format("%.1f", metrics.availableStorageGb)} GB storage left. Recording in high quality may fill device storage rapidly.",
                    recommendationText = "Consider 720p or 1080p to save space.",
                    calculatedBitrateMbps = targetBitrate,
                    recommendedResolution = recommendedRes,
                    recommendedFps = recommendedFps,
                    isUpscaling = isUpscaling
                )
            }

            is4KSelected && nativeLong < 2560 -> {
                SmartOptimizationResult(
                    severity = DetectionSeverity.WARNING,
                    title = "Upscaling to 4K Detected",
                    message = "Your device screen native resolution is ${nativeLong}p (${if (nativeLong <= 1280) "720p HD" else "1080p Full HD"}). Selecting 4K will upscale video pixels, resulting in larger file size (~2-4x) and higher GPU/battery consumption without true 4K source details.",
                    recommendationText = "Recommended: ${recommendedRes.label} @ ${recommendedFps.label} for optimal native clarity and performance.",
                    calculatedBitrateMbps = targetBitrate,
                    recommendedResolution = recommendedRes,
                    recommendedFps = recommendedFps,
                    isUpscaling = true
                )
            }

            isUpscaling -> {
                SmartOptimizationResult(
                    severity = DetectionSeverity.WARNING,
                    title = "Resolution Upscaling Notice",
                    message = "Selected resolution (${settings.resolution.shortLabel}) is higher than your screen's physical native resolution (${nativeLong}p).",
                    recommendationText = "Match native resolution (${recommendedRes.shortLabel}) for sharpest recording without unnecessary file bloat.",
                    calculatedBitrateMbps = targetBitrate,
                    recommendedResolution = recommendedRes,
                    recommendedFps = recommendedFps,
                    isUpscaling = true
                )
            }

            fpsExceedsRefresh -> {
                SmartOptimizationResult(
                    severity = DetectionSeverity.HIGH_LOAD,
                    title = "Display Refresh Rate Limit",
                    message = "You selected ${settings.frameRate.fps} FPS, but your display refresh rate is capped at ${metrics.refreshRateHz.toInt()} Hz.",
                    recommendationText = "Set FPS to ${metrics.refreshRateHz.toInt()} FPS for smooth frame sync.",
                    calculatedBitrateMbps = targetBitrate,
                    recommendedResolution = recommendedRes,
                    recommendedFps = recommendedFps,
                    isUpscaling = false
                )
            }

            is4KSelected && !metrics.supports4KEncoder -> {
                SmartOptimizationResult(
                    severity = DetectionSeverity.WARNING,
                    title = "4K Hardware Limit Notice",
                    message = "Device media encoder may lag when encoding 4K resolution at high frame rates.",
                    recommendationText = "If recording stutters, use 2K (1440p) or 1080p.",
                    calculatedBitrateMbps = targetBitrate,
                    recommendedResolution = recommendedRes,
                    recommendedFps = recommendedFps,
                    isUpscaling = false
                )
            }

            else -> {
                SmartOptimizationResult(
                    severity = DetectionSeverity.OPTIMAL,
                    title = "Optimal Settings Selected",
                    message = "Configuration matches your hardware capabilities perfectly. Crisp, lag-free 60FPS+ capture enabled.",
                    recommendationText = "Display: ${metrics.nativeWidth}x${metrics.nativeHeight} @ ${metrics.refreshRateHz.toInt()}Hz | RAM: ${String.format("%.1f", metrics.totalRamGb)} GB",
                    calculatedBitrateMbps = targetBitrate,
                    recommendedResolution = recommendedRes,
                    recommendedFps = recommendedFps,
                    isUpscaling = false
                )
            }
        }
    }
}
