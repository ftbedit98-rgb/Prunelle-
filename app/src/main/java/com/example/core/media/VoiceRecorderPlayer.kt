package com.example.core.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import java.io.File

class VoiceRecorderManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0L

    fun startRecording(): File? {
        stopRecording(discard = true)
        val dir = File(context.filesDir, "voice_notes").apply { mkdirs() }
        val file = File(dir, "voice_${System.currentTimeMillis()}.m4a")
        return runCatching {
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            outputFile = file
            startTimeMs = System.currentTimeMillis()
            file
        }.getOrNull()
    }

    fun stopRecording(discard: Boolean = false): Pair<String, Long>? {
        val file = outputFile
        val duration = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(500L)
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        outputFile = null
        if (discard || file == null || !file.exists()) {
            file?.delete()
            return null
        }
        return file.absolutePath to duration
    }
}

class VoicePlayerManager {
    private var mediaPlayer: MediaPlayer? = null
    var currentlyPlayingId: String? = null
        private set

    fun playOrToggle(
        context: Context,
        messageId: String,
        filePathOrUri: String,
        onCompletion: () -> Unit
    ): Boolean {
        if (currentlyPlayingId == messageId && mediaPlayer?.isPlaying == true) {
            stop()
            return false
        }
        stop()
        return runCatching {
            val player = MediaPlayer().apply {
                val parsed = runCatching { Uri.parse(filePathOrUri) }.getOrNull()
                if (parsed != null && (parsed.scheme == "content" || parsed.scheme == "file")) {
                    setDataSource(context, parsed)
                } else {
                    setDataSource(filePathOrUri)
                }
                setOnCompletionListener {
                    stop()
                    onCompletion()
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    onCompletion()
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
            currentlyPlayingId = messageId
            true
        }.getOrElse {
            stop()
            onCompletion()
            false
        }
    }

    fun stop() {
        runCatching {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
        }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        currentlyPlayingId = null
    }
}

data class LocalVideoMeta(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val bitrateBps: Int
) {
    val durationFormatted: String
        get() {
            val totalSec = (durationMs / 1000L).toInt().coerceAtLeast(0)
            val min = totalSec / 60
            val sec = totalSec % 60
            return if (min > 0) "${min}m ${sec}s" else "${sec}s"
        }

    val resolutionFormatted: String
        get() = if (width > 0 && height > 0) "${width}x${height}" else "Inconnue"
}

object VideoMetadataExtractor {
    fun extract(context: Context, uriString: String): LocalVideoMeta {
        val retriever = MediaMetadataRetriever()
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "content" || uri.scheme == "file") {
                retriever.setDataSource(context, uri)
            } else {
                retriever.setDataSource(uriString)
            }
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull() ?: 0
            LocalVideoMeta(duration, width, height, bitrate)
        } catch (_: Exception) {
            LocalVideoMeta(0L, 0, 0, 0)
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun extractKeyFrames(context: Context, uriString: String, count: Int = 4): List<Bitmap> {
        val retriever = MediaMetadataRetriever()
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "content" || uri.scheme == "file") {
                retriever.setDataSource(context, uri)
            } else {
                retriever.setDataSource(uriString)
            }
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(1000L) ?: 4000L
            val step = durationMs / count.coerceAtLeast(1)
            (0 until count).mapNotNull { idx ->
                val timeUs = (idx * step) * 1000L
                val raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: return@mapNotNull null
                val maxSide = 720
                if (raw.width <= maxSide && raw.height <= maxSide) {
                    raw
                } else {
                    val scale = minOf(maxSide.toFloat() / raw.width, maxSide.toFloat() / raw.height)
                    Bitmap.createScaledBitmap(
                        raw,
                        (raw.width * scale).toInt().coerceAtLeast(1),
                        (raw.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            runCatching { retriever.release() }
        }
    }
}
