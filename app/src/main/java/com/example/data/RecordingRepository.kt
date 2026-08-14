package com.example.data

import kotlinx.coroutines.flow.Flow

class RecordingRepository(private val dao: RecordingDao) {
    val allRecordings: Flow<List<RecordingEntity>> = dao.getAllRecordings()

    suspend fun insert(recording: RecordingEntity): Long = dao.insertRecording(recording)

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun updateTitle(id: Long, newTitle: String) = dao.updateTitle(id, newTitle)
}
