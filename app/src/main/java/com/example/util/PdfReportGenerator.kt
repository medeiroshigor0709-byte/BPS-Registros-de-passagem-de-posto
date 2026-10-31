package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.model.ChecklistTemplateItem
import com.example.ui.viewmodel.ItemCheckState
import com.example.ui.viewmodel.ShiftFormUiState
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportGenerator {

    fun generateShiftPdf(
        context: Context,
        formState: ShiftFormUiState,
        activeItems: List<ChecklistTemplateItem>
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 (in points)
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Palette
        val navyBg = Color.rgb(15, 23, 42)
        val goldPrimary = Color.rgb(212, 175, 55)
        val goldLight = Color.rgb(246, 226, 122)
        val textDark = Color.rgb(30, 41, 59)
        val textMuted = Color.rgb(100, 116, 139)
        val lineGray = Color.rgb(226, 232, 240)
        val cardBg = Color.rgb(248, 250, 252)

        val greenOk = Color.rgb(22, 163, 74)
        val orangeWarn = Color.rgb(217, 119, 6)
        val redError = Color.rgb(220, 38, 38)

        // 1. Top Header Banner
        paint.color = navyBg
        canvas.drawRect(0f, 0f, 595f, 90f, paint)

        // Draw gold accent bar at bottom of header
        paint.color = goldPrimary
        canvas.drawRect(0f, 88f, 595f, 91f, paint)

        // Load & draw logo
        try {
            val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bps_official_logo)
            if (logoBitmap != null) {
                val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, 60, 60, true)
                canvas.drawBitmap(scaledLogo, 25f, 15f, null)
            }
        } catch (_: Exception) {}

        // Header Titles
        paint.color = Color.WHITE
        paint.textSize = 18f
        paint.isFakeBoldText = true
        canvas.drawText("BPS FACILITIES", 95f, 38f, paint)

        paint.color = goldLight
        paint.textSize = 11f
        paint.isFakeBoldText = true
        canvas.drawText("RELATÓRIO DE PASSAGEM DE POSTO & CHECKLIST OPERACIONAL", 95f, 55f, paint)

        paint.color = Color.rgb(203, 213, 225)
        paint.textSize = 9f
        paint.isFakeBoldText = false
        val dateFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
        val dateString = dateFormat.format(Date())
        canvas.drawText("Emissão: $dateString | Documento Oficial", 95f, 72f, paint)

        var yPos = 110f

        // 2. Info Cards Block
        paint.color = cardBg
        val infoRect = RectF(25f, yPos, 570f, yPos + 65f)
        canvas.drawRoundRect(infoRect, 8f, 8f, paint)

        paint.color = lineGray
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(infoRect, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        // Metadata Text
        paint.color = textDark
        paint.textSize = 10f
        paint.isFakeBoldText = true
        canvas.drawText("POSTO DE SERVIÇO:", 35f, yPos + 22f, paint)
        paint.isFakeBoldText = false
        canvas.drawText(formState.postName.replace("*", ""), 145f, yPos + 22f, paint)

        paint.isFakeBoldText = true
        canvas.drawText("SAUDAÇÃO:", 35f, yPos + 40f, paint)
        paint.isFakeBoldText = false
        canvas.drawText(formState.greeting, 145f, yPos + 40f, paint)

        paint.isFakeBoldText = true
        canvas.drawText("PORTEIRO QUE RECEBE:", 310f, yPos + 22f, paint)
        paint.isFakeBoldText = false
        paint.color = goldPrimary
        val receiving = if (formState.receivingEmployee.isNotBlank()) formState.receivingEmployee else "Higor"
        canvas.drawText(receiving, 440f, yPos + 22f, paint)

        paint.color = textDark
        paint.isFakeBoldText = true
        canvas.drawText("PORTEIRO QUE ENTREGA:", 310f, yPos + 40f, paint)
        paint.isFakeBoldText = false
        val departing = if (formState.departingEmployee.isNotBlank()) formState.departingEmployee else "Não especificado"
        canvas.drawText(departing, 440f, yPos + 40f, paint)

        yPos += 80f

        // 3. Checklist Table Section Title
        paint.color = textDark
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("ITENS DO CHECKLIST VERIFICADOS", 25f, yPos, paint)

        yPos += 10f

        // Table Header
        paint.color = navyBg
        canvas.drawRoundRect(RectF(25f, yPos, 570f, yPos + 22f), 4f, 4f, paint)

        paint.color = Color.WHITE
        paint.textSize = 9f
        paint.isFakeBoldText = true
        canvas.drawText("ITEM / EQUIPAMENTO", 35f, yPos + 15f, paint)
        canvas.drawText("CATEGORIA", 220f, yPos + 15f, paint)
        canvas.drawText("OBRIGATÓRIO", 330f, yPos + 15f, paint)
        canvas.drawText("STATUS", 420f, yPos + 15f, paint)
        canvas.drawText("OBSERVAÇÃO", 480f, yPos + 15f, paint)

        yPos += 24f

        // Table Rows
        var rowIndex = 0
        for (item in activeItems) {
            if (!item.isEnabled) continue
            if (item.isCamerasItem) continue // handled in dedicated section

            if (rowIndex % 2 == 0) {
                paint.color = Color.rgb(241, 245, 249)
                canvas.drawRect(25f, yPos, 570f, yPos + 20f, paint)
            }

            val check = formState.itemStates[item.id] ?: ItemCheckState("OK")

            // Title
            paint.color = textDark
            paint.textSize = 9f
            paint.isFakeBoldText = false
            val itemLabel = "${item.emoji} ${item.title}"
            canvas.drawText(itemLabel.take(28), 35f, yPos + 14f, paint)

            // Category
            paint.color = textMuted
            canvas.drawText(item.category.take(18), 220f, yPos + 14f, paint)

            // Required
            paint.color = if (item.isRequired) goldPrimary else textMuted
            canvas.drawText(if (item.isRequired) "Sim" else "Não", 330f, yPos + 14f, paint)

            // Status Badge
            val statusColor = when (check.status) {
                "OK" -> greenOk
                "ATENCAO" -> orangeWarn
                "DEFEITO" -> redError
                else -> textMuted
            }
            paint.color = statusColor
            paint.isFakeBoldText = true
            val statusLabel = when (check.status) {
                "OK" -> "OK"
                "ATENCAO" -> "ATENÇÃO"
                "DEFEITO" -> "DEFEITO"
                else -> check.status
            }
            canvas.drawText(statusLabel, 420f, yPos + 14f, paint)

            // Observation
            paint.color = textMuted
            paint.isFakeBoldText = false
            val obs = if (check.observation.isNotBlank()) check.observation else "-"
            canvas.drawText(obs.take(16), 480f, yPos + 14f, paint)

            yPos += 20f
            rowIndex++
        }

        yPos += 15f

        // 4. CFTV Cameras Dedicated Card
        paint.color = cardBg
        val cftvRect = RectF(25f, yPos, 570f, yPos + 60f)
        canvas.drawRoundRect(cftvRect, 8f, 8f, paint)
        paint.color = lineGray
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(cftvRect, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        paint.color = textDark
        paint.textSize = 10f
        paint.isFakeBoldText = true
        canvas.drawText("🎥 MONITORAMENTO CFTV / CÂMERAS", 35f, yPos + 20f, paint)

        paint.textSize = 9f
        paint.isFakeBoldText = false
        canvas.drawText("• Funcionando: ${formState.camerasWorkingCount}", 35f, yPos + 36f, paint)
        canvas.drawText("• Monitor 1: ${formState.monitor1Details}", 35f, yPos + 50f, paint)
        canvas.drawText("• Monitor 2: ${formState.monitor2Details}", 310f, yPos + 50f, paint)

        yPos += 75f

        // 5. General Notes / Occurrences (if any)
        if (formState.generalNotes.isNotBlank()) {
            paint.color = cardBg
            val notesRect = RectF(25f, yPos, 570f, yPos + 40f)
            canvas.drawRoundRect(notesRect, 8f, 8f, paint)
            paint.color = lineGray
            paint.style = Paint.Style.STROKE
            canvas.drawRoundRect(notesRect, 8f, 8f, paint)
            paint.style = Paint.Style.FILL

            paint.color = textDark
            paint.textSize = 9f
            paint.isFakeBoldText = true
            canvas.drawText("📝 OCORRÊNCIAS / OBSERVAÇÕES:", 35f, yPos + 16f, paint)
            paint.isFakeBoldText = false
            canvas.drawText(formState.generalNotes.take(100), 35f, yPos + 30f, paint)

            yPos += 50f
        }

        // 6. Signatures Area
        val signatureY = 740f
        paint.color = textMuted
        paint.strokeWidth = 1f
        // Line for departing guard
        canvas.drawLine(40f, signatureY, 260f, signatureY, paint)
        // Line for receiving guard
        canvas.drawLine(335f, signatureY, 555f, signatureY, paint)

        paint.color = textDark
        paint.textSize = 9f
        paint.isFakeBoldText = true
        canvas.drawText("Assinatura do Porteiro que Entrega", 65f, signatureY + 14f, paint)
        canvas.drawText("Assinatura do Porteiro que Assume", 365f, signatureY + 14f, paint)

        // 7. Footer
        paint.color = textMuted
        paint.textSize = 8f
        paint.isFakeBoldText = false
        canvas.drawText("BPS Facilities - Segurança & Facilities | Documento autenticado via Sistema Operacional de Portaria", 85f, 810f, paint)

        pdfDocument.finishPage(page)

        // Page 2: Photographic Annex (if any items have photos attached)
        val itemsWithPhotos = activeItems.filter {
            val path = formState.itemStates[it.id]?.photoPath
            path != null && File(path).exists()
        }

        if (itemsWithPhotos.isNotEmpty()) {
            val page2Info = PdfDocument.PageInfo.Builder(595, 842, 2).create()
            val page2 = pdfDocument.startPage(page2Info)
            val canvas2: Canvas = page2.canvas

            // Page 2 Header Banner
            paint.color = navyBg
            canvas2.drawRect(0f, 0f, 595f, 65f, paint)
            paint.color = goldPrimary
            canvas2.drawRect(0f, 63f, 595f, 65f, paint)

            paint.color = Color.WHITE
            paint.textSize = 14f
            paint.isFakeBoldText = true
            canvas2.drawText("BPS FACILITIES", 25f, 28f, paint)

            paint.color = goldLight
            paint.textSize = 10f
            canvas2.drawText("ANEXO FOTOGRÁFICO DE IRREGULARIDADES E OCORRÊNCIAS", 25f, 48f, paint)

            for ((idx, item) in itemsWithPhotos.take(4).withIndex()) {
                val check = formState.itemStates[item.id]!!
                val photoPath = check.photoPath!!
                val xPos = if (idx % 2 == 0) 25f else 305f
                val currentY = if (idx < 2) 85f else 430f

                // Draw photo card background
                paint.color = cardBg
                val cardRect = RectF(xPos, currentY, xPos + 265f, currentY + 325f)
                canvas2.drawRoundRect(cardRect, 8f, 8f, paint)
                paint.color = lineGray
                paint.style = Paint.Style.STROKE
                canvas2.drawRoundRect(cardRect, 8f, 8f, paint)
                paint.style = Paint.Style.FILL

                // Load and draw photo
                try {
                    val rawBitmap = BitmapFactory.decodeFile(photoPath)
                    if (rawBitmap != null) {
                        val scaled = Bitmap.createScaledBitmap(rawBitmap, 245, 200, true)
                        canvas2.drawBitmap(scaled, xPos + 10f, currentY + 10f, null)
                    }
                } catch (_: Exception) {}

                // Item info under photo
                paint.color = textDark
                paint.textSize = 11f
                paint.isFakeBoldText = true
                canvas2.drawText("${item.emoji} ${item.title}", xPos + 10f, currentY + 230f, paint)

                val statusLabel = when (check.status) {
                    "OK" -> "OK"
                    "ATENCAO" -> "ATENÇÃO"
                    "DEFEITO" -> "DEFEITO / FORA DO AR"
                    else -> check.status
                }
                paint.color = if (check.status == "DEFEITO") redError else orangeWarn
                paint.textSize = 9f
                canvas2.drawText("STATUS: $statusLabel", xPos + 10f, currentY + 248f, paint)

                paint.color = textDark
                paint.isFakeBoldText = false
                paint.textSize = 9f
                val obsText = if (check.observation.isNotBlank()) "Obs: ${check.observation}" else "Sem observações adicionais"
                canvas2.drawText(obsText.take(35), xPos + 10f, currentY + 266f, paint)
                if (obsText.length > 35) {
                    canvas2.drawText(obsText.substring(35).take(35), xPos + 10f, currentY + 280f, paint)
                }

                paint.color = textMuted
                paint.textSize = 8f
                canvas2.drawText("Evidência registrada no posto", xPos + 10f, currentY + 305f, paint)
            }

            // Footer on page 2
            paint.color = textMuted
            paint.textSize = 8f
            paint.isFakeBoldText = false
            canvas2.drawText("BPS Facilities - Anexo Fotográfico Oficial | Página 2/2", 180f, 810f, paint)

            pdfDocument.finishPage(page2)
        }

        // Write to file
        val outputDir = File(context.cacheDir, "reports")
        if (!outputDir.exists()) outputDir.mkdirs()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val pdfFile = File(outputDir, "BPS_Relatorio_Portaria_$timeStamp.pdf")

        FileOutputStream(pdfFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return pdfFile
    }

    /**
     * Shares the generated PDF file via standard Android share sheet (Email, WhatsApp as document, Drive, etc.)
     */
    fun sharePdf(context: Context, file: File, subject: String = "Relatório de Passagem de Posto - BPS Facilities") {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, "Segue em anexo o relatório em PDF da passagem de posto da BPS Facilities.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            val chooser = Intent.createChooser(shareIntent, "Salvar ou Enviar Relatório PDF:")
            chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao compartilhar PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Opens the generated PDF in any installed PDF viewer
     */
    fun viewPdf(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(viewIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Nenhum leitor de PDF encontrado. Use o botão de compartilhar.", Toast.LENGTH_SHORT).show()
        }
    }
}
