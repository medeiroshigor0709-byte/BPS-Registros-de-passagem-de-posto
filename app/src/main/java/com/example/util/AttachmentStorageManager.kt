package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.model.AttachmentType
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

data class StoredAttachmentFile(
    val type: String,
    val displayName: String,
    val filePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String
)

object AttachmentStorageManager {

    private const val ATTACHMENTS_DIR = "shift_attachments"
    private const val CHECKLIST_PHOTOS_DIR = "checklist_photos"

    fun getAttachmentsDir(context: Context): File {
        return File(context.filesDir, ATTACHMENTS_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    fun getChecklistPhotosDir(context: Context): File {
        return File(context.filesDir, CHECKLIST_PHOTOS_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Copies a photo or PDF from a content Uri into persistent private storage (`filesDir/shift_attachments`),
     * using SHA-256 content addressing to avoid file duplication on disk.
     */
    fun importAttachmentFromUri(
        context: Context,
        uri: Uri,
        expectedType: String
    ): Result<StoredAttachmentFile> {
        return try {
            val resolver = context.contentResolver
            val rawMime = resolver.getType(uri)?.lowercase(Locale.ROOT)
            val displayName = resolveDisplayName(context, uri, expectedType)

            val ext = resolveExtension(displayName, rawMime, expectedType)
            if (expectedType == AttachmentType.PDF && ext != "pdf") {
                return Result.failure(IllegalArgumentException("O arquivo selecionado não é um documento PDF válido."))
            }
            if (expectedType == AttachmentType.PHOTO && ext !in setOf("jpg", "jpeg", "png", "webp")) {
                return Result.failure(IllegalArgumentException("Formato de imagem não suportado. Use JPG, JPEG, PNG ou WEBP."))
            }

            val dir = getAttachmentsDir(context)
            val tempFile = File(dir, "tmp_${System.nanoTime()}.$ext")
            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L
            val headerBytes = ByteArray(12)
            var headerRead = 0

            val inputStream = resolver.openInputStream(uri)
                ?: return Result.failure(IllegalStateException("Não foi possível abrir o arquivo selecionado."))

            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        if (headerRead < headerBytes.size) {
                            val toCopy = minOf(read, headerBytes.size - headerRead)
                            System.arraycopy(buffer, 0, headerBytes, headerRead, toCopy)
                            headerRead += toCopy
                        }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        totalBytes += read
                    }
                }
            }

            if (totalBytes <= 0L) {
                tempFile.delete()
                return Result.failure(IllegalArgumentException("O arquivo selecionado está vazio."))
            }

            if (expectedType == AttachmentType.PDF) {
                val headerStr = String(headerBytes, 0, minOf(headerRead, 5), Charsets.US_ASCII)
                if (!headerStr.startsWith("%PDF")) {
                    tempFile.delete()
                    return Result.failure(IllegalArgumentException("O arquivo '$displayName' não possui cabeçalho PDF válido."))
                }
            }

            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            val normalizedExt = if (ext == "jpeg") "jpg" else ext
            val finalFile = File(dir, "$sha256.$normalizedExt")

            if (finalFile.exists() && finalFile.length() == totalBytes) {
                // Content-addressed deduplication: reuse existing physical file
                tempFile.delete()
            } else {
                if (finalFile.exists()) finalFile.delete()
                if (!tempFile.renameTo(finalFile)) {
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                }
            }

            val mimeType = when (expectedType) {
                AttachmentType.PDF -> "application/pdf"
                else -> rawMime ?: when (normalizedExt) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    else -> "image/jpeg"
                }
            }

            Result.success(
                StoredAttachmentFile(
                    type = expectedType,
                    displayName = displayName,
                    filePath = finalFile.absolutePath,
                    mimeType = mimeType,
                    sizeBytes = finalFile.length(),
                    sha256 = sha256
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun saveChecklistBitmap(context: Context, bitmap: Bitmap): String {
        val dir = getChecklistPhotosDir(context)
        val file = File(dir, "foto_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file.absolutePath
    }

    fun copyChecklistUri(context: Context, uri: Uri): String? {
        return importAttachmentFromUri(context, uri, AttachmentType.PHOTO).getOrNull()?.filePath
    }

    fun deletePhysicalFileIfUnreferenced(context: Context, filePath: String, remainingReferences: Int) {
        if (remainingReferences > 0) return
        try {
            val file = File(filePath)
            val attachmentsDir = getAttachmentsDir(context).canonicalPath
            if (file.exists() && file.canonicalPath.startsWith(attachmentsDir)) {
                file.delete()
            }
        } catch (_: Exception) {}
    }

    fun resolveDisplayName(context: Context, uri: Uri, expectedType: String): String {
        var name: String? = null
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx)
                }
            }
        } catch (_: Exception) {}

        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment?.substringAfterLast('/')
        }
        return if (!name.isNullOrBlank()) {
            name!!
        } else {
            val ts = System.currentTimeMillis()
            if (expectedType == AttachmentType.PDF) "documento_$ts.pdf" else "foto_$ts.jpg"
        }
    }

    private fun resolveExtension(displayName: String, mimeType: String?, expectedType: String): String {
        val extFromName = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (expectedType == AttachmentType.PDF) {
            return if (mimeType == "application/pdf" || extFromName == "pdf") "pdf" else extFromName
        }
        return when {
            mimeType == "image/png" || extFromName == "png" -> "png"
            mimeType == "image/webp" || extFromName == "webp" -> "webp"
            mimeType == "image/jpeg" || mimeType == "image/jpg" || extFromName == "jpg" || extFromName == "jpeg" -> "jpg"
            mimeType?.startsWith("image/") == true -> "jpg"
            else -> extFromName
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format(Locale("pt", "BR"), "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
