package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AudioSourceType
import com.example.data.RecorderSettings
import com.example.data.VideoBitrate
import com.example.data.VideoCodecType
import com.example.data.VideoFrameRate
import com.example.data.VideoOrientation
import com.example.data.VideoResolution
import com.example.detector.DeviceMetrics

@Composable
fun SettingsScreen(
    settings: RecorderSettings,
    deviceMetrics: DeviceMetrics?,
    onResolutionChanged: (VideoResolution) -> Unit,
    onFrameRateChanged: (VideoFrameRate) -> Unit,
    onBitrateChanged: (VideoBitrate) -> Unit,
    onAudioSourceChanged: (AudioSourceType) -> Unit,
    onOrientationChanged: (VideoOrientation) -> Unit,
    onCodecChanged: (VideoCodecType) -> Unit,
    onCountdownChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeDialog by remember { mutableStateOf<SettingDialogType?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        item {
            // Header
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )
        }

        // --- Video Settings Section ---
        item {
            SettingCategoryHeader("Video Settings", Icons.Default.HighQuality)
        }

        item {
            SettingListItem(
                title = "Video Resolution",
                summary = "${settings.resolution.label} (${settings.resolution.width}x${settings.resolution.height})",
                onClick = { activeDialog = SettingDialogType.Resolution }
            )
        }

        item {
            SettingListItem(
                title = "Frame Rate (FPS)",
                summary = settings.frameRate.label,
                onClick = { activeDialog = SettingDialogType.FrameRate }
            )
        }

        item {
            SettingListItem(
                title = "Video Bitrate",
                summary = settings.bitrate.label,
                onClick = { activeDialog = SettingDialogType.Bitrate }
            )
        }

        item {
            SettingListItem(
                title = "Video Codec",
                summary = settings.codec.label,
                onClick = { activeDialog = SettingDialogType.Codec }
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        }

        // --- Audio & Controls Section ---
        item {
            SettingCategoryHeader("Audio & Controls", Icons.Default.AudioFile)
        }

        item {
            SettingListItem(
                title = "Audio Source",
                summary = settings.audioSource.label,
                onClick = { activeDialog = SettingDialogType.AudioSource }
            )
        }

        item {
            SettingListItem(
                title = "Orientation",
                summary = settings.orientation.label,
                onClick = { activeDialog = SettingDialogType.Orientation }
            )
        }

        item {
            SettingListItem(
                title = "Countdown Timer",
                summary = if (settings.countdownSeconds == 0) "Off (Instant)" else "${settings.countdownSeconds} Seconds",
                onClick = { activeDialog = SettingDialogType.Countdown }
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        }

        // --- Device Info Section ---
        item {
            SettingCategoryHeader("Device Specifications", Icons.Default.Info)
        }

        if (deviceMetrics != null) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    InfoRow("Display Resolution", "${deviceMetrics.screenWidthPx} x ${deviceMetrics.screenHeightPx} px")
                    InfoRow("Refresh Rate", "${deviceMetrics.refreshRateHz.toInt()} Hz")
                    InfoRow("System RAM", "${String.format("%.1f", deviceMetrics.totalRamGb)} GB")
                    InfoRow("Free Storage", "${String.format("%.1f", deviceMetrics.availableStorageGb)} GB")
                    InfoRow("4K Encoder", if (deviceMetrics.supports4KEncoder) "Supported" else "Not Supported")
                    InfoRow("HEVC Encoder", if (deviceMetrics.supportsHevcEncoder) "Supported" else "Not Supported")
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // --- Dialog Popups for Selection ---
    when (activeDialog) {
        SettingDialogType.Resolution -> {
            ListSelectionDialog(
                title = "Video Resolution",
                options = VideoResolution.values().toList(),
                selectedOption = settings.resolution,
                labelMapper = { "${it.label} (${it.width}x${it.height})" },
                onSelect = {
                    onResolutionChanged(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        SettingDialogType.FrameRate -> {
            ListSelectionDialog(
                title = "Frame Rate (FPS)",
                options = VideoFrameRate.values().toList(),
                selectedOption = settings.frameRate,
                labelMapper = { it.label },
                onSelect = {
                    onFrameRateChanged(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        SettingDialogType.Bitrate -> {
            ListSelectionDialog(
                title = "Video Bitrate",
                options = VideoBitrate.values().toList(),
                selectedOption = settings.bitrate,
                labelMapper = { it.label },
                onSelect = {
                    onBitrateChanged(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        SettingDialogType.Codec -> {
            ListSelectionDialog(
                title = "Video Codec",
                options = VideoCodecType.values().toList(),
                selectedOption = settings.codec,
                labelMapper = { it.label },
                onSelect = {
                    onCodecChanged(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        SettingDialogType.AudioSource -> {
            ListSelectionDialog(
                title = "Audio Source",
                options = AudioSourceType.values().toList(),
                selectedOption = settings.audioSource,
                labelMapper = { it.label },
                onSelect = {
                    onAudioSourceChanged(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        SettingDialogType.Orientation -> {
            ListSelectionDialog(
                title = "Video Orientation",
                options = VideoOrientation.values().toList(),
                selectedOption = settings.orientation,
                labelMapper = { it.label },
                onSelect = {
                    onOrientationChanged(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        SettingDialogType.Countdown -> {
            ListSelectionDialog(
                title = "Countdown Timer",
                options = listOf(0, 3, 5, 10),
                selectedOption = settings.countdownSeconds,
                labelMapper = { if (it == 0) "Off (Instant)" else "$it Seconds" },
                onSelect = {
                    onCountdownChanged(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        null -> {}
    }
}

private enum class SettingDialogType {
    Resolution, FrameRate, Bitrate, Codec, AudioSource, Orientation, Countdown
}

@Composable
private fun SettingCategoryHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingListItem(
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun <T> ListSelectionDialog(
    title: String,
    options: List<T>,
    selectedOption: T,
    labelMapper: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                options.forEach { option ->
                    val isSelected = option == selectedOption
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .selectable(
                                selected = isSelected,
                                onClick = { onSelect(option) },
                                role = Role.RadioButton
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = labelMapper(option),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
