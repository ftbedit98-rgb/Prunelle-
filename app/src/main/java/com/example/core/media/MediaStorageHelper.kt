package com.example.core.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object MediaStorageHelper {

    /**
     * Copies a picked photo or video URI into app-private persistent storage (`filesDir/<subdir>`)
     * so wallpapers, avatars, and media survive app restarts and offline mode without broken content URIs.
     */
    suspend fun persistUriToInternalFile(
        context: Context,
        sourceUriString: String?,
        subdir: String,
        fixedNameWithoutExt: String? = null
    ): String? = withContext(Dispatchers.IO) {
        if (sourceUriString.isNullOrBlank()) return@withContext null
        val uri = Uri.parse(sourceUriString)
        if (uri.scheme == "file" && uri.path?.let { File(it).exists() } == true) {
            return@withContext sourceUriString
        }
        runCatching {
            val mime = context.contentResolver.getType(uri).orEmpty()
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.takeIf { it.isNotBlank() }
                ?: when {
                    mime.startsWith("video/") -> "mp4"
                    mime.startsWith("image/") -> "jpg"
                    else -> "bin"
                }
            val dir = File(context.filesDir, subdir).apply { mkdirs() }
            val name = fixedNameWithoutExt?.let { "${it}.$ext" }
                ?: "${subdir}_${UUID.randomUUID().toString().take(10)}.$ext"
            val dest = File(dir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output, bufferSize = 16 * 1024)
                }
            } ?: return@withContext null
            Uri.fromFile(dest).toString()
        }.getOrNull() ?: sourceUriString
    }

    suspend fun saveBitmapToInternalPng(
        context: Context,
        bitmap: Bitmap,
        subdir: String,
        fileName: String
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, subdir).apply { mkdirs() }
            val outFile = File(dir, fileName)
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 96, fos)
            }
            outFile.absolutePath
        }.getOrNull()
    }

    fun loadBitmapFromUriOrPath(context: Context, uriOrPath: String?, maxDim: Int = 1024): Bitmap? {
        if (uriOrPath.isNullOrBlank()) return null
        return runCatching {
            val stream = if (uriOrPath.startsWith("/")) {
                File(uriOrPath).takeIf { it.exists() }?.inputStream()
            } else {
                val uri = Uri.parse(uriOrPath)
                if (uri.scheme == "file" && uri.path != null) {
                    File(uri.path!!).takeIf { it.exists() }?.inputStream()
                } else {
                    context.contentResolver.openInputStream(uri)
                }
            }
            stream?.use { s ->
                val raw = BitmapFactory.decodeStream(s) ?: return null
                if (raw.width <= maxDim && raw.height <= maxDim) {
                    raw
                } else {
                    val scale = minOf(maxDim.toFloat() / raw.width, maxDim.toFloat() / raw.height)
                    Bitmap.createScaledBitmap(
                        raw,
                        (raw.width * scale).toInt().coerceAtLeast(1),
                        (raw.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                }
            }
        }.getOrNull()
    }

    fun shareImageFile(context: Context, filePath: String, chooserTitle: String, textCaption: String? = null): Boolean {
        val file = if (filePath.startsWith("file://")) {
            Uri.parse(filePath).path?.let { File(it) }
        } else {
            File(filePath)
        } ?: return false
        if (!file.exists()) return false

        return runCatching {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                if (!textCaption.isNullOrBlank()) {
                    putExtra(Intent.EXTRA_TEXT, textCaption)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    fun shareFormattedText(context: Context, title: String, text: String): Boolean {
        return runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    fun deletePersistedFileIfOwned(context: Context, uriOrPath: String?) {
        if (uriOrPath.isNullOrBlank()) return
        runCatching {
            val file = if (uriOrPath.startsWith("file://")) {
                Uri.parse(uriOrPath).path?.let { File(it) }
            } else if (uriOrPath.startsWith("/")) {
                File(uriOrPath)
            } else null
            if (file != null && file.canonicalPath.startsWith(context.filesDir.canonicalPath)) {
                file.delete()
            }
        }
    }
}
