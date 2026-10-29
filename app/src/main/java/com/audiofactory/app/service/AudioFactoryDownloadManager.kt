package com.audiofactory.app.service

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Toast
import com.audiofactory.app.R
import java.io.File
import java.io.FileOutputStream

class AudioFactoryDownloadManager(private val context: Context) {

    private val downloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /**
     * Handles downloads initiated from the WebView.
     */
    fun enqueueDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ): Boolean {
        return try {
            if (url.startsWith("data:")) {
                handleDataUrlDownload(url, mimeType)
            } else {
                handleHttpDownload(url, userAgent, contentDisposition, mimeType)
            }
        } catch (e: Exception) {
            Toast.makeText(context, R.string.download_failed, Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun handleHttpDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ): Boolean {
        val uri = Uri.parse(url)
        val guessedFileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val finalFileName = if (guessedFileName.isNullOrBlank() || guessedFileName == "downloadfile") {
            "AudioFactory_${System.currentTimeMillis()}.${guessExtension(mimeType)}"
        } else {
            guessedFileName
        }

        val request = DownloadManager.Request(uri).apply {
            setTitle(finalFileName)
            setDescription("Audio Factory Export")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, finalFileName)
            setMimeType(mimeType ?: "audio/mpeg")

            // Forward session cookies so authenticated exports succeed
            val cookies = CookieManager.getInstance().getCookie(url)
            if (!cookies.isNullOrEmpty()) {
                addRequestHeader("Cookie", cookies)
            }
            if (!userAgent.isNullOrEmpty()) {
                addRequestHeader("User-Agent", userAgent)
            }
        }

        downloadManager.enqueue(request)
        Toast.makeText(context, context.getString(R.string.download_started), Toast.LENGTH_SHORT).show()
        return true
    }

    /**
     * Handles Base64-encoded Data URLs produced by client-side audio rendering.
     */
    fun handleDataUrlDownload(dataUrl: String, requestedMimeType: String?): Boolean {
        val commaIndex = dataUrl.indexOf(',')
        if (commaIndex == -1) return false

        val metadata = dataUrl.substring(0, commaIndex)
        val base64Data = dataUrl.substring(commaIndex + 1)

        val resolvedMimeType = if (metadata.contains(";")) {
            metadata.substring(5, metadata.indexOf(';'))
        } else {
            requestedMimeType ?: "audio/wav"
        }

        val extension = guessExtension(resolvedMimeType)
        val fileName = "AudioFactory_${System.currentTimeMillis()}.$extension"

        val bytes = Base64.decode(base64Data, Base64.DEFAULT)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, resolvedMimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(bytes)
                }
                Toast.makeText(context, context.getString(R.string.download_completed), Toast.LENGTH_LONG).show()
                true
            } else {
                false
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { it.write(bytes) }
            Toast.makeText(context, context.getString(R.string.download_completed), Toast.LENGTH_LONG).show()
            true
        }
    }

    private fun guessExtension(mimeType: String?): String {
        return when (mimeType?.lowercase()) {
            "audio/wav", "audio/x-wav" -> "wav"
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/ogg", "application/ogg" -> "ogg"
            "audio/aac" -> "aac"
            "audio/flac" -> "flac"
            "application/json" -> "json"
            else -> "wav"
        }
    }
}
