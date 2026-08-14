package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AudioSourceType
import com.example.data.RecorderSettings
import com.example.data.RecordingEntity
import com.example.data.VideoFrameRate
import com.example.data.VideoResolution
import com.example.detector.DeviceMetrics
import com.example.detector.SmartOptimizationResult
import com.example.service.RecordServiceState
import com.example.ui.components.QuickSettingBar
import com.example.ui.components.RecordingControlSection
import com.example.ui.components.SmartDetectionCard
import com.example.ui.components.VideoItemCard

@Composable
fun HomeScreen(
    serviceState: RecordServiceState,
    settings: RecorderSettings,
    deviceMetrics: DeviceMetrics?,
    optimizationResult: SmartOptimizationResult?,
    recordings: List<RecordingEntity>,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onResolutionChanged: (VideoResolution) -> Unit,
    onFrameRateChanged: (VideoFrameRate) -> Unit,
    onAudioSourceChanged: (AudioSourceType) -> Unit,
    onApplySmartRecommendation: () -> Unit,
    onPlayVideo: (RecordingEntity) -> Unit,
    onShareVideo: (RecordingEntity) -> Unit,
    onRenameVideo: (RecordingEntity) -> Unit,
    onDeleteVideo: (RecordingEntity) -> Unit,
    onNavigateToGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))

            // Clean Title
            Text(
                text = "TakiRec Pro",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Main Recording Control Card
            RecordingControlSection(
                serviceState = serviceState,
                settings = settings,
                onStartClick = onStartRecording,
                onStopClick = onStopRecording,
                onPauseClick = onPauseRecording,
                onResumeClick = onResumeRecording
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Game Floating Controls Card
            val context = LocalContext.current
            val hasOverlayPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.provider.Settings.canDrawOverlays(context)
            } else {
                true
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Game Floating Controls",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (hasOverlayPermission)
                        "Floating bubble active. Control pause/resume and stop directly over any game or app."
                    else
                        "Enable 'Display over other apps' to use the floating controls widget while gaming.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!hasOverlayPermission) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                val intent = Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Enable Floating Bubble",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Hardware info banner if needed
            if (optimizationResult != null && optimizationResult.severity != com.example.detector.DetectionSeverity.OPTIMAL) {
                Spacer(modifier = Modifier.height(16.dp))
                SmartDetectionCard(
                    deviceMetrics = deviceMetrics,
                    optimizationResult = optimizationResult,
                    onApplyRecommendation = onApplySmartRecommendation
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Quick Preset Selectors
            Text(
                text = "Quick Presets",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            QuickSettingBar(
                title = "Resolution",
                items = VideoResolution.values().toList(),
                selectedItem = settings.resolution,
                itemLabel = { it.label },
                onItemSelected = onResolutionChanged
            )

            QuickSettingBar(
                title = "Frame Rate",
                items = VideoFrameRate.values().toList(),
                selectedItem = settings.frameRate,
                itemLabel = { it.label },
                onItemSelected = onFrameRateChanged
            )

            QuickSettingBar(
                title = "Audio Input",
                items = AudioSourceType.values().toList(),
                selectedItem = settings.audioSource,
                itemLabel = { it.label },
                onItemSelected = onAudioSourceChanged
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Recent Recordings Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Recent Recordings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                if (recordings.isNotEmpty()) {
                    TextButton(onClick = onNavigateToGallery) {
                        Text(
                            text = "View All (${recordings.size})",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (recordings.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No recordings yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Tap the record button to start recording your screen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            items(recordings.take(3), key = { it.id }) { recording ->
                VideoItemCard(
                    recording = recording,
                    onPlayClick = { onPlayVideo(recording) },
                    onShareClick = { onShareVideo(recording) },
                    onRenameClick = { onRenameVideo(recording) },
                    onDeleteClick = { onDeleteVideo(recording) },
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
