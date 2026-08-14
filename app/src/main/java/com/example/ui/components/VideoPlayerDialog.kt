package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.data.RecordingEntity
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

enum class PlayerScaleMode {
    FIT,   // Fit with aspect ratio
    FILL   // Fill entire screen (no black bars)
}

@Composable
fun VideoPlayerDialog(
    recording: RecordingEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }

    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var durationMs by remember { mutableIntStateOf(0) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableFloatStateOf(0f) }
    var controlsVisible by remember { mutableStateOf(true) }
    var scaleMode by remember { mutableStateOf(PlayerScaleMode.FIT) }

    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }

    // Helper to apply matrix scaling
    fun updateTextureScale(tv: TextureView, vWidth: Int, vHeight: Int, mode: PlayerScaleMode) {
        if (vWidth <= 0 || vHeight <= 0) return
        val viewWidth = tv.width.toFloat()
        val viewHeight = tv.height.toFloat()
        if (viewWidth <= 0 || viewHeight <= 0) return

        val sx: Float
        val sy: Float

        when (mode) {
            PlayerScaleMode.FIT -> {
                val videoRatio = vWidth.toFloat() / vHeight.toFloat()
                val screenRatio = viewWidth / viewHeight
                if (videoRatio > screenRatio) {
                    sx = 1f
                    sy = (viewWidth / videoRatio) / viewHeight
                } else {
                    sx = (viewHeight * videoRatio) / viewWidth
                    sy = 1f
                }
            }
            PlayerScaleMode.FILL -> {
                val videoRatio = vWidth.toFloat() / vHeight.toFloat()
                val screenRatio = viewWidth / viewHeight
                if (videoRatio > screenRatio) {
                    sx = (viewHeight * videoRatio) / viewWidth
                    sy = 1f
                } else {
                    sx = 1f
                    sy = (viewWidth / videoRatio) / viewHeight
                }
            }
        }

        val matrix = Matrix().apply {
            setScale(sx, sy, viewWidth / 2f, viewHeight / 2f)
        }
        tv.setTransform(matrix)
    }

    // Auto-hide controls after 3.5 seconds
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3500)
            controlsVisible = false
        }
    }

    // Progress polling timer
    LaunchedEffect(isPlaying, isPrepared, isUserSeeking) {
        while (isPlaying && !isUserSeeking) {
            mediaPlayerRef?.let { mp ->
                try {
                    currentPositionMs = mp.currentPosition
                } catch (ignored: Exception) {}
            }
            delay(250)
        }
    }

    Dialog(
        onDismissRequest = {
            try {
                mediaPlayerRef?.stop()
                mediaPlayerRef?.release()
            } catch (ignored: Exception) {}
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            color = Color.Black
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        controlsVisible = !controlsVisible
                    },
                contentAlignment = Alignment.Center
            ) {
                // High-performance TextureView Video Surface
                AndroidView(
                    factory = { ctx ->
                        val tv = TextureView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                        tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                                val s = Surface(surface)
                                val mp = MediaPlayer().apply {
                                    setSurface(s)
                                    setOnPreparedListener { player ->
                                        isPrepared = true
                                        durationMs = player.duration
                                        videoWidth = player.videoWidth
                                        videoHeight = player.videoHeight
                                        updateTextureScale(tv, player.videoWidth, player.videoHeight, scaleMode)
                                        player.start()
                                        isPlaying = true
                                    }
                                    setOnVideoSizeChangedListener { _, w, h ->
                                        videoWidth = w
                                        videoHeight = h
                                        updateTextureScale(tv, w, h, scaleMode)
                                    }
                                    setOnCompletionListener {
                                        isPlaying = false
                                        controlsVisible = true
                                    }
                                    setOnErrorListener { _, what, extra ->
                                        hasError = true
                                        errorMessage = "Video playback error ($what, $extra)"
                                        true
                                    }
                                }

                                try {
                                    val file = File(recording.filePath)
                                    if (file.exists() && file.length() > 0) {
                                        mp.setDataSource(file.absolutePath)
                                    } else {
                                        mp.setDataSource(ctx, Uri.parse(recording.fileUri))
                                    }
                                    mp.prepareAsync()
                                } catch (e: Exception) {
                                    hasError = true
                                    errorMessage = e.localizedMessage ?: "Failed to open video file"
                                }

                                mediaPlayerRef = mp
                            }

                            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                                updateTextureScale(tv, videoWidth, videoHeight, scaleMode)
                            }

                            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                try {
                                    mediaPlayerRef?.stop()
                                    mediaPlayerRef?.release()
                                    mediaPlayerRef = null
                                } catch (ignored: Exception) {}
                                return true
                            }

                            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                        }
                        textureViewRef = tv
                        tv
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Update scale when user clicks scale toggle
                LaunchedEffect(scaleMode, videoWidth, videoHeight) {
                    textureViewRef?.let { tv ->
                        updateTextureScale(tv, videoWidth, videoHeight, scaleMode)
                    }
                }

                // Loading Indicator
                if (!isPrepared && !hasError) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading recording...",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Error State Overlay
                if (hasError) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "Playback Error",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = errorMessage.ifEmpty { "Unable to decode video format." },
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Retry Button
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    hasError = false
                                    isPrepared = false
                                    mediaPlayerRef?.let { mp ->
                                        try {
                                            mp.reset()
                                            val file = File(recording.filePath)
                                            if (file.exists()) mp.setDataSource(file.absolutePath)
                                            else mp.setDataSource(context, Uri.parse(recording.fileUri))
                                            mp.prepareAsync()
                                        } catch (e: Exception) {
                                            hasError = true
                                        }
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Replay,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Open externally button
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.DarkGray,
                                modifier = Modifier.clickable {
                                    try {
                                        val file = File(recording.filePath)
                                        val uri = if (file.exists()) {
                                            FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                        } else {
                                            Uri.parse(recording.fileUri)
                                        }
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "video/mp4")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Play with"))
                                    } catch (ignored: Exception) {}
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("External Player", color = Color.White)
                                }
                            }
                        }
                    }
                }

                // Controls Overlay
                AnimatedVisibility(
                    visible = controlsVisible && isPrepared && !hasError,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        // Top Controls Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 16.dp, vertical = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = recording.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${recording.height}p • ${recording.fps} FPS • ${formatFileSize(recording.sizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray
                                )
                            }

                            // Scale Toggle: FIT vs FILL (Fill Screen)
                            IconButton(onClick = {
                                scaleMode = if (scaleMode == PlayerScaleMode.FIT) PlayerScaleMode.FILL else PlayerScaleMode.FIT
                            }) {
                                Icon(
                                    imageVector = if (scaleMode == PlayerScaleMode.FIT) Icons.Default.Fullscreen else Icons.Default.FitScreen,
                                    contentDescription = if (scaleMode == PlayerScaleMode.FIT) "Fill Screen" else "Fit Screen",
                                    tint = Color.White
                                )
                            }

                            // Share Action
                            IconButton(onClick = {
                                try {
                                    val file = File(recording.filePath)
                                    val uri = if (file.exists()) {
                                        FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                    } else {
                                        Uri.parse(recording.fileUri)
                                    }
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "video/mp4"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                                } catch (ignored: Exception) {}
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = Color.White
                                )
                            }

                            // Close Action
                            IconButton(onClick = {
                                try {
                                    mediaPlayerRef?.stop()
                                    mediaPlayerRef?.release()
                                } catch (ignored: Exception) {}
                                onDismiss()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White
                                )
                            }
                        }

                        // Center Playback Buttons (-10s, Play/Pause, +10s)
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // -10s Rewind
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(52.dp)
                                    .clickable {
                                        mediaPlayerRef?.let { mp ->
                                            val target = (mp.currentPosition - 10000).coerceAtLeast(0)
                                            mp.seekTo(target)
                                            currentPositionMs = target
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Replay10,
                                        contentDescription = "Rewind 10 seconds",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }

                            // Play / Pause Button
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(68.dp)
                                    .clickable {
                                        mediaPlayerRef?.let { mp ->
                                            if (isPlaying) {
                                                mp.pause()
                                                isPlaying = false
                                            } else {
                                                mp.start()
                                                isPlaying = true
                                            }
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                            }

                            // +10s Forward
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(52.dp)
                                    .clickable {
                                        mediaPlayerRef?.let { mp ->
                                            val target = (mp.currentPosition + 10000).coerceAtMost(durationMs)
                                            mp.seekTo(target)
                                            currentPositionMs = target
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Forward10,
                                        contentDescription = "Forward 10 seconds",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }

                        // Bottom Scrubber Bar & Timers
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 20.dp, vertical = 24.dp)
                        ) {
                            val activeProgress = if (isUserSeeking) seekPositionMs else currentPositionMs.toFloat()
                            val maxProgress = durationMs.toFloat().coerceAtLeast(1f)

                            Slider(
                                value = activeProgress.coerceIn(0f, maxProgress),
                                onValueChange = { newVal ->
                                    isUserSeeking = true
                                    seekPositionMs = newVal
                                },
                                onValueChangeFinished = {
                                    isUserSeeking = false
                                    mediaPlayerRef?.seekTo(seekPositionMs.toInt())
                                    currentPositionMs = seekPositionMs.toInt()
                                },
                                valueRange = 0f..maxProgress,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.Gray.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatMsToTime(activeProgress.toLong()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (scaleMode == PlayerScaleMode.FIT) "Fit Screen" else "Fill Screen",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = formatMsToTime(durationMs.toLong()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayerRef?.stop()
                mediaPlayerRef?.release()
                mediaPlayerRef = null
            } catch (ignored: Exception) {}
        }
    }
}

private fun formatMsToTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

private fun formatFileSize(bytes: Long): String {
    val mb = bytes / (1024f * 1024f)
    return if (mb >= 1024) {
        String.format(Locale.getDefault(), "%.2f GB", mb / 1024f)
    } else {
        String.format(Locale.getDefault(), "%.1f MB", mb)
    }
}
