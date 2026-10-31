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
import com.example.data.model.ChecklistTemplateItem
import com.example.data.model.Employee
import com.example.data.model.ShiftDraft
import com.example.data.model.ShiftReport
import com.example.data.repository.BpsRepository
import com.example.util.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
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
    // Specific camera fields as requested by user
    val camerasWorkingCount: String = "28 funcionando",
    val monitor1Details: String = "1 Câmera 15 fora do ar",
    val monitor2Details: String = "2 Câmeras 11,15 e 16 fora do ar",
    val generalNotes: String = "",
    val isCamerasActive: Boolean = true,
    val formatStyle: MessageFormatStyle = MessageFormatStyle.TOPICS
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureDefaultDataSeeded()
            restoreDraftIfExists()
            detectAutoGreeting()
        }
    }

    private suspend fun restoreDraftIfExists() {
        val draft = repository.getDraftDirect()
        if (draft != null) {
            _formState.update { current ->
                current.copy(
                    greeting = draft.greeting,
                    postName = draft.postName,
                    receivingEmployee = draft.receivingEmployee,
                    departingEmployee = draft.departingEmployee,
                    camerasWorkingCount = draft.cameraWorkingCount,
                    monitor1Details = draft.monitor1Details,
                    monitor2Details = draft.monitor2Details,
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
        val photosDir = File(context.cacheDir, "checklist_photos")
        if (!photosDir.exists()) photosDir.mkdirs()
        val file = File(photosDir, "foto_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return file.absolutePath
    }

    fun copyUriToLocalCache(context: Context, uri: Uri): String? {
        return try {
            val photosDir = File(context.cacheDir, "checklist_photos")
            if (!photosDir.exists()) photosDir.mkdirs()
            val file = File(photosDir, "foto_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
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
                generalNotes = s.generalNotes
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

    /**
     * Builds the formatted message string.
     * When style is TOPICS (default):
     * Places each item on its own separate line in bulleted topics:
     *
     * Boa Noite passo o posto para o porteiro: Higor
     *
     * *PORTARIA*
     * • 📱 Celular: *OK*
     * • 🔌 Carregador: *OK*
     * • 📞 Interfone: *OK*
     * ...
     */
    fun buildFormattedMessage(activeItems: List<ChecklistTemplateItem>): String {
        val state = _formState.value
        val sb = StringBuilder()
        val recipient = if (state.receivingEmployee.isNotBlank()) state.receivingEmployee else "Porteiro"

        when (state.formatStyle) {
            MessageFormatStyle.TOPICS -> {
                // Em tópicos com marcadores (•) - um item embaixo do outro
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
                // Em linhas simples (sem marcador de ponto) - um item embaixo do outro
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
                // Formato original em linha contínua
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

        return sb.toString().trimEnd()
    }

    /**
     * Verifies if any required item is not answered/has issues without observation
     */
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
     * Saves a checklist locally in Room even without internet connection (Pending Sync)
     */
    fun saveReportOffline(formattedMessage: String, context: Context) {
        viewModelScope.launch {
            val state = _formState.value
            val photosCount = state.itemStates.values.count { it.photoPath != null }
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
                hasPhotos = photosCount > 0,
                photoCount = photosCount
            )
            repository.saveReport(report)
            repository.clearDraft()
            Toast.makeText(context, "Checklist salvo localmente no Room! Pronto para envio posterior.", Toast.LENGTH_LONG).show()
        }
    }

    fun syncPendingReportToWhatsApp(report: ShiftReport, context: Context) {
        sendToWhatsApp(context, report.reportContent)
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

    fun sendToWhatsApp(context: Context, message: String) {
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

        // Also save automatically to history as sent
        saveCurrentReportToHistory(message)
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
            val photosCount = state.itemStates.values.count { it.photoPath != null }
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
                hasPhotos = photosCount > 0,
                photoCount = photosCount
            )
            repository.saveReport(report)
            repository.clearDraft()
        }
    }

    fun deleteReport(report: ShiftReport) {
        viewModelScope.launch {
            repository.deleteReport(report)
        }
    }

    // PDF Export
    fun exportAndSharePdf(context: Context, activeItems: List<ChecklistTemplateItem>) {
        try {
            val file = com.example.util.PdfReportGenerator.generateShiftPdf(
                context = context,
                formState = _formState.value,
                activeItems = activeItems
            )
            com.example.util.PdfReportGenerator.sharePdf(context, file)
            saveCurrentReportToHistory(buildFormattedMessage(activeItems))
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao gerar PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun exportAndViewPdf(context: Context, activeItems: List<ChecklistTemplateItem>) {
        try {
            val file = com.example.util.PdfReportGenerator.generateShiftPdf(
                context = context,
                formState = _formState.value,
                activeItems = activeItems
            )
            com.example.util.PdfReportGenerator.viewPdf(context, file)
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
