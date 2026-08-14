package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val fileUri: String,
    val filePath: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrateMbps: Int,
    val sizeBytes: Long,
    val audioSource: String,
    val timestamp: Long = System.currentTimeMillis()
)
