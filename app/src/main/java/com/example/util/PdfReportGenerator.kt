package com.example.util

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.model.AttachmentType
import com.example.data.model.CameraMonitoringState
import com.example.data.model.ChecklistTemplateItem
import com.example.data.model.ShiftAttachment
import com.example.ui.viewmodel.ItemCheckState
import com.example.ui.viewmodel.ShiftFormUiState
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PdfChecklistRow(
    val title: String,
    val emoji: String,
    val category: String,
    val isRequired: Boolean,
    val statusCode: String,
    val statusLabel: String,
    val observation: String
)

data class PdfAnnexPhoto(
    val title: String,
    val subtitle: String,
    val description: String,
    val path: String,
    val isError: Boolean
)

data class ShiftPdfDocumentPlan(
    val modelName: String,
    val postName: String,
    val greeting: String,
    val departingEmployee: String,
    val receivingEmployee: String,
    val formattedDateTime: String,
    val timestamp: Long,
    val checklistRows: List<PdfChecklistRow>,
    val cameraMonitoring: CameraMonitoringState,
    val camerasWorkingCount: String,
    val monitor1Details: String,
    val monitor2Details: String,
    val notesLines: List<String>,
    val linkedPdfAttachments: List<ShiftAttachment>,
    val linkedPhotoAttachments: List<ShiftAttachment>,
    val annexPhotos: List<PdfAnnexPhoto>,
    val mainPageCount: Int,
    val annexPageCount: Int
) {
    val totalPages: Int
        get() = mainPageCount + annexPageCount
}

object PdfReportGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_X = 25f
    private const val CONTENT_RIGHT = 570f
    private const val MAX_CONTENT_Y = 786f
    private const val FOOTER_Y = 818f
    private const val TABLE_ROW_HEIGHT = 20f
    private const val TABLE_HEADER_HEIGHT = 24f
    private const val SIGNATURE_BLOCK_HEIGHT = 106f

    private sealed class RenderPageAction {
        data object StartFirstPage : RenderPageAction()
        data class StartContinuationPage(val showTableHeader: Boolean) : RenderPageAction()
        data object DrawTableHeader : RenderPageAction()
        data class DrawChecklistRow(val row: PdfChecklistRow, val isStriped: Boolean) : RenderPageAction()
        data object DrawCftvCard : RenderPageAction()
        data object DrawNotesAndAttachmentsCard : RenderPageAction()
        data object DrawNominalSignatures : RenderPageAction()
    }

    /**
     * Validates mandatory nominal identifications and prevents identical departing/receiving names.
     */
    fun validateForPdf(formState: ShiftFormUiState): String? {
        val departing = formState.departingEmployee.trim()
        val receiving = formState.receivingEmployee.trim()
        return when {
            departing.isBlank() ->
                "Preencha o nome do Porteiro que Entrega o Posto antes de gerar o PDF."
            receiving.isBlank() ->
                "Preencha o nome do Porteiro que Assume (Recebe) o Posto antes de gerar o PDF."
            departing.equals(receiving, ignoreCase = true) ->
                "O Porteiro que Entrega e o Porteiro que Assume não podem ser a mesma pessoa."
            else -> null
        }
    }

    /**
     * Builds the deterministic, multi-page layout plan so signatures, 17-item checklist,
     * 32-camera monitoring, observations, attachments and page counters never overlap.
     */
    fun buildPdfPlan(
        formState: ShiftFormUiState,
        activeItems: List<ChecklistTemplateItem>,
        reportTimestamp: Long = System.currentTimeMillis()
    ): ShiftPdfDocumentPlan {
        validateForPdf(formState)?.let { errorMsg ->
            throw IllegalArgumentException(errorMsg)
        }

        val departing = formState.departingEmployee.trim()
        val receiving = formState.receivingEmployee.trim()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"))
        val formattedDateTime = dateFormat.format(Date(reportTimestamp))

        // Operational rows (Câmeras stays integrated into the dedicated 32-camera CFTV section)
        val checklistRows = activeItems
            .filter { it.isEnabled && !it.isCamerasItem }
            .sortedBy { it.orderIndex }
            .map { item ->
                val check = formState.itemStates[item.id] ?: ItemCheckState("OK")
                val statusLabel = when (check.status) {
                    "OK" -> "OK"
                    "ATENCAO" -> "ATENÇÃO"
                    "DEFEITO" -> "DEFEITO"
                    "NAO_POSSUI" -> "N/A"
                    else -> check.status
                }
                PdfChecklistRow(
                    title = item.title,
                    emoji = item.emoji,
                    category = item.category,
                    isRequired = item.isRequired,
                    statusCode = check.status,
                    statusLabel = statusLabel,
                    observation = check.observation
                )
            }

        val obsAttachments = formState.observationAttachments
        val linkedPdfs = obsAttachments.filter { it.type == AttachmentType.PDF }
        val linkedPhotos = obsAttachments.filter { it.type == AttachmentType.PHOTO }

        val notesLines = if (formState.generalNotes.isNotBlank()) {
            wrapText(formState.generalNotes.trim(), maxCharsPerLine = 88)
        } else {
            emptyList()
        }

        val annexPhotos = mutableListOf<PdfAnnexPhoto>()
        activeItems
            .filter { it.isEnabled && !it.isCamerasItem }
            .sortedBy { it.orderIndex }
            .forEach { item ->
                val check = formState.itemStates[item.id]
                val path = check?.photoPath
                if (path != null && File(path).exists()) {
                    val statusLabel = when (check.status) {
                        "OK" -> "OK"
                        "ATENCAO" -> "ATENÇÃO"
                        "DEFEITO" -> "DEFEITO / FORA DO AR"
                        else -> check.status
                    }
                    annexPhotos.add(
                        PdfAnnexPhoto(
                            title = "${item.emoji} ${item.title}",
                            subtitle = "STATUS: $statusLabel",
                            description = if (check.observation.isNotBlank()) "Obs: ${check.observation}" else "Sem observações adicionais",
                            path = path,
                            isError = check.status == "DEFEITO"
                        )
                    )
                }
            }

        linkedPhotos
            .filter { File(it.filePath).exists() }
            .forEach { att ->
                annexPhotos.add(
                    PdfAnnexPhoto(
                        title = "📎 ${att.displayName}",
                        subtitle = "ANEXO DA OBSERVAÇÃO",
                        description = "Fotografia vinculada ao registro (${AttachmentStorageManager.formatFileSize(att.sizeBytes)})",
                        path = att.filePath,
                        isError = false
                    )
                )
            }

        val annexPageCount = if (annexPhotos.isEmpty()) 0 else (annexPhotos.size + 3) / 4

        val tempPlan = ShiftPdfDocumentPlan(
            modelName = ChecklistTemplateItem.DEFAULT_MODEL_NAME,
            postName = formState.postName.replace("*", "").trim().ifBlank { "PORTARIA" },
            greeting = formState.greeting.ifBlank { "Passagem de Posto" },
            departingEmployee = departing,
            receivingEmployee = receiving,
            formattedDateTime = formattedDateTime,
            timestamp = reportTimestamp,
            checklistRows = checklistRows,
            cameraMonitoring = formState.cameraMonitoring,
            camerasWorkingCount = formState.camerasWorkingCount.ifBlank { formState.cameraMonitoring.toWorkingCountText() },
            monitor1Details = formState.monitor1Details.ifBlank { formState.cameraMonitoring.toMonitorDetailsText(1) },
            monitor2Details = formState.monitor2Details.ifBlank { formState.cameraMonitoring.toMonitorDetailsText(2) },
            notesLines = notesLines,
            linkedPdfAttachments = linkedPdfs,
            linkedPhotoAttachments = linkedPhotos,
            annexPhotos = annexPhotos,
            mainPageCount = 1,
            annexPageCount = annexPageCount
        )

        val (_, mainPages) = paginateMainReport(tempPlan)
        return tempPlan.copy(mainPageCount = mainPages)
    }

    private fun computeNotesCardHeight(plan: ShiftPdfDocumentPlan): Float {
        var height = 24f // title + padding
        height += if (plan.notesLines.isNotEmpty()) {
            plan.notesLines.size * 12f + 4f
        } else {
            14f
        }
        if (plan.linkedPhotoAttachments.isNotEmpty() || plan.linkedPdfAttachments.isNotEmpty()) {
            height += 16f
        }
        if (plan.linkedPdfAttachments.isNotEmpty()) {
            height += plan.linkedPdfAttachments.size.coerceAtMost(5) * 11f + 4f
        }
        return height + 8f
    }

    private fun computeCftvCardHeight(plan: ShiftPdfDocumentPlan): Float {
        val m1Offline = plan.cameraMonitoring.monitor1.filter { !it.isWorking }
        val m2Offline = plan.cameraMonitoring.monitor2.filter { !it.isWorking }
        return if (m1Offline.isNotEmpty() || m2Offline.isNotEmpty()) 94f else 82f
    }

    private fun paginateMainReport(plan: ShiftPdfDocumentPlan): Pair<List<RenderPageAction>, Int> {
        val actions = mutableListOf<RenderPageAction>()
        var pageCount = 1
        actions.add(RenderPageAction.StartFirstPage)

        // After top header (91f) + gap (14f) + info card (74f) + gap (14f) + section title (14f)
        var yPos = 207f

        actions.add(RenderPageAction.DrawTableHeader)
        yPos += TABLE_HEADER_HEIGHT

        plan.checklistRows.forEachIndexed { idx, row ->
            if (yPos + TABLE_ROW_HEIGHT > MAX_CONTENT_Y) {
                pageCount++
                actions.add(RenderPageAction.StartContinuationPage(showTableHeader = true))
                yPos = 74f + TABLE_HEADER_HEIGHT
            }
            actions.add(RenderPageAction.DrawChecklistRow(row = row, isStriped = idx % 2 == 0))
            yPos += TABLE_ROW_HEIGHT
        }

        yPos += 12f

        val cftvHeight = computeCftvCardHeight(plan)
        if (yPos + cftvHeight > MAX_CONTENT_Y) {
            pageCount++
            actions.add(RenderPageAction.StartContinuationPage(showTableHeader = false))
            yPos = 74f
        }
        actions.add(RenderPageAction.DrawCftvCard)
        yPos += cftvHeight + 10f

        if (plan.notesLines.isNotEmpty() || plan.linkedPdfAttachments.isNotEmpty() || plan.linkedPhotoAttachments.isNotEmpty()) {
            val notesHeight = computeNotesCardHeight(plan)
            if (yPos + notesHeight > MAX_CONTENT_Y) {
                pageCount++
                actions.add(RenderPageAction.StartContinuationPage(showTableHeader = false))
                yPos = 74f
            }
            actions.add(RenderPageAction.DrawNotesAndAttachmentsCard)
            yPos += notesHeight + 10f
        }

        if (yPos + SIGNATURE_BLOCK_HEIGHT > MAX_CONTENT_Y) {
            pageCount++
            actions.add(RenderPageAction.StartContinuationPage(showTableHeader = false))
        }
        actions.add(RenderPageAction.DrawNominalSignatures)

        return actions to pageCount
    }

    fun generateShiftPdf(
        context: Context,
        formState: ShiftFormUiState,
        activeItems: List<ChecklistTemplateItem>,
        reportTimestamp: Long = System.currentTimeMillis()
    ): File {
        val plan = buildPdfPlan(
            formState = formState,
            activeItems = activeItems,
            reportTimestamp = reportTimestamp
        )

        val outputDir = File(context.filesDir, "generated_pdfs")
        if (!outputDir.exists()) outputDir.mkdirs()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(reportTimestamp))
        val pdfFile = File(outputDir, "BPS_Relatorio_Portaria_$timeStamp.pdf")

        val wroteNative = tryWriteWithNativePdfDocument(context, plan, pdfFile)
        if (!wroteNative || !pdfFile.exists() || pdfFile.length() < 100L) {
            writePurePdf14TextFallback(plan, pdfFile)
        }

        return pdfFile
    }

    private fun tryWriteWithNativePdfDocument(
        context: Context,
        plan: ShiftPdfDocumentPlan,
        pdfFile: File
    ): Boolean {
        val pdfDocument = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val navyBg = Color.rgb(15, 23, 42)
        val goldPrimary = Color.rgb(212, 175, 55)
        val goldDark = Color.rgb(161, 128, 30)
        val goldLight = Color.rgb(246, 226, 122)
        val textDark = Color.rgb(30, 41, 59)
        val textMuted = Color.rgb(100, 116, 139)
        val lineGray = Color.rgb(203, 213, 225)
        val cardBg = Color.rgb(248, 250, 252)
        val greenOk = Color.rgb(22, 163, 74)
        val orangeWarn = Color.rgb(217, 119, 6)
        val redError = Color.rgb(220, 38, 38)

        return try {
            val (actions, _) = paginateMainReport(plan)
            var currentPageNum = 0
            var currentPage: PdfDocument.Page? = null
            var canvas: Canvas? = null
            var yPos = 0f

            fun drawFooter(targetCanvas: Canvas, pageNum: Int) {
                paint.color = lineGray
                paint.strokeWidth = 0.8f
                targetCanvas.drawLine(MARGIN_X, FOOTER_Y - 14f, CONTENT_RIGHT, FOOTER_Y - 14f, paint)
                paint.color = textMuted
                paint.textSize = 8f
                paint.isFakeBoldText = false
                paint.typeface = Typeface.DEFAULT
                targetCanvas.drawText(
                    "BPS Facilities - Segurança & Facilities | Registro Nominal Operacional de Portaria | Página $pageNum/${plan.totalPages}",
                    48f,
                    FOOTER_Y,
                    paint
                )
            }

            fun startNewPage(isFirst: Boolean) {
                currentPage?.let { page ->
                    canvas?.let { c -> drawFooter(c, currentPageNum) }
                    pdfDocument.finishPage(page)
                }
                currentPageNum++
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNum).create()
                val newPage = pdfDocument.startPage(pageInfo)
                currentPage = newPage
                canvas = newPage.canvas
                val c = newPage.canvas

                if (isFirst) {
                    // Full Header Banner
                    paint.color = navyBg
                    c.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 90f, paint)
                    paint.color = goldPrimary
                    c.drawRect(0f, 88f, PAGE_WIDTH.toFloat(), 91f, paint)

                    try {
                        val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bps_official_logo)
                        if (logoBitmap != null) {
                            val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, 58, 58, true)
                            c.drawBitmap(scaledLogo, 25f, 16f, null)
                        }
                    } catch (_: Exception) {}

                    paint.color = Color.WHITE
                    paint.textSize = 18f
                    paint.isFakeBoldText = true
                    c.drawText("BPS FACILITIES", 94f, 36f, paint)

                    paint.color = goldLight
                    paint.textSize = 10.5f
                    paint.isFakeBoldText = true
                    c.drawText("RELATÓRIO DE PASSAGEM DE POSTO & CHECKLIST OPERACIONAL", 94f, 54f, paint)

                    paint.color = Color.rgb(203, 213, 225)
                    paint.textSize = 8.5f
                    paint.isFakeBoldText = false
                    c.drawText("Passagem: ${plan.formattedDateTime} | Modelo: ${plan.modelName}", 94f, 72f, paint)

                    // Identification Info Card
                    yPos = 105f
                    paint.color = cardBg
                    val infoRect = RectF(MARGIN_X, yPos, CONTENT_RIGHT, yPos + 74f)
                    c.drawRoundRect(infoRect, 8f, 8f, paint)
                    paint.color = lineGray
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1f
                    c.drawRoundRect(infoRect, 8f, 8f, paint)
                    paint.style = Paint.Style.FILL

                    // Left metadata
                    paint.color = textDark
                    paint.textSize = 9.5f
                    paint.isFakeBoldText = true
                    c.drawText("POSTO DE SERVIÇO:", 35f, yPos + 20f, paint)
                    paint.isFakeBoldText = false
                    c.drawText(plan.postName.take(24), 145f, yPos + 20f, paint)

                    paint.isFakeBoldText = true
                    c.drawText("SAUDAÇÃO:", 35f, yPos + 39f, paint)
                    paint.isFakeBoldText = false
                    c.drawText(plan.greeting.take(24), 145f, yPos + 39f, paint)

                    paint.isFakeBoldText = true
                    c.drawText("DATA E HORA:", 35f, yPos + 58f, paint)
                    paint.isFakeBoldText = false
                    c.drawText(plan.formattedDateTime, 145f, yPos + 58f, paint)

                    // Right nominal guard identification (Delivery -> Receiving, strict non-inverted order)
                    paint.color = textDark
                    paint.isFakeBoldText = true
                    c.drawText("PORTEIRO QUE ENTREGA:", 295f, yPos + 24f, paint)
                    paint.color = goldDark
                    c.drawText(plan.departingEmployee.take(24), 432f, yPos + 24f, paint)

                    paint.color = textDark
                    c.drawText("PORTEIRO QUE ASSUME:", 295f, yPos + 50f, paint)
                    paint.color = greenOk
                    c.drawText(plan.receivingEmployee.take(24), 432f, yPos + 50f, paint)

                    yPos = 194f
                    paint.color = textDark
                    paint.textSize = 11.5f
                    paint.isFakeBoldText = true
                    c.drawText("ITENS DO CHECKLIST VERIFICADOS (${plan.modelName})", MARGIN_X, yPos, paint)
                    yPos = 207f
                } else {
                    paint.color = navyBg
                    c.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 58f, paint)
                    paint.color = goldPrimary
                    c.drawRect(0f, 56f, PAGE_WIDTH.toFloat(), 58f, paint)
                    paint.color = Color.WHITE
                    paint.textSize = 13f
                    paint.isFakeBoldText = true
                    c.drawText("BPS FACILITIES — CONTINUAÇÃO DO RELATÓRIO", MARGIN_X, 26f, paint)
                    paint.color = goldLight
                    paint.textSize = 9f
                    paint.isFakeBoldText = false
                    c.drawText(
                        "Posto: ${plan.postName} • Entrega: ${plan.departingEmployee} → Assume: ${plan.receivingEmployee} • ${plan.formattedDateTime}",
                        MARGIN_X,
                        44f,
                        paint
                    )
                    yPos = 74f
                }
            }

            fun drawTableHeaderOnCurrent(c: Canvas) {
                paint.color = navyBg
                c.drawRoundRect(RectF(MARGIN_X, yPos, CONTENT_RIGHT, yPos + 22f), 4f, 4f, paint)
                paint.color = Color.WHITE
                paint.textSize = 8.5f
                paint.isFakeBoldText = true
                c.drawText("ITEM / EQUIPAMENTO", 35f, yPos + 14.5f, paint)
                c.drawText("CATEGORIA", 215f, yPos + 14.5f, paint)
                c.drawText("OBRIGATÓRIO", 332f, yPos + 14.5f, paint)
                c.drawText("STATUS", 418f, yPos + 14.5f, paint)
                c.drawText("OBSERVAÇÃO", 482f, yPos + 14.5f, paint)
                yPos += TABLE_HEADER_HEIGHT
            }

            actions.forEach { action ->
                when (action) {
                    RenderPageAction.StartFirstPage -> startNewPage(isFirst = true)
                    is RenderPageAction.StartContinuationPage -> {
                        startNewPage(isFirst = false)
                        if (action.showTableHeader) {
                            canvas?.let { drawTableHeaderOnCurrent(it) }
                        }
                    }
                    RenderPageAction.DrawTableHeader -> {
                        canvas?.let { drawTableHeaderOnCurrent(it) }
                    }
                    is RenderPageAction.DrawChecklistRow -> {
                        val c = canvas!!
                        val row = action.row
                        if (action.isStriped) {
                            paint.color = Color.rgb(241, 245, 249)
                            c.drawRect(MARGIN_X, yPos, CONTENT_RIGHT, yPos + TABLE_ROW_HEIGHT, paint)
                        }

                        paint.color = textDark
                        paint.textSize = 8.8f
                        paint.isFakeBoldText = false
                        c.drawText("${row.emoji} ${row.title}".take(28), 35f, yPos + 13.5f, paint)

                        paint.color = textMuted
                        c.drawText(row.category.take(20), 215f, yPos + 13.5f, paint)

                        paint.color = if (row.isRequired) goldDark else textMuted
                        c.drawText(if (row.isRequired) "Sim" else "Não", 344f, yPos + 13.5f, paint)

                        paint.color = when (row.statusCode) {
                            "OK" -> greenOk
                            "ATENCAO" -> orangeWarn
                            "DEFEITO" -> redError
                            else -> textMuted
                        }
                        paint.isFakeBoldText = true
                        c.drawText(row.statusLabel, 418f, yPos + 13.5f, paint)

                        paint.color = textMuted
                        paint.isFakeBoldText = false
                        val obs = if (row.observation.isNotBlank()) row.observation else "-"
                        c.drawText(obs.take(18), 482f, yPos + 13.5f, paint)

                        yPos += TABLE_ROW_HEIGHT
                    }
                    RenderPageAction.DrawCftvCard -> {
                        yPos += 10f
                        val c = canvas!!
                        val camState = plan.cameraMonitoring
                        val m1Sum = camState.monitor1Summary
                        val m2Sum = camState.monitor2Summary
                        val cardHeight = computeCftvCardHeight(plan)

                        paint.color = cardBg
                        val cftvRect = RectF(MARGIN_X, yPos, CONTENT_RIGHT, yPos + cardHeight)
                        c.drawRoundRect(cftvRect, 8f, 8f, paint)
                        paint.color = lineGray
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 1f
                        c.drawRoundRect(cftvRect, 8f, 8f, paint)
                        paint.style = Paint.Style.FILL

                        paint.color = textDark
                        paint.textSize = 9.5f
                        paint.isFakeBoldText = true
                        c.drawText(
                            "🎥 MONITORAMENTO CFTV — 32 CÂMERAS (${camState.totalWorking}/${camState.totalCameras} funcionando | ${camState.totalOffline} fora do ar)",
                            35f,
                            yPos + 17f,
                            paint
                        )

                        paint.textSize = 8.5f
                        paint.isFakeBoldText = false
                        c.drawText("• Situação Geral: ${plan.camerasWorkingCount}", 35f, yPos + 34f, paint)
                        c.drawText(
                            "• Monitor 1 (${m1Sum.working}/16 OK, ${m1Sum.offline} fora): ${plan.monitor1Details}".take(90),
                            35f,
                            yPos + 50f,
                            paint
                        )
                        c.drawText(
                            "• Monitor 2 (${m2Sum.working}/16 OK, ${m2Sum.offline} fora): ${plan.monitor2Details}".take(90),
                            35f,
                            yPos + 66f,
                            paint
                        )

                        val offlineIds = buildList {
                            camState.monitor1.filter { !it.isWorking }.forEach { add("M1-C${it.cameraNumber.toString().padStart(2, '0')}") }
                            camState.monitor2.filter { !it.isWorking }.forEach { add("M2-C${it.cameraNumber.toString().padStart(2, '0')}") }
                        }
                        if (offlineIds.isNotEmpty()) {
                            paint.color = redError
                            paint.isFakeBoldText = true
                            c.drawText(
                                "• Câmeras fora do ar: ${offlineIds.joinToString(", ")}".take(92),
                                35f,
                                yPos + 82f,
                                paint
                            )
                        }

                        yPos += cardHeight + 8f
                    }
                    RenderPageAction.DrawNotesAndAttachmentsCard -> {
                        val c = canvas!!
                        val cardHeight = computeNotesCardHeight(plan)
                        paint.color = cardBg
                        val notesRect = RectF(MARGIN_X, yPos, CONTENT_RIGHT, yPos + cardHeight)
                        c.drawRoundRect(notesRect, 8f, 8f, paint)
                        paint.color = lineGray
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 1f
                        c.drawRoundRect(notesRect, 8f, 8f, paint)
                        paint.style = Paint.Style.FILL

                        var textY = yPos + 16f
                        paint.color = textDark
                        paint.textSize = 9f
                        paint.isFakeBoldText = true
                        c.drawText("📝 OCORRÊNCIAS / OBSERVAÇÕES:", 35f, textY, paint)
                        textY += 13f

                        paint.isFakeBoldText = false
                        paint.textSize = 8.5f
                        if (plan.notesLines.isNotEmpty()) {
                            plan.notesLines.forEach { line ->
                                c.drawText(line, 35f, textY, paint)
                                textY += 12f
                            }
                        } else {
                            paint.color = textMuted
                            c.drawText("Sem observações textuais adicionais.", 35f, textY, paint)
                            textY += 13f
                        }

                        if (plan.linkedPhotoAttachments.isNotEmpty() || plan.linkedPdfAttachments.isNotEmpty()) {
                            paint.color = textMuted
                            paint.textSize = 8f
                            c.drawText(
                                "📎 Anexos vinculados: ${plan.linkedPhotoAttachments.size} foto(s) e ${plan.linkedPdfAttachments.size} documento(s) PDF",
                                35f,
                                textY + 2f,
                                paint
                            )
                            textY += 13f
                            plan.linkedPdfAttachments.take(5).forEach { pdfAtt ->
                                c.drawText(
                                    "   • PDF: ${pdfAtt.displayName} (${AttachmentStorageManager.formatFileSize(pdfAtt.sizeBytes)})".take(88),
                                    35f,
                                    textY + 2f,
                                    paint
                                )
                                textY += 11f
                            }
                        }

                        yPos += cardHeight + 10f
                    }
                    RenderPageAction.DrawNominalSignatures -> {
                        val c = canvas!!
                        val boxTop = maxOf(yPos + 10f, MAX_CONTENT_Y - SIGNATURE_BLOCK_HEIGHT)
                        val boxHeight = 96f

                        paint.color = textDark
                        paint.textSize = 8.5f
                        paint.isFakeBoldText = true
                        c.drawText("IDENTIFICAÇÃO E ASSINATURA NOMINAL OPERACIONAL", MARGIN_X, boxTop - 4f, paint)

                        // Left Signature Box: Departing Guard (Porteiro que Entrega)
                        drawSignatureCard(
                            canvas = c,
                            paint = paint,
                            left = MARGIN_X,
                            top = boxTop,
                            right = 288f,
                            bottom = boxTop + boxHeight,
                            headerLabel = "ASSINATURA DO PORTEIRO QUE ENTREGA",
                            guardName = plan.departingEmployee,
                            roleCaption = "Responsável pela entrega do posto",
                            dateText = "Confirmado em ${plan.formattedDateTime}",
                            cardBg = cardBg,
                            borderColor = lineGray,
                            accentColor = goldDark,
                            textDark = textDark,
                            textMuted = textMuted
                        )

                        // Right Signature Box: Receiving Guard (Porteiro que Assume)
                        drawSignatureCard(
                            canvas = c,
                            paint = paint,
                            left = 307f,
                            top = boxTop,
                            right = CONTENT_RIGHT,
                            bottom = boxTop + boxHeight,
                            headerLabel = "ASSINATURA DO PORTEIRO QUE ASSUME",
                            guardName = plan.receivingEmployee,
                            roleCaption = "Responsável pelo recebimento do posto",
                            dateText = "Confirmado em ${plan.formattedDateTime}",
                            cardBg = cardBg,
                            borderColor = lineGray,
                            accentColor = greenOk,
                            textDark = textDark,
                            textMuted = textMuted
                        )
                    }
                }
            }

            currentPage?.let { page ->
                canvas?.let { c -> drawFooter(c, currentPageNum) }
                pdfDocument.finishPage(page)
            }

            // Photographic Annex Pages
            if (plan.annexPhotos.isNotEmpty()) {
                plan.annexPhotos.chunked(4).forEach { chunk ->
                    currentPageNum++
                    val annexInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNum).create()
                    val annexPage = pdfDocument.startPage(annexInfo)
                    val c2 = annexPage.canvas

                    paint.color = navyBg
                    c2.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 65f, paint)
                    paint.color = goldPrimary
                    c2.drawRect(0f, 63f, PAGE_WIDTH.toFloat(), 65f, paint)

                    paint.color = Color.WHITE
                    paint.textSize = 14f
                    paint.isFakeBoldText = true
                    c2.drawText("BPS FACILITIES", MARGIN_X, 28f, paint)

                    paint.color = goldLight
                    paint.textSize = 10f
                    c2.drawText("ANEXO FOTOGRÁFICO DE IRREGULARIDADES E OBSERVAÇÕES", MARGIN_X, 48f, paint)

                    chunk.forEachIndexed { idx, photo ->
                        val xPos = if (idx % 2 == 0) 25f else 305f
                        val currentY = if (idx < 2) 85f else 430f

                        paint.color = cardBg
                        val cardRect = RectF(xPos, currentY, xPos + 265f, currentY + 325f)
                        c2.drawRoundRect(cardRect, 8f, 8f, paint)
                        paint.color = lineGray
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 1f
                        c2.drawRoundRect(cardRect, 8f, 8f, paint)
                        paint.style = Paint.Style.FILL

                        try {
                            val rawBitmap = BitmapFactory.decodeFile(photo.path)
                            if (rawBitmap != null) {
                                val boxRect = RectF(xPos + 10f, currentY + 10f, xPos + 255f, currentY + 210f)
                                val scale = minOf(boxRect.width() / rawBitmap.width, boxRect.height() / rawBitmap.height)
                                val dw = rawBitmap.width * scale
                                val dh = rawBitmap.height * scale
                                val left = boxRect.left + (boxRect.width() - dw) / 2f
                                val top = boxRect.top + (boxRect.height() - dh) / 2f
                                c2.drawBitmap(rawBitmap, null, RectF(left, top, left + dw, top + dh), paint)
                                rawBitmap.recycle()
                            }
                        } catch (_: Exception) {}

                        paint.color = textDark
                        paint.textSize = 10.5f
                        paint.isFakeBoldText = true
                        c2.drawText(photo.title.take(28), xPos + 10f, currentY + 230f, paint)

                        paint.color = if (photo.isError) redError else orangeWarn
                        paint.textSize = 9f
                        c2.drawText(photo.subtitle, xPos + 10f, currentY + 248f, paint)

                        paint.color = textDark
                        paint.isFakeBoldText = false
                        paint.textSize = 9f
                        c2.drawText(photo.description.take(35), xPos + 10f, currentY + 266f, paint)
                        if (photo.description.length > 35) {
                            c2.drawText(photo.description.substring(35).take(35), xPos + 10f, currentY + 280f, paint)
                        }

                        paint.color = textMuted
                        paint.textSize = 8f
                        c2.drawText("Evidência registrada no posto", xPos + 10f, currentY + 305f, paint)
                    }

                    drawFooter(c2, currentPageNum)
                    pdfDocument.finishPage(annexPage)
                }
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

    private fun drawSignatureCard(
        canvas: Canvas,
        paint: Paint,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        headerLabel: String,
        guardName: String,
        roleCaption: String,
        dateText: String,
        cardBg: Int,
        borderColor: Int,
        accentColor: Int,
        textDark: Int,
        textMuted: Int
    ) {
        val rect = RectF(left, top, right, bottom)
        paint.color = cardBg
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, 8f, 8f, paint)

        paint.color = borderColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(rect, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        // Top accent bar inside card
        paint.color = accentColor
        canvas.drawRoundRect(RectF(left, top, right, top + 4f), 2f, 2f, paint)

        val centerX = (left + right) / 2f
        val prevAlign = paint.textAlign
        paint.textAlign = Paint.Align.CENTER

        paint.color = textMuted
        paint.textSize = 7.8f
        paint.isFakeBoldText = true
        canvas.drawText(headerLabel, centerX, top + 19f, paint)

        // Nominal Signature Name (prominent, italic-serif or bold style)
        paint.color = textDark
        paint.textSize = 13.5f
        paint.isFakeBoldText = true
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
        canvas.drawText(guardName.take(28), centerX, top + 46f, paint)
        paint.typeface = Typeface.DEFAULT

        // Signature rule line
        paint.color = textMuted
        paint.strokeWidth = 0.9f
        canvas.drawLine(left + 16f, top + 55f, right - 16f, top + 55f, paint)

        paint.color = textDark
        paint.textSize = 8.5f
        paint.isFakeBoldText = true
        canvas.drawText(roleCaption, centerX, top + 70f, paint)

        paint.color = textMuted
        paint.textSize = 7.5f
        paint.isFakeBoldText = false
        canvas.drawText(dateText, centerX, top + 84f, paint)

        paint.textAlign = prevAlign
    }

    /**
     * Spec-compliant pure-Kotlin PDF 1.4 text writer used as a resilient fallback when
     * Android's native PdfDocument backend is unavailable (e.g. JVM unit tests).
     */
    private fun writePurePdf14TextFallback(plan: ShiftPdfDocumentPlan, pdfFile: File) {
        val objects = mutableListOf<ByteArray>()
        objects.add(ByteArray(0)) // 1: Catalog
        objects.add(ByteArray(0)) // 2: Pages
        val fontObjId = 3
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>".toByteArray(Charsets.US_ASCII))

        val pageObjectIds = mutableListOf<Int>()
        val pageLines = buildFallbackPagesText(plan)

        pageLines.forEach { lines ->
            val streamBuilder = StringBuilder()
            streamBuilder.append("BT\n/F1 10 Tf\n")
            var y = 805
            lines.forEach { line ->
                val safeLine = toPdfSafeText(line)
                streamBuilder.append("1 0 0 1 32 $y Tm ($safeLine) Tj\n")
                y -= 14
            }
            streamBuilder.append("ET\n")
            val streamBytes = streamBuilder.toString().toByteArray(Charsets.ISO_8859_1)
            val contentObjId = objects.size + 1
            val contentObj = "<< /Length ${streamBytes.size} >>\nstream\n".toByteArray(Charsets.US_ASCII) +
                streamBytes +
                "\nendstream".toByteArray(Charsets.US_ASCII)
            objects.add(contentObj)

            val pageObjId = objects.size + 1
            val pageObj = (
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $PAGE_WIDTH $PAGE_HEIGHT] " +
                    "/Resources << /Font << /F1 $fontObjId 0 R >> >> /Contents $contentObjId 0 R >>"
                ).toByteArray(Charsets.US_ASCII)
            objects.add(pageObj)
            pageObjectIds.add(pageObjId)
        }

        objects[0] = "<< /Type /Catalog /Pages 2 0 R >>".toByteArray(Charsets.US_ASCII)
        val kids = pageObjectIds.joinToString(" ") { "$it 0 R" }
        objects[1] = "<< /Type /Pages /Kids [$kids] /Count ${pageObjectIds.size} >>".toByteArray(Charsets.US_ASCII)

        FileOutputStream(pdfFile).use { out ->
            val header = "%PDF-1.4\n".toByteArray(Charsets.US_ASCII)
            out.write(header)
            var offset = header.size
            val offsets = mutableListOf<Int>()
            objects.forEachIndexed { idx, bytes ->
                offsets.add(offset)
                val start = "${idx + 1} 0 obj\n".toByteArray(Charsets.US_ASCII)
                val end = "\nendobj\n".toByteArray(Charsets.US_ASCII)
                out.write(start)
                out.write(bytes)
                out.write(end)
                offset += start.size + bytes.size + end.size
            }
            val xrefOffset = offset
            val xref = StringBuilder().apply {
                append("xref\n0 ${objects.size + 1}\n")
                append("0000000000 65535 f \n")
                offsets.forEach { append(String.format(Locale.US, "%010d 00000 n \n", it)) }
                append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\n")
                append("startxref\n$xrefOffset\n%%EOF\n")
            }
            out.write(xref.toString().toByteArray(Charsets.US_ASCII))
        }
    }

    private fun buildFallbackPagesText(plan: ShiftPdfDocumentPlan): List<List<String>> {
        val allLines = mutableListOf<String>()
        allLines.add("BPS FACILITIES - RELATORIO DE PASSAGEM DE POSTO")
        allLines.add("Modelo: ${plan.modelName} | Passagem: ${plan.formattedDateTime}")
        allLines.add("Posto: ${plan.postName} | Saudacao: ${plan.greeting}")
        allLines.add("PORTEIRO QUE ENTREGA: ${plan.departingEmployee}")
        allLines.add("PORTEIRO QUE ASSUME: ${plan.receivingEmployee}")
        allLines.add("------------------------------------------------------------")
        allLines.add("ITENS DO CHECKLIST VERIFICADOS:")
        plan.checklistRows.forEach { row ->
            val obs = if (row.observation.isNotBlank()) " | Obs: ${row.observation}" else ""
            allLines.add("- ${row.title} [${row.category}] | Obrig: ${if (row.isRequired) "Sim" else "Nao"} | ${row.statusLabel}$obs")
        }
        allLines.add("------------------------------------------------------------")
        val cam = plan.cameraMonitoring
        allLines.add("MONITORAMENTO CFTV (32 CAMERAS): ${cam.totalWorking}/${cam.totalCameras} funcionando | ${cam.totalOffline} fora do ar")
        allLines.add("Monitor 1 (${cam.monitor1Summary.working}/16 OK, ${cam.monitor1Summary.offline} fora): ${plan.monitor1Details}")
        allLines.add("Monitor 2 (${cam.monitor2Summary.working}/16 OK, ${cam.monitor2Summary.offline} fora): ${plan.monitor2Details}")
        if (plan.notesLines.isNotEmpty() || plan.linkedPdfAttachments.isNotEmpty() || plan.linkedPhotoAttachments.isNotEmpty()) {
            allLines.add("------------------------------------------------------------")
            allLines.add("OCORRENCIAS / OBSERVACOES:")
            plan.notesLines.forEach { allLines.add(it) }
            if (plan.linkedPhotoAttachments.isNotEmpty() || plan.linkedPdfAttachments.isNotEmpty()) {
                allLines.add("Anexos: ${plan.linkedPhotoAttachments.size} foto(s), ${plan.linkedPdfAttachments.size} PDF(s)")
                plan.linkedPdfAttachments.forEach { allLines.add("  PDF: ${it.displayName}") }
            }
        }
        allLines.add("------------------------------------------------------------")
        allLines.add("ASSINATURA DO PORTEIRO QUE ENTREGA: ${plan.departingEmployee} (Responsavel pela entrega do posto)")
        allLines.add("ASSINATURA DO PORTEIRO QUE ASSUME: ${plan.receivingEmployee} (Responsavel pelo recebimento do posto)")
        allLines.add("BPS Facilities - Registro Nominal Operacional de Portaria")

        val pages = allLines.chunked(48)
        return pages.mapIndexed { idx, page ->
            page + listOf("Pagina ${idx + 1}/${pages.size}")
        }
    }

    private fun toPdfSafeText(input: String): String {
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        val ascii = decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^\\x20-\\x7E]"), "")
        return ascii
            .replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)")
    }

    private fun wrapText(text: String, maxCharsPerLine: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val lines = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            var remaining = paragraph.trim()
            if (remaining.isEmpty()) return@forEach
            while (remaining.length > maxCharsPerLine) {
                val splitAt = remaining.lastIndexOf(' ', maxCharsPerLine).let { idx ->
                    if (idx > maxCharsPerLine / 3) idx else maxCharsPerLine
                }
                lines.add(remaining.substring(0, splitAt).trim())
                remaining = remaining.substring(splitAt).trim()
            }
            if (remaining.isNotEmpty()) {
                lines.add(remaining)
            }
        }
        return lines
    }

    fun buildPdfShareIntent(
        context: Context,
        file: File,
        subject: String = "Relatório de Passagem de Posto - BPS Facilities"
    ): Intent {
        require(file.exists() && file.length() > 0L) {
            "Arquivo PDF inválido ou inexistente para compartilhamento."
        }
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Shares the generated PDF file exclusively via Android native share sheet (WhatsApp, Email, Drive, etc.)
     * using ACTION_SEND + application/pdf + FileProvider temporary read permission.
     */
    fun sharePdf(
        context: Context,
        file: File,
        subject: String = "Relatório de Passagem de Posto - BPS Facilities"
    ): Boolean {
        return try {
            val shareIntent = buildPdfShareIntent(context, file, subject)
            val chooser = Intent.createChooser(shareIntent, "Enviar PDF pelo WhatsApp ou outro aplicativo:").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao compartilhar PDF: ${e.message}", Toast.LENGTH_LONG).show()
            false
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
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            context.startActivity(viewIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Nenhum leitor de PDF encontrado. Use o botão de compartilhar.", Toast.LENGTH_SHORT).show()
        }
    }
}
