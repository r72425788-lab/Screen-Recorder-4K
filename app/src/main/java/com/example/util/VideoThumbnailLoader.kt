package com.example.util

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object VideoThumbnailLoader {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8 // 1/8th of available memory
    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    suspend fun loadThumbnail(filePath: String): Bitmap? = withContext(Dispatchers.IO) {
        if (filePath.isBlank()) return@withContext null

        memoryCache.get(filePath)?.let {
            return@withContext it
        }

        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) return@withContext null

        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            var bitmap: Bitmap? = null
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                try {
                    bitmap = retriever.getScaledFrameAtTime(
                        500_000,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        320,
                        180
                    )
                } catch (ignored: Exception) {}
            }

            if (bitmap == null) {
                val rawBitmap = retriever.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

                if (rawBitmap != null) {
                    val targetWidth = 320
                    val targetHeight = (rawBitmap.height * (targetWidth.toFloat() / rawBitmap.width)).toInt().coerceAtLeast(1)
                    bitmap = Bitmap.createScaledBitmap(rawBitmap, targetWidth, targetHeight, true)
                    if (bitmap != rawBitmap) {
                        rawBitmap.recycle()
                    }
                }
            }

            if (bitmap != null) {
                memoryCache.put(filePath, bitmap)
                return@withContext bitmap
            }
        } catch (e: Exception) {
            // Ignore corrupted or unreadable video frames
        } finally {
            try {
                retriever?.release()
            } catch (ignored: Exception) {}
        }
        return@withContext null
    }
}

@Composable
fun rememberVideoThumbnail(filePath: String): ImageBitmap? {
    val bitmapState = remember(filePath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(filePath) {
        val bmp = VideoThumbnailLoader.loadThumbnail(filePath)
        if (bmp != null) {
            bitmapState.value = bmp.asImageBitmap()
        }
    }

    return bitmapState.value
}
