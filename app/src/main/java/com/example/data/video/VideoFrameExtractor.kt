package com.example.data.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class ExtractedFrame(
    val index: Int,
    val timestampMs: Long,
    val formattedTimestamp: String,
    val bitmap: Bitmap
)

data class VideoExtractionResult(
    val sourceLabel: String,
    val durationMs: Long,
    val resolution: String,
    val frames: List<ExtractedFrame>
)

class VideoFrameExtractor(private val context: Context) {

    suspend fun extractFromVideoUri(
        uri: Uri,
        targetFrameCount: Int = 5
    ): VideoExtractionResult = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri) ?: "Rekaman_Jalan.mp4"
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull()?.coerceAtLeast(1000L) ?: 5000L
            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val resolution = if (!widthStr.isNullOrBlank() && !heightStr.isNullOrBlank()) {
                "${widthStr}x${heightStr}"
            } else {
                "HD"
            }

            val count = targetFrameCount.coerceIn(3, 6)
            val frames = mutableListOf<ExtractedFrame>()
            for (i in 0 until count) {
                val fraction = if (count == 1) 0.5 else (i + 0.5) / count.toDouble()
                val timeMs = (durationMs * fraction).toLong().coerceIn(0L, durationMs)
                val rawBitmap = retriever.getFrameAtTime(
                    timeMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: retriever.getFrameAtTime(timeMs * 1000L)
                if (rawBitmap != null) {
                    val scaled = scaleBitmapForAi(rawBitmap, maxDimension = 720)
                    if (scaled !== rawBitmap) rawBitmap.recycle()
                    frames.add(
                        ExtractedFrame(
                            index = frames.size + 1,
                            timestampMs = timeMs,
                            formattedTimestamp = formatTimestamp(timeMs),
                            bitmap = scaled
                        )
                    )
                }
            }

            if (frames.isEmpty()) {
                throw IllegalStateException("Tidak dapat mengekstrak frame dari file video ini.")
            }

            val durationSec = (durationMs / 1000L).coerceAtLeast(1L)
            VideoExtractionResult(
                sourceLabel = "${displayName.take(60)} (${durationSec} dtk)",
                durationMs = durationMs,
                resolution = resolution,
                frames = frames
            )
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun extractFromImageUri(uri: Uri): VideoExtractionResult = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri) ?: "Snapshot_CCTV.jpg"
        val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: throw IllegalStateException("Gagal membaca gambar pengawasan jalan.")

        val scaled = scaleBitmapForAi(rawBitmap, maxDimension = 960)
        if (scaled !== rawBitmap) rawBitmap.recycle()

        VideoExtractionResult(
            sourceLabel = "${displayName.take(60)} (Frame CCTV)",
            durationMs = 0L,
            resolution = "${scaled.width}x${scaled.height}",
            frames = listOf(
                ExtractedFrame(
                    index = 1,
                    timestampMs = 0L,
                    formattedTimestamp = "00:00",
                    bitmap = scaled
                )
            )
        )
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }

    private fun scaleBitmapForAi(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val ratio = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
        val newW = (width * ratio).toInt().coerceAtLeast(1)
        val newH = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun formatTimestamp(ms: Long): String {
        val totalSec = ms / 1000L
        val mins = totalSec / 60L
        val secs = totalSec % 60L
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}
