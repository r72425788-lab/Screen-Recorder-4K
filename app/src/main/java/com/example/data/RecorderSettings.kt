package com.example.data

enum class VideoResolution(val label: String, val width: Int, val height: Int, val shortLabel: String) {
    RES_4K("4K UHD (2160p)", 3840, 2160, "4K"),
    RES_2K("2K QHD (1440p)", 2560, 1440, "2K"),
    RES_1080P("Full HD (1080p)", 1920, 1080, "1080p"),
    RES_720P("HD (720p)", 1280, 720, "720p"),
    RES_480P("SD (480p)", 854, 480, "480p")
}

enum class VideoFrameRate(val fps: Int, val label: String) {
    FPS_120(120, "120 FPS"),
    FPS_90(90, "90 FPS"),
    FPS_60(60, "60 FPS"),
    FPS_30(30, "30 FPS"),
    FPS_24(24, "24 FPS")
}

enum class VideoBitrate(val mbps: Int, val label: String) {
    AUTO(0, "Auto Recommended"),
    MBPS_8(8, "8 Mbps"),
    MBPS_12(12, "12 Mbps"),
    MBPS_16(16, "16 Mbps"),
    MBPS_24(24, "24 Mbps"),
    MBPS_36(36, "36 Mbps"),
    MBPS_50(50, "50 Mbps"),
    MBPS_80(80, "80 Mbps (Ultra)")
}

enum class AudioSourceType(val label: String, val iconResId: String) {
    MIC("Microphone", "mic"),
    INTERNAL("Internal Audio", "volume_up"),
    INTERNAL_AND_MIC("Internal + Mic", "composite"),
    MUTE("Muted / Silent", "mic_off")
}

enum class VideoOrientation(val label: String) {
    AUTO("Auto (Follow Screen)"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class VideoCodecType(val label: String, val mimeType: String) {
    H264("H.264 / AVC (High Compatibility)", "video/avc"),
    HEVC("HEVC / H.265 (High Compression)", "video/hevc")
}

data class RecorderSettings(
    val resolution: VideoResolution = VideoResolution.RES_1080P,
    val frameRate: VideoFrameRate = VideoFrameRate.FPS_60,
    val bitrate: VideoBitrate = VideoBitrate.AUTO,
    val audioSource: AudioSourceType = AudioSourceType.MIC,
    val orientation: VideoOrientation = VideoOrientation.AUTO,
    val codec: VideoCodecType = VideoCodecType.H264,
    val countdownSeconds: Int = 3,
    val showFloatingOverlay: Boolean = true,
    val autoStopLowStorage: Boolean = true
)
