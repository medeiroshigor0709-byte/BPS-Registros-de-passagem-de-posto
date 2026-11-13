package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SelectedImageItem(
    val uri: Uri,
    val displayName: String,
    val formatLabel: String,
    val sizeBytes: Long = 0L
)

data class ImageToPdfResult(
    val pdfFile: File,
    val pageCount: Int,
    val fileSizeBytes: Long
)

object ImageToPdfConverter {

    private val SUPPORTED_MIME_TYPES = setOf(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp"
    )

    private val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    private const val A4_SHORT = 595
    private const val A4_LONG = 842
    private const val PAGE_MARGIN = 24f
    private const val MAX_BITMAP_DIMENSION = 2048

    fun isSupportedImage(context: Context, uri: Uri): Pair<Boolean, String> {
        val mime = context.contentResolver.getType(uri)?.lowercase(Locale.ROOT)
        val displayName = AttachmentStorageManager.resolveDisplayName(context, uri, "PHOTO")
        val ext = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)

        if (mime != null && mime in SUPPORTED_MIME_TYPES) {
            return true to formatBadgeFromMimeOrExt(mime, ext)
        }
        if (ext in SUPPORTED_EXTENSIONS) {
            return true to ext.uppercase(Locale.ROOT)
        }

        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
            val detectedMime = opts.outMimeType?.lowercase(Locale.ROOT)
            if (detectedMime != null && detectedMime in SUPPORTED_MIME_TYPES) {
                true to formatBadgeFromMimeOrExt(detectedMime, ext)
            } else {
                false to (detectedMime ?: ext.ifBlank { "DESCONHECIDO" })
            }
        } catch (_: Exception) {
            false to "ERRO"
        }
    }

    private fun formatBadgeFromMimeOrExt(mime: String?, ext: String): String {
        return when {
            mime == "image/png" || ext == "png" -> "PNG"
            mime == "image/webp" || ext == "webp" -> "WEBP"
            mime == "image/jpeg" || mime == "image/jpg" || ext == "jpg" || ext == "jpeg" -> "JPG"
            else -> ext.uppercase(Locale.ROOT).ifBlank { "IMG" }
        }
    }

    /**
     * Converts a list of image URIs offline into a single multi-page PDF document.
     * Each image occupies exactly one page, auto-oriented and fitted without cropping.
     */
    fun convertImagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        outputFileNamePrefix: String = "BPS_Imagens"
    ): Result<ImageToPdfResult> {
        if (imageUris.isEmpty()) {
            return Result.failure(IllegalArgumentException("Selecione pelo menos uma imagem para converter."))
        }

        val bitmaps = mutableListOf<Bitmap>()
        return try {
            imageUris.forEachIndexed { index, uri ->
                val (supported, detectedFormat) = isSupportedImage(context, uri)
                if (!supported) {
                    throw IllegalArgumentException(
                        "Imagem #${index + 1} possui formato não suportado ($detectedFormat). Aceitos: JPG, JPEG, PNG e WEBP."
                    )
                }

                val bitmap = decodeOrientedBitmap(context, uri)
                    ?: throw IllegalStateException("Não foi possível decodificar a imagem #${index + 1}.")
                bitmaps.add(bitmap)
            }

            val outputDir = File(context.filesDir, "generated_pdfs").apply {
                if (!exists()) mkdirs()
            }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val pdfFile = File(outputDir, "${outputFileNamePrefix}_$timestamp.pdf")

            val wroteWithNative = tryWriteWithAndroidPdfDocument(bitmaps, pdfFile)
            if (!wroteWithNative) {
                writeWithPurePdf14Fallback(bitmaps, pdfFile)
            }

            Result.success(
                ImageToPdfResult(
                    pdfFile = pdfFile,
                    pageCount = bitmaps.size,
                    fileSizeBytes = pdfFile.length()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            bitmaps.forEach { bmp ->
                if (!bmp.isRecycled) bmp.recycle()
            }
        }
    }

    private fun tryWriteWithAndroidPdfDocument(bitmaps: List<Bitmap>, pdfFile: File): Boolean {
        val pdfDocument = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        return try {
            bitmaps.forEachIndexed { index, bitmap ->
                val isLandscape = bitmap.width > bitmap.height
                val pageWidth = if (isLandscape) A4_LONG else A4_SHORT
                val pageHeight = if (isLandscape) A4_SHORT else A4_LONG

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                canvas.drawColor(Color.WHITE)

                val availWidth = pageWidth - (PAGE_MARGIN * 2f)
                val availHeight = pageHeight - (PAGE_MARGIN * 2f)
                val scale = minOf(
                    availWidth / bitmap.width.toFloat(),
                    availHeight / bitmap.height.toFloat()
                )

                val drawWidth = bitmap.width * scale
                val drawHeight = bitmap.height * scale
                val left = (pageWidth - drawWidth) / 2f
                val top = (pageHeight - drawHeight) / 2f
                val destRect = RectF(left, top, left + drawWidth, top + drawHeight)

                canvas.drawBitmap(bitmap, null, destRect, paint)
                pdfDocument.finishPage(page)
            }

            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            true
        } catch (_: Throwable) {
            false
        } finally {
            try {
                pdfDocument.close()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Spec-compliant PDF 1.4 multi-page JPEG XObject writer used as a resilient fallback
     * when Android's native PdfDocument backend is unavailable (e.g., JVM unit tests).
     */
    private fun writeWithPurePdf14Fallback(bitmaps: List<Bitmap>, pdfFile: File) {
        val objects = mutableListOf<ByteArray>()

        // Object 1: Catalog (placeholder, updated after knowing page ids)
        // Object 2: Pages (placeholder)
        objects.add(ByteArray(0))
        objects.add(ByteArray(0))

        val pageObjectIds = mutableListOf<Int>()

        bitmaps.forEach { bitmap ->
            val isLandscape = bitmap.width > bitmap.height
            val pageWidth = if (isLandscape) A4_LONG else A4_SHORT
            val pageHeight = if (isLandscape) A4_SHORT else A4_LONG

            val availWidth = pageWidth - (PAGE_MARGIN * 2f)
            val availHeight = pageHeight - (PAGE_MARGIN * 2f)
            val scale = minOf(
                availWidth / bitmap.width.coerceAtLeast(1).toFloat(),
                availHeight / bitmap.height.coerceAtLeast(1).toFloat()
            )
            val drawWidth = bitmap.width * scale
            val drawHeight = bitmap.height * scale
            val left = (pageWidth - drawWidth) / 2f
            val bottom = (pageHeight - drawHeight) / 2f

            val jpegStream = ByteArrayOutputStream()
            // Composite on white background for PNG/WEBP transparency
            val rgbBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(rgbBitmap)
            c.drawColor(Color.WHITE)
            c.drawBitmap(bitmap, 0f, 0f, null)
            rgbBitmap.compress(Bitmap.CompressFormat.JPEG, 92, jpegStream)
            rgbBitmap.recycle()
            val jpegBytes = jpegStream.toByteArray()

            // Image XObject
            val imageObjId = objects.size + 1
            val imgHeader = (
                "<< /Type /XObject /Subtype /Image /Width ${bitmap.width} /Height ${bitmap.height} " +
                    "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpegBytes.size} >>\nstream\n"
                ).toByteArray(Charsets.US_ASCII)
            val imgFooter = "\nendstream".toByteArray(Charsets.US_ASCII)
            objects.add(imgHeader + jpegBytes + imgFooter)

            // Content Stream
            val contentCmd = String.format(
                Locale.US,
                "q\n%.2f 0 0 %.2f %.2f %.2f cm\n/Im0 Do\nQ\n",
                drawWidth,
                drawHeight,
                left,
                bottom
            ).toByteArray(Charsets.US_ASCII)
            val contentObjId = objects.size + 1
            val contentObj = "<< /Length ${contentCmd.size} >>\nstream\n".toByteArray(Charsets.US_ASCII) +
                contentCmd +
                "\nendstream".toByteArray(Charsets.US_ASCII)
            objects.add(contentObj)

            // Page Object
            val pageObjId = objects.size + 1
            val pageObj = (
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $pageWidth $pageHeight] " +
                    "/Resources << /XObject << /Im0 $imageObjId 0 R >> >> /Contents $contentObjId 0 R >>"
                ).toByteArray(Charsets.US_ASCII)
            objects.add(pageObj)
            pageObjectIds.add(pageObjId)
        }

        objects[0] = "<< /Type /Catalog /Pages 2 0 R >>".toByteArray(Charsets.US_ASCII)
        val kids = pageObjectIds.joinToString(" ") { "$it 0 R" }
        objects[1] = "<< /Type /Pages /Kids [$kids] /Count ${pageObjectIds.size} >>".toByteArray(Charsets.US_ASCII)

        FileOutputStream(pdfFile).use { out ->
            val header = "%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n".toByteArray(Charsets.ISO_8859_1)
            out.write(header)
            var offset = header.size
            val offsets = mutableListOf<Int>()

            objects.forEachIndexed { idx, objBytes ->
                offsets.add(offset)
                val objStart = "${idx + 1} 0 obj\n".toByteArray(Charsets.US_ASCII)
                val objEnd = "\nendobj\n".toByteArray(Charsets.US_ASCII)
                out.write(objStart)
                out.write(objBytes)
                out.write(objEnd)
                offset += objStart.size + objBytes.size + objEnd.size
            }

            val xrefOffset = offset
            val xrefBuilder = StringBuilder()
            xrefBuilder.append("xref\n")
            xrefBuilder.append("0 ${objects.size + 1}\n")
            xrefBuilder.append("0000000000 65535 f \n")
            offsets.forEach { off ->
                xrefBuilder.append(String.format(Locale.US, "%010d 00000 n \n", off))
            }
            xrefBuilder.append("trailer\n")
            xrefBuilder.append("<< /Size ${objects.size + 1} /Root 1 0 R >>\n")
            xrefBuilder.append("startxref\n")
            xrefBuilder.append("$xrefOffset\n")
            xrefBuilder.append("%%EOF\n")
            out.write(xrefBuilder.toString().toByteArray(Charsets.US_ASCII))
        }
    }

    fun savePdfToDestinationUri(context: Context, sourcePdf: File, destinationUri: Uri): Result<Unit> {
        return try {
            if (!sourcePdf.exists()) {
                return Result.failure(IllegalStateException("Arquivo PDF temporário não encontrado."))
            }
            val out = context.contentResolver.openOutputStream(destinationUri)
                ?: return Result.failure(IllegalStateException("Não foi possível gravar no destino selecionado."))
            out.use { output ->
                sourcePdf.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun decodeOrientedBitmap(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
            return null
        }

        var inSampleSize = 1
        val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
        while ((maxDim / inSampleSize) > MAX_BITMAP_DIMENSION) {
            inSampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            this.inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val rawBitmap = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

        val orientation = try {
            resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return rawBitmap
        }

        val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        if (rotated !== rawBitmap) {
            rawBitmap.recycle()
        }
        return rotated
    }
}
