package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.RecordingEntity
import com.example.ui.components.VideoItemCard
import java.util.Locale

@Composable
fun GalleryScreen(
    recordings: List<RecordingEntity>,
    onPlayVideo: (RecordingEntity) -> Unit,
    onShareVideo: (RecordingEntity) -> Unit,
    onRenameVideo: (Long, String) -> Unit,
    onDeleteVideo: (RecordingEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var renamingRecording by remember { mutableStateOf<RecordingEntity?>(null) }
    var renameInputText by remember { mutableStateOf("") }
    var deletingRecording by remember { mutableStateOf<RecordingEntity?>(null) }

    val filteredRecordings = recordings.filter {
        it.title.contains(searchQuery, ignoreCase = true)
    }

    val totalSizeBytes = recordings.sumOf { it.sizeBytes }
    val totalSizeMb = totalSizeBytes / (1024f * 1024f)
    val totalSizeText = if (totalSizeMb >= 1024) {
        String.format(Locale.getDefault(), "%.2f GB", totalSizeMb / 1024f)
    } else {
        String.format(Locale.getDefault(), "%.1f MB", totalSizeMb)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search recorded videos...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Gallery Stats Box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Recorded Media Gallery",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${recordings.size} Videos • Total Size: $totalSizeText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (filteredRecordings.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No matching recordings found" else "No screen recordings saved yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredRecordings, key = { it.id }) { recording ->
                VideoItemCard(
                    recording = recording,
                    onPlayClick = { onPlayVideo(recording) },
                    onShareClick = { onShareVideo(recording) },
                    onRenameClick = {
                        renamingRecording = recording
                        renameInputText = recording.title
                    },
                    onDeleteClick = {
                        deletingRecording = recording
                    },
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Rename Dialog
    renamingRecording?.let { rec ->
        AlertDialog(
            onDismissRequest = { renamingRecording = null },
            title = { Text("Rename Recording") },
            text = {
                OutlinedTextField(
                    value = renameInputText,
                    onValueChange = { renameInputText = it },
                    singleLine = true,
                    label = { Text("Video Title") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (renameInputText.isNotBlank()) {
                        onRenameVideo(rec.id, renameInputText.trim())
                    }
                    renamingRecording = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingRecording = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    deletingRecording?.let { rec ->
        AlertDialog(
            onDismissRequest = { deletingRecording = null },
            title = { Text("Delete Recording?") },
            text = { Text("Are you sure you want to permanently delete '${rec.title}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteVideo(rec)
                        deletingRecording = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingRecording = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
