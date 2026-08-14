package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.RecordingEntity
import com.example.service.RecordServiceState
import com.example.ui.MainViewModel
import com.example.ui.components.VideoPlayerDialog
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ScreenRecorderTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var selectedVideoForPlayer by mutableStateOf<RecordingEntity?>(null)
    private var videoToRename by mutableStateOf<RecordingEntity?>(null)
    private var renameInputText by mutableStateOf("")

    // ActivityResultLauncher for MediaProjection Intent
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.startRecording(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: true
        if (micGranted) {
            launchScreenCapture()
        } else {
            Toast.makeText(this, "Microphone permission required for audio recording", Toast.LENGTH_SHORT).show()
            launchScreenCapture()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ScreenRecorderTheme {
                val serviceState by viewModel.serviceState.collectAsStateWithLifecycle()
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                val deviceMetrics by viewModel.deviceMetrics.collectAsStateWithLifecycle()
                val optimizationResult by viewModel.optimizationResult.collectAsStateWithLifecycle()
                val recordings by viewModel.recordingsList.collectAsStateWithLifecycle()

                var selectedTab by remember { mutableIntStateOf(0) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Record") },
                                label = { Text("Record", fontWeight = FontWeight.Medium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                )
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Gallery") },
                                label = { Text("Gallery", fontWeight = FontWeight.Medium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                )
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                label = { Text("Settings", fontWeight = FontWeight.Medium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                )
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (selectedTab) {
                            0 -> HomeScreen(
                                serviceState = serviceState,
                                settings = settings,
                                deviceMetrics = deviceMetrics,
                                optimizationResult = optimizationResult,
                                recordings = recordings,
                                onStartRecording = { checkPermissionsAndStart() },
                                onStopRecording = { viewModel.stopRecording() },
                                onPauseRecording = { viewModel.pauseRecording() },
                                onResumeRecording = { viewModel.resumeRecording() },
                                onResolutionChanged = { viewModel.updateResolution(it) },
                                onFrameRateChanged = { viewModel.updateFrameRate(it) },
                                onAudioSourceChanged = { viewModel.updateAudioSource(it) },
                                onApplySmartRecommendation = { viewModel.applySmartRecommendation() },
                                onPlayVideo = { selectedVideoForPlayer = it },
                                onShareVideo = { shareRecording(it) },
                                onRenameVideo = { 
                                    videoToRename = it
                                    renameInputText = it.title
                                },
                                onDeleteVideo = { viewModel.deleteRecording(it) },
                                onNavigateToGallery = { selectedTab = 1 }
                            )

                            1 -> GalleryScreen(
                                recordings = recordings,
                                onPlayVideo = { selectedVideoForPlayer = it },
                                onShareVideo = { shareRecording(it) },
                                onRenameVideo = { id, title -> viewModel.renameRecording(id, title) },
                                onDeleteVideo = { viewModel.deleteRecording(it) }
                            )

                            2 -> SettingsScreen(
                                settings = settings,
                                deviceMetrics = deviceMetrics,
                                onResolutionChanged = { viewModel.updateResolution(it) },
                                onFrameRateChanged = { viewModel.updateFrameRate(it) },
                                onBitrateChanged = { viewModel.updateBitrate(it) },
                                onAudioSourceChanged = { viewModel.updateAudioSource(it) },
                                onOrientationChanged = { viewModel.updateOrientation(it) },
                                onCodecChanged = { viewModel.updateCodec(it) },
                                onCountdownChanged = { viewModel.updateCountdown(it) }
                            )
                        }

                        // Full Screen Video Player Popup
                        selectedVideoForPlayer?.let { rec ->
                            VideoPlayerDialog(
                                recording = rec,
                                onDismiss = { selectedVideoForPlayer = null }
                            )
                        }

                        // Rename Dialog for HomeScreen
                        videoToRename?.let { rec ->
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { videoToRename = null },
                                title = { Text("Rename Recording") },
                                text = {
                                    androidx.compose.material3.OutlinedTextField(
                                        value = renameInputText,
                                        onValueChange = { renameInputText = it },
                                        singleLine = true,
                                        label = { Text("Video Title") }
                                    )
                                },
                                confirmButton = {
                                    androidx.compose.material3.Button(onClick = {
                                        if (renameInputText.isNotBlank()) {
                                            viewModel.renameRecording(rec.id, renameInputText.trim())
                                        }
                                        videoToRename = null
                                    }) {
                                        Text("Save")
                                    }
                                },
                                dismissButton = {
                                    androidx.compose.material3.TextButton(onClick = { videoToRename = null }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            launchScreenCapture()
        }
    }

    private fun launchScreenCapture() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = projectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(captureIntent)
    }

    private fun shareRecording(recording: RecordingEntity) {
        try {
            val file = java.io.File(recording.filePath)
            val uri = if (file.exists()) {
                androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "$packageName.fileprovider",
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
            startActivity(Intent.createChooser(shareIntent, "Share Screen Recording"))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not share video: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
