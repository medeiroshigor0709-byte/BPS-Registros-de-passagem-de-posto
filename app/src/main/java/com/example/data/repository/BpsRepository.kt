package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.ChecklistTemplateDao
import com.example.data.local.EmployeeDao
import com.example.data.local.ShiftAttachmentDao
import com.example.data.local.ShiftCameraRecordDao
import com.example.data.local.ShiftDraftDao
import com.example.data.local.ShiftReportDao
import com.example.data.model.CameraMonitoringState
import com.example.data.model.ChecklistTemplateItem
import com.example.data.model.Employee
import com.example.data.model.ShiftAttachment
import com.example.data.model.ShiftCameraRecord
import com.example.data.model.ShiftDraft
import com.example.data.model.ShiftReport
import com.example.util.AttachmentStorageManager
import kotlinx.coroutines.flow.Flow

class BpsRepository(
    private val database: AppDatabase,
    private val employeeDao: EmployeeDao = database.employeeDao(),
    private val templateDao: ChecklistTemplateDao = database.checklistTemplateDao(),
    private val reportDao: ShiftReportDao = database.shiftReportDao(),
    private val draftDao: ShiftDraftDao = database.shiftDraftDao(),
    private val attachmentDao: ShiftAttachmentDao = database.shiftAttachmentDao(),
    private val cameraRecordDao: ShiftCameraRecordDao = database.shiftCameraRecordDao()
) {
    // Employees
    val allEmployees: Flow<List<Employee>> = employeeDao.getAllEmployees()
    val activeEmployees: Flow<List<Employee>> = employeeDao.getActiveEmployees()

    suspend fun saveEmployee(employee: Employee): Long {
        return if (employee.id == 0L) {
            employeeDao.insertEmployee(employee)
        } else {
            employeeDao.updateEmployee(employee)
            employee.id
        }
    }

    suspend fun deleteEmployee(employee: Employee) = employeeDao.deleteEmployee(employee)

    // Checklist Templates
    val allTemplateItems: Flow<List<ChecklistTemplateItem>> = templateDao.getAllTemplateItems()
    val activeTemplateItems: Flow<List<ChecklistTemplateItem>> = templateDao.getActiveTemplateItems()

    suspend fun saveTemplateItem(item: ChecklistTemplateItem): Long {
        return if (item.id == 0L) {
            templateDao.insertItem(item)
        } else {
            templateDao.updateItem(item)
            item.id
        }
    }

    suspend fun deleteTemplateItem(item: ChecklistTemplateItem) = templateDao.deleteItem(item)

    suspend fun resetTemplatesToDefault() {
        templateDao.clearAll()
        val defaultItems = listOf(
            ChecklistTemplateItem(title = "Celular", emoji = "📱", isRequired = true, isCamerasItem = false, orderIndex = 0, category = "Equipamentos"),
            ChecklistTemplateItem(title = "Carregador", emoji = "🔌", isRequired = true, isCamerasItem = false, orderIndex = 1, category = "Equipamentos"),
            ChecklistTemplateItem(title = "Interfone", emoji = "📞", isRequired = true, isCamerasItem = false, orderIndex = 2, category = "Comunicação"),
            ChecklistTemplateItem(title = "Livro de ocorrência", emoji = "📔", isRequired = true, isCamerasItem = false, orderIndex = 3, category = "Documentação"),
            ChecklistTemplateItem(title = "Rádio HTS", emoji = "📻", isRequired = true, isCamerasItem = false, orderIndex = 4, category = "Comunicação"),
            ChecklistTemplateItem(title = "Cerca elétrica", emoji = "🚧", isRequired = true, isCamerasItem = false, orderIndex = 5, category = "Segurança Perimetral"),
            ChecklistTemplateItem(title = "Câmaras", emoji = "🎥", isRequired = true, isCamerasItem = true, orderIndex = 6, category = "CFTV & Monitoramento"),
            ChecklistTemplateItem(title = "Guarda chuva", emoji = "☔️", isRequired = false, isCamerasItem = false, orderIndex = 7, category = "Materiais de Apoio"),
            ChecklistTemplateItem(title = "Guarda Sol", emoji = "⛱️", isRequired = false, isCamerasItem = false, orderIndex = 8, category = "Materiais de Apoio"),
            ChecklistTemplateItem(title = "Controle", emoji = "🕹️", isRequired = true, isCamerasItem = false, orderIndex = 9, category = "Acesso & Portões"),
            ChecklistTemplateItem(title = "Caixa d'água", emoji = "🌊", isRequired = true, isCamerasItem = false, orderIndex = 10, category = "Instalações Prediais"),
            ChecklistTemplateItem(title = "Ar condicionado", emoji = "💨", isRequired = true, isCamerasItem = false, orderIndex = 11, category = "Equipamentos da Portaria"),
            ChecklistTemplateItem(title = "Chaves", emoji = "🗝️", isRequired = true, isCamerasItem = false, orderIndex = 12, category = "Acesso & Chaves"),
            ChecklistTemplateItem(title = "Extintor pó químico", emoji = "🧯", isRequired = true, isCamerasItem = false, orderIndex = 13, category = "Prevenção & Combate")
        )
        templateDao.insertAll(defaultItems)
    }

    // Reports History & Offline Sync Layer
    val allReports: Flow<List<ShiftReport>> = reportDao.getAllReports()
    val pendingReports: Flow<List<ShiftReport>> = reportDao.getPendingSyncReports()
    val pendingCount: Flow<Int> = reportDao.getPendingCount()

    fun searchReports(query: String): Flow<List<ShiftReport>> = reportDao.searchReports(query)

    suspend fun saveReport(report: ShiftReport): Long = reportDao.insertReport(report)

    /**
     * Atomically saves a shift handover report together with its independent 32-camera snapshot
     * (Monitor 1: 16 cameras + Monitor 2: 16 cameras) and any linked observation attachments.
     */
    suspend fun saveReportWithCamerasAndAttachments(
        report: ShiftReport,
        cameraState: CameraMonitoringState,
        draftAttachments: List<ShiftAttachment>,
        clearDraftAfter: Boolean = true
    ): Long {
        return database.withTransaction {
            val snapshotJson = cameraState.toSnapshotJson()
            val finalReport = report.copy(
                cameraWorkingCount = cameraState.toWorkingCountText(),
                monitor1Details = cameraState.toMonitorDetailsText(1),
                monitor2Details = cameraState.toMonitorDetailsText(2),
                cameraStatesJson = snapshotJson,
                attachmentCount = draftAttachments.size
            )
            val newReportId = reportDao.insertReport(finalReport)

            val now = finalReport.timestamp
            val cameraRecords = (cameraState.monitor1 + cameraState.monitor2).map { ch ->
                ShiftCameraRecord(
                    reportId = newReportId,
                    monitorNumber = ch.monitorNumber,
                    cameraNumber = ch.cameraNumber,
                    isWorking = ch.isWorking,
                    recordedAt = now
                )
            }
            cameraRecordDao.insertAll(cameraRecords)

            if (draftAttachments.isNotEmpty()) {
                val linkedAttachments = draftAttachments.map { att ->
                    att.copy(id = 0, reportId = newReportId)
                }
                attachmentDao.insertAttachments(linkedAttachments)
            }

            if (clearDraftAfter) {
                draftDao.clearDraft()
                attachmentDao.deleteDraftAttachments()
            }

            newReportId
        }
    }

    suspend fun getCameraRecordsForReportDirect(reportId: Long): List<ShiftCameraRecord> =
        cameraRecordDao.getCamerasForReportDirect(reportId)

    suspend fun markReportAsSynced(id: Long) = reportDao.markAsSynced(id, System.currentTimeMillis())

    suspend fun markAllAsSynced() = reportDao.markAllAsSynced(System.currentTimeMillis())

    suspend fun deleteReport(report: ShiftReport, context: Context? = null) {
        val attachedPaths = attachmentDao.getAttachmentsForReportDirect(report.id).map { it.filePath }.distinct()
        reportDao.deleteReport(report)
        if (context != null) {
            attachedPaths.forEach { path ->
                val refs = attachmentDao.countReferencesByFilePath(path)
                AttachmentStorageManager.deletePhysicalFileIfUnreferenced(context, path, refs)
            }
        }
    }

    // Observation Attachments (Draft & Per-Report)
    val draftAttachments: Flow<List<ShiftAttachment>> = attachmentDao.getDraftAttachments()

    fun getAttachmentsForReport(reportId: Long): Flow<List<ShiftAttachment>> =
        attachmentDao.getAttachmentsForReport(reportId)

    suspend fun getAttachmentsForReportDirect(reportId: Long): List<ShiftAttachment> =
        attachmentDao.getAttachmentsForReportDirect(reportId)

    suspend fun addDraftAttachment(attachment: ShiftAttachment): Pair<ShiftAttachment, Boolean> {
        val existing = attachmentDao.findDraftAttachmentBySha256(attachment.sha256)
        if (existing != null) {
            return existing to true // Already attached to current draft (no duplicate!)
        }
        val newId = attachmentDao.insertAttachment(attachment.copy(id = 0, reportId = null))
        return attachment.copy(id = newId, reportId = null) to false
    }

    suspend fun addAttachmentToReport(reportId: Long, attachment: ShiftAttachment): Pair<ShiftAttachment, Boolean> {
        val existing = attachmentDao.findReportAttachmentBySha256(reportId, attachment.sha256)
        if (existing != null) {
            return existing to true
        }
        val insertedId = database.withTransaction {
            val id = attachmentDao.insertAttachment(attachment.copy(id = 0, reportId = reportId))
            val newCount = attachmentDao.countAttachmentsForReport(reportId)
            reportDao.updateAttachmentCount(reportId, newCount)
            id
        }
        return attachment.copy(id = insertedId, reportId = reportId) to false
    }

    suspend fun removeAttachment(attachment: ShiftAttachment, context: Context? = null) {
        database.withTransaction {
            attachmentDao.deleteAttachment(attachment)
            val reportId = attachment.reportId
            if (reportId != null) {
                val newCount = attachmentDao.countAttachmentsForReport(reportId)
                reportDao.updateAttachmentCount(reportId, newCount)
            }
        }
        if (context != null) {
            val refs = attachmentDao.countReferencesByFilePath(attachment.filePath)
            AttachmentStorageManager.deletePhysicalFileIfUnreferenced(context, attachment.filePath, refs)
        }
    }

    suspend fun updateReportNotes(reportId: Long, generalNotes: String) {
        val count = attachmentDao.countAttachmentsForReport(reportId)
        reportDao.updateNotesAndAttachmentCount(reportId, generalNotes, count)
    }

    // Draft autosave
    suspend fun saveDraft(draft: ShiftDraft) = draftDao.saveDraft(draft)
    suspend fun getDraftDirect(): ShiftDraft? = draftDao.getDraftDirect()
    suspend fun clearDraft() {
        draftDao.clearDraft()
        attachmentDao.deleteDraftAttachments()
    }

    suspend fun ensureDefaultDataSeeded() {
        if (templateDao.getTemplateCount() == 0) {
            AppDatabase.populateInitialData(database)
        }
    }
}
