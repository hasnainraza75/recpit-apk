package com.example.util

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageDownloadManager {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun saveBase64Image(context: Context, dataUrl: String, suggestedFilename: String? = null) {
        Thread {
            try {
                val commaIndex = dataUrl.indexOf(",")
                val header = if (commaIndex > 0) dataUrl.substring(0, commaIndex) else ""
                val base64Data = if (commaIndex > 0) dataUrl.substring(commaIndex + 1) else dataUrl

                // Determine mime type and extension
                val mimeType = when {
                    header.contains("image/jpeg") || header.contains("image/jpg") -> "image/jpeg"
                    header.contains("image/webp") -> "image/webp"
                    header.contains("application/pdf") -> "application/pdf"
                    else -> "image/png"
                }

                val extension = when (mimeType) {
                    "image/jpeg" -> ".jpg"
                    "image/webp" -> ".webp"
                    "application/pdf" -> ".pdf"
                    else -> ".png"
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val cleanFilename = if (!suggestedFilename.isNullOrBlank() && suggestedFilename.contains(".")) {
                    suggestedFilename
                } else if (!suggestedFilename.isNullOrBlank()) {
                    "$suggestedFilename$extension"
                } else {
                    "Receipt_$timestamp$extension"
                }

                // Decode verbatim raw bytes to preserve exact pixel quality without compression
                val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)

                val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveToMediaStoreQ(context, imageBytes, cleanFilename, mimeType)
                } else {
                    saveToLegacyStorage(context, imageBytes, cleanFilename, mimeType)
                }

                mainHandler.post {
                    if (success) {
                        Toast.makeText(
                            context,
                            "Receipt saved successfully to Photos/Downloads ($cleanFilename)",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(context, "Failed to save receipt image", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mainHandler.post {
                    Toast.makeText(context, "Error saving receipt: ${e.localizedMessage ?: "Unknown"}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun saveToMediaStoreQ(
        context: Context,
        bytes: ByteArray,
        filename: String,
        mimeType: String
    ): Boolean {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ReceiptMakerPro")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, contentValues) ?: return false

        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(bytes)
                outputStream.flush()
            }

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
            return true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return false
        }
    }

    private fun saveToLegacyStorage(
        context: Context,
        bytes: ByteArray,
        filename: String,
        mimeType: String
    ): Boolean {
        return try {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val appDir = File(picturesDir, "ReceiptMakerPro")
            if (!appDir.exists()) {
                appDir.mkdirs()
            }
            val targetFile = File(appDir, filename)
            FileOutputStream(targetFile).use { out ->
                out.write(bytes)
                out.flush()
            }

            // Trigger MediaScanner so it instantly appears in gallery
            MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                arrayOf(mimeType),
                null
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun downloadHttpFile(
        context: Context,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimetype: String?
    ) {
        try {
            val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                if (!mimetype.isNullOrBlank()) {
                    setMimeType(mimetype)
                }
                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrBlank()) {
                    addRequestHeader("cookie", cookies)
                }
                if (!userAgent.isNullOrBlank()) {
                    addRequestHeader("User-Agent", userAgent)
                }
                setDescription("Downloading receipt...")
                setTitle(filename)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            }

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            dm?.enqueue(request)
            Toast.makeText(context, "Downloading $filename...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(browserIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "Unable to process download", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
