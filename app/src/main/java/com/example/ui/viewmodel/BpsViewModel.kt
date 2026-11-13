package com.example.ui.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.AttachmentType
import com.example.data.model.CameraMonitoringState
import com.example.data.model.CameraStatus
import com.example.data.model.ChecklistTemplateItem
import com.example.data.model.Employee
import com.example.data.model.ShiftAttachment
import com.example.data.model.ShiftDraft
import com.example.data.model.ShiftReport
import com.example.data.repository.BpsRepository
import com.example.util.AttachmentStorageManager
import com.example.util.ImageToPdfConverter
import com.example.util.NetworkMonitor
import com.example.util.PdfReportGenerator
import com.example.util.SelectedImageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

data class ItemCheckState(
    val status: String = "OK", // "OK", "ATENCAO", "DEFEITO", "NAO_POSSUI"
    val observation: String = "",
    val photoPath: String? = null
)

enum class MessageFormatStyle(val label: String, val shortDesc: String) {
    TOPICS("Em Tópicos (•)", "Itens em lista com marcadores"),
    LINES("Linhas Simples", "Um item por linha"),
    INLINE("Linha Contínua", "Texto corrido original")
}

data class ShiftFormUiState(
    val greeting: String = "Boa Noite",
    val postName: String = "*PORTARIA*",
    val receivingEmployee: String = "Higor",
    val departingEmployee: String = "",
    val itemStates: Map<Long, ItemCheckState> = emptyMap(),
    val cameraMonitoring: CameraMonitoringState = CameraMonitoringState.allWorking(),
    val camerasWorkingCount: String = CameraMonitoringState.allWorking().toWorkingCountText(),
    val monitor1Details: String = CameraMonitoringState.allWorking().toMonitorDetailsText(1),
    val monitor2Details: String = CameraMonitoringState.allWorking().toMonitorDetailsText(2),
    val generalNotes: String = "",
    val observationAttachments: List<ShiftAttachment> = emptyList(),
    val isCamerasActive: Boolean = true,
    val formatStyle: MessageFormatStyle = MessageFormatStyle.TOPICS
)

data class ImageToPdfUiState(
    val selectedImages: List<SelectedImageItem> = emptyList(),
    val isConverting: Boolean = false,
    val generatedPdfFile: File? = null,
    val generatedPageCount: Int = 0,
    val generatedFileSizeBytes: Long = 0L,
    val statusMessage: String? = null,
    val isError: Boolean = false
)

data class AttachmentFeedback(
    val message: String,
    val isError: Boolean = false
)

class BpsViewModel(
    private val repository: BpsRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), networkMonitor.checkCurrentConnectivity())

    val employees: StateFlow<List<Employee>> = repository.allEmployees
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val templateItems: StateFlow<List<ChecklistTemplateItem>> = repository.allTemplateItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reportsHistory: StateFlow<List<ShiftReport>> = repository.allReports
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingReports: StateFlow<List<ShiftReport>> = repository.pendingReports
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingCount: StateFlow<Int> = repository.pendingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _formState = MutableStateFlow(ShiftFormUiState())
    val formState: StateFlow<ShiftFormUiState> = _formState.asStateFlow()

    private val _imageToPdfState = MutableStateFlow(ImageToPdfUiState())
    val imageToPdfState: StateFlow<ImageToPdfUiState> = _imageToPdfState.asStateFlow()

    private val _attachmentFeedback = MutableStateFlow<AttachmentFeedback?>(null)
    val attachmentFeedback: StateFlow<AttachmentFeedback?> = _attachmentFeedback.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureDefaultDataSeeded()
            restoreDraftIfExists()
            detectAutoGreeting()
        }
        viewModelScope.launch {
            repository.draftAttachments.collect { attachments ->
                _formState.update { it.copy(observationAttachments = attachments) }
            }
        }
    }

    private suspend fun restoreDraftIfExists() {
        val draft = repository.getDraftDirect()
        if (draft != null) {
            val restoredCameras = CameraMonitoringState.fromSnapshotJson(draft.cameraStatesJson)
                ?: CameraMonitoringState.fromLegacyStrings(draft.monitor1Details, draft.monitor2Details)

            _formState.update { current ->
                current.copy(
                    greeting = draft.greeting,
                    postName = draft.postName,
                    receivingEmployee = draft.receivingEmployee,
                    departingEmployee = draft.departingEmployee,
                    cameraMonitoring = restoredCameras,
                    camerasWorkingCount = restoredCameras.toWorkingCountText(),
                    monitor1Details = restoredCameras.toMonitorDetailsText(1),
                    monitor2Details = restoredCameras.toMonitorDetailsText(2),
                    generalNotes = draft.generalNotes
                )
            }
        }
    }

    private fun detectAutoGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val defaultGreeting = when {
            hour in 5..11 -> "Bom Dia"
            hour in 12..17 -> "Boa Tarde"
            else -> "Boa Noite"
        }
        if (_formState.value.greeting.isBlank()) {
            _formState.update { it.copy(greeting = defaultGreeting) }
        }
    }

    fun updateGreeting(greeting: String) {
        _formState.update { it.copy(greeting = greeting) }
        autosaveDraft()
    }

    fun updatePostName(postName: String) {
        _formState.update { it.copy(postName = postName) }
        autosaveDraft()
    }

    fun updateReceivingEmployee(name: String) {
        _formState.update { it.copy(receivingEmployee = name) }
        autosaveDraft()
    }

    fun updateDepartingEmployee(name: String) {
        _formState.update { it.copy(departingEmployee = name) }
        autosaveDraft()
    }

    fun updateItemStatus(itemId: Long, status: String, observation: String = "") {
        _formState.update { state ->
            val currentMap = state.itemStates.toMutableMap()
            val existing = currentMap[itemId]
            currentMap[itemId] = ItemCheckState(
                status = status,
                observation = observation,
                photoPath = existing?.photoPath
            )
            state.copy(itemStates = currentMap)
        }
        autosaveDraft()
    }

    fun attachPhotoToItem(itemId: Long, photoPath: String) {
        _formState.update { state ->
            val currentMap = state.itemStates.toMutableMap()
            val existing = currentMap[itemId] ?: ItemCheckState("ATENCAO")
            currentMap[itemId] = existing.copy(photoPath = photoPath)
            state.copy(itemStates = currentMap)
        }
    }

    fun removePhotoFromItem(itemId: Long) {
        _formState.update { state ->
            val currentMap = state.itemStates.toMutableMap()
            val existing = currentMap[itemId]
            if (existing != null) {
                currentMap[itemId] = existing.copy(photoPath = null)
            }
            state.copy(itemStates = currentMap)
        }
    }

    fun saveBitmapToLocalCache(context: Context, bitmap: Bitmap): String {
        return AttachmentStorageManager.saveChecklistBitmap(context, bitmap)
    }

    fun copyUriToLocalCache(context: Context, uri: Uri): String? {
        return AttachmentStorageManager.copyChecklistUri(context, uri)
    }

    // 32-Camera Monitoring Actions (Monitor 1: 16 cameras + Monitor 2: 16 cameras)
    fun setCameraStatus(monitorNumber: Int, cameraNumber: Int, status: CameraStatus) {
        _formState.update { state ->
            val updatedCameras = state.cameraMonitoring.withCameraStatus(monitorNumber, cameraNumber, status)
            state.copy(
                cameraMonitoring = updatedCameras,
                camerasWorkingCount = updatedCameras.toWorkingCountText(),
                monitor1Details = updatedCameras.toMonitorDetailsText(1),
                monitor2Details = updatedCameras.toMonitorDetailsText(2)
            )
        }
        autosaveDraft()
    }

    fun toggleCameraStatus(monitorNumber: Int, cameraNumber: Int) {
        _formState.update { state ->
            val updatedCameras = state.cameraMonitoring.toggleCamera(monitorNumber, cameraNumber)
            state.copy(
                cameraMonitoring = updatedCameras,
                camerasWorkingCount = updatedCameras.toWorkingCountText(),
                monitor1Details = updatedCameras.toMonitorDetailsText(1),
                monitor2Details = updatedCameras.toMonitorDetailsText(2)
            )
        }
        autosaveDraft()
    }

    fun setAllMonitorCameras(monitorNumber: Int, status: CameraStatus) {
        _formState.update { state ->
            val updatedCameras = state.cameraMonitoring.withAllMonitorStatus(monitorNumber, status)
            state.copy(
                cameraMonitoring = updatedCameras,
                camerasWorkingCount = updatedCameras.toWorkingCountText(),
                monitor1Details = updatedCameras.toMonitorDetailsText(1),
                monitor2Details = updatedCameras.toMonitorDetailsText(2)
            )
        }
        autosaveDraft()
    }

    fun applyAllCamerasWorking() {
        val updatedCameras = CameraMonitoringState.allWorking()
        _formState.update { state ->
            state.copy(
                cameraMonitoring = updatedCameras,
                camerasWorkingCount = updatedCameras.toWorkingCountText(),
                monitor1Details = updatedCameras.toMonitorDetailsText(1),
                monitor2Details = updatedCameras.toMonitorDetailsText(2)
            )
        }
        autosaveDraft()
    }

    fun applyDefaultBpsCameraPreset() {
        val updatedCameras = CameraMonitoringState.defaultBpsPreset()
        _formState.update { state ->
            state.copy(
                cameraMonitoring = updatedCameras,
                camerasWorkingCount = updatedCameras.toWorkingCountText(),
                monitor1Details = updatedCameras.toMonitorDetailsText(1),
                monitor2Details = updatedCameras.toMonitorDetailsText(2)
            )
        }
        autosaveDraft()
    }

    fun updateCamerasInfo(workingCount: String, mon1: String, mon2: String) {
        _formState.update {
            it.copy(
                camerasWorkingCount = workingCount,
                monitor1Details = mon1,
                monitor2Details = mon2
            )
        }
        autosaveDraft()
    }

    fun updateGeneralNotes(notes: String) {
        _formState.update { it.copy(generalNotes = notes) }
        autosaveDraft()
    }

    // Observation Attachments (Draft & History Report editing)
    fun clearAttachmentFeedback() {
        _attachmentFeedback.value = null
    }

    fun attachObservationFiles(context: Context, uris: List<Uri>, type: String) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var addedCount = 0
            var duplicateCount = 0
            var lastError: String? = null

            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    val imported = AttachmentStorageManager.importAttachmentFromUri(context, uri, type)
                    imported.onSuccess { stored ->
                        val (_, wasDuplicate) = repository.addDraftAttachment(
                            ShiftAttachment(
                                reportId = null,
                                type = stored.type,
                                displayName = stored.displayName,
                                filePath = stored.filePath,
                                mimeType = stored.mimeType,
                                sizeBytes = stored.sizeBytes,
                                sha256 = stored.sha256
                            )
                        )
                        if (wasDuplicate) duplicateCount++ else addedCount++
                    }.onFailure { err ->
                        lastError = err.message
                    }
                }
            }

            val label = if (type == AttachmentType.PDF) "PDF(s)" else "foto(s)"
            _attachmentFeedback.value = when {
                lastError != null && addedCount == 0 -> AttachmentFeedback(lastError!!, isError = true)
                addedCount > 0 && duplicateCount > 0 ->
                    AttachmentFeedback("$addedCount $label anexado(s) ($duplicateCount já estava(m) anexado(s)).")
                addedCount > 0 ->
                    AttachmentFeedback("$addedCount $label anexado(s) com armazenamento persistente.")
                duplicateCount > 0 ->
                    AttachmentFeedback("Arquivo(s) já anexado(s) a esta observação.")
                else -> null
            }
        }
    }

    fun removeDraftAttachment(context: Context, attachment: ShiftAttachment) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeAttachment(attachment, context)
            _attachmentFeedback.value = AttachmentFeedback("Anexo '${attachment.displayName}' removido.")
        }
    }

    fun observeAttachmentsForReport(reportId: Long): Flow<List<ShiftAttachment>> =
        repository.getAttachmentsForReport(reportId)

    suspend fun getAttachmentsForReportDirect(reportId: Long): List<ShiftAttachment> =
        repository.getAttachmentsForReportDirect(reportId)

    fun attachFilesToSavedReport(context: Context, reportId: Long, uris: List<Uri>, type: String) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var addedCount = 0
            var duplicateCount = 0
            var lastError: String? = null

            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    val imported = AttachmentStorageManager.importAttachmentFromUri(context, uri, type)
                    imported.onSuccess { stored ->
                        val (_, wasDuplicate) = repository.addAttachmentToReport(
                            reportId = reportId,
                            attachment = ShiftAttachment(
                                reportId = reportId,
                                type = stored.type,
                                displayName = stored.displayName,
                                filePath = stored.filePath,
                                mimeType = stored.mimeType,
                                sizeBytes = stored.sizeBytes,
                                sha256 = stored.sha256
                            )
                        )
                        if (wasDuplicate) duplicateCount++ else addedCount++
                    }.onFailure { err ->
                        lastError = err.message
                    }
                }
            }

            val msg = when {
                lastError != null && addedCount == 0 -> lastError!!
                addedCount > 0 -> "$addedCount anexo(s) adicionado(s) ao registro #$reportId!"
                duplicateCount > 0 -> "Arquivo(s) já vinculado(s) a este registro."
                else -> ""
            }
            if (msg.isNotBlank()) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun removeAttachmentFromSavedReport(context: Context, attachment: ShiftAttachment) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeAttachment(attachment, context)
        }
    }

    fun updateSavedReportNotes(reportId: Long, notes: String, context: Context) {
        viewModelScope.launch {
            repository.updateReportNotes(reportId, notes)
            Toast.makeText(context, "Observações atualizadas no registro!", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun getCameraStateForReport(report: ShiftReport): CameraMonitoringState {
        CameraMonitoringState.fromSnapshotJson(report.cameraStatesJson)?.let { return it }
        val records = repository.getCameraRecordsForReportDirect(report.id)
        CameraMonitoringState.fromRecords(records)?.let { return it }
        return CameraMonitoringState.fromLegacyStrings(report.monitor1Details, report.monitor2Details)
    }

    // Image to PDF Converter Operations
    fun addImagesForPdf(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val validItems = mutableListOf<SelectedImageItem>()
            val unsupportedNames = mutableListOf<String>()

            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    val name = AttachmentStorageManager.resolveDisplayName(context, uri, AttachmentType.PHOTO)
                    val (supported, badge) = ImageToPdfConverter.isSupportedImage(context, uri)
                    if (supported) {
                        val existingUris = _imageToPdfState.value.selectedImages.map { it.uri }
                        if (uri !in existingUris && validItems.none { it.uri == uri }) {
                            validItems.add(
                                SelectedImageItem(
                                    uri = uri,
                                    displayName = name,
                                    formatLabel = badge
                                )
                            )
                        }
                    } else {
                        unsupportedNames.add("$name ($badge)")
                    }
                }
            }

            _imageToPdfState.update { state ->
                val updated = state.selectedImages + validItems
                val message = when {
                    unsupportedNames.isNotEmpty() && validItems.isEmpty() ->
                        "Formato não suportado: ${unsupportedNames.joinToString(", ")}. Aceitos: JPG, JPEG, PNG, WEBP."
                    unsupportedNames.isNotEmpty() ->
                        "${validItems.size} imagem(ns) adicionada(s). Ignorada(s) por formato inválido: ${unsupportedNames.joinToString(", ")}"
                    validItems.isNotEmpty() ->
                        "${validItems.size} imagem(ns) pronta(s) para conversão em PDF."
                    else -> "As imagens selecionadas já estão na lista."
                }
                state.copy(
                    selectedImages = updated,
                    generatedPdfFile = null,
                    statusMessage = message,
                    isError = unsupportedNames.isNotEmpty() && validItems.isEmpty()
                )
            }
        }
    }

    fun removeImageFromPdfQueue(index: Int) {
        _imageToPdfState.update { state ->
            if (index !in state.selectedImages.indices) return@update state
            val mutable = state.selectedImages.toMutableList().apply { removeAt(index) }
            state.copy(
                selectedImages = mutable,
                generatedPdfFile = null,
                statusMessage = null,
                isError = false
            )
        }
    }

    fun moveImageInPdfQueue(fromIndex: Int, toIndex: Int) {
        _imageToPdfState.update { state ->
            if (fromIndex !in state.selectedImages.indices || toIndex !in state.selectedImages.indices) {
                return@update state
            }
            val mutable = state.selectedImages.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            state.copy(selectedImages = mutable, generatedPdfFile = null)
        }
    }

    fun clearImagesForPdf() {
        _imageToPdfState.value = ImageToPdfUiState()
    }

    fun convertSelectedImagesToPdf(context: Context) {
        val currentImages = _imageToPdfState.value.selectedImages
        if (currentImages.isEmpty()) {
            _imageToPdfState.update {
                it.copy(statusMessage = "Selecione ao menos uma imagem antes de converter.", isError = true)
            }
            return
        }

        viewModelScope.launch {
            _imageToPdfState.update {
                it.copy(isConverting = true, statusMessage = null, isError = false)
            }
            val result = withContext(Dispatchers.IO) {
                ImageToPdfConverter.convertImagesToPdf(
                    context = context,
                    imageUris = currentImages.map { it.uri }
                )
            }
            result.onSuccess { pdfResult ->
                _imageToPdfState.update {
                    it.copy(
                        isConverting = false,
                        generatedPdfFile = pdfResult.pdfFile,
                        generatedPageCount = pdfResult.pageCount,
                        generatedFileSizeBytes = pdfResult.fileSizeBytes,
                        statusMessage = "PDF gerado com sucesso! (${pdfResult.pageCount} página(s) • ${AttachmentStorageManager.formatFileSize(pdfResult.fileSizeBytes)})",
                        isError = false
                    )
                }
            }.onFailure { err ->
                _imageToPdfState.update {
                    it.copy(
                        isConverting = false,
                        generatedPdfFile = null,
                        statusMessage = err.message ?: "Erro ao converter imagens em PDF.",
                        isError = true
                    )
                }
            }
        }
    }

    fun saveGeneratedPdfToUri(context: Context, destinationUri: Uri) {
        val file = _imageToPdfState.value.generatedPdfFile ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                ImageToPdfConverter.savePdfToDestinationUri(context, file, destinationUri)
            }
            result.onSuccess {
                _imageToPdfState.update {
                    it.copy(
                        statusMessage = "Documento PDF salvo no dispositivo com sucesso!",
                        isError = false
                    )
                }
                Toast.makeText(context, "PDF salvo no dispositivo com sucesso!", Toast.LENGTH_LONG).show()
            }.onFailure { err ->
                _imageToPdfState.update {
                    it.copy(
                        statusMessage = "Erro ao salvar PDF: ${err.message}",
                        isError = true
                    )
                }
            }
        }
    }

    private fun autosaveDraft() {
        viewModelScope.launch {
            val s = _formState.value
            val draft = ShiftDraft(
                greeting = s.greeting,
                postName = s.postName,
                receivingEmployee = s.receivingEmployee,
                departingEmployee = s.departingEmployee,
                cameraWorkingCount = s.camerasWorkingCount,
                monitor1Details = s.monitor1Details,
                monitor2Details = s.monitor2Details,
                generalNotes = s.generalNotes,
                cameraStatesJson = s.cameraMonitoring.toSnapshotJson()
            )
            repository.saveDraft(draft)
        }
    }

    fun clearFormAndDraft() {
        viewModelScope.launch {
            repository.clearDraft()
            _formState.value = ShiftFormUiState()
            detectAutoGreeting()
        }
    }

    fun updateMessageFormatStyle(style: MessageFormatStyle) {
        _formState.update { it.copy(formatStyle = style) }
    }

    fun buildFormattedMessage(activeItems: List<ChecklistTemplateItem>): String {
        val state = _formState.value
        val sb = StringBuilder()
        val recipient = if (state.receivingEmployee.isNotBlank()) state.receivingEmployee else "Porteiro"

        when (state.formatStyle) {
            MessageFormatStyle.TOPICS -> {
                sb.append("${state.greeting} passo o posto para o porteiro: ${recipient}\n\n")
                sb.append("${state.postName}\n")

                for (item in activeItems) {
                    if (!item.isEnabled) continue

                    if (item.isCamerasItem) {
                        val camWorking = if (state.camerasWorkingCount.isNotBlank()) state.camerasWorkingCount else "OK"
                        sb.append("• ${item.emoji} ${item.title}: ${camWorking}\n")
                        if (state.monitor1Details.isNotBlank()) {
                            sb.append("   ↳ *Monitor* ${state.monitor1Details}\n")
                        }
                        if (state.monitor2Details.isNotBlank()) {
                            sb.append("   ↳ *Monitor* ${state.monitor2Details}\n")
                        }
                    } else {
                        val check = state.itemStates[item.id] ?: ItemCheckState("OK")
                        val statusText = when (check.status) {
                            "OK" -> "*OK*"
                            "ATENCAO" -> if (check.observation.isNotBlank()) "*Obs: ${check.observation}*" else "*Atenção*"
                            "DEFEITO" -> if (check.observation.isNotBlank()) "*Defeito: ${check.observation}*" else "*Fora do ar*"
                            "NAO_POSSUI" -> "*N/A*"
                            else -> "*${check.status}*"
                        }
                        val photoBadge = if (check.photoPath != null) " 📸 [Foto]" else ""
                        sb.append("• ${item.emoji} ${item.title}: $statusText$photoBadge\n")
                    }
                }
            }
            MessageFormatStyle.LINES -> {
                sb.append("${state.greeting} passo o posto para o porteiro: ${recipient}\n\n")
                sb.append("${state.postName}\n")

                for (item in activeItems) {
                    if (!item.isEnabled) continue

                    if (item.isCamerasItem) {
                        val camWorking = if (state.camerasWorkingCount.isNotBlank()) state.camerasWorkingCount else "OK"
                        sb.append("${item.emoji} ${item.title}: ${camWorking}\n")
                        if (state.monitor1Details.isNotBlank()) {
                            sb.append("   ↳ *Monitor* ${state.monitor1Details}\n")
                        }
                        if (state.monitor2Details.isNotBlank()) {
                            sb.append("   ↳ *Monitor* ${state.monitor2Details}\n")
                        }
                    } else {
                        val check = state.itemStates[item.id] ?: ItemCheckState("OK")
                        val statusText = when (check.status) {
                            "OK" -> "*OK*"
                            "ATENCAO" -> if (check.observation.isNotBlank()) "*Obs: ${check.observation}*" else "*Atenção*"
                            "DEFEITO" -> if (check.observation.isNotBlank()) "*Defeito: ${check.observation}*" else "*Fora do ar*"
                            "NAO_POSSUI" -> "*N/A*"
                            else -> "*${check.status}*"
                        }
                        val photoBadge = if (check.photoPath != null) " 📸 [Foto]" else ""
                        sb.append("${item.emoji} ${item.title}: $statusText$photoBadge\n")
                    }
                }
            }
            MessageFormatStyle.INLINE -> {
                sb.append("${state.greeting} passo o posto para o porteiro: ${recipient}    ${state.postName}")
                for (item in activeItems) {
                    if (!item.isEnabled) continue

                    if (item.isCamerasItem) {
                        sb.append(" ")
                        val camText = buildString {
                            append("${item.emoji} ${item.title}: ${state.camerasWorkingCount}")
                            if (state.monitor1Details.isNotBlank()) {
                                append(" *Monitor* ${state.monitor1Details}")
                            }
                            if (state.monitor2Details.isNotBlank()) {
                                append(" *Monitor* ${state.monitor2Details}")
                            }
                        }
                        sb.append(camText)
                    } else {
                        val check = state.itemStates[item.id] ?: ItemCheckState("OK")
                        val statusText = when (check.status) {
                            "OK" -> "*OK*"
                            "ATENCAO" -> if (check.observation.isNotBlank()) "*Obs: ${check.observation}*" else "*Atenção*"
                            "DEFEITO" -> if (check.observation.isNotBlank()) "*Defeito: ${check.observation}*" else "*Fora do ar*"
                            "NAO_POSSUI" -> "*N/A*"
                            else -> "*${check.status}*"
                        }
                        val photoBadge = if (check.photoPath != null) " 📸 [Foto]" else ""
                        sb.append(" ${item.emoji} ${item.title} $statusText$photoBadge")
                    }
                }
            }
        }

        val totalPhotos = state.itemStates.values.count { it.photoPath != null }
        if (totalPhotos > 0) {
            sb.append("\n📸 *$totalPhotos foto(s) de irregularidade anexada(s) ao relatório em PDF*")
        }

        if (state.generalNotes.isNotBlank()) {
            sb.append("\n\n📝 *Ocorrências/Obs:* ${state.generalNotes}")
        }

        if (state.observationAttachments.isNotEmpty()) {
            val obsPhotos = state.observationAttachments.count { it.type == AttachmentType.PHOTO }
            val obsPdfs = state.observationAttachments.count { it.type == AttachmentType.PDF }
            sb.append("\n📎 *Anexos da observação:* $obsPhotos foto(s), $obsPdfs PDF(s)")
        }

        return sb.toString().trimEnd()
    }

    fun validateRequiredItems(activeItems: List<ChecklistTemplateItem>): List<String> {
        val missing = mutableListOf<String>()
        val state = _formState.value
        if (state.receivingEmployee.isBlank()) {
            missing.add("Porteiro que recebe o posto")
        }
        for (item in activeItems) {
            if (item.isEnabled && item.isRequired && !item.isCamerasItem) {
                val check = state.itemStates[item.id]
                if (check != null && check.status == "DEFEITO" && check.observation.isBlank()) {
                    missing.add("${item.emoji} ${item.title} (requer observação de defeito)")
                }
            }
        }
        return missing
    }

    /**
     * Saves a checklist locally in Room even without internet connection (Pending Sync),
     * preserving its 32-camera snapshot and observation attachments independently.
     */
    fun saveReportOffline(formattedMessage: String, context: Context) {
        viewModelScope.launch {
            val state = _formState.value
            val itemPhotosCount = state.itemStates.values.count { it.photoPath != null }
            val obsPhotosCount = state.observationAttachments.count { it.type == AttachmentType.PHOTO }
            val totalPhotos = itemPhotosCount + obsPhotosCount
            val report = ShiftReport(
                greeting = state.greeting,
                postName = state.postName,
                departingEmployee = state.departingEmployee,
                receivingEmployee = state.receivingEmployee,
                reportContent = formattedMessage,
                cameraWorkingCount = state.camerasWorkingCount,
                monitor1Details = state.monitor1Details,
                monitor2Details = state.monitor2Details,
                generalNotes = state.generalNotes,
                isPendingSync = true,
                syncStatus = "PENDENTE",
                hasPhotos = totalPhotos > 0,
                photoCount = totalPhotos
            )
            repository.saveReportWithCamerasAndAttachments(
                report = report,
                cameraState = state.cameraMonitoring,
                draftAttachments = state.observationAttachments,
                clearDraftAfter = true
            )
            Toast.makeText(context, "Checklist, 32 câmeras e anexos salvos localmente!", Toast.LENGTH_LONG).show()
        }
    }

    fun syncPendingReportToWhatsApp(report: ShiftReport, context: Context) {
        sendToWhatsAppInternal(context, report.reportContent, saveHistory = false)
        viewModelScope.launch {
            repository.markReportAsSynced(report.id)
            Toast.makeText(context, "Relatório sincronizado e enviado!", Toast.LENGTH_SHORT).show()
        }
    }

    fun markReportAsSynced(reportId: Long) {
        viewModelScope.launch {
            repository.markReportAsSynced(reportId)
        }
    }

    fun markAllAsSynced(context: Context) {
        viewModelScope.launch {
            repository.markAllAsSynced()
            Toast.makeText(context, "Todos os relatórios foram marcados como sincronizados!", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendToWhatsApp(context: Context, message: String, saveHistory: Boolean = true) {
        sendToWhatsAppInternal(context, message, saveHistory)
    }

    private fun sendToWhatsAppInternal(context: Context, message: String, saveHistory: Boolean) {
        try {
            val encodedMessage = Uri.encode(message)
            val directWhatsappIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?text=$encodedMessage")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(directWhatsappIntent)
            Toast.makeText(context, "Abrindo WhatsApp...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val chooser = Intent.createChooser(shareIntent, "Compartilhar via WhatsApp ou aplicativo:")
                chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(chooser)
            } catch (err: Exception) {
                Toast.makeText(context, "Não foi possível abrir o WhatsApp: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }

        if (saveHistory) {
            saveCurrentReportToHistory(message)
        }
    }

    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Registro BPS Facilities", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Texto copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
    }

    fun saveCurrentReportToHistory(formattedMessage: String) {
        viewModelScope.launch {
            val state = _formState.value
            val itemPhotosCount = state.itemStates.values.count { it.photoPath != null }
            val obsPhotosCount = state.observationAttachments.count { it.type == AttachmentType.PHOTO }
            val totalPhotos = itemPhotosCount + obsPhotosCount
            val report = ShiftReport(
                greeting = state.greeting,
                postName = state.postName,
                departingEmployee = state.departingEmployee,
                receivingEmployee = state.receivingEmployee,
                reportContent = formattedMessage,
                cameraWorkingCount = state.camerasWorkingCount,
                monitor1Details = state.monitor1Details,
                monitor2Details = state.monitor2Details,
                generalNotes = state.generalNotes,
                isPendingSync = false,
                syncStatus = "ENVIADO",
                syncedAt = System.currentTimeMillis(),
                hasPhotos = totalPhotos > 0,
                photoCount = totalPhotos
            )
            repository.saveReportWithCamerasAndAttachments(
                report = report,
                cameraState = state.cameraMonitoring,
                draftAttachments = state.observationAttachments,
                clearDraftAfter = true
            )
        }
    }

    fun deleteReport(report: ShiftReport, context: Context? = null) {
        viewModelScope.launch {
            repository.deleteReport(report, context)
        }
    }

    // PDF Export
    fun exportAndSharePdf(context: Context, activeItems: List<ChecklistTemplateItem>) {
        try {
            val file = PdfReportGenerator.generateShiftPdf(
                context = context,
                formState = _formState.value,
                activeItems = activeItems
            )
            PdfReportGenerator.sharePdf(context, file)
            saveCurrentReportToHistory(buildFormattedMessage(activeItems))
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao gerar PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun generateShiftPdfFile(context: Context, activeItems: List<ChecklistTemplateItem>): File? {
        return try {
            PdfReportGenerator.generateShiftPdf(
                context = context,
                formState = _formState.value,
                activeItems = activeItems
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao gerar PDF: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    fun exportAndViewPdf(context: Context, activeItems: List<ChecklistTemplateItem>) {
        try {
            val file = PdfReportGenerator.generateShiftPdf(
                context = context,
                formState = _formState.value,
                activeItems = activeItems
            )
            PdfReportGenerator.viewPdf(context, file)
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao abrir PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Template Customization Operations
    fun saveTemplateItem(item: ChecklistTemplateItem) {
        viewModelScope.launch {
            repository.saveTemplateItem(item)
        }
    }

    fun deleteTemplateItem(item: ChecklistTemplateItem) {
        viewModelScope.launch {
            repository.deleteTemplateItem(item)
        }
    }

    fun toggleItemRequired(item: ChecklistTemplateItem) {
        viewModelScope.launch {
            repository.saveTemplateItem(item.copy(isRequired = !item.isRequired))
        }
    }

    fun toggleItemEnabled(item: ChecklistTemplateItem) {
        viewModelScope.launch {
            repository.saveTemplateItem(item.copy(isEnabled = !item.isEnabled))
        }
    }

    fun resetTemplateToDefault() {
        viewModelScope.launch {
            repository.resetTemplatesToDefault()
        }
    }

    // Employee Management Operations
    fun saveEmployee(employee: Employee) {
        viewModelScope.launch {
            repository.saveEmployee(employee)
        }
    }

    fun deleteEmployee(employee: Employee) {
        viewModelScope.launch {
            repository.deleteEmployee(employee)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
}

class BpsViewModelFactory(
    private val repository: BpsRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BpsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BpsViewModel(repository, networkMonitor) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
